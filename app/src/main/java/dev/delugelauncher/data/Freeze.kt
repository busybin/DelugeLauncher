// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import dev.delugelauncher.BuildConfig
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/**
 * The four ways an app can be put out of the way.
 * Declaration order is the menu order: the gentlest method that needs no special right first.
 */
enum class FreezeMethod {
    /** `am force-stop`: one-shot kill; nothing is persisted to undo. */
    STOP,

    /** `pm suspend`: still listed, greyed, cannot be opened. */
    SUSPEND,

    /** `pm disable-user`: gone from the launcher and settings, and no component may run. */
    DISABLE,

    /** `pm hide`: gone from launcher and settings like an uninstall, data kept. Root only. */
    HIDE,
}

/**
 * Which privileged identity may run a freeze.
 *
 * Chosen per method, not per app: one batch suspend over Shizuku's binder is far faster than
 * a root shell per package, while hiding only exists on root or the device owner. Unfreezing
 * ignores this on purpose and sweeps every channel — a method is a speed choice, being stuck
 * frozen is not.
 */
enum class FreezeChannel { AUTO, SHIZUKU, ROOT, OWNER }

/** What the device gives this app to work with. */
enum class FreezeAccess { ROOT, SHIZUKU, DEVICE_OWNER, NONE }

/** Which root implementation answered `su`. */
enum class RootProvider { MAGISK, KERNELSU, APATCH, SUPERSU, OTHER, NONE }

/** The permission picture the freeze screen reports. */
data class FreezeStatus(
    /**
     * Whether a real probe has run. Until it has, every field below is a placeholder, and
     * no screen may read a false value as "not installed" or "no access".
     */
    val checked: Boolean = false,
    val access: FreezeAccess = FreezeAccess.NONE,
    /** Root may exist without being the channel in use: hiding apps needs it either way. */
    val rootGranted: Boolean = false,
    val rootProvider: RootProvider = RootProvider.NONE,
    val rootVersion: String? = null,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuAuthorized: Boolean = false,
    /** The manager's package, so a fork is named instead of being called "Shizuku". */
    val shizukuManagerPackage: String? = null,
    /** The manager's own versionName (Shevery 14.6.0), not the binder API level. */
    val shizukuManagerVersion: String? = null,
    /** The Shizuku binder API level. */
    val shizukuVersion: Int = 0,
    /** The admin component is enabled; a plain device admin can still lock the screen. */
    val deviceAdmin: Boolean = false,
    /** This app owns the phone, so the suspend and hide policy calls answer. */
    val deviceOwner: Boolean = false,
) {
    /**
     * Whether a method can run with the channels this phone offers. Being this app's device
     * owner adds suspend and hide; disabling and force-stopping still need a shell.
     */
    fun supports(method: FreezeMethod): Boolean = when (method) {
        FreezeMethod.SUSPEND -> access != FreezeAccess.NONE
        FreezeMethod.HIDE -> deviceOwner || rootGranted
        FreezeMethod.DISABLE, FreezeMethod.STOP -> rootGranted || shizukuAuthorized
    }

    /**
     * The same question for an app in a specific profile.
     *
     * A device owner only manages the profile its process runs in, so offering it a
     * work-profile hide or suspend is a tab that can never work. Root and Shizuku can act on
     * another profile with `--user`; hiding still needs root.
     */
    /** What a picker may offer: everything until the first probe has answered. */
    fun offered(method: FreezeMethod, userSerial: Long = 0L): Boolean =
        !checked || supports(method, userSerial)

    fun supports(method: FreezeMethod, userSerial: Long): Boolean {
        if (!checked) return true
        if (userSerial == 0L) return supports(method)
        val shell = rootGranted || shizukuAuthorized
        return when (method) {
            FreezeMethod.HIDE -> rootGranted
            FreezeMethod.DISABLE, FreezeMethod.STOP, FreezeMethod.SUSPEND -> shell
        }
    }
}

/** How one freeze or unfreeze ended, so the caller can say why instead of failing silently. */
enum class FreezeOutcome {
    DONE,
    NO_ACCESS,
    SYSTEM_APPS_OFF,
    NOT_LISTED,
    FAILED,
    DISABLED,

    /** Permanent, and the caller only lifts automatically: the flag kept it frozen. */
    SKIPPED,

    /** One of the apps that keeps the phone usable: keyboard, home, Shizuku or root. */
    PROTECTED,
}

/** What a whole-list run managed. */
data class FreezeBatchResult(
    val done: Int = 0,
    val failed: Int = 0,
    val noAccess: Boolean = false,
    /**
     * How many of the requested apps were left alone because they are not on the freeze list.
     * Only a [FreezeManager.freezeMany] run with `listedOnly` fills this in: a folder menu must
     * not silently put apps nobody listed onto the list and freeze them.
     */
    val skipped: Int = 0,
    /**
     * How many apps a bulk lift deliberately left frozen because they are permanent. Counted
     * against the record, not the flag: a permanent flag on an app that was not frozen is not
     * an app "kept frozen".
     */
    val keptPermanent: Int = 0,
)

/**
 * Reads a package state into a yes/no answer for the freeze paths.
 *
 * The one rule both directions share: a state the device would not hand over is **not** an
 * answer. Treating "unknown" as held made a whole freeze run a silent no-op, and treating it
 * as free dropped the record of an app that was still frozen.
 */
internal object FreezeVerdict {
    fun held(state: FreezeAppState?, parkedByPlatform: Boolean): Boolean {
        state ?: return false
        if (!state.installed) return false
        if (state.hidden || state.suspended) return true
        return !state.enabled && !parkedByPlatform
    }

    fun free(state: FreezeAppState?, suspended: Boolean, parkedByPlatform: Boolean): Boolean {
        state ?: return false
        // Parked by the platform ("disabled until used"): a shell cannot change this state,
        // so claiming the app is free would leave it disabled behind a record that says it was
        // lifted. Report it as still held and let the lift attempt say why.
        if (parkedByPlatform) return false
        if (!state.installed) return true
        // Hiding is its own flag and an enabled app can still be hidden, so this one cannot
        // answer "free" for it: only the hide probe can.
        if (state.hidden) return false
        if (!state.enabled && !parkedByPlatform) return false
        return !suspended
    }
}

/** The shell commands behind a freeze, kept pure so they can be tested without a device. */
object FreezeCommands {

    /** Package names are the only thing interpolated into a shell command, so refuse anything else. */
    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")

    fun isPackageName(value: String): Boolean = PACKAGE_NAME.matches(value)

    /**
     * `--user` for a profile other than the one the shell starts in.
     *
     * The main profile needs none: the shell itself runs there, and every list written before
     * profiles were freezable must keep producing the exact commands it always did.
     */
    private fun userOption(userId: Int): String = if (userId == 0) "" else " --user $userId"

    /** Runs in order; the force stop is what makes the state change take effect at once. */
    fun freeze(packageName: String, method: FreezeMethod, userId: Int = 0): List<String> {
        if (!isPackageName(packageName)) return emptyList()
        val stop = "am force-stop${userOption(userId)} $packageName"
        return when (method) {
            FreezeMethod.DISABLE -> listOf(stop, "pm disable-user --user $userId $packageName")
            FreezeMethod.HIDE -> listOf(stop, "pm hide${userOption(userId)} $packageName")
            FreezeMethod.SUSPEND -> listOf(
                stop,
                "appops set${userOption(userId)} $packageName RUN_ANY_IN_BACKGROUND ignore",
                "appops set${userOption(userId)} $packageName RUN_IN_BACKGROUND ignore",
                "pm suspend --user $userId $packageName",
            )
            FreezeMethod.STOP -> listOf(stop)
        }
    }

    /**
     * Lifts a platform park. `DISABLED_UNTIL_USED` means "enabled on first use", and the
     * system does exactly that when the app is launched, which a shell may do even though it
     * may not change the state itself. The start is immediately followed by a stop so the
     * app does not stay on screen for an unfreeze the user did not ask to open.
     */
    fun unpark(packageName: String, userId: Int = 0): List<String> {
        if (!isPackageName(packageName)) return emptyList()
        return listOf(
            "am start -n \$(cmd package resolve-activity --brief $packageName | tail -1)",
            "am force-stop${userOption(userId)} $packageName",
        )
    }

    /** The exact reverse of [freeze], plus every other reverse for the unfreeze-all fallback. */
    fun unfreeze(packageName: String, method: FreezeMethod, userId: Int = 0): List<String> {
        if (!isPackageName(packageName)) return emptyList()
        return when (method) {
            FreezeMethod.DISABLE -> listOf("pm enable --user $userId $packageName")
            FreezeMethod.HIDE -> listOf("pm unhide${userOption(userId)} $packageName")
            FreezeMethod.SUSPEND -> listOf(
                "appops set${userOption(userId)} $packageName RUN_IN_BACKGROUND allow",
                "appops set${userOption(userId)} $packageName RUN_ANY_IN_BACKGROUND allow",
                "pm unsuspend --user $userId $packageName",
            )
            FreezeMethod.STOP -> emptyList()
        }
    }

    /**
     * Lifts every known kind of freeze, whatever the stored method says.
     *
     * The stored method can be wrong — the app may have been frozen with another method on
     * another build, or by another tool — and then "unfreeze" following only that method
     * leaves it stuck. Each command is harmless when the app is not in that state.
     */
    fun unfreezeEverything(packageName: String, userId: Int = 0): List<String> {
        if (!isPackageName(packageName)) return emptyList()
        return listOf(
            "pm enable --user $userId $packageName",
            "pm unhide${userOption(userId)} $packageName",
            "appops set${userOption(userId)} $packageName RUN_IN_BACKGROUND allow",
            "appops set${userOption(userId)} $packageName RUN_ANY_IN_BACKGROUND allow",
            "pm unsuspend --user $userId $packageName",
        )
    }

    /**
     * The same unsuspend, run as the shell uid.
     *
     * A pause is keyed by the identity that made it, and `pm` names its caller. A package
     * paused while the manager ran as shell keeps that record after the manager switches to
     * root; the root call then answers cleanly and changes nothing. Root can still clear it
     * by dropping to the shell uid, which is the only other name `pm` writes.
     */
    fun unfreezeAsShell(packageName: String, userId: Int = 0): List<String> {
        if (!isPackageName(packageName)) return emptyList()
        return listOf("su 2000 -c \"pm unsuspend --user $userId $packageName\"")
    }
}

/**
 * Which apps a whole-list run still has to touch, kept pure so the rule can be pinned down.
 *
 * A run used to walk the entire list every time, so "freeze all" took as long with one app
 * left as with a hundred and seventy. The state reads here are local, so skipping the apps
 * already where they belong costs almost nothing.
 */
object FreezeSelection {

    /**
     * The apps whose freeze still has to run. A STOP is always repeated: it persists nothing,
     * so whatever process it killed can have come back since.
     */
    fun toFreeze(
        plan: Map<String, FreezeMethod>,
        skip: Set<String>,
        isFrozen: (String) -> Boolean,
        /**
         * Keys the device does not have at all. They stay on the list through an uninstall,
         * but a run can neither freeze nor claim to have failed on them.
         */
        absent: Set<String> = emptySet(),
    ): List<String> = plan.entries
        .filter { (packageName, method) ->
            packageName !in skip && packageName !in absent &&
                (method == FreezeMethod.STOP || !isFrozen(packageName))
        }
        .map { it.key }

    /**
     * The apps a whole-list lift has to touch: anything the device still reports as held,
     * plus a HIDE record because that state cannot be read back. A DISABLE or SUSPEND record
     * on an app the platform reports as free is stale — a reinstall leaves exactly that —
     * and asking for access to lift nothing would block the list on a device without root.
     * A STOP app has nothing to undo and is left out unless it is genuinely held.
     *
     * [permanent] keys are never lifted: the flag means "only a single unfreeze may touch
     * me", and a single [FreezeManager.unfreeze] does not come through here.
     */
    fun toLift(
        candidates: Collection<String>,
        applied: Set<String>,
        plan: Map<String, FreezeMethod>,
        locked: Set<String> = emptySet(),
        permanent: Set<String> = emptySet(),
        isFrozen: (String) -> Boolean,
    ): List<String> = candidates.filter { packageName ->
        if (packageName in locked || packageName in permanent) return@filter false
        val method = plan[packageName]
        val frozen = isFrozen(packageName)
        if (method == FreezeMethod.STOP && !frozen) false
        else frozen || (packageName in applied && method == FreezeMethod.HIDE)
    }

    /**
     * The members of a group action that are already on the freeze list.
     *
     * Freezing a folder used to list every member under SUSPEND on its way through, so tapping
     * "freeze" on a folder quietly grew the list by apps nobody had chosen. [listedOnly] keeps
     * the run to what the list already describes; the rest are counted, not touched.
     */
    fun listedOnlyTargets(
        members: List<String>,
        plan: Map<String, FreezeMethod>,
    ): List<String> = members.filter { it in plan }

    /**
     * What a listed-only group run will touch, and how many it leaves alone.
     *
     * Kept here rather than in the suspend run so the counting rule — a duplicate member is one
     * app, a skipped one is not a failure — can be read and tested without a device.
     */
    fun listedOnlyBatch(
        members: List<String>,
        plan: Map<String, FreezeMethod>,
    ): ListedBatch {
        val unique = members.distinct()
        val targets = listedOnlyTargets(unique, plan)
        return ListedBatch(targets = targets, skipped = unique.size - targets.size)
    }

    /** [targets] to freeze and how many members the run deliberately left alone. */
    data class ListedBatch(val targets: List<String>, val skipped: Int)

    /**
     * Whether a lift under [method] can be confirmed by reading the device afterwards.
     *
     * Hiding has no public state to read, so there a clean command is the only answer there
     * is; without this the lift was believed to have happened and its command never ran.
     */
    fun verifiable(method: FreezeMethod?): Boolean = method != FreezeMethod.HIDE
}

/** One app in one profile, as the freeze list names it. */
data class FreezeTarget(
    val packageName: String,
    val userId: Int,
    val userSerial: Long,
) {
    val key: String get() = FreezeTargetKey.of(packageName, userSerial)
}

/**
 * How the freeze list names an app: the bare package name in the profile the launcher runs
 * in, and package plus `|u<serial>` for any other. That is the encoding favorites and renames
 * already use, so every list written before profiles were freezable keeps its keys.
 */
object FreezeTargetKey {
    fun of(packageName: String, userSerial: Long): String = EntryKeys.app(packageName, userSerial)

    /** A package name cannot contain a bar, so the suffix starts at the first one. */
    fun packageName(key: String): String = key.substringBefore('|')

    fun userSerial(key: String): Long = EntryKeys.userSerial(key)
}

/** One app's state in one profile, as the package manager reports it. */
data class FreezeAppState(
    val enabled: Boolean = true,
    val hidden: Boolean = false,
    val suspended: Boolean = false,
    val stopped: Boolean = false,
    val installed: Boolean = true,
    val system: Boolean = false,
    val uid: Int = -1,
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * Every app's state at one instant, read with one package manager call.
 *
 * The freeze paths used to ask two questions per app (`getApplicationInfo` and
 * `isPackageSuspended`), so a list of 170 apps paid 340 round trips on every screen-off and
 * every lift. `getInstalledPackages` answers all of it — enabled, hidden, suspended, stopped,
 * installed, system, uid, install and update times — in one call for the whole device. The
 * main profile is read this way; another profile is read per package through [LauncherApps]
 * only when the freeze list happens to name one of its apps.
 */
class FreezeSnapshot private constructor(
    private val context: Context,
    private val main: Map<String, FreezeAppState>,
) {

    private val others = mutableMapOf<String, FreezeAppState?>()
    private val othersLock = Any()

    /**
     * Whether the main-profile read succeeded and simply does not contain this package.
     * An empty map is a failed read, not proof that every app is gone.
     */
    fun isKnownAbsent(target: FreezeTarget): Boolean =
        target.userSerial == 0L && main.isNotEmpty() && target.packageName !in main

    /**
     * Whether the app is not on the device at all, in this profile or any other. The freeze
     * list keeps an app through its uninstall, but a run has nothing to do for it until it
     * is back.
     */
    fun isOffDevice(target: FreezeTarget): Boolean =
        isKnownAbsent(target) || (target.userSerial != 0L && state(target) == null)

    fun state(target: FreezeTarget): FreezeAppState? {
        if (target.userSerial == 0L) return main[target.packageName]
        val key = "${target.userSerial}/${target.packageName}"
        // Parallel freeze work reads other profiles at the same time.
        synchronized(othersLock) {
            if (others.containsKey(key)) return others[key]
            val value = profileState(target)
            others[key] = value
            return value
        }
    }

    private fun profileState(target: FreezeTarget): FreezeAppState? = runCatching {
        val user = context.getSystemService(UserManager::class.java)
            .getUserForSerialNumber(target.userSerial) ?: return null
        val info = context.getSystemService(LauncherApps::class.java)
            .getApplicationInfo(target.packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES, user)
        FreezeAppState(
            enabled = info.enabled,
            hidden = FreezeState.isHidden(info),
            suspended = info.flags and ApplicationInfo.FLAG_SUSPENDED != 0,
            stopped = info.flags and ApplicationInfo.FLAG_STOPPED != 0,
            installed = info.flags and ApplicationInfo.FLAG_INSTALLED != 0,
            system = info.flags and FreezeState.SYSTEM_FLAGS != 0,
            uid = info.uid,
        )
    }.getOrNull()

    companion object {
        fun read(context: Context): FreezeSnapshot =
            FreezeSnapshot(context, FreezeState.mainStates(context))
    }
}

/** Reads the live state the package manager reports for an app. */
object FreezeState {

    private const val MATCH_UNINSTALLED = PackageManager.MATCH_UNINSTALLED_PACKAGES

    internal const val SYSTEM_FLAGS =
        ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP

    /**
     * The whole main profile in one call.
     *
     * `FLAG_SUSPENDED` is filled per user by the platform itself (`PackageParser` copies the
     * user state into the flags), so the slow per-package `isPackageSuspended` question is
     * not needed for the list. Installed-but-hidden apps come back too, which is the whole
     * point of matching uninstalled packages.
     */
    fun mainStates(context: Context): Map<String, FreezeAppState> = runCatching {
        context.packageManager.getInstalledPackages(MATCH_UNINSTALLED).mapNotNull { info ->
            val ai = info.applicationInfo ?: return@mapNotNull null
            info.packageName to FreezeAppState(
                enabled = ai.enabled,
                hidden = isHidden(ai),
                suspended = ai.flags and ApplicationInfo.FLAG_SUSPENDED != 0,
                stopped = ai.flags and ApplicationInfo.FLAG_STOPPED != 0,
                installed = ai.flags and ApplicationInfo.FLAG_INSTALLED != 0,
                system = ai.flags and SYSTEM_FLAGS != 0,
                uid = ai.uid,
                installedAt = info.firstInstallTime,
                updatedAt = info.lastUpdateTime,
            )
        }.toMap()
    }.getOrDefault(emptyMap())

    fun applicationInfo(context: Context, packageName: String): ApplicationInfo? = runCatching {
        context.packageManager.getApplicationInfo(packageName, MATCH_UNINSTALLED)
    }.getOrNull()

    /** The same for an app in another profile; only that profile's launcher can answer. */
    fun applicationInfo(context: Context, packageName: String, userSerial: Long): ApplicationInfo? {
        if (userSerial == 0L) return applicationInfo(context, packageName)
        return runCatching {
            val user = context.getSystemService(UserManager::class.java)
                .getUserForSerialNumber(userSerial) ?: return null
            context.getSystemService(LauncherApps::class.java)
                .getApplicationInfo(packageName, MATCH_UNINSTALLED, user)
        }.getOrNull()
    }

    /** The activity a launcher would start, which is what a stored row has to name. */
    fun launcherComponent(context: Context, packageName: String): ComponentName? = runCatching {
        context.packageManager.getLaunchIntentForPackage(packageName)?.component
            ?: context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(packageName),
                PackageManager.MATCH_DISABLED_COMPONENTS or
                    PackageManager.MATCH_UNINSTALLED_PACKAGES,
            ).firstOrNull()?.activityInfo?.let { ComponentName(packageName, it.name) }
    }.getOrNull()

    /** A preinstalled app, including one updated in place. */
    fun isSystemApp(context: Context, packageName: String): Boolean {
        val flags = applicationInfo(context, packageName)?.flags ?: return false
        return flags and SYSTEM_FLAGS != 0
    }

    /**
     * Whether the platform itself is holding the app in "disabled until used".
     *
     * Restored and long-unused apps land there. The first launch clears it on its own, and
     * until that happens no shell caller may change the state — `pm enable` and
     * `pm default-state` both answer with a SecurityException, so neither Shizuku nor an adb
     * shell can lift it. Counting it as a freeze leaves the app with a lift that can never
     * succeed and a tap that never launches anything.
     */
    fun isParkedForUse(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationEnabledSetting(packageName) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
    }.getOrDefault(false)

    fun isFrozen(context: Context, packageName: String, method: FreezeMethod): Boolean {
        val info = applicationInfo(context, packageName) ?: return false
        return when (method) {
            FreezeMethod.DISABLE -> !info.enabled
            FreezeMethod.HIDE -> isHidden(info)
            // The ApplicationInfo flag is not reliably carried, and the package manager's own
            // answer is what the rest of the app already believes.
            FreezeMethod.SUSPEND -> runCatching {
                context.packageManager.isPackageSuspended(packageName)
            }.getOrDefault(false)
            FreezeMethod.STOP ->
                info.flags and ApplicationInfo.FLAG_STOPPED == ApplicationInfo.FLAG_STOPPED
        }
    }

    /**
     * `PRIVATE_FLAG_HIDDEN` has no public accessor. Reflection is best-effort: null means the
     * field could not be read, which is different from "visible" and must not be treated as a
     * confirmed lift.
     */
    fun hiddenState(info: ApplicationInfo): Boolean? = runCatching {
        val field = ApplicationInfo::class.java.getField("privateFlags")
        field.getInt(info) and 1 != 0
    }.getOrNull()

    fun isHidden(info: ApplicationInfo): Boolean = hiddenState(info) ?: false
}

/**
 * The apps a freeze must never touch while the guard is on: the current keyboard, the home
 * app, enabled accessibility services, the tools that grant this feature its power, and the
 * three apps whose absence is felt as a broken phone rather than a tidier one.
 *
 * The last group is what automatic freezing made urgent. A rule that sleeps "everything" at
 * night would otherwise take the clock with the pending morning alarm, the dialer that has to
 * ring, and the messenger that receives the code. None of them is safe to freeze by accident,
 * and none of them is listed anywhere the user would think to check.
 */
object FreezeProtection {
    /**
     * The app that rings. Protected wherever the guard reaches: a freeze switch is not a
     * reason to make the phone unable to answer a call, and the switch itself says that
     * turning it off takes calls and messages with it.
     */
    fun dialer(context: Context): String? = runCatching {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    }.getOrNull()

    /**
     * Packages that must never be frozen, whatever the list says.
     *
     * Losing any of these is not "a tidier phone": the package installer is how Deluge itself
     * is reinstalled, Settings is where a disabled app is enabled again, SystemUI is the
     * phone, and the permission controller is what keeps every other app working. They are
     * named literally because no API reports "this one is load bearing".
     */
    private val SYSTEM_CRITICAL = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
    )

    /**
     * The installed packages the phone cannot do without: the screen, Settings, the installer
     * and the permission layer. Frozen one of these and there may be nothing left to tap on
     * and no screen left to undo it from, so a phone that can still be asked has to be asked.
     */
    fun loadBearing(context: Context): Set<String> = SYSTEM_CRITICAL.filterTo(mutableSetOf()) { name ->
        runCatching { context.packageManager.getPackageInfo(name, 0) }.isSuccess
    }

    fun packages(context: Context, privileged: Set<String>): Set<String> {
        val base = buildSet {
            addAll(loadBearing(context))
            runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            }.getOrNull()?.substringBefore('/')?.let { if (FreezeCommands.isPackageName(it)) add(it) }
            runCatching {
                context.packageManager.resolveActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY,
                )?.activityInfo?.packageName
            }.getOrNull()?.let { add(it) }
            runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
            }.getOrNull()?.split(':')?.forEach { service ->
                service.substringBefore('/').takeIf { FreezeCommands.isPackageName(it) }?.let(::add)
            }
            addAll(privileged)
        }

        val roles = listOfNotNull(
            // The role holders API is gone from newer SDKs, but these two getters still
            // answer the same question, and they work back to API 26.
            dialer(context),
            @Suppress("DEPRECATION")
            runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
        )
        // Whatever app owns the next alarm. This is the clock in practice, but an OEM clock
        // or a third-party alarm app answers the same way, and freezing the owner is what
        // makes the alarm silent.
        val alarm = runCatching {
            context.getSystemService(AlarmManager::class.java)
                ?.nextAlarmClock?.showIntent?.creatorPackage
        }.getOrNull()
        return merge(base, roles + listOfNotNull(alarm))
    }

    /** [base] plus the packages a shell or the system named, with blanks and junk dropped. */
    internal fun merge(base: Set<String>, extra: Collection<String?>): Set<String> = buildSet {
        addAll(base)
        extra.forEach { name ->
            name?.takeIf { FreezeCommands.isPackageName(it) }?.let(::add)
        }
    }
}

/** A shell that runs commands as root (if the device grants it) or through Shizuku. */
class FreezeShell(private val context: Context) {

    private data class RootState(val provider: RootProvider, val version: String?)

    private val policy = DeviceOwnerPolicy(context)

    private var rootState: RootState? = null

    /** When `su` last refused; avoids turning every resume into another prompt. */
    private var rootMissAt = 0L

    /**
     * The channel picture. [probeRoot] stays true for every action and for the freeze screen;
     * a quiet startup probe passes false so a launcher without root never raises `su`.
     */
    fun status(probeRoot: Boolean = true): FreezeStatus {
        val root = rootState ?: if (probeRoot) rootState() else null
        val running = shizukuRunning()
        val authorized = running && shizukuAuthorized()
        val manager = shizukuManager()
        val owner = policy.active
        return FreezeStatus(
            checked = true,
            // The policy channel needs no external process, so it wins when set up.
            // Shizuku is next because its shell starts in a few milliseconds, while `su`
            // has to set up a mount namespace every time. Root stays for hiding apps and
            // as the fallback.
            access = when {
                owner -> FreezeAccess.DEVICE_OWNER
                authorized -> FreezeAccess.SHIZUKU
                root != null -> FreezeAccess.ROOT
                else -> FreezeAccess.NONE
            },
            rootGranted = root != null,
            rootProvider = root?.provider ?: RootProvider.NONE,
            rootVersion = root?.version,
            shizukuInstalled = manager != null,
            shizukuRunning = running,
            shizukuAuthorized = authorized,
            shizukuManagerPackage = manager,
            shizukuManagerVersion = manager?.let(::managerVersion),
            shizukuVersion = if (running) runCatching { rikka.shizuku.Shizuku.getVersion() }.getOrDefault(0) else 0,
            deviceAdmin = policy.adminActive,
            deviceOwner = owner,
        )
    }

    fun access(): FreezeAccess = status().access

    /** Whether a root manager is installed, so a startup probe cannot raise a surprise prompt. */
    fun rootManagerInstalled(): Boolean = ROOT_PACKAGES.keys.any(::installed)

    private fun installed(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
    }.isSuccess

    private fun managerVersion(packageName: String): String? = runCatching {
        context.packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Asks `su` once per process, and asks again after a refusal: a prompt that timed out is
     * worth retrying when the user opens the screen again.
     */
    private fun rootState(): RootState? {
        rootState?.let { return it }
        synchronized(this) {
            rootState?.let { return it }
            val now = SystemClock.elapsedRealtime()
            if (rootMissAt != 0L && now - rootMissAt < ROOT_RETRY_MS) return null
            val result = runProcess(arrayOf("su", "-c", ROOT_PROBE), ROOT_TIMEOUT_MS)
            if (result?.first != 0 || !result.second.contains("uid=0")) {
                rootMissAt = now
                return null
            }
            val lines = result.second.lineSequence().map { it.trim() }.toList()
            val provider = when {
                lines.contains(PROVIDER_MAGISK) -> RootProvider.MAGISK
                lines.contains(PROVIDER_KERNELSU) -> RootProvider.KERNELSU
                lines.contains(PROVIDER_APATCH) -> RootProvider.APATCH
                else -> installedProvider()
            }
            val version = lines.firstOrNull {
                it.isNotEmpty() &&
                    it != PROVIDER_MAGISK &&
                    it != PROVIDER_KERNELSU &&
                    it != PROVIDER_APATCH &&
                    !it.startsWith("uid=")
            }
            rootMissAt = 0L
            return RootState(provider, version).also { rootState = it }
        }
    }

    /** Forgets a previous answer, so a grant made after a refusal is seen on the next probe. */
    fun invalidateRoot() {
        synchronized(this) {
            rootState = null
            rootMissAt = 0L
        }
    }

    /**
     * A hidden root manager can leave no directory behind, so the installed package is the
     * second answer. Unknown is still root; the provider is only a label.
     */
    private fun installedProvider(): RootProvider = ROOT_PACKAGES.entries
        .firstOrNull { (packageName, _) ->
            runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
        }
        ?.value
        ?: RootProvider.OTHER

    private fun shizukuRunning(): Boolean = runCatching { rikka.shizuku.Shizuku.pingBinder() }
        .getOrDefault(false)

    /**
     * Shows Shizuku's own permission dialog when it is installed and running but this app was
     * never authorised. A fresh install (and every reinstall) starts unauthorised, and without
     * this the only way in was Shizuku's manager, which no screen of ours points at.
     */
    internal fun requestShizuku(): Boolean = runCatching {
        if (!shizukuRunning() || shizukuAuthorized()) return false
        rikka.shizuku.Shizuku.requestPermission(4213)
        true
    }.getOrDefault(false)

    private fun shizukuAuthorized(): Boolean = runCatching {
        rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * The installed manager that defines the Shizuku API permission.
     *
     * Any manager that can serve this API has to declare that permission — official Shizuku,
     * Shizuku-Next and Shevery all do — so this finds the family by contract instead of by a
     * package name, and a future fork needs no change here. The known packages are a fallback
     * for ROMs where a custom permission is not visible to another app.
     */
    private fun shizukuManager(): String? {
        val declared = runCatching {
            context.packageManager.getPermissionInfo(SHIZUKU_PERMISSION, 0).packageName
        }.getOrNull()
        if (declared != null && installed(declared)) return declared
        return SHIZUKU_MANAGERS.firstOrNull(::installed)
    }

    fun shizukuInstalled(): Boolean = shizukuManager() != null

    fun openShizuku(): Boolean = runCatching {
        val manager = shizukuManager() ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(manager)
            ?: android.content.Intent().setClassName(manager, SHIZUKU_MAIN_ACTIVITY)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /** Runs one command and reports whether it exited cleanly with nothing alarming printed. */
    fun run(command: String): Boolean {
        val (code, output) = execute(command) ?: return false
        return code == 0 && !looksLikeFailure(output)
    }

    /**
     * Whether a shell result carries a diagnostic rather than ordinary text.
     *
     * Matching the word anywhere rejected packages whose name happens to contain it, so a
     * line only counts when the message itself starts with one of the shapes the platform
     * prints for a refusal.
     */
    internal fun looksLikeFailure(output: String): Boolean = output.lineSequence().any { line ->
        val text = line.trim()
        text.startsWith("Exception") ||
            text.startsWith("Error") ||
            text.startsWith("java.lang.") ||
            text.startsWith("SecurityException")
    }

    /**
     * Runs one command through root only, never through Shizuku's shell user.
     *
     * Hiding an app needs `MANAGE_USERS`, which the shell user does not hold even when
     * Shizuku is connected. The binder path handles a root-mode Shizuku; this is the plain
     * `su` fallback for a rooted phone without Shizuku.
     */
    fun runAsRoot(command: String): Boolean {
        val (code, output) = runScript(command, COMMAND_TIMEOUT_MS, root = true) ?: return false
        return code == 0 && !looksLikeFailure(output)
    }

    /** The command's output, for the dumps the guards parse; null when there is no access. */
    fun output(command: String): String? = execute(command)?.second

    /**
     * Runs command groups through a few shells at once and reports each group's exit status.
     *
     * A whole-list run used to fork one shell per command: 170 apps meant ~700 root round
     * trips and minutes of waiting, so the button looked dead. Here one bucket of groups goes
     * through a single shell and each group ends with a marker carrying its status; the
     * buckets run in parallel. A group is a package's commands, so the caller still learns
     * which packages failed.
     *
     * [root] picks the channel: true is the root shell and nothing else (hiding apps needs
     * it), false is Shizuku's shell and nothing else, null prefers Shizuku and falls back.
     */
    fun runGroups(groups: List<List<String>>, root: Boolean? = null): List<Boolean> {
        if (groups.isEmpty()) return emptyList()
        val answers = arrayOfNulls<Boolean>(groups.size)
        val buckets = List(minOf(BATCH_SHELLS, groups.size)) { mutableListOf<Int>() }
        groups.indices.forEach { buckets[it % buckets.size].add(it) }
        val workers = buckets.filter { it.isNotEmpty() }.map { bucket ->
            thread(isDaemon = true) {
                val script = bucket.joinToString("\n") { index ->
                    buildString {
                        append("ok=0\n")
                        groups[index].forEach { append(it).append(" 2>&1 || ok=1\n") }
                        append("echo \"@@E").append(index).append(":\$ok\"\n")
                    }
                }
                runScript(script, BATCH_TIMEOUT_MS, root)?.second?.let { output ->
                    MARKER.findAll(output).forEach { match ->
                        val index = match.groupValues[1].toIntOrNull() ?: return@forEach
                        if (index in answers.indices) answers[index] = match.groupValues[2] == "0"
                    }
                }
            }
        }
        workers.forEach { it.join() }
        return answers.map { it ?: false }
    }

    /**
     * The managers that give this feature its power: the Shizuku family, whatever declares
     * the API permission, and every installed root manager. Freezing one can cut off the
     * only way back.
     */
    fun protectedPackages(): Set<String> = buildSet {
        shizukuManager()?.let(::add)
        addAll(
            ROOT_PACKAGES.keys.filter {
                runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess
            }
        )
        // A device owner is the only way to unhide an app on a phone without root. When the
        // owner is a borrowed one (Dhizuku and friends), freezing or uninstalling it takes
        // that way back with it, and a hidden app has no icon left to tap.
        // The getter is hidden API, so it is read reflectively and simply skipped when the
        // platform refuses; the known hosts below cover the borrowed-owner case anyway.
        runCatching {
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            val getter = dpm.javaClass.getMethod("getDeviceOwnerComponentOnAnyUser")
            (getter.invoke(dpm) as? ComponentName)?.packageName
        }.getOrNull()?.takeIf { FreezeCommands.isPackageName(it) }?.let(::add)
        DO_HOST_PACKAGES.filter {
            runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess
        }.forEach(::add)
    }

    /** Apps that hand out device-owner powers; losing one strands every hidden package. */
    private val DO_HOST_PACKAGES = setOf("com.rosan.dhizuku", "io.github.muntashirakon.AppManager")

    /** Whether a root shell answered the probe, regardless of which channel is in use. */
    fun rootAvailable(): Boolean = rootState() != null

    /** Whether Shizuku is running with access granted. */
    fun shizukuAvailable(): Boolean = shizukuReady()

    /** Whether a channel is already known to work, so a command cannot raise a prompt. */
    fun quietAccess(): Boolean = shizukuReady() || rootState != null

    /** Runs one command on whichever channel is ready; null when neither is. */
    fun runCapture(command: String, timeoutMs: Long = COMMAND_TIMEOUT_MS): Pair<Int, String>? =
        runScript(command, timeoutMs)

    private fun execute(command: String): Pair<Int, String>? = runScript(command, COMMAND_TIMEOUT_MS)

    private fun runScript(command: String, timeoutMs: Long, root: Boolean? = null): Pair<Int, String>? {
        // Upgrade test hook, debug builds only: when a host has created `cmd-bridge/` in this
        // app's files dir, commands go through files the host executes as root. It exists so
        // the freeze/upgrade test can run on an AVD that has no su for apps and no Shizuku
        // grant, and it is inert in a release build.
        if (bridgeActive()) runViaBridge(command, timeoutMs)?.let { return it }
        return when {
            root == true -> runProcess(arrayOf("su", "-c", command), timeoutMs)
        root == false -> if (shizukuReady()) runShizuku(command) else null
            shizukuReady() -> runShizuku(command)
            rootState() != null -> runProcess(arrayOf("su", "-c", command), timeoutMs)
            else -> null
        }
    }

    /**
     * Debug-only file bridge used by the upgrade test.
     *
     * Writes the script to `cmd-bridge/req.<id>` and waits for `out.<id>` and `code.<id>`.
     * A host loop running as root executes each request, so the test needs no su for apps and
     * no Shizuku grant -- and nothing here runs unless the folder exists and this is a debug
     * build.
     */
    private fun runViaBridge(command: String, timeoutMs: Long): Pair<Int, String>? = runCatching {
        val dir = java.io.File(context.filesDir, "cmd-bridge")
        if (!dir.isDirectory) return null
        val id = System.nanoTime().toString()
        val request = java.io.File(dir, "req.$id")
        request.writeText(command)
        val out = java.io.File(dir, "out.$id")
        val code = java.io.File(dir, "code.$id")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (code.exists() && out.exists()) {
                val exit = code.readText().trim().toIntOrNull() ?: 0
                val text = out.readText()
                code.delete()
                out.delete()
                return exit to text
            }
            Thread.sleep(60)
        }
        null
    }.getOrNull()

    /** Whether the debug bridge should carry this command. */
    internal fun bridgeActive(): Boolean =
        BuildConfig.DEBUG && java.io.File(context.filesDir, "cmd-bridge").isDirectory

    private fun shizukuReady(): Boolean = shizukuRunning() && shizukuAuthorized()

    private fun runProcess(argv: Array<String>, timeoutMs: Long): Pair<Int, String>? = runCatching {
        val process = ProcessBuilder(*argv).redirectErrorStream(true).start()
        // Drain while it runs: a dump can fill the pipe and then waitFor never returns.
        val output = java.io.ByteArrayOutputStream()
        val reader = thread(isDaemon = true) {
            runCatching { process.inputStream.copyTo(output) }
        }
        val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            reader.join(READER_JOIN_MS)
            return@runCatching null
        }
        reader.join(READER_JOIN_MS)
        process.exitValue() to output.toString(Charsets.UTF_8.name())
    }.getOrNull()

    /**
     * Shizuku's own process API: the command is written to a shell's stdin and the shell is
     * told to exit, so the output can be read when it finishes.
     */
    private fun runShizuku(command: String): Pair<Int, String>? = runCatching {
        val binder = rikka.shizuku.Shizuku.getBinder() ?: return null
        val service = moe.shizuku.server.IShizukuService.Stub.asInterface(binder) ?: return null
        val process = service.newProcess(arrayOf("sh"), null, null)
        ParcelFileDescriptor.AutoCloseOutputStream(process.outputStream).use { input ->
            input.write("$command\nexit\n".toByteArray())
        }
        val output = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
            .bufferedReader().use { it.readText() }
        val code = process.waitFor()
        runCatching { process.destroy() }
        code to output
    }.getOrNull()

    companion object {
        /**
         * Declared by every manager that speaks the Shizuku API. Reading its owner names the
         * manager, so no fork needs to be listed here.
         */
        private const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"

        /** The manager's activity; forks keep the name so Shizuku-aware apps still open them. */
        private const val SHIZUKU_MAIN_ACTIVITY = "moe.shizuku.manager.MainActivity"

        /**
         * API-compatible managers that shipped under their own package name. The permission
         * owner above is the primary answer; these cover a ROM that hides a custom permission.
         */
        private val SHIZUKU_MANAGERS = listOf(
            "moe.shizuku.privileged.api",
            "com.hamondev.shevery",
        )
        private const val ROOT_TIMEOUT_MS = 15_000L
        /** A refusal is retried after this, not before; a grant needs no restart. */
        private const val ROOT_RETRY_MS = 15_000L
        private const val COMMAND_TIMEOUT_MS = 20_000L
        private const val BATCH_TIMEOUT_MS = 180_000L
        private const val READER_JOIN_MS = 2_000L
        private const val BATCH_SHELLS = 8
        private val MARKER = Regex("@@E(\\d+):(\\d+)")

        private const val PROVIDER_MAGISK = "provider=magisk"
        private const val PROVIDER_KERNELSU = "provider=kernelsu"
        private const val PROVIDER_APATCH = "provider=apatch"

        /** One `su` round trip answers both "is there root" and "which root". */
        private val ROOT_PROBE = """
            id
            [ -d /data/adb/magisk ] && echo $PROVIDER_MAGISK
            [ -d /data/adb/ksu ] && echo $PROVIDER_KERNELSU
            [ -d /data/adb/ap ] && echo $PROVIDER_APATCH
            magisk -v 2>/dev/null || ksud -V 2>/dev/null || apd -V 2>/dev/null || true
        """.trimIndent()

        private val ROOT_PACKAGES = mapOf(
            "com.topjohnwu.magisk" to RootProvider.MAGISK,
            "io.github.huskydg.magisk" to RootProvider.MAGISK,
            "me.weishu.kernelsu" to RootProvider.KERNELSU,
            "com.rifsxd.ksunext" to RootProvider.KERNELSU,
            "me.bmax.apatch" to RootProvider.APATCH,
            "eu.chainfire.supersu" to RootProvider.SUPERSU,
        )
    }
}

/**
 * Picks out the two things the automatic freeze has to leave alone.
 *
 * Both are read through the elevated shell, so neither usage access nor a notification
 * listener is needed from the user.
 */
object FreezeGuards {
    private val FOREGROUND = Regex(
        "(?:topResumedActivity|mResumedActivity|mFocusedApp)[^\\n]*?" +
            "([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/",
    )
    private val NOTIFICATION_PACKAGE = Regex("\\bpkg=([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)")

    fun foregroundPackage(dump: String): String? =
        FOREGROUND.find(dump)?.groupValues?.getOrNull(1)

    fun notifiedPackages(dump: String): Set<String> =
        NOTIFICATION_PACKAGE.findAll(dump).map { it.groupValues[1] }.toSet()
}


/** One stored row of the freeze list: what to draw when the app itself cannot be listed. */
data class FreezeRow(
    /** As the app was called when it joined the list, before any rename override. */
    val label: String? = null,
    /** The launcher activity it had at that moment, as a flattened ComponentName. */
    val component: String? = null,
)

/**
 * Keeps the freeze list, and freezes and unfreezes what is on it.
 *
 * Two things are stored and they are deliberately not the same thing: the list
 * ([Prefs.freezeMethods], key to method) is what the user chose, and [Prefs.freezeApplied]
 * is which of those are frozen at this moment. Choosing an app does not freeze it — only a
 * trigger does — and unfreezing leaves the app on the list, so the next trigger puts it back.
 *
 * A key names one app in one profile (see [FreezeTargetKey]); the main profile's key is the
 * bare package name, which is what every list written before profiles were freezable uses.
 */
class FreezeManager(private val context: Context, private val prefs: Prefs) {

    private val shell = FreezeShell(context)
    /** The firewall shares the freeze list's privileged channel; same identity, same limits. */
    internal val networkShell: FreezeShell get() = shell
    private val binder = FreezeBinder(context)
    private val policy = DeviceOwnerPolicy(context)
    private val deepFreeze = DeepFreeze(prefs, shell, ::targetOf)
    private val userManager: UserManager get() = context.getSystemService(UserManager::class.java)

    /** One whole-list run at a time: a second tap waits instead of racing the first. */
    private val gate = Mutex()

    // Filled by a quiet probe at process start and on return, and by a full probe when the
    // freeze or access screen asks. Root is the only part that can raise a prompt, so the
    // quiet pass checks it only when freezing is already in use.
    private val _status = MutableStateFlow(FreezeStatus())
    val status: StateFlow<FreezeStatus> = _status

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes

    /** When the channel last answered; see [ensureAccess]. Under the [gate] or a lucky read. */
    @Volatile
    private var probedAt = 0L

    /** A second entry point must not start a parallel probe, and another `su` prompt. */
    private val probeGate = Mutex()

    @Volatile
    private var protectedAt = 0L

    @Volatile
    private var protectedSet: Set<String> = emptySet()

    /**
     * Set once the external backup has been read, even when it is missing. Without this the
     * first write after every change would probe the file again; a missing file is an answer
     * too, and the next write replaces it with the current list.
     */
    private var backupRead = false

    /** The file exists but this build cannot parse it; it is preserved, never overwritten. */
    @Volatile
    private var backupUnreadable = false

    /** The backup outlives this package's app data and is readable through root/Shizuku. */
    private val backupPath: String
        get() = "/data/local/tmp/.deluge-launcher-freeze-u" +
            android.os.Process.myUserHandle().hashCode() + ".json"

    /**
     * The full picture: who can freeze, what the device no longer holds, and which pause
     * records died while the screen was away. Asked when the freeze screen opens.
     */
    suspend fun refreshAccess() {
        probeAccess(force = true)
        // A reinstall has empty app data. Restore the list before reconciling it, so the
        // frozen apps are still described by the screen that is about to be drawn.
        restoreBackupIfNeeded()
        reconcile()
        upgradeSuspendDialog()
        // Existing plans written by a build without a backup get one on the first visit.
        writeBackup()
        // Rows frozen before this build or before the switch was on still need their app-ops,
        // and rows no longer suspended need them back. Under the gate: a sync racing a user's
        // freeze/unfreeze would write the journal from a stale snapshot.
        gate.withLock { deepFreeze.sync(prefs.freezeAppliedNow()) }
    }

    /**
     * Lifts the list and gives up device ownership, in that order.
     *
     * The platform normally clears the owner's policy state with the admin, but a removal
     * that fails halfway must not leave an app hidden, so the list is released first.
     */
    suspend fun removeDeviceOwner(): Boolean {
        if (!policy.active) return false
        // The permanent flag does not apply here: the policy channel is about to go away, and
        // an app left suspended by it would need root or Shizuku to come back.
        runCatching { unfreezeAll(ignorePermanent = true) }
        val removed = withContext(Dispatchers.IO) { policy.removeDeviceOwner() }
        if (removed) refreshAccess()
        return removed
    }

    /**
     * Whether the external backup can be read without raising a root prompt.
     *
     * The backup is only useful after a reinstall, when the app data is gone. Reading it on
     * every cold start through a root prompt would make a normal launch ask for root for no
     * reason; Shizuku already granted is the one channel that can answer silently.
     */
    fun canUseBackupQuietly(): Boolean = shell.quietAccess()

    /** Whether the privileged channel answers without a prompt; the firewall shares it. */
    fun hasQuietAccess(): Boolean = shell.quietAccess()

    /** Whether there is anything worth writing at a cold start. */
    suspend fun hasFreezePlan(): Boolean =
        prefs.freezeMethodsNow().isNotEmpty() || prefs.freezeRowsNow().isNotEmpty()

    /** Whether a lock record would be lost with this package's data. */
    suspend fun hasLockedApps(): Boolean = prefs.lockedAppsNow().isNotEmpty()

    /** The package actually on top, used only to confirm a locked launch made it. */
    suspend fun foregroundPackage(): String? = withContext(Dispatchers.IO) {
        if (!shell.quietAccess()) return@withContext null
        val output = shell.output(
            "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|mFocusedApp'"
        ) ?: return@withContext null
        FreezeGuards.foregroundPackage(output)
    }

    /**
     * Restores the list kept outside app data after this package was reinstalled.
     *
     * The comparison is against the package's first install time, not its data: an update
     * keeps the data and the time, while uninstall/reinstall changes both. Merging instead
     * of replacing also covers a freeze made in the moment before the restore runs.
     */
    suspend fun restoreBackupIfNeeded(): Boolean = gate.withLock {
        val restored = restoreBackupLocked()
        if (restored) writeBackupLocked()
        restored
    }

    /**
     * Writes the current plan outside app data, so uninstalling Deluge does not strand the
     * apps it froze. Called after a freeze state change and when the freeze screen opens.
     */
    suspend fun writeBackup(): Boolean = gate.withLock {
        if (!shell.quietAccess()) return@withLock false
        if (!backupRead) restoreBackupLocked()
        if (!backupRead || backupUnreadable) return@withLock false
        writeBackupLocked()
    }

    private suspend fun restoreBackupLocked(): Boolean {
        if (backupRead) return false
        // `cat` alone cannot tell "no file yet" from "a file we cannot parse": a first run
        // would read an empty string, call the backup damaged, and then refuse to write one
        // for the rest of the process — the record this class exists to keep would never
        // exist. The marker makes absence explicit and leaves it writable.
        val text = withContext(Dispatchers.IO) {
            shell.output("if [ -f $backupPath ]; then cat $backupPath; else echo $BACKUP_ABSENT; fi")
        } ?: return false
        if (text.isBlank() || text.trim() == BACKUP_ABSENT) {
            backupRead = true
            return false
        }
        // Only a backup that parsed counts as read. A file from another build, or one cut
        // short by a crash, must not be replaced by this build's empty state: it is the only
        // copy of the user's list, and overwriting it would lose the apps still frozen.
        val backup = FreezeBackup.decode(text)
        if (backup == null) {
            backupUnreadable = true
            return false
        }
        backupRead = true
        // Same install normally means this build's own state is current. The exception is a
        // data wipe: firstInstallTime survives it, but the plan, rows and lock list do not.
        // Restoring in that case is the only way to get the hidden, suspended apps back.
        val currentHasState = prefs.freezeMethodsNow().isNotEmpty() ||
            prefs.freezeRowsNow().isNotEmpty() ||
            prefs.lockedAppsNow().isNotEmpty()
        if (backup.firstInstallTime == firstInstallTime() && currentHasState) return false
        // Current values win: the user may already have added something after the reinstall,
        // and a restore must not undo that. Everything else comes from the backup.
        val methods = backup.methods + prefs.freezeMethodsNow()
        prefs.setFreezeMethods(methods)
        prefs.setFreezeRows(backup.rows + prefs.freezeRowsNow())
        // A backup written before methods were stored carries applied with no methods; the
        // filter would drop every row and the apps would stay frozen with nothing naming them.
        val applied = (backup.applied + prefs.freezeAppliedNow())
            .filterTo(mutableSetOf()) { methods.isEmpty() || it in methods }
        prefs.setFreezeApplied(applied, applied = true)
        // Merged the same way, and only for keys that end up listed: a pin on an app the
        // merged list does not carry would name nothing.
        prefs.setFreezePinnedKeys(
            (backup.pinned + prefs.freezePinnedNow()).filterTo(mutableSetOf()) { it in methods }
        )
        // Permanent flags are the reason a reinstall can still leave an app asleep on purpose;
        // merged the same way and filtered to rows the merged list actually carries.
        prefs.setFreezePermanentKeys(
            (backup.permanent + prefs.freezePermanentNow()).filterTo(mutableSetOf()) { it in methods }
        )
        prefs.setFreezeEnabled(backup.enabled)
        prefs.setFreezeSystemApps(backup.systemApps)
        prefs.setFreezeProtect(backup.protect)
        // A locked app is hidden and suspended, so the list that names it is the only way back
        // into it after this package's data was lost. Merge rather than replace: a lock added
        // in the moment before the restore runs must survive too.
        val currentLocks = prefs.lockedAppsNow()
        prefs.addAppLocks(backup.locked.filterNot { it in currentLocks })
        _changes.value += 1
        return true
    }

    private suspend fun writeBackupLocked(): Boolean {
        if (backupUnreadable) return false
        val snapshot = FreezeBackup.Snapshot(
            firstInstallTime = firstInstallTime(),
            enabled = prefs.freezeEnabledNow(),
            systemApps = prefs.freezeSystemAppsNow(),
            protect = prefs.freezeProtectNow(),
            methods = prefs.freezeMethodsNow(),
            applied = prefs.freezeAppliedNow(),
            rows = prefs.freezeRowsNow(),
            locked = prefs.lockedAppsNow(),
            pinned = prefs.freezePinnedNow(),
            permanent = prefs.freezePermanentNow(),
        )
        val encoded = android.util.Base64.encodeToString(
            FreezeBackup.encode(snapshot).toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        val temporary = "$backupPath.tmp"
        return withContext(Dispatchers.IO) {
            // Readable by whichever channel writes next: a root-written 0600 file would be
            // invisible to Shizuku after a reinstall. 0640 with the shell group is enough for
            // root and Shizuku, and keeps ordinary apps on /data/local/tmp out of the list.
            shell.run(
                "umask 077; rm -f $temporary; echo $encoded | base64 -d > $temporary && " +
                    "chmod 0640 $temporary && (chown root:shell $temporary 2>/dev/null || true) && " +
                    "mv -f $temporary $backupPath",
            )
        }
    }

    /**
     * Loads the backup before a state-changing run, so a reinstall restores the old list
     * before that run decides what to freeze or lift.
     */
    private suspend fun loadBackupLocked() {
        if (!backupRead && shell.quietAccess()) restoreBackupLocked()
    }

    /** The write side of [writeBackup] for callers already holding [gate]. */
    private suspend fun persistBackupLocked() {
        if (!backupRead || !shell.quietAccess()) return
        writeBackupLocked()
    }

    private fun firstInstallTime(): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }.getOrDefault(0L)

    /**
     * The channel, asked again only when the last answer is old.
     *
     * Every freeze and unfreeze used to run the whole [refreshAccess] pass: a `su` fork, a
     * plan-wide state read and a pause-dialog upgrade. Two taps a second apart cannot change
     * the answer, and a failed operation still forces a fresh probe.
     */
    private suspend fun ensureAccess(): FreezeAccess {
        val access = probeAccess(force = false)
        // Nothing to act with yet: ask once, so the next attempt has a channel instead of
        // failing silently on every tap.
        if (access == FreezeAccess.NONE) shell.requestShizuku()
        return access
    }

    /**
     * The first probe of a process. It runs off the main thread and never blocks a frame.
     *
     * Shizuku is checked first because pinging its binder and reading its permission cannot
     * raise a prompt. Root is probed only when the user already uses freezing and a root
     * manager is installed, so a launcher that merely sits on the home screen never asks `su`.
     */
    suspend fun checkAccessOnStart() {
        if (probedAt != 0L) return
        probeAccess(force = true, quietRoot = true)
    }

    /** A cheap foreground re-probe, for a Shizuku service started after the launcher. */
    suspend fun refreshAccessQuietly() {
        probeAccess(force = false, quietRoot = true)
    }

    private suspend fun probeAccess(force: Boolean, quietRoot: Boolean = false): FreezeAccess {
        // The debug bridge runs every command as root on the host, so the access gate must not
        // answer "no channel" and abort before a command is ever sent.
        if (shell.bridgeActive()) {
            _status.value = _status.value.copy(
                checked = true,
                access = FreezeAccess.ROOT,
                rootGranted = true,
            )
            probedAt = SystemClock.elapsedRealtime()
            return FreezeAccess.ROOT
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && now - probedAt < PROBE_TTL_MS) return _status.value.access
        // Wait for an in-flight probe instead of answering with its stale placeholder: a
        // screen-off run and a tap in the same tick used to make the tap read the initial
        // NONE and report "no access" until it was pressed again.
        return probeGate.withLock {
            val afterWait = SystemClock.elapsedRealtime()
            if (!force && afterWait - probedAt < PROBE_TTL_MS) return@withLock _status.value.access
            try {
                val probeRoot = !quietRoot ||
                    (shell.rootManagerInstalled() &&
                        (prefs.freezeEnabledNow() || prefs.freezeMethodsNow().isNotEmpty()))
                _status.value = withContext(Dispatchers.IO) { shell.status(probeRoot = probeRoot) }
                probedAt = SystemClock.elapsedRealtime()
                _status.value.access
            } catch (t: Throwable) {
                // A failed probe is not an answer: leave the old one and let the caller carry on.
                _status.value.access
            }
        }
    }

    /**
     * Gives the pause dialog to the apps that were paused before it existed.
     *
     * Pausing again under the same suspender replaces the parameters the system stores, so
     * this is what turns an old record — one that dead-ends on "More details" when another app
     * tries to open it — into one with an unpause button. Runs until every app takes it: a
     * failed call leaves the flag alone for the next visit.
     */
    private suspend fun upgradeSuspendDialog() {
        if (prefs.freezeDialogReadyNow()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !binder.available()) return
        val plan = prefs.freezeMethodsNow()
        val targets = prefs.freezeAppliedNow()
            .filter { plan[it] == FreezeMethod.SUSPEND }
            .mapNotNull(::targetOf)
        if (targets.isEmpty()) {
            prefs.setFreezeDialogReady()
            return
        }
        val done = withContext(Dispatchers.IO) {
            targets.map { target ->
                async { binder.refreshSuspend(target.packageName, target.userId) == true }
            }.awaitAll()
        }
        if (done.all { it }) prefs.setFreezeDialogReady()
    }

    /**
     * Brings the applied set back in line with the device, without touching the list.
     *
     * The list is the user's choice and outlives an uninstall of the app it names: an app
     * removed and installed again must come back with the method it was given. Only the
     * applied state is corrected here. An app that is gone has nothing frozen, an app that
     * is recorded as frozen but is not — the user re-enabled it in Settings, or a STOP did
     * not survive a reboot — stops being drawn as frozen, and an app whose pause was lifted
     * by the system's own "Unpause and open" button gets its background app-ops
     * un-restricted. Only states the platform answers are checked, because HIDE has no
     * public answer.
     */
    private suspend fun reconcile() = gate.withLock {
        val plan = prefs.freezeMethodsNow()
        val applied = prefs.freezeAppliedNow()
        if (plan.isEmpty() && applied.isEmpty()) return@withLock
        val locked = lockedMainKeys()
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        val noLonger = applied.filter { key ->
            // The lock owns these; reconcile must not drop a record the lock is still using.
            if (key in locked) return@filter false
            val target = targetOf(key) ?: return@filter true
            val method = plan[key]
            if (snapshot.isKnownAbsent(target)) return@filter true
            if (snapshot.state(target)?.installed == false) return@filter true
            when (method) {
                null -> true
                FreezeMethod.DISABLE, FreezeMethod.STOP ->
                    !isHeld(snapshot, target, method)
                else -> false
            }
        }
        // Only the profile this app runs in: another profile's suspend flag is not readable
        // with the same confidence, and a wrong "free" there would drop a real record.
        val unpaused = withContext(Dispatchers.IO) {
            applied.filter { key ->
                key !in locked &&
                    plan[key] == FreezeMethod.SUSPEND &&
                    FreezeTargetKey.userSerial(key) == 0L &&
                    targetOf(key)?.let {
                        snapshot.state(it)?.installed != false &&
                            !suspendedNow(it) && snapshot.state(it)?.enabled != false
                    } == true
            }
        }
        if (unpaused.isNotEmpty()) {
            restoreBackground(unpaused, snapshot)
            prefs.setFreezeApplied(unpaused.toSet(), applied = false)
        }
        if (noLonger.isEmpty()) {
            if (unpaused.isNotEmpty()) _changes.value += 1
            return@withLock
        }
        prefs.setFreezeApplied(noLonger.toSet(), applied = false)
        _changes.value += 1
    }

    /** Lifts the background app-ops a pause left behind, through the binder when it can. */
    private suspend fun restoreBackground(keys: List<String>, snapshot: FreezeSnapshot) {
        withContext(Dispatchers.IO) {
            val groups = keys.mapNotNull { key ->
                val target = targetOf(key) ?: return@mapNotNull null
                val uid = snapshot.state(target)?.uid ?: -1
                if (binder.available() && uid > 0) {
                    binder.setBackgroundAllowed(target.packageName, uid)
                    null
                } else {
                    FreezeCommands.unfreeze(target.packageName, FreezeMethod.SUSPEND, target.userId)
                }
            }.filter { it.isNotEmpty() }
            if (groups.isNotEmpty()) shell.runGroups(groups)
        }
    }

    fun hideAvailable(): Boolean = _status.value.rootGranted

    fun shizukuInstalled(): Boolean = shell.shizukuInstalled()

    fun openShizuku(): Boolean = shell.openShizuku()

    /** Whether the package is preinstalled, asked before a system app may join the list. */
    fun isSystemApp(packageName: String): Boolean = FreezeState.isSystemApp(context, packageName)

    /** Carries an older build's "which are lifted" over to this build's "which are frozen". */
    suspend fun migrateLegacyState(): Boolean {
        val applied = prefs.migrateFreezeApplied()
        val permanent = prefs.migrateFreezePermanent()
        if (applied || permanent) _changes.value += 1
        return applied || permanent
    }

    /**
     * Turns the whole feature on or off.
     *
     * Off is the destructive direction and is refused halfway: every frozen app is lifted
     * first, and only when none is left frozen is the list emptied and the switch flipped.
     * A failure keeps the feature on so the screen that can fix it is still reachable.
     */
    suspend fun setEnabled(enabled: Boolean): FreezeBatchResult {
        if (enabled) {
            prefs.setFreezeEnabled(true)
            _changes.value += 1
            return FreezeBatchResult()
        }
        return gate.withLock {
            loadBackupLocked()
            val applied = prefs.freezeAppliedNow()
            val plan = prefs.freezeMethodsNow()
            val locked = lockedMainKeys()
            val resolved = (plan.keys + applied).distinct()
                .filterNot { it in locked }
                .mapNotNull { key -> targetOf(key)?.let { key to it } }
            val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
            // Every listed app is restored, not only the ones the applied record remembers: a
            // crash between a freeze and its record would otherwise leave an app stuck with the
            // feature gone and no screen left to lift it.
            val targets = resolved.filter { (key, target) ->
                mustLift(key, plan[key], applied, snapshot, target)
            }
            var lifted = 0
            if (targets.isNotEmpty()) {
                if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeBatchResult(noAccess = true)
                val done = withContext(Dispatchers.IO) {
                    liftedIn(targets.map { it.first to plan[it.first] })
                }
                lifted = done.size
                prefs.setFreezeApplied(done, applied = false)
                _changes.value += 1
                if (done.size != targets.size) {
                    return@withLock FreezeBatchResult(done = lifted, failed = targets.size - done.size)
                }
            }
            prefs.clearFreezePlan(locked)
            prefs.clearFreezeActionBindings()
            // Nothing is frozen by this feature any more, so nothing may stay hardened either.
            deepFreeze.release()
            prefs.setFreezeEnabled(false)
            _changes.value += 1
            persistBackupLocked()
            FreezeBatchResult(done = lifted)
        }
    }

    /**
     * Inverts the selection within one method: listed apps leave the list, unlisted ones join
     * it. Apps listed under another method are left where they are.
     *
     * Removing a frozen app has to lift it, so no access means nothing is changed at all.
     */
    suspend fun invertPlan(apps: List<AppInfo>, method: FreezeMethod): FreezeBatchResult = gate.withLock {
        if (!prefs.freezeEnabledNow()) return@withLock FreezeBatchResult()
        loadBackupLocked()
        val current = prefs.freezeMethodsNow()
        val unique = apps.distinctBy { it.freezeKey }
        val listed = unique.filter { current[it.freezeKey] == method }
        val unlisted = unique.filter { current[it.freezeKey] == null }
        if (listed.isEmpty() && unlisted.isEmpty()) return@withLock FreezeBatchResult()
        val applied = prefs.freezeAppliedNow()
        val locked = lockedMainKeys()
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        val toLift = withContext(Dispatchers.IO) {
            listed.filter { app ->
                val target = targetOf(app.freezeKey) ?: return@filter false
                app.freezeKey !in locked &&
                    mustLift(app.freezeKey, current[app.freezeKey], applied, snapshot, target)
            }
        }
        var done = 0
        if (toLift.isNotEmpty()) {
            if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeBatchResult(noAccess = true)
            val lifted = withContext(Dispatchers.IO) {
                liftedIn(toLift.map { it.freezeKey to current[it.freezeKey] })
            }
            prefs.setFreezeApplied(lifted, applied = false)
            prefs.removeFreezeTargets(lifted)
            done += lifted.size
            if (lifted.size != toLift.size) {
                _changes.value += 1
                persistBackupLocked()
                return@withLock FreezeBatchResult(done = done, failed = toLift.size - lifted.size)
            }
        }
        val liftedKeys = toLift.mapTo(mutableSetOf()) { it.freezeKey }
        val plainRemovals = listed.filterNot { it.freezeKey in liftedKeys || it.freezeKey in locked }
        prefs.removeFreezeTargets(plainRemovals.map { it.freezeKey })
        prefs.setFreezeApplied(plainRemovals.mapTo(mutableSetOf()) { it.freezeKey }, applied = false)
        done += plainRemovals.size
        val guarded = protectedPackages()
        val joining = unlisted.filter { app ->
            val target = targetOf(app.freezeKey) ?: return@filter false
            app.freezeKey !in locked &&
                target.packageName !in guarded && systemAllowed(target, snapshot)
        }
        if (joining.isNotEmpty()) {
            prefs.setFreezeRows(
                joining.associate {
                    it.freezeKey to FreezeRow(it.label, it.componentName.flattenToString())
                }
            )
            prefs.setFreezeMethods(joining.associate { it.freezeKey to method })
            done += joining.size
        }
        _changes.value += 1
        persistBackupLocked()
        FreezeBatchResult(done = done)
    }

    /** Adds an app to the list. Nothing is frozen by this. */
    suspend fun addToPlan(app: AppInfo, method: FreezeMethod): FreezeOutcome =
        addToPlanKey(app.freezeKey, app.label, app.componentName.flattenToString(), method, app.system)

    /**
     * Adds one key that carries no [AppInfo] — the unused-apps list has only what the store
     * recorded. Nothing is frozen by this; the next trigger does that.
     */
    suspend fun addKeyToPlan(key: String, method: FreezeMethod = FreezeMethod.SUSPEND): FreezeOutcome =
        addToPlanKey(key, label = null, component = null, method = method)

    /** Adds a batch to the list with one method — Select all. Nothing is frozen by this. */
    suspend fun addManyToPlan(apps: List<AppInfo>, method: FreezeMethod): Int {
        if (!prefs.freezeEnabledNow()) return 0
        // The rows already carry this: one less whole-device read for a screen of toggles.
        val systemEnabled = prefs.freezeSystemAppsNow()
        val guarded = protectedPackages()
        val locked = lockedMainKeys()
        val taken = apps.filter { app ->
            app.kind == EntryKind.APP &&
                app.packageName != context.packageName &&
                app.packageName !in guarded &&
                app.freezeKey !in locked &&
                (systemEnabled || !app.system) &&
                // A device owner cannot act on another profile; listing the app there would
                // only produce a row that fails on every trigger.
                _status.value.offered(method, app.userSerial)
        }
        if (taken.isEmpty()) return 0
        prefs.setFreezeRows(
            taken.associate {
                it.freezeKey to FreezeRow(it.label, it.componentName.flattenToString())
            }
        )
        prefs.setFreezeMethods(taken.associate { it.freezeKey to method })
        _changes.value += 1
        return taken.size
    }

    private suspend fun addToPlanKey(
        key: String,
        label: String?,
        component: String?,
        method: FreezeMethod,
        /** The row's own answer, so a toggle does not re-read every package on the device. */
        system: Boolean? = null,
    ): FreezeOutcome {
        if (!prefs.freezeEnabledNow()) return FreezeOutcome.DISABLED
        if (key in lockedMainKeys()) return FreezeOutcome.PROTECTED
        val target = targetOf(key) ?: return FreezeOutcome.FAILED
        if (target.packageName == context.packageName) return FreezeOutcome.FAILED
        if (!_status.value.offered(method, target.userSerial)) return FreezeOutcome.FAILED
        val isSystem = system ?: withContext(Dispatchers.IO) {
            FreezeSnapshot.read(context).state(target)?.system == true
        }
        if (!systemAllowed(isSystem)) return FreezeOutcome.SYSTEM_APPS_OFF
        if (target.packageName in protectedPackages()) return FreezeOutcome.PROTECTED
        if (label != null || component != null) prefs.setFreezeRow(key, FreezeRow(label, component))
        prefs.setFreezeMethod(key, method)
        _changes.value += 1
        return FreezeOutcome.DONE
    }

    /** Freezes one listed app now, under the method the list remembers. */
    suspend fun freeze(app: AppInfo): FreezeOutcome =
        freezeKey(app.freezeKey, app.label, app.componentName.flattenToString())

    /** Freezes one key, for shortcuts that only carry what they were pinned with. */
    suspend fun freezeKey(key: String, label: String? = null, component: String? = null): FreezeOutcome =
        gate.withLock {
            freezeOneLocked(key, { prefs.freezeMethodsNow()[key] }, label, component)
        }

    /**
     * Freezes one row under [methodOf], with the gate already held.
     *
     * The method is asked for late and through a lambda so a caller can freeze under a method
     * that is not on the list yet — the method switch freezes before it records, and a failed
     * freeze must leave the old record untouched.
     */
    private suspend fun freezeOneLocked(
        key: String,
        methodOf: suspend () -> FreezeMethod?,
        label: String? = null,
        component: String? = null,
    ): FreezeOutcome {
        if (!prefs.freezeEnabledNow()) return FreezeOutcome.DISABLED
        // The focus session is showing this app; freezing it would take the only thing
        // on screen away. It is not a freeze feature, so it must not disturb one either.
        val focus = prefs.focusNow()
        // Freeze keys carry a profile suffix (`pkg|u<serial>`); comparing the raw key
        // would only ever match the main-profile spelling of a package.
        if (focus.active && FreezeTargetKey.packageName(key) == focus.packageName) {
            return FreezeOutcome.PROTECTED
        }
        // The lock owns this app's suspend/hide state. Freezing it again, especially with
        // DISABLE, would change a state the lock is about to need back.
        if (key in lockedMainKeys()) return FreezeOutcome.PROTECTED
        loadBackupLocked()
        val target = targetOf(key) ?: return FreezeOutcome.FAILED
        if (target.packageName == context.packageName) return FreezeOutcome.FAILED
        val method = methodOf() ?: return FreezeOutcome.NOT_LISTED
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        if (!systemAllowed(target, snapshot)) return FreezeOutcome.SYSTEM_APPS_OFF
        if (target.packageName in protectedPackages()) return FreezeOutcome.PROTECTED
        if (ensureAccess() == FreezeAccess.NONE) return FreezeOutcome.NO_ACCESS
        // The first load ran before access was proved; now a root/Shizuku-only backup
        // can be read before this freeze writes over it.
        loadBackupLocked()
        if (label != null || component != null) prefs.setFreezeRow(key, FreezeRow(label, component))
        // A package the platform parked in "disabled until used" cannot be changed by a
        // shell at all ("Shell cannot change component state"), and it is already out of the
        // user's way: treat the freeze as done and only clear what it left behind.
        val already = isHeld(snapshot, target, method) ||
            (method == FreezeMethod.DISABLE && parkedByPlatform(target))
        if (!already) {
            val frozen = withContext(Dispatchers.IO) { frozenIn(mapOf(key to method), snapshot) }
            if (key !in frozen) return FreezeOutcome.FAILED
        } else {
            // Already asleep: the only thing left is what it left behind — its card in
            // recents and any notification in the shade.
            withContext(Dispatchers.IO) {
                frozenIn(mapOf(key to FreezeMethod.STOP), snapshot)
            }
        }
        prefs.setFreezeApplied(setOf(key), applied = true)
        _changes.value += 1
        persistBackupLocked()
        return FreezeOutcome.DONE
    }

    /**
     * Freezes [app] with [method] right now, listing it first when it is not there yet.
     *
     * The list is what a later Freeze all works from, so freezing a single app from a row menu
     * is what puts it on the list too — one tap, no separate step to remember.
     */
    suspend fun freezeNow(app: AppInfo, method: FreezeMethod): FreezeOutcome {
        val added = addToPlan(app, method)
        if (added != FreezeOutcome.DONE) return added
        return freezeKey(app.freezeKey)
    }

    /** Unfreezes one app for now; it stays on the list. */
    suspend fun unfreeze(app: AppInfo): FreezeOutcome = unfreezeKey(app.freezeKey)
    /**
     * Freezes a group — today a folder — without asking for a method again.
     *
     * Each app keeps the method the list already gives it. An app nobody listed is left alone
     * and counted in [FreezeBatchResult.skipped]: a folder is a way to reach apps, not a second
     * way to decide the freeze list. Progress stops at the first missing channel instead of
     * raising the same prompt per app.
     */
    suspend fun freezeMany(apps: List<AppInfo>): FreezeBatchResult = gate.withLock {
        if (!prefs.freezeEnabledNow()) return@withLock FreezeBatchResult()
        val plan = prefs.freezeMethodsNow()
        val keys = apps.distinctBy { it.freezeKey }
        // The skip count is fixed before the run: an app the run never reached is not the same
        // as one it was told to leave alone, and only the latter is a skip.
        val batch = FreezeSelection.listedOnlyBatch(keys.map { it.freezeKey }, plan)
        val allowed = batch.targets.toSet()
        val candidates = keys.filter { it.freezeKey in allowed }
        val skipped = batch.skipped
        if (candidates.isEmpty()) return@withLock FreezeBatchResult(skipped = skipped)
        val focus = prefs.focusNow()
        val focusPackage = focus.takeIf { it.active }?.packageName
        val locked = lockedMainKeys()
        loadBackupLocked()
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        val guarded = protectedPackages()
        // Everything the single-row path refuses is refused here too, but counted once per row
        // instead of running its own transaction (and its own state read) per app.
        var failed = 0
        val runnable = buildMap {
            candidates.forEach { app ->
                val key = app.freezeKey
                val target = targetOf(key)
                val method = plan[key] ?: FreezeMethod.SUSPEND
                if (target == null ||
                    target.packageName == context.packageName ||
                    target.packageName == focusPackage ||
                    key in locked ||
                    target.packageName in guarded ||
                    !_status.value.offered(method, target.userSerial) ||
                    !systemAllowed(target, snapshot)
                ) {
                    failed++
                    return@forEach
                }
                put(key, method to target)
            }
        }
        if (runnable.isEmpty()) {
            return@withLock FreezeBatchResult(failed = failed, skipped = skipped)
        }
        if (ensureAccess() == FreezeAccess.NONE) {
            return@withLock FreezeBatchResult(
                failed = failed + runnable.size,
                noAccess = true,
                skipped = skipped,
            )
        }
        // The first load ran before access was proved; a root/Shizuku-only backup can be read
        // now, before this run writes over it.
        loadBackupLocked()
        prefs.setFreezeRows(
            candidates.filter { it.freezeKey in runnable }
                .associate {
                    it.freezeKey to FreezeRow(it.label, it.componentName.flattenToString())
                }
        )
        val applied = prefs.freezeAppliedNow()
        // Already asleep under the memory's method: only its recents card and notification
        // are left, which is the same STOP pass the single-row path runs.
        val already = runnable.filter { (_, pair) ->
            isHeld(snapshot, pair.second, pair.first) ||
                (pair.first == FreezeMethod.DISABLE && parkedByPlatform(pair.second))
        }
        val need = runnable.filterKeys { it !in already }
        if (already.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                frozenIn(already.keys.associateWith { FreezeMethod.STOP }, snapshot)
            }
        }
        val doneKeys = if (need.isEmpty()) {
            already.keys.toSet()
        } else {
            withContext(Dispatchers.IO) {
                frozenIn(need.mapValues { it.value.first }, snapshot)
            } + already.keys
        }
        prefs.setFreezeApplied(doneKeys, applied = true)
        _changes.value += 1
        persistBackupLocked()
        FreezeBatchResult(
            done = doneKeys.size,
            failed = failed + (runnable.size - doneKeys.size),
            skipped = skipped,
        )
    }

    /**
     * Releases a group, leaving every app on the list for the next trigger.
     *
     * Only members the record says are frozen are touched and counted. A folder can hold a
     * shortcut or an app nobody froze; reporting those as released (or as failures) was a
     * lie about what the run did.
     */
    suspend fun unfreezeMany(apps: List<AppInfo>): FreezeBatchResult = gate.withLock {
        loadBackupLocked()
        val applied = prefs.freezeAppliedNow()
        val plan = prefs.freezeMethodsNow()
        val locked = lockedMainKeys()
        val permanent = prefs.freezePermanentNow()
        val keys = apps.map { it.freezeKey }.distinct()
            .filter { it !in locked && it in applied }
        val kept = keys.count { it in permanent }
        val want = keys.filterNot { it in permanent }
        if (want.isEmpty()) return@withLock FreezeBatchResult(keptPermanent = kept)
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        val resolved = want.mapNotNull { key ->
            targetOf(key)?.let { key to (plan[key] to it) }
        }.toMap()
        // A key the device no longer resolves is a failure, exactly as the single-row path
        // reports it, and keeps its record for the next attempt.
        var failed = want.size - resolved.size
        val free = resolved.filter { (key, pair) ->
            !mustLift(key, pair.first, applied, snapshot, pair.second)
        }
        val lift = resolved - free.keys
        var done = 0
        if (lift.isNotEmpty()) {
            if (ensureAccess() == FreezeAccess.NONE) {
                return@withLock FreezeBatchResult(
                    done = 0,
                    failed = failed + lift.size,
                    noAccess = true,
                    keptPermanent = kept,
                )
            }
            loadBackupLocked()
            val lifted = withContext(Dispatchers.IO) {
                liftedIn(lift.map { it.key to it.value.first })
            }
            done += lifted.size
            failed += lift.size - lifted.size
            prefs.setFreezeApplied(lifted, applied = false)
        }
        // The record alone said frozen, but the app is free: drop it without asking for a
        // channel, and take back anything deep freeze left on it.
        if (free.isNotEmpty()) {
            prefs.setFreezeApplied(free.keys, applied = false)
            done += free.size
        }
        _changes.value += 1
        persistBackupLocked()
        // Network and app-ops of lifted rows were already restored inside liftedIn; rows that
        // were free only needed their leftover hardening removed.
        if (free.isNotEmpty()) deepFreeze.release(free.keys)
        FreezeBatchResult(done = done, failed = failed, keptPermanent = kept)
    }


    /** The same for a caller that only knows the main profile's package name. */
    suspend fun unfreeze(packageName: String): FreezeOutcome = unfreezeKey(packageName)

    /**
     * Lifts one row.
     *
     * [keepPermanent] is for the automatic paths — a rule waking an app, the midnight limit
     * thaw, undoing a rule run: those must not undo a decision the user made by hand. An
     * explicit tap on the row leaves it false, even when the row is permanent.
     */
    suspend fun unfreezeKey(key: String, keepPermanent: Boolean = false): FreezeOutcome = gate.withLock {
        unfreezeOneLocked(key, keepPermanent)
    }

    /** [unfreezeKey] with the gate already held, for a transaction that lifts and re-freezes. */
    private suspend fun unfreezeOneLocked(key: String, keepPermanent: Boolean): FreezeOutcome {
        val target = targetOf(key) ?: return FreezeOutcome.FAILED
        // A locked app's suspend/hide layers are the lock's to lift, and only after the
        // device has authenticated the launch. An unfreeze here would be a free way in.
        if (key in lockedMainKeys()) return FreezeOutcome.DONE
        if (keepPermanent && key in prefs.freezePermanentNow()) return FreezeOutcome.SKIPPED
        loadBackupLocked()
        // The record alone is not a reason to ask for root. After a reinstall the package is
        // enabled again but the old applied entry remains; that entry must be dropped without
        // access, or the app looks frozen and cannot open. HIDE is the one state the platform
        // cannot report back, so there the record still decides.
        val applied = prefs.freezeAppliedNow()
        val plan = prefs.freezeMethodsNow()
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        val method = plan[key]
        val needsLift = mustLift(key, method, applied, snapshot, target)
        if (!needsLift) {
            if (key in applied) {
                prefs.setFreezeApplied(setOf(key), applied = false)
                _changes.value += 1
                persistBackupLocked()
            }
            deepFreeze.release(setOf(key))
            return FreezeOutcome.DONE
        }
        if (ensureAccess() == FreezeAccess.NONE) return FreezeOutcome.NO_ACCESS
        loadBackupLocked()
        val lifted = withContext(Dispatchers.IO) {
            liftedIn(listOf(key to method))
        }
        if (key !in lifted) return FreezeOutcome.FAILED
        prefs.setFreezeApplied(setOf(key), applied = false)
        _changes.value += 1
        persistBackupLocked()
        return FreezeOutcome.DONE
    }

    /**
     * Changes which method a row freezes with, without caring whether it is frozen now.
     *
     * The record is written last: lift the old state, freeze the new one, and only then say
     * the row uses the new method. A failure leaves the row looking the way it did, or, when
     * the old state could not be restored either, says so by leaving the plan alone and the
     * app awake — never "the list says suspend, the app is disabled".
     *
     * A row that is not on the list yet just joins it: choosing a method is not the same as
     * freezing now, which keeps the one rule the page is built on.
     */
    suspend fun setMethod(key: String, method: FreezeMethod): FreezeOutcome = gate.withLock {
        if (!prefs.freezeEnabledNow()) return@withLock FreezeOutcome.DISABLED
        val plan = prefs.freezeMethodsNow()
        val previous = plan[key]
        if (previous == null) {
            prefs.setFreezeMethod(key, method)
            // STOP is a one-shot kill, so it cannot also be permanent. Losing the flag with
            // the method is the honest order; the page asks before offering STOP here.
            if (method == FreezeMethod.STOP) prefs.setFreezePermanent(key, false)
            _changes.value += 1
            persistBackupLocked()
            return@withLock FreezeOutcome.DONE
        }
        if (previous == method) return@withLock FreezeOutcome.DONE
        val target = targetOf(key) ?: return@withLock FreezeOutcome.FAILED
        val applied = prefs.freezeAppliedNow()
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        val frozen = mustLift(key, previous, applied, snapshot, target)
        if (!frozen) {
            prefs.setFreezeMethod(key, method)
            if (method == FreezeMethod.STOP) prefs.setFreezePermanent(key, false)
            _changes.value += 1
            persistBackupLocked()
            return@withLock FreezeOutcome.DONE
        }
        when (val lifted = unfreezeOneLocked(key, keepPermanent = false)) {
            FreezeOutcome.DONE -> Unit
            else -> return@withLock lifted
        }
        val frozenAgain = freezeOneLocked(key, { method })
        if (frozenAgain == FreezeOutcome.DONE) {
            prefs.setFreezeMethod(key, method)
            if (method == FreezeMethod.STOP) prefs.setFreezePermanent(key, false)
            _changes.value += 1
            persistBackupLocked()
            return@withLock FreezeOutcome.DONE
        }
        // Best effort: put it back the way it was, so a failed switch is not a freeze lost.
        freezeOneLocked(key, { previous })
        return@withLock FreezeOutcome.FAILED
    }

    /**
     * Marks or unmarks one listed row's permanent flag. Returns how many rows changed.
     *
     * A row that is not on the list has nothing to make permanent, and STOP is a one-shot kill
     * that cannot survive a reboot, so neither takes the flag. Marking is not freezing: the
     * next trigger still does that.
     */
    suspend fun setPermanent(apps: List<AppInfo>, permanent: Boolean): Int = gate.withLock {
        val plan = prefs.freezeMethodsNow()
        val keys = apps.map { it.freezeKey }.filterTo(mutableSetOf()) { key ->
            val method = plan[key] ?: return@filterTo false
            !permanent || method != FreezeMethod.STOP
        }
        if (keys.isEmpty()) return@withLock 0
        val current = prefs.freezePermanentNow()
        val changed = if (permanent) keys - current else keys intersect current
        if (changed.isEmpty()) return@withLock 0
        prefs.setFreezePermanentKeys(if (permanent) current + keys else current - keys)
        _changes.value += 1
        persistBackupLocked()
        changed.size
    }

    /** Takes an app off the list for good, unfreezing it if it was frozen. */
    suspend fun removeFromPlan(app: AppInfo): FreezeOutcome = removeKey(app.freezeKey)

    /**
     * Releases a freeze record that predates the app lock, then drops it.
     *
     * A locked app must not be left in a state only the freeze list knows how to undo: the
     * lock hides and suspends by itself, and `revealForLock` does not re-enable a package
     * frozen with DISABLE. This bypasses the ordinary lock filter because the caller has
     * already authenticated the app; it is the one path allowed to lift that state.
     */
    suspend fun releaseForLock(packageName: String): FreezeOutcome = gate.withLock {
        val key = FreezeTargetKey.of(packageName, 0L)
        val target = targetOf(key) ?: return@withLock FreezeOutcome.FAILED
        loadBackupLocked()
        val plan = prefs.freezeMethodsNow()
        val applied = prefs.freezeAppliedNow()
        if (key !in plan && key !in applied) {
            deepFreeze.release(setOf(key))
            return@withLock FreezeOutcome.DONE
        }
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        val method = plan[key]
        val needsLift = mustLift(key, method, applied, snapshot, target)
        if (needsLift) {
            if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeOutcome.NO_ACCESS
            val lifted = withContext(Dispatchers.IO) {
                liftedIn(listOf(key to method), ignoreLock = true)
            }
            if (key !in lifted) return@withLock FreezeOutcome.FAILED
        }
        prefs.setFreezeApplied(setOf(key), applied = false)
        prefs.removeFreezeTarget(key)
        _changes.value += 1
        persistBackupLocked()
        deepFreeze.release(setOf(key))
        FreezeOutcome.DONE
    }

    suspend fun removeKey(key: String): FreezeOutcome = gate.withLock { removeKeyLocked(key) }

    /**
     * Takes a batch off the list, unfreezing what was frozen. Returns how many rows were
     * actually dealt with; a row that could not be lifted keeps its record.
     */
    suspend fun removeManyFromPlan(apps: List<AppInfo>): Int = gate.withLock {
        apps.count { removeKeyLocked(it.freezeKey) == FreezeOutcome.DONE }
    }

    private suspend fun removeKeyLocked(key: String): FreezeOutcome {
        loadBackupLocked()
        // Removing the row must not take the lock's suspend/hide with it. The lock stays in
        // force and the freeze record simply stops claiming ownership of it. DISABLE is the
        // exception: the lock never owns that state, so dropping its only record would leave
        // the app disabled after the lock is removed. Lift that one before forgetting it.
        if (key in lockedMainKeys()) {
            val method = prefs.freezeMethodsNow()[key]
            val target = targetOf(key)
            if (method == FreezeMethod.DISABLE && target != null) {
                val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
                val applied = prefs.freezeAppliedNow()
                if (mustLift(key, method, applied, snapshot, target)) {
                    if (ensureAccess() == FreezeAccess.NONE) return FreezeOutcome.NO_ACCESS
                    val lifted = withContext(Dispatchers.IO) {
                        liftedIn(listOf(key to method), ignoreLock = true)
                    }
                    if (key !in lifted) return FreezeOutcome.FAILED
                }
            }
            prefs.setFreezeApplied(setOf(key), applied = false)
            prefs.removeFreezeTarget(key)
            _changes.value += 1
            persistBackupLocked()
            deepFreeze.release(setOf(key))
            return FreezeOutcome.DONE
        }
        val target = targetOf(key)
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        val applied = prefs.freezeAppliedNow()
        val method = prefs.freezeMethodsNow()[key]
        val needsLift = target != null && mustLift(key, method, applied, snapshot, target)
        if (needsLift) {
            if (ensureAccess() == FreezeAccess.NONE) return FreezeOutcome.NO_ACCESS
            val lifted = withContext(Dispatchers.IO) {
                liftedIn(listOf(key to method))
            }
            // Keeping the row is better than dropping the only record of a stuck app.
            if (key !in lifted) return FreezeOutcome.FAILED
            prefs.setFreezeApplied(setOf(key), applied = false)
        }
        prefs.removeFreezeTarget(key)
        _changes.value += 1
        persistBackupLocked()
        deepFreeze.release(setOf(key))
        return FreezeOutcome.DONE
    }

    /**
     * How many listed apps the device actually reports as held right now.
     *
     * The applied record answers "what did we do", not "what is still true": an app can be
     * uninstalled or lifted by another tool since. A tile that reads the record showed
     * ACTIVE over an empty run.
     */
    suspend fun heldNowCount(): Int = withContext(Dispatchers.IO) {
        val plan = prefs.freezeMethodsNow()
        if (plan.isEmpty()) return@withContext 0
        val snapshot = FreezeSnapshot.read(context)
        plan.count { (key, method) ->
            val target = targetOf(key) ?: return@count false
            if (snapshot.isOffDevice(target)) return@count false
            // The state this entry's own method would leave it in, not "looks disabled some
            // way": an app another tool disabled must not read as this list being asleep, or
            // the tile would offer to thaw a list it never froze.
            isHeld(snapshot, target, method)
        }
    }

    /**
     * Freezes the whole list, each under its own method.
     *
     * [auto] is the screen-off run: it leaves out the app on screen and any app with a
     * notification, so the thing the user is listening to or looking at does not disappear.
     * A handover asks for the same background run but must not skip those: the guest is
     * holding the phone, and an app left awake is exactly what the handover promised to sleep.
     *
     * The run itself is not cancellable: the commands and the record they end with belong
     * together, and a screen that goes away mid-run must not leave an app frozen with
     * nothing on record to lift it.
     */
    suspend fun freezeAll(
        auto: Boolean = false,
        skipInUse: Boolean = auto,
        /**
         * Whether the packages that hold the phone together are left out. On for the paths
         * with no dialog to ask in (the tile, the corner gesture, a broadcast, a rule); the
         * freeze screen turns it off because it asked first.
         */
        skipBrick: Boolean = auto,
    ): FreezeBatchResult =
        withContext(NonCancellable) {
            gate.withLock {
                if (!prefs.freezeEnabledNow()) return@withLock FreezeBatchResult()
                if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeBatchResult(noAccess = true)
                // A reinstall's first trigger should work from the old list, not an empty one.
                loadBackupLocked()
                val plan = prefs.freezeMethodsNow()
                if (plan.isEmpty()) return@withLock FreezeBatchResult()
                val locked = lockedMainKeys()
                // The focus session's target stays awake even when the user's freeze rules
                // would otherwise sleep everything.
                val focusPackage = prefs.focusNow()
                    .takeIf { it.active }
                    ?.packageName
                    ?.takeIf { it.isNotBlank() }
                // Forced: automatic freezing runs off a timer, and a keyboard the user
                // switched to a moment ago is exactly what the stale copy would miss.
                val guarded = protectedPackages(force = true) + listOfNotNull(focusPackage)
                val systemEnabled = prefs.freezeSystemAppsNow()
                // Unattended runs never take the screen, Settings or the installer: there is
                // nobody to read a warning, and the phone would not come back on its own.
                val bricked = if (skipBrick) {
                    withContext(Dispatchers.IO) { FreezeProtection.loadBearing(context) }
                } else {
                    emptySet()
                }
                val skip = if (skipInUse) withContext(Dispatchers.IO) { autoSkip() } else emptySet()
                val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
                val (frozenBefore, targets) = withContext(Dispatchers.IO) {
                    val gone = mutableSetOf<String>()
                    val allowed = plan.filterKeys { key ->
                        if (key in locked) return@filterKeys false
                        val target = targetOf(key) ?: return@filterKeys false
                        // The list keeps an app through its uninstall on purpose, but there is
                        // nothing to freeze until it comes back: the commands answer "No UID"
                        // and the run counted it as a failure every single time.
                        if (snapshot.isOffDevice(target)) gone += key
                        val state = snapshot.state(target)
                        state?.installed != false &&
                            target.packageName != context.packageName &&
                            target.packageName !in guarded &&
                            target.packageName !in bricked &&
                            (systemEnabled || state?.system != true)
                    }
                    // One state read per app, shared by the skip decision and the record.
                    val seen = mutableMapOf<String, Boolean>()
                    val isFrozen: (String) -> Boolean = { key ->
                        seen.getOrPut(key) {
                            targetOf(key)?.let { target ->
                                looksFrozen(snapshot, target, plan[key]) ||
                                    (plan[key] == FreezeMethod.DISABLE && parkedByPlatform(target))
                            } == true
                        }
                    }
                    val already = allowed.keys.filter { it !in skip && isFrozen(it) }
                    already to FreezeSelection.toFreeze(allowed, skip, isFrozen, gone)
                        .associateWith { allowed.getValue(it) }
                }
                // Only the apps that need it: after the first screen-off every screen-off reads the
                // state of the list instead of running four commands per app all over again.
                val done = withContext(Dispatchers.IO) { frozenIn(targets, snapshot) }
                // An app already asleep is skipped above, which is what makes a second run cheap,
                // but its card in recents and any notification left in the shade were drawn
                // before it went to sleep. A force stop is the one command that clears both.
                // Left out of the screen-off run: it freezes what a previous run already did,
                // and paying for it every time the screen goes off buys nothing.
                if (!auto && frozenBefore.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        frozenIn(frozenBefore.associateWith { FreezeMethod.STOP }, snapshot)
                    }
                }
                val failed = targets.keys - done
                prefs.setFreezeApplied(frozenBefore.toSet() + done, applied = true)
                _changes.value += 1
                persistBackupLocked()
                // Rows the selection skipped as already frozen are hardened here too; without
                // this they would stay unhardened until their next real freeze.
                deepFreeze.sync(prefs.freezeAppliedNow())
                FreezeBatchResult(done = done.size, failed = failed.size)
            }
        }

    /**
     * Unfreezes the whole list, not only what the applied record remembers; the list is left
     * alone. The wider net matters: a record lost to a crash or an older build would
     * otherwise leave an app suspended with no screen willing to lift it.
     *
     * Permanent keys are skipped and keep their applied record: the flag means "only a single
     * unfreeze may touch me". [unfreeze] ignores it.
     *
     * [ignorePermanent] is for the one caller that is tearing the channel down rather than
     * lifting a list: giving up device ownership while a permanent app is suspended would
     * leave that state with no channel left to undo it.
     */
    suspend fun unfreezeAll(ignorePermanent: Boolean = false): FreezeBatchResult = withContext(NonCancellable) {
        gate.withLock {
            // The first Unfreeze all after a reinstall must see the list the old install
            // froze, or it would have nothing to lift.
            loadBackupLocked()
            val appliedNow = prefs.freezeAppliedNow()
            val plan = prefs.freezeMethodsNow()
            if (appliedNow.isEmpty() && plan.isEmpty()) return@withLock FreezeBatchResult()
            val locked = lockedMainKeys()
            val permanent = if (ignorePermanent) emptySet() else prefs.freezePermanentNow()
            val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
            // Permanent rows that were actually holding something back, for an honest toast:
            // a flag on an app that is not frozen (or no longer installed) is not "kept".
            val kept = permanent.count { key ->
                val target = targetOf(key) ?: return@count false
                snapshot.state(target)?.installed == true && looksFrozen(snapshot, target, plan[key])
            }
            val candidates = (appliedNow + plan.keys).distinct()
                .filterNot { it in locked || it in permanent }
            val targets = withContext(Dispatchers.IO) {
                // The record is the answer only for HIDE, whose state cannot be read back; a
                // record left on an enabled app by a reinstall must not demand access.
                val held = candidates.mapNotNull { key ->
                    val target = targetOf(key) ?: return@mapNotNull null
                    // A package that is gone has nothing to lift, even when the old record
                    // was HIDE. Leaving it out lets a no-access unfreezeAll drop the record.
                    if (snapshot.isKnownAbsent(target)) return@mapNotNull null
                    if (snapshot.state(target)?.installed == false) return@mapNotNull null
                    // Unreadable state: try the lift. Assuming "free" here deleted the record
                    // of an app that was still frozen, with nothing left to lift it by.
                    if (snapshot.state(target) == null) return@mapNotNull (key to true)
                    key to looksFrozen(snapshot, target, plan[key])
                }.toMap()
                FreezeSelection.toLift(held.keys, appliedNow, plan, locked, permanent) {
                    held[it] == true
                }
            }
            // Permanent rows survive this run, whatever the record says: the app is still frozen.
            val keep = locked + permanent
            if (targets.isEmpty()) {
                if (appliedNow.any { it !in keep }) {
                    prefs.setFreezeApplied(appliedNow - keep, applied = false)
                    _changes.value += 1
                    persistBackupLocked()
                }
                deepFreeze.sync(prefs.freezeAppliedNow())
                return@withLock FreezeBatchResult(keptPermanent = kept)
            }
            if (ensureAccess() == FreezeAccess.NONE) {
                return@withLock FreezeBatchResult(noAccess = true, keptPermanent = kept)
            }
            val done = withContext(Dispatchers.IO) {
                liftedIn(targets.map { it to plan[it] })
            }
            val failed = targets.filter { it !in done }
            // Locked and permanent apps keep their freeze record: this run deliberately did
            // not lift them, and the record is what a later Unfreeze all works from.
            prefs.setFreezeApplied(appliedNow - failed.toSet() - keep, applied = false)
            _changes.value += 1
            persistBackupLocked()
            deepFreeze.sync(prefs.freezeAppliedNow())
            FreezeBatchResult(done = done.size, failed = failed.size, keptPermanent = kept)
        }
    }

    /**
     * Turns the system-app section on or off. Turning it off also lifts anything system-level
     * that is frozen right now: hiding the rows and leaving those apps disabled would leave
     * the user no way back to them.
     */
    suspend fun setSystemAppsEnabled(enabled: Boolean): FreezeBatchResult = gate.withLock {
        loadBackupLocked()
        var unfrozen = 0
        if (!enabled) {
            val plan = prefs.freezeMethodsNow()
            val locked = lockedMainKeys()
            val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
            val applied = prefs.freezeAppliedNow()
            val systemPlan = plan.keys.filter { key ->
                targetOf(key)?.let { snapshot.state(it)?.system == true } == true
            }
            val frozen = systemPlan.filter { key ->
                // The lock owns a locked app's suspend/hide state; turning the system section
                // off must not take that layer away.
                key !in locked &&
                    targetOf(key)?.let {
                        mustLift(key, plan[key], applied, snapshot, it)
                    } == true
            }
            unfrozen = frozen.size
            if (frozen.isNotEmpty()) {
                if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeBatchResult(noAccess = true)
                val lifted = withContext(Dispatchers.IO) {
                    liftedIn(frozen.map { it to plan[it] })
                }
                prefs.setFreezeApplied(lifted, applied = false)
                if (lifted.size != frozen.size) {
                    return@withLock FreezeBatchResult(
                        done = lifted.size,
                        failed = frozen.size - lifted.size,
                    )
                }
            }
            // Keep a locked app's record: the lock may still be sitting on a freeze state,
            // and dropping the only record of it would leave no way to lift that state.
            val removable = systemPlan.filterNot { it in locked }
            prefs.removeFreezeTargets(removable)
            prefs.setFreezeApplied(removable.toSet(), applied = false)
        }
        prefs.setFreezeSystemApps(enabled)
        _changes.value += 1
        persistBackupLocked()
        FreezeBatchResult(done = unfrozen)
    }

    /** The list as a file the user can keep across phones; frozen state is not part of it. */
    suspend fun exportPlan(): String = FreezePlanFile.encode(
        prefs.freezeMethodsNow(),
        prefs.freezeRowsNow(),
        prefs.freezePinnedNow(),
        prefs.freezePermanentNow(),
    )

    /**
     * Adds the apps from an exported list that are installed and not already listed.
     * Returns how many joined, or -1 when the file is not one of ours.
     *
     * Pins and permanent flags travel with the file, so the apps the user keeps asleep through
     * a bulk lift stay that way on the new phone. Both are only restored for an app that is
     * actually listed afterwards, and the list's own flags win over the file's.
     */
    suspend fun importPlan(text: String): Int {
        val file = FreezePlanFile.decodePlan(text) ?: return -1
        val entries = file.entries
        if (entries.isEmpty()) return 0
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        val current = prefs.freezeMethodsNow()
        // Same guards as adding one app: a file from another phone may name this launcher,
        // a protected app or a system app while the system switch is off.
        val guarded = protectedPackages()
        val systemEnabled = prefs.freezeSystemAppsNow()
        val usable = entries.filter { (key, _) ->
            val target = targetOf(key) ?: return@filter false
            key !in current &&
                target.packageName != context.packageName &&
                target.packageName !in guarded &&
                (systemEnabled || snapshot.state(target)?.system != true) &&
                snapshot.state(target)?.installed == true
        }
        val incomingPins = usable.keys.filter { it in file.pinned }
        val incomingPermanent = usable.keys.filter { it in file.permanent }
        if (usable.isEmpty()) return 0
        val rows = withContext(Dispatchers.IO) {
            usable.mapValues { (key, entry) ->
                val packageName = FreezeTargetKey.packageName(key)
                val label = entry.label ?: FreezeState.applicationInfo(context, packageName)
                    ?.let { runCatching { context.packageManager.getApplicationLabel(it).toString() }
                        .getOrNull() }
                FreezeRow(label, FreezeState.launcherComponent(context, packageName)?.flattenToString())
            }
        }
        prefs.setFreezeRows(rows)
        prefs.setFreezeMethods(usable.mapValues { it.value.method })
        // Flags last: the record is what makes a pin or a permanent flag meaningful, so a
        // failure before this point leaves nothing pointing at an app the list never received.
        // Only for apps this import actually adds, the same rule the methods follow: a key
        // already on this phone's list keeps the flags the user gave it here.
        if (incomingPins.isNotEmpty()) {
            prefs.addFreezePinnedKeys(incomingPins.toSet())
        }
        if (incomingPermanent.isNotEmpty()) {
            prefs.addFreezePermanentKeys(incomingPermanent.toSet())
        }
        _changes.value += 1
        return usable.size
    }

    private suspend fun systemAllowed(isSystem: Boolean): Boolean =
        prefs.freezeSystemAppsNow() || !isSystem

    private suspend fun systemAllowed(target: FreezeTarget, snapshot: FreezeSnapshot): Boolean =
        systemAllowed(snapshot.state(target)?.system == true)

    /**
     * The apps no freeze may touch while the guard is on; read at most every half minute.
     *
     * The app that answers calls rides the same switch as the SMS app rather than being
     * unreleasable: on a phone whose SIM is data only, neither can ring or arrive, and the
     * guard's description says which two rows turning it off hands back.
     */
    suspend fun protectedPackages(force: Boolean = false): Set<String> {
        if (!prefs.freezeProtectNow()) return emptySet()
        val dialer = setOfNotNull(FreezeProtection.dialer(context))
        val now = SystemClock.elapsedRealtime()
        if (!force && now - protectedAt < PROTECT_TTL_MS) return protectedSet + dialer
        val packages = withContext(Dispatchers.IO) {
            FreezeProtection.packages(context, shell.protectedPackages())
        }
        protectedSet = packages
        protectedAt = now
        return packages + dialer
    }

    /** Reads the two dumps in one elevated shell, only when the settings ask for them. */
    private suspend fun autoSkip(): Set<String> {
        val wantForeground = prefs.freezeAutoSkipForegroundNow()
        val wantNotifications = prefs.freezeAutoSkipNotificationsNow()
        if (!wantForeground && !wantNotifications) return emptySet()
        val script = buildString {
            if (wantForeground) append(FOREGROUND_DUMP).append('\n')
            if (wantNotifications) append(NOTIFICATION_DUMP)
        }
        val output = shell.output(script) ?: return emptySet()
        val skip = mutableSetOf<String>()
        if (wantForeground) FreezeGuards.foregroundPackage(output)?.let(skip::add)
        if (wantNotifications) skip += FreezeGuards.notifiedPackages(output)
        return skip
    }

    /**
     * Freezes [targets] and answers with the set that took.
     *
     * Shizuku's binder path does one IPC per app where the shell path forks a `pm` process per
     * command. Hiding needs root: when Shizuku itself runs as root the binder can do it too,
     * and only a shell-mode manager falls back to the root shell.
     * Whatever the binder could not do falls back to the command path.
     */
    private suspend fun frozenIn(
        targets: Map<String, FreezeMethod>,
        snapshot: FreezeSnapshot,
    ): Set<String> {
        if (targets.isEmpty()) return emptySet()
        val locked = lockedMainKeys()
        val resolved = targets
            .filterKeys { it !in locked }
            .mapNotNull { (key, method) ->
                val target = targetOf(key) ?: return@mapNotNull null
                // Uninstalled while the run was being set up: nothing to send commands to.
                if (snapshot.isOffDevice(target)) return@mapNotNull null
                key to (method to target)
            }
        if (resolved.isEmpty()) return emptySet()
        // Hardening comes first: the app is already muted by the time it is suspended, not a
        // moment later. A failed freeze is rolled back below. Only suspend rows take app-ops:
        // stop is a one-shot kill, disable and hide already stop every component.
        // The cleanup pass runs its targets as STOP even when the row is held by a suspend,
        // so the plan's method decides, not the pass's.
        val plan = prefs.freezeMethodsNow()
        val hardening = resolved.filter { (key, pair) ->
            pair.first == FreezeMethod.SUSPEND || plan[key] == FreezeMethod.SUSPEND
        }.associate { (key, pair) -> key to pair.second }
        deepFreeze.harden(hardening)
        val channels = prefs.freezeChannelsNow()
        fun allowed(method: FreezeMethod, executor: FreezeExecutor): Boolean =
            executor in FreezeRoute.executors(method, channels[method] ?: FreezeChannel.AUTO)
        val done = mutableSetOf<String>()
        if (policy.active) {
            val ownerDone = withContext(Dispatchers.IO) {
                resolved.mapNotNull { (key, pair) ->
                    val (method, target) = pair
                    if (!allowed(method, FreezeExecutor.POLICY)) return@mapNotNull null
                    if (policy.freeze(target.packageName, method, target.userId) == true) key else null
                }
            }
            done += ownerDone
        }
        val canHide = binder.available() && binder.canHide()
        val binderable = resolved.filter { (key, pair) ->
            key !in done &&
                allowed(pair.first, FreezeExecutor.BINDER) &&
                (pair.first != FreezeMethod.HIDE || canHide)
        }
        if (binder.available() && binderable.isNotEmpty()) {
            val results = withContext(Dispatchers.IO) {
                binderable.map { (key, pair) ->
                    val (method, target) = pair
                    async {
                        key to (binder.freeze(
                            target.packageName,
                            method,
                            target.userId,
                            snapshot.state(target)?.uid ?: -1,
                        ) == true)
                    }
                }.awaitAll()
            }
            done += results.filter { it.second }.map { it.first }
        }
        fun shellRun(
            entries: List<Pair<String, Pair<FreezeMethod, FreezeTarget>>>,
            root: Boolean?,
        ) {
            if (entries.isEmpty()) return
            val answers = shell.runGroups(
                entries.map { (_, pair) ->
                    val (method, target) = pair
                    FreezeCommands.freeze(target.packageName, method, target.userId)
                },
                root = root,
            )
            done += entries.indices.filter { answers.getOrNull(it) == true }.map { entries[it].first }
        }
        // Root is asked for the methods that picked it, and for hiding under Auto, where a
        // plain shell cannot do the job. An explicit Shizuku channel is left to the binder;
        // a failure there is reported, not quietly rerouted.
        val rootShell = resolved.filter { (key, pair) ->
            key !in done && allowed(pair.first, FreezeExecutor.ROOT_SHELL)
        }
        if (rootShell.isNotEmpty() && shell.rootAvailable()) shellRun(rootShell, root = true)
        val left = resolved.filter { (key, pair) ->
            key !in done && allowed(pair.first, FreezeExecutor.SHELL)
        }
        if (left.isNotEmpty()) shellRun(left, root = null)
        // A row that did not freeze must not keep restrictions meant for a frozen app. Rows
        // already on the applied record stay hardened: they are still asleep.
        val failed = resolved.map { it.first }.toSet() - done
        if (failed.isNotEmpty()) {
            val applied = prefs.freezeAppliedNow()
            val rollback = failed.filterNot { it in applied }.toSet()
            if (rollback.isNotEmpty()) deepFreeze.release(rollback)
        }
        return done
    }

    /**
     * Lifts every known kind of freeze, not only the one the list remembers.
     *
     * The stored method can be wrong — frozen by another tool, or by an older build — and
     * following only it leaves the app stuck, so the whole set is run and each command is
     * harmless when the app is not in that state.
     *
     * A lift is only believed when the platform says the app is free again. Android records
     * who suspended a package: one suspended by root answers "still suspended" to Shizuku's
     * shell while the command still exits 0, which is exactly how apps were left "managed by
     * root" after the record had already been cleared. Anything still held after the binder
     * pass is retried through the root shell, which can undo root.
     */
    private suspend fun liftedIn(
        targets: List<Pair<String, FreezeMethod?>>,
        /** The app lock calls this for a package it owns; it is the one allowed past the filter. */
        ignoreLock: Boolean = false,
    ): Set<String> {
        if (targets.isEmpty()) return emptySet()
        val locked = if (ignoreLock) emptySet() else lockedMainKeys()
        val resolved = targets.distinctBy { it.first }
            .filterNot { it.first in locked }
            .mapNotNull { (key, method) -> targetOf(key)?.let { key to (method to it) } }
        if (resolved.isEmpty()) return emptySet()
        val keys = resolved.mapTo(mutableSetOf()) { it.first }
        var states = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        if (policy.active) {
            withContext(Dispatchers.IO) {
                resolved.map { (_, pair) ->
                    val target = pair.second
                    async { policy.unfreeze(target.packageName, target.userId) }
                }.awaitAll()
            }
            states = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        }
        if (binder.available()) {
            withContext(Dispatchers.IO) {
                resolved.map { (key, pair) ->
                    val (_, target) = pair
                    async {
                        binder.unfreeze(
                            target.packageName,
                            target.userId,
                            states.state(target)?.uid ?: -1,
                        )
                        key
                    }
                }.awaitAll()
            }
            states = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        }
        val canHide = policy.active || (binder.available() && binder.canHide())
        var pending = withContext(Dispatchers.IO) {
            resolved.filter { (_, pair) ->
                val (method, target) = pair
                // An unreadable hidden flag is not proof of a failed unhide: the command's
                // own exit status is the only answer this phone gives, and pretending
                // otherwise pinned every HIDE row to "still frozen" forever.
                !freeNow(states, target, method) ||
                    (method == FreezeMethod.HIDE && !canHide)
            }.map { it.first to it.second.first }
        }
        // A suspension is recorded against whoever made it and only that identity can lift it,
        // so both are tried before a command is called a failure. Each pass is method-specific
        // first (a suspend costs three commands, not five); the full net comes last.
        if (pending.isNotEmpty() && shell.rootAvailable()) {
            pending = shellLift(pending, perMethod = true, root = true)
        }
        if (pending.isNotEmpty() && shell.shizukuAvailable()) {
            pending = shellLift(pending, perMethod = true, root = false)
        }
        if (pending.isNotEmpty()) {
            pending = shellLift(
                pending,
                perMethod = false,
                root = if (shell.rootAvailable()) true else null,
            )
        }
        // A pause recorded under the other identity is invisible to the command above. Root
        // can still lift it by running the command as the shell uid.
        if (pending.isNotEmpty() && shell.rootAvailable()) {
            pending = shellLift(pending, perMethod = false, root = true, asShell = true)
        }
        val failed = pending.mapTo(mutableSetOf()) { it.first }
        val done = resolved.map { it.first }.filterNot { it in failed }.toSet()
        // The app-ops go back after the lift, because the method's own `allow` would
        // overwrite an earlier restore.
        if (done.isNotEmpty()) deepFreeze.release(done)
        return done
    }

    /**
     * The packages the other profiles still report as disabled, one `pm list` per profile.
     *
     * A failed read answers with an empty set on purpose: the profile is then judged by the
     * command's exit status, which is what the old behaviour did, rather than by a guess.
     */
    private fun disabledInOtherProfiles(
        resolved: List<Pair<String, Pair<FreezeMethod?, FreezeTarget>>>,
    ): Set<String> {
        val userIds = resolved.mapNotNull { it.second.second.userId.takeIf { id -> id != 0 } }.toSet()
        if (userIds.isEmpty()) return emptySet()
        return userIds.flatMap { userId ->
            shell.output("pm list packages -d --user $userId")?.lineSequence()
                ?.map { it.trim().removePrefix("package:") }
                ?.filter { FreezeCommands.isPackageName(it) }
                ?.toList()
                .orEmpty()
        }.toSet()
    }

    private suspend fun shellLift(
        pending: List<Pair<String, FreezeMethod?>>,
        perMethod: Boolean,
        root: Boolean?,
        /** Runs each unsuspend as the shell uid instead of the caller this channel has. */
        asShell: Boolean = false,
    ): List<Pair<String, FreezeMethod?>> {
        val resolved = pending.mapNotNull { (key, method) ->
            targetOf(key)?.let { key to (method to it) }
        }
        if (resolved.isEmpty()) return emptyList()
        val answers = shell.runGroups(
            resolved.map { (_, pair) ->
                val (method, target) = pair
                val lift = when {
                    asShell -> FreezeCommands.unfreezeAsShell(target.packageName, target.userId)
                    perMethod && method != null ->
                        FreezeCommands.unfreeze(target.packageName, method, target.userId)
                    else -> FreezeCommands.unfreezeEverything(target.packageName, target.userId)
                }
                // A parked package answers "Shell cannot change component state" to every lift;
                // starting it once is what the platform accepts as "used" and enables it.
                if (FreezeState.isParkedForUse(context, target.packageName)) {
                    FreezeCommands.unpark(target.packageName, target.userId) + lift
                } else {
                    lift
                }
            },
            root = if (asShell) true else root,
        )
        val states = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        // The package manager's own list, per user, is the only answer a work profile gives:
        // LauncherApps cannot read another profile here, and a command that exits cleanly is
        // not proof by itself.
        val stillDisabled = withContext(Dispatchers.IO) { disabledInOtherProfiles(resolved) }
        return withContext(Dispatchers.IO) {
            resolved.filterIndexed { index, (_, pair) ->
                val (method, target) = pair
                val answered = answers.getOrNull(index) == true
                if (target.userSerial != 0L && target.packageName in stillDisabled) return@filterIndexed true
                // Hiding has no public state to read; when the hidden flag can be read, it is
                // the only proof the app came back. Another profile's flags are read through
                // LauncherApps only, so a clean command is the only answer there is.
                val verifiable = if (method == FreezeMethod.HIDE) {
                    target.userSerial == 0L &&
                        FreezeState.applicationInfo(context, target.packageName, 0L)
                            ?.let { FreezeState.hiddenState(it) != null } == true
                } else {
                    FreezeSelection.verifiable(method) && target.userSerial == 0L
                }
                !answered || (verifiable && !freeNow(states, target, method))
            }.map { it.first to it.second.first }
        }
    }

    /**
     * The freeze keys whose suspend/hide state belongs to the app lock, not to this list.
     *
     * Only the main profile can be hidden or suspended by the lock; a work-profile lock is
     * the password gate alone and must keep freezing normally.
     */
    private suspend fun lockedMainKeys(): Set<String> =
        prefs.lockedAppsNow().asSequence()
            .filterNot { EntryKeys.isShortcut(it) || EntryKeys.isPrivateSpace(it) }
            .filter { EntryKeys.userSerial(it) == 0L }
            .mapTo(mutableSetOf()) { FreezeTargetKey.of(it.substringBefore('/'), 0L) }

    private fun targetOf(key: String): FreezeTarget? {
        val packageName = FreezeTargetKey.packageName(key)
        if (!FreezeCommands.isPackageName(packageName)) return null
        val serial = FreezeTargetKey.userSerial(key)
        if (serial == 0L) return FreezeTarget(packageName, 0, 0L)
        // A command takes the user id, while the key carries the serial every other list uses.
        val user = runCatching { userManager.getUserForSerialNumber(serial) }.getOrNull()
            ?: return null
        val userId = user.userId ?: return null
        return FreezeTarget(packageName, userId, serial)
    }

    /** Whether the device reports the app as held, using the one state read of this pass. */
    private fun looksFrozen(
        snapshot: FreezeSnapshot,
        target: FreezeTarget,
        method: FreezeMethod?,
    ): Boolean {
        if (snapshot.isKnownAbsent(target)) return false
        // A state that could not be read is not proof of anything. Saying "frozen" here made
        // the whole run a silent no-op.
        return FreezeVerdict.held(snapshot.state(target), parkedByPlatform(target))
    }

    /**
     * Whether lifting [key] is actually needed.
     *
     * The applied record used to count on its own. That is wrong after an uninstall and
     * reinstall: the new package is enabled and free, but the old record remains, and asking
     * for root before dropping it leaves the app looking frozen on a device where access was
     * lost. DISABLE and SUSPEND have readable state; HIDE does not, so only HIDE still trusts
     * the record. A package that is gone has nothing to lift.
     */
    private fun mustLift(
        key: String,
        method: FreezeMethod?,
        applied: Set<String>,
        snapshot: FreezeSnapshot,
        target: FreezeTarget,
    ): Boolean {
        if (snapshot.isOffDevice(target)) return false
        if (snapshot.state(target)?.installed == false) return false
        // Unreadable state: ask the commands rather than assume the record is stale.
        if (snapshot.state(target) == null) return true
        return looksFrozen(snapshot, target, method) ||
            (key in applied && method == FreezeMethod.HIDE)
    }

    /** The platform's own "disabled until used" spot is neither a freeze nor ours to lift. */
    private fun parkedByPlatform(target: FreezeTarget): Boolean =
        target.userSerial == 0L && FreezeState.isParkedForUse(context, target.packageName)

    /** Whether the app is in the exact state [method] would leave it in. */
    private fun isHeld(snapshot: FreezeSnapshot, target: FreezeTarget, method: FreezeMethod): Boolean {
        val state = snapshot.state(target) ?: return false
        return when (method) {
            FreezeMethod.DISABLE -> !state.enabled && !parkedByPlatform(target)
            FreezeMethod.HIDE -> state.hidden
            FreezeMethod.SUSPEND -> state.suspended
            FreezeMethod.STOP -> state.stopped
        }
    }

    /**
     * Whether the device reports the app as free again, on every state it can read.
     *
     * The suspend flag in [FreezeSnapshot] comes from the package manager's user state, but a
     * lift must not be believed on a flag alone: `isPackageSuspended` is the answer the lift
     * path has always trusted, and a wrong "free" here is exactly what leaves an app stuck
     * behind a record that says it was lifted. Another profile cannot be asked that way, so
     * there the snapshot's flag is all there is.
     */
    private fun freeNow(
        snapshot: FreezeSnapshot,
        target: FreezeTarget,
        method: FreezeMethod?,
    ): Boolean {
        val state = snapshot.state(target) ?: return false
        if (!state.installed) return true
        // Hiding is a separate flag; an enabled app can still be hidden, and the old check
        // only looked at enabled/suspended, so a failed unhide was read as a success.
        if (method == FreezeMethod.HIDE) {
            val info = FreezeState.applicationInfo(context, target.packageName, target.userSerial)
                ?: return true
            return FreezeState.hiddenState(info)?.not() ?: false
        }
        val suspended = if (target.userSerial == 0L) suspendedNow(target) else state.suspended
        return FreezeVerdict.free(state, suspended, parkedByPlatform(target))
    }

    private fun suspendedNow(target: FreezeTarget): Boolean = target.userSerial == 0L && runCatching {
        context.packageManager.isPackageSuspended(target.packageName)
    }.getOrDefault(false)

    companion object {
        /** Printed instead of a file body when the backup does not exist yet. */
        private const val BACKUP_ABSENT = "__deluge_no_backup__"

        /** Long enough to cover a burst of taps, short enough to notice a revoked grant. */
        private const val PROBE_TTL_MS = 10_000L
        private const val PROTECT_TTL_MS = 30_000L

        private const val FOREGROUND_DUMP =
            "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|mFocusedApp'"
        private const val NOTIFICATION_DUMP =
            "dumpsys notification --noredact | grep 'pkg='"
    }
}

/**
 * The freeze list as a small JSON file, so moving to another phone does not mean rebuilding
 * it by hand. Only the list travels: which apps are frozen right now is a fact about one
 * device and would grey out apps that were never touched.
 */
object FreezePlanFile {

    data class Entry(val method: FreezeMethod, val label: String?)

    /**
     * The whole file: the list, which keys are pinned, and which are permanent. Version 1
     * had no pins; version 2 had no permanent flag. Both decode with what was absent left
     * empty rather than being refused.
     */
    data class Plan(
        val entries: Map<String, Entry>,
        val pinned: Set<String> = emptySet(),
        val permanent: Set<String> = emptySet(),
    ) {
        /** Kept so a caller that only wants the list reads the same as before. */
        val keys: Set<String> get() = entries.keys
    }

    fun encode(
        plan: Map<String, FreezeMethod>,
        rows: Map<String, FreezeRow>,
        pinned: Set<String> = emptySet(),
        permanent: Set<String> = emptySet(),
    ): String {
        val apps = org.json.JSONArray()
        plan.forEach { (key, method) ->
            apps.put(
                org.json.JSONObject().apply {
                    put("key", key)
                    put("method", method.name)
                    rows[key]?.label?.takeIf { it.isNotBlank() }?.let { put("label", it.take(MAX_LABEL)) }
                }
            )
        }
        fun keys(values: Set<String>): org.json.JSONArray {
            val array = org.json.JSONArray()
            // A flag only means something for a key the file actually carries.
            values.filter { it in plan }.sorted().forEach { array.put(it) }
            return array
        }
        return org.json.JSONObject()
            .put("version", VERSION)
            .put("apps", apps)
            .put("pinned", keys(pinned))
            .put("permanent", keys(permanent))
            .toString()
    }

    /** Null for anything that is not a file this build wrote or an older one it still reads. */
    fun decode(text: String): Map<String, Entry>? = decodePlan(text)?.entries

    /** The whole file, pins included; version 1 files decode with an empty pinned set. */
    fun decodePlan(text: String): Plan? = runCatching {
        if (text.length > MAX_FILE_CHARS) return null
        val root = org.json.JSONObject(text)
        val version = root.optInt("version")
        if (version !in MIN_VERSION..VERSION) return null
        val apps = root.optJSONArray("apps") ?: return null
        if (apps.length() > MAX_ENTRIES) return null
        val entries = buildMap {
            for (index in 0 until apps.length()) {
                val item = apps.optJSONObject(index) ?: continue
                val key = item.optString("key")
                val packageName = FreezeTargetKey.packageName(key)
                if (!FreezeCommands.isPackageName(packageName)) continue
                // Reject aliases such as a main-profile app written as `pkg|u0`, and serials
                // that overflowed to a main-profile key.
                if (key != FreezeTargetKey.of(packageName, FreezeTargetKey.userSerial(key))) continue
                val method = FreezeMethod.entries
                    .firstOrNull { it.name == item.optString("method") } ?: continue
                val label = item.optString("label")
                    .takeIf { it.isNotBlank() }
                    ?.take(MAX_LABEL)
                    ?.filterNot { Character.isISOControl(it) }
                put(key, Entry(method, label))
            }
        }
        // Absent in version 1, which is exactly what "no pins" looked like there.
        val pinnedArray = root.optJSONArray("pinned") ?: org.json.JSONArray()
        fun readSet(array: org.json.JSONArray): Set<String> = buildSet {
            for (index in 0 until minOf(array.length(), MAX_ENTRIES)) {
                val key = array.optString(index)
                // A flag for anything the file does not list would be a ghost exemption.
                if (key in entries) add(key)
            }
        }
        Plan(
            entries,
            readSet(pinnedArray),
            // Absent before version 3: those files simply had no permanent rows.
            readSet(root.optJSONArray("permanent") ?: org.json.JSONArray()),
        )
    }.getOrNull()

    /** The newest format this build writes; [MIN_VERSION] is the oldest it still reads. */
    private const val VERSION = 3
    private const val MIN_VERSION = 1
    private const val MAX_FILE_CHARS = 1_000_000
    private const val MAX_ENTRIES = 5_000
    private const val MAX_LABEL = 120
}

/**
 * The freeze list plus its applied state, kept outside the app's data directory.
 *
 * The exported plan file is for moving to another phone and deliberately omits applied
 * state. This one has the opposite job: after Deluge itself is uninstalled and installed
 * again, the apps it froze are still held by the system, and without their old records the
 * fresh install has no screen that knows how to lift them. It is written through the same
 * root/Shizuku channel the freeze itself needs, so it is no wider access than the feature.
 */
object FreezeBackup {

    data class Snapshot(
        val firstInstallTime: Long,
        val enabled: Boolean = true,
        val systemApps: Boolean = false,
        val protect: Boolean = true,
        val methods: Map<String, FreezeMethod> = emptyMap(),
        val applied: Set<String> = emptySet(),
        val rows: Map<String, FreezeRow> = emptyMap(),
        /** Lock keys, so a reinstall can still lift hide/suspend the lock applied. */
        val locked: Set<String> = emptySet(),
        /** Keys a bulk lift must leave frozen, so the pin survives a reinstall too. */
        val pinned: Set<String> = emptySet(),
        /** Keys only a single unfreeze may lift, restored so a reinstall keeps them asleep. */
        val permanent: Set<String> = emptySet(),
    )

    fun encode(snapshot: Snapshot): String {
        val methods = org.json.JSONObject()
        snapshot.methods.forEach { (key, method) ->
            if (validKey(key)) methods.put(key, method.name)
        }
        val rows = org.json.JSONObject()
        snapshot.rows.forEach { (key, row) ->
            if (!validKey(key)) return@forEach
            val item = org.json.JSONObject()
            row.label?.takeIf { it.isNotBlank() }
                ?.take(MAX_LABEL)
                ?.filterNot { Character.isISOControl(it) }
                ?.let { item.put("label", it) }
            row.component?.takeIf { it.isNotBlank() }
                ?.take(MAX_COMPONENT)
                ?.filterNot { Character.isISOControl(it) }
                ?.let { item.put("component", it) }
            if (item.length() > 0) rows.put(key, item)
        }
        val applied = org.json.JSONArray()
        snapshot.applied.filter(::validKey).forEach { applied.put(it) }
        val locked = org.json.JSONArray()
        snapshot.locked.filter(::validLockKey).forEach { locked.put(it) }
        val pinned = org.json.JSONArray()
        // Only a key the backup also lists: a pin with no list entry is unreadable anyway.
        snapshot.pinned.filter { validKey(it) && it in snapshot.methods }.forEach { pinned.put(it) }
        val permanent = org.json.JSONArray()
        snapshot.permanent.filter { validKey(it) && it in snapshot.methods }.forEach { permanent.put(it) }
        return org.json.JSONObject()
            .put("version", VERSION)
            .put("firstInstall", snapshot.firstInstallTime)
            .put("enabled", snapshot.enabled)
            .put("systemApps", snapshot.systemApps)
            .put("protect", snapshot.protect)
            .put("methods", methods)
            .put("applied", applied)
            .put("rows", rows)
            .put("locked", locked)
            .put("pinned", pinned)
            .put("permanent", permanent)
            .toString()
    }

    /** Null for anything that is not a backup this build wrote or still reads. */
    fun decode(text: String): Snapshot? = runCatching {
        if (text.length > MAX_FILE_CHARS) return null
        val root = org.json.JSONObject(text)
        if (root.optInt("version") !in MIN_VERSION..VERSION) return null
        val methods = root.optJSONObject("methods") ?: org.json.JSONObject()
        if (methods.length() > MAX_ENTRIES) return null
        val decodedMethods = buildMap {
            val keys = methods.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!validKey(key)) continue
                val method = FreezeMethod.entries
                    .firstOrNull { it.name == methods.optString(key) } ?: continue
                put(key, method)
            }
        }
        val appliedArray = root.optJSONArray("applied") ?: org.json.JSONArray()
        val decodedApplied = buildSet {
            for (index in 0 until minOf(appliedArray.length(), MAX_ENTRIES)) {
                val key = appliedArray.optString(index)
                if (key in decodedMethods) add(key)
            }
        }
        val rows = root.optJSONObject("rows") ?: org.json.JSONObject()
        val decodedRows = buildMap {
            val keys = rows.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!validKey(key)) continue
                val item = rows.optJSONObject(key) ?: continue
                val label = item.optString("label")
                    .takeIf { it.isNotBlank() }
                    ?.take(MAX_LABEL)
                    ?.filterNot { Character.isISOControl(it) }
                val component = item.optString("component")
                    .takeIf { it.isNotBlank() }
                    ?.take(MAX_COMPONENT)
                    ?.filterNot { Character.isISOControl(it) }
                if (label != null || component != null) put(key, FreezeRow(label, component))
            }
        }
        val lockedArray = root.optJSONArray("locked") ?: org.json.JSONArray()
        val decodedLocked = buildSet {
            for (index in 0 until minOf(lockedArray.length(), MAX_ENTRIES)) {
                val key = lockedArray.optString(index)
                if (validLockKey(key)) add(key)
            }
        }
        val pinnedArray = root.optJSONArray("pinned") ?: org.json.JSONArray()
        val decodedPinned = buildSet {
            for (index in 0 until minOf(pinnedArray.length(), MAX_ENTRIES)) {
                val key = pinnedArray.optString(index)
                if (key in decodedMethods) add(key)
            }
        }
        val permanentArray = root.optJSONArray("permanent") ?: org.json.JSONArray()
        val decodedPermanent = buildSet {
            for (index in 0 until minOf(permanentArray.length(), MAX_ENTRIES)) {
                val key = permanentArray.optString(index)
                if (key in decodedMethods) add(key)
            }
        }
        Snapshot(
            firstInstallTime = root.optLong("firstInstall", 0L),
            enabled = root.optBoolean("enabled", true),
            systemApps = root.optBoolean("systemApps", false),
            protect = root.optBoolean("protect", true),
            methods = decodedMethods,
            applied = decodedApplied,
            rows = decodedRows,
            locked = decodedLocked,
            pinned = decodedPinned,
            permanent = decodedPermanent,
        )
    }.getOrNull()

    private fun validKey(key: String): Boolean {
        val packageName = FreezeTargetKey.packageName(key)
        if (!FreezeCommands.isPackageName(packageName)) return false
        return key == FreezeTargetKey.of(packageName, FreezeTargetKey.userSerial(key))
    }

    /** Lock keys can name an app, a shortcut, the private space or search, not only a package. */
    private fun validLockKey(key: String): Boolean {
        if (key.isEmpty() || key.length > MAX_LOCK_KEY) return false
        if (key.any { Character.isISOControl(it) }) return false
        if (key == EntryKeys.SEARCH || key == EntryKeys.PRIVATE_SPACE) return true
        if (EntryKeys.isShortcut(key)) return EntryKeys.parseShortcut(key) != null
        val slash = key.indexOf('/')
        if (slash <= 0) return false
        val packageName = key.substring(0, slash)
        if (!FreezeCommands.isPackageName(packageName)) return false
        // The class part may move after an update, but it must still look like a component.
        return key.substring(slash + 1).substringBefore('|').isNotEmpty()
    }

    private const val VERSION = 2
    private const val MIN_VERSION = 1
    private const val MAX_FILE_CHARS = 1_000_000
    private const val MAX_ENTRIES = 5_000
    private const val MAX_LABEL = 120
    private const val MAX_COMPONENT = 512
    private const val MAX_LOCK_KEY = 1_200
}

/**
 * The freeze screen's order, kept out of the composable so a test can pin the rule.
 *
 * [nameOf] is the name to sort by, resolved from renames by the screen; [pinyin] sorts Chinese
 * names by their romanised form, so ZhiFuBao lands under Z rather than at its codepoint.
 */
object FreezeListing {

    fun sorted(
        apps: List<AppInfo>,
        sort: FreezeSort,
        nameOf: (AppInfo) -> String,
        pinyin: Boolean,
    ): List<AppInfo> {
        val key: (AppInfo) -> String = { app ->
            val name = nameOf(app)
            if (pinyin) Pinyin.latin(name) else name.lowercase(java.util.Locale.ROOT)
        }
        val comparator = when (sort) {
            FreezeSort.NAME_ASC -> compareBy(key)
            FreezeSort.NAME_DESC -> compareByDescending(key)
            FreezeSort.INSTALLED -> compareByDescending<AppInfo> { it.installedAt }.thenBy(key)
            FreezeSort.UPDATED -> compareByDescending<AppInfo> { it.updatedAt }.thenBy(key)
        }
        return apps.sortedWith(comparator)
    }
}
