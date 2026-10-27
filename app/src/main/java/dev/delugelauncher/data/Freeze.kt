// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

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
 * The four ways an app can be put out of the way, one per tab of the freeze screen.
 * Declaration order is the tab order: the gentlest method that needs no special right first.
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

/** What the device gives this app to work with. */
enum class FreezeAccess { ROOT, SHIZUKU, NONE }

/** Which root implementation answered `su`. */
enum class RootProvider { MAGISK, KERNELSU, APATCH, SUPERSU, OTHER, NONE }

/** The permission picture the freeze screen reports. */
data class FreezeStatus(
    val access: FreezeAccess = FreezeAccess.NONE,
    /** Root may exist without being the channel in use: hiding apps needs it either way. */
    val rootGranted: Boolean = false,
    val rootProvider: RootProvider = RootProvider.NONE,
    val rootVersion: String? = null,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuAuthorized: Boolean = false,
    val shizukuVersion: Int = 0,
)

/** How one freeze or unfreeze ended, so the caller can say why instead of failing silently. */
enum class FreezeOutcome {
    DONE,
    NO_ACCESS,
    SYSTEM_APPS_OFF,
    NOT_LISTED,
    FAILED,
    DISABLED,

    /** One of the apps that keeps the phone usable: keyboard, home, Shizuku or root. */
    PROTECTED,
}

/** What a whole-list run managed. */
data class FreezeBatchResult(
    val done: Int = 0,
    val failed: Int = 0,
    val noAccess: Boolean = false,
)

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
    ): List<String> = plan.entries
        .filter { (packageName, method) ->
            packageName !in skip && (method == FreezeMethod.STOP || !isFrozen(packageName))
        }
        .map { it.key }

    /**
     * The apps a whole-list lift has to touch: anything the device still reports as held,
     * plus a HIDE record because that state cannot be read back. A DISABLE or SUSPEND record
     * on an app the platform reports as free is stale — a reinstall leaves exactly that —
     * and asking for access to lift nothing would block the list on a device without root.
     * A STOP app has nothing to undo and is left out unless it is genuinely held.
     */
    fun toLift(
        candidates: Collection<String>,
        applied: Set<String>,
        plan: Map<String, FreezeMethod>,
        locked: Set<String> = emptySet(),
        isFrozen: (String) -> Boolean,
    ): List<String> = candidates.filter { packageName ->
        if (packageName in locked) return@filter false
        val method = plan[packageName]
        val frozen = isFrozen(packageName)
        if (method == FreezeMethod.STOP && !frozen) false
        else frozen || (packageName in applied && method == FreezeMethod.HIDE)
    }

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
     * `PRIVATE_FLAG_HIDDEN` has no public accessor. Reflection is best-effort: where it is
     * refused the caller falls back to "absent from the launcher's list", which hidden apps
     * are anyway.
     */
    fun isHidden(info: ApplicationInfo): Boolean = runCatching {
        val field = ApplicationInfo::class.java.getField("privateFlags")
        field.getInt(info) and 1 != 0
    }.getOrDefault(false)
}

/**
 * The apps a freeze must never touch while the guard is on: the current keyboard, the home
 * app, enabled accessibility services, and the tools that grant this feature its power.
 * Freezing any of them can leave the phone unusable with no way back into the screen that
 * would lift them.
 */
object FreezeProtection {
    fun packages(context: Context, privileged: Set<String>): Set<String> = buildSet {
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
}

/** A shell that runs commands as root (if the device grants it) or through Shizuku. */
class FreezeShell(private val context: Context) {

    private data class RootState(val provider: RootProvider, val version: String?)

    private var rootState: RootState? = null

    /** When `su` last refused; avoids turning every resume into another prompt. */
    private var rootMissAt = 0L

    fun status(): FreezeStatus {
        val root = rootState()
        val running = shizukuRunning()
        val authorized = running && shizukuAuthorized()
        return FreezeStatus(
            // Shizuku first: its shell starts in a few milliseconds, while `su` has to set up
            // a mount namespace every time. Root stays for hiding apps and as the fallback.
            access = when {
                authorized -> FreezeAccess.SHIZUKU
                root != null -> FreezeAccess.ROOT
                else -> FreezeAccess.NONE
            },
            rootGranted = root != null,
            rootProvider = root?.provider ?: RootProvider.NONE,
            rootVersion = root?.version,
            shizukuInstalled = shizukuInstalled(),
            shizukuRunning = running,
            shizukuAuthorized = authorized,
            shizukuVersion = if (running) runCatching { rikka.shizuku.Shizuku.getVersion() }.getOrDefault(0) else 0,
        )
    }

    fun access(): FreezeAccess = status().access

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

    private fun shizukuAuthorized(): Boolean = runCatching {
        rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * The installed manager that defines the Shizuku API permission.
     *
     * Any manager that can serve this API has to declare that permission — official Shizuku,
     * Shizuku-Next and Shevery all do — so this finds the family by contract instead of by a
     * package name, and a future fork needs no change here.
     */
    private fun shizukuManager(): String? = runCatching {
        context.packageManager.getPermissionInfo(SHIZUKU_PERMISSION, 0).packageName
    }.getOrNull()

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
        return code == 0 && !output.contains("Exception") && !output.contains("Error")
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
        return code == 0 && !output.contains("Exception") && !output.contains("Error")
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
    }

    /** Whether a root shell answered the probe, regardless of which channel is in use. */
    fun rootAvailable(): Boolean = rootState() != null

    /** Whether Shizuku is running with access granted. */
    fun shizukuAvailable(): Boolean = shizukuReady()

    /** Whether a channel is already known to work, so a command cannot raise a prompt. */
    fun quietAccess(): Boolean = shizukuReady() || rootState != null

    private fun execute(command: String): Pair<Int, String>? = runScript(command, COMMAND_TIMEOUT_MS)

    private fun runScript(command: String, timeoutMs: Long, root: Boolean? = null): Pair<Int, String>? = when {
        root == true -> runProcess(arrayOf("su", "-c", command), timeoutMs)
        root == false -> if (shizukuReady()) runShizuku(command) else null
        shizukuReady() -> runShizuku(command)
        rootState() != null -> runProcess(arrayOf("su", "-c", command), timeoutMs)
        else -> null
    }

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
    private val binder = FreezeBinder(context)
    private val userManager: UserManager get() = context.getSystemService(UserManager::class.java)

    /** One whole-list run at a time: a second tap waits instead of racing the first. */
    private val gate = Mutex()

    // Checked only when the freeze screen asks: probing root can raise a prompt, which has no
    // business happening during a cold start.
    private val _status = MutableStateFlow(FreezeStatus())
    val status: StateFlow<FreezeStatus> = _status

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes

    /** When the channel last answered; see [ensureAccess]. Under the [gate] or a lucky read. */
    @Volatile
    private var probedAt = 0L

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
    }

    /**
     * Whether the external backup can be read without raising a root prompt.
     *
     * The backup is only useful after a reinstall, when the app data is gone. Reading it on
     * every cold start through a root prompt would make a normal launch ask for root for no
     * reason; Shizuku already granted is the one channel that can answer silently.
     */
    fun canUseBackupQuietly(): Boolean = shell.quietAccess()

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
        if (!backupRead) return@withLock false
        writeBackupLocked()
    }

    private suspend fun restoreBackupLocked(): Boolean {
        if (backupRead) return false
        val text = withContext(Dispatchers.IO) { shell.output("cat $backupPath") } ?: return false
        backupRead = true
        val backup = FreezeBackup.decode(text) ?: return false
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
        val applied = (backup.applied + prefs.freezeAppliedNow())
            .filterTo(mutableSetOf()) { it in methods }
        prefs.setFreezeApplied(applied, applied = true)
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
        val snapshot = FreezeBackup.Snapshot(
            firstInstallTime = firstInstallTime(),
            enabled = prefs.freezeEnabledNow(),
            systemApps = prefs.freezeSystemAppsNow(),
            protect = prefs.freezeProtectNow(),
            methods = prefs.freezeMethodsNow(),
            applied = prefs.freezeAppliedNow(),
            rows = prefs.freezeRowsNow(),
            locked = prefs.lockedAppsNow(),
        )
        val encoded = android.util.Base64.encodeToString(
            FreezeBackup.encode(snapshot).toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        val temporary = "$backupPath.tmp"
        return withContext(Dispatchers.IO) {
            // Readable by whichever channel writes next: a root-written 0600 file would be
            // invisible to Shizuku after a reinstall. The rename also replaces a file owned
            // by the other channel, which a plain redirect could not open for writing.
            shell.run(
                "umask 077; rm -f $temporary; echo $encoded | base64 -d > $temporary && " +
                    "chmod 0644 $temporary && mv -f $temporary $backupPath",
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
    private suspend fun ensureAccess(): FreezeAccess = probeAccess(force = false)

    private suspend fun probeAccess(force: Boolean): FreezeAccess {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - probedAt < PROBE_TTL_MS) return _status.value.access
        _status.value = withContext(Dispatchers.IO) { shell.status() }
        probedAt = SystemClock.elapsedRealtime()
        return _status.value.access
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
    private suspend fun reconcile() {
        val plan = prefs.freezeMethodsNow()
        val applied = prefs.freezeAppliedNow()
        if (plan.isEmpty() && applied.isEmpty()) return
        val locked = lockedMainKeys()
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        val noLonger = applied.filter { key ->
            val target = targetOf(key) ?: return@filter true
            if (snapshot.state(target)?.installed == false || snapshot.isKnownAbsent(target)) {
                return@filter true
            }
            when (val method = plan[key]) {
                null -> true
                FreezeMethod.DISABLE, FreezeMethod.STOP -> !isHeld(snapshot, target, method)
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
            return
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
        val wrote = prefs.migrateFreezeApplied()
        if (wrote) _changes.value += 1
        return wrote
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
                (systemEnabled || !app.system)
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
        if (key in lockedMainKeys()) return FreezeOutcome.DONE
        val target = targetOf(key) ?: return FreezeOutcome.FAILED
        if (target.packageName == context.packageName) return FreezeOutcome.FAILED
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
            if (!prefs.freezeEnabledNow()) return@withLock FreezeOutcome.DISABLED
            // The lock owns this app's suspend/hide state. Freezing it again, especially with
            // DISABLE, would change a state the lock is about to need back.
            if (key in lockedMainKeys()) return@withLock FreezeOutcome.DONE
            loadBackupLocked()
            val target = targetOf(key) ?: return@withLock FreezeOutcome.FAILED
            if (target.packageName == context.packageName) return@withLock FreezeOutcome.FAILED
            val method = prefs.freezeMethodsNow()[key] ?: return@withLock FreezeOutcome.NOT_LISTED
            val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
            if (!systemAllowed(target, snapshot)) return@withLock FreezeOutcome.SYSTEM_APPS_OFF
            if (target.packageName in protectedPackages()) return@withLock FreezeOutcome.PROTECTED
            if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeOutcome.NO_ACCESS
            // The first load ran before access was proved; now a root/Shizuku-only backup
            // can be read before this freeze writes over it.
            loadBackupLocked()
            if (label != null || component != null) prefs.setFreezeRow(key, FreezeRow(label, component))
            val already = key in prefs.freezeAppliedNow() && isHeld(snapshot, target, method)
            if (!already) {
                val frozen = withContext(Dispatchers.IO) { frozenIn(mapOf(key to method), snapshot) }
                if (key !in frozen) return@withLock FreezeOutcome.FAILED
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
            FreezeOutcome.DONE
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

    /** The same for a caller that only knows the main profile's package name. */
    suspend fun unfreeze(packageName: String): FreezeOutcome = unfreezeKey(packageName)

    suspend fun unfreezeKey(key: String): FreezeOutcome = gate.withLock {
        val target = targetOf(key) ?: return@withLock FreezeOutcome.FAILED
        // A locked app's suspend/hide layers are the lock's to lift, and only after the
        // device has authenticated the launch. An unfreeze here would be a free way in.
        if (key in lockedMainKeys()) return@withLock FreezeOutcome.DONE
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
            return@withLock FreezeOutcome.DONE
        }
        if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeOutcome.NO_ACCESS
        loadBackupLocked()
        val lifted = withContext(Dispatchers.IO) {
            liftedIn(listOf(key to method))
        }
        if (key !in lifted) return@withLock FreezeOutcome.FAILED
        prefs.setFreezeApplied(setOf(key), applied = false)
        _changes.value += 1
        persistBackupLocked()
        FreezeOutcome.DONE
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
        if (key !in plan && key !in applied) return@withLock FreezeOutcome.DONE
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
        FreezeOutcome.DONE
    }

    suspend fun removeKey(key: String): FreezeOutcome = gate.withLock {
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
                    if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeOutcome.NO_ACCESS
                    val lifted = withContext(Dispatchers.IO) {
                        liftedIn(listOf(key to method), ignoreLock = true)
                    }
                    if (key !in lifted) return@withLock FreezeOutcome.FAILED
                }
            }
            prefs.setFreezeApplied(setOf(key), applied = false)
            prefs.removeFreezeTarget(key)
            _changes.value += 1
            persistBackupLocked()
            return@withLock FreezeOutcome.DONE
        }
        val target = targetOf(key)
        val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        val applied = prefs.freezeAppliedNow()
        val method = prefs.freezeMethodsNow()[key]
        val needsLift = target != null && mustLift(key, method, applied, snapshot, target)
        if (needsLift) {
            if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeOutcome.NO_ACCESS
            val lifted = withContext(Dispatchers.IO) {
                liftedIn(listOf(key to method))
            }
            // Keeping the row is better than dropping the only record of a stuck app.
            if (key !in lifted) return@withLock FreezeOutcome.FAILED
            prefs.setFreezeApplied(setOf(key), applied = false)
        }
        prefs.removeFreezeTarget(key)
        _changes.value += 1
        persistBackupLocked()
        FreezeOutcome.DONE
    }

    /**
     * Freezes the whole list, each under its own method.
     *
     * [auto] is the screen-off run: it leaves out the app on screen and any app with a
     * notification, so the thing the user is listening to or looking at does not disappear.
     *
     * The run itself is not cancellable: the commands and the record they end with belong
     * together, and a screen that goes away mid-run must not leave an app frozen with
     * nothing on record to lift it.
     */
    suspend fun freezeAll(auto: Boolean = false): FreezeBatchResult =
        withContext(NonCancellable) {
            gate.withLock {
                if (!prefs.freezeEnabledNow()) return@withLock FreezeBatchResult()
                if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeBatchResult(noAccess = true)
                // A reinstall's first trigger should work from the old list, not an empty one.
                loadBackupLocked()
                val plan = prefs.freezeMethodsNow()
                if (plan.isEmpty()) return@withLock FreezeBatchResult()
                val locked = lockedMainKeys()
                val guarded = protectedPackages()
                val systemEnabled = prefs.freezeSystemAppsNow()
                val skip = if (auto) withContext(Dispatchers.IO) { autoSkip() } else emptySet()
                val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
                val (frozenBefore, targets) = withContext(Dispatchers.IO) {
                    val allowed = plan.filterKeys { key ->
                        if (key in locked) return@filterKeys false
                        val target = targetOf(key) ?: return@filterKeys false
                        val state = snapshot.state(target)
                        // An uninstall keeps the list entry for the reinstall, but there is
                        // no package to command until it comes back.
                        state?.installed != false &&
                            target.packageName != context.packageName &&
                            target.packageName !in guarded &&
                            (systemEnabled || state?.system != true)
                    }
                    // One state read per app, shared by the skip decision and the record.
                    val seen = mutableMapOf<String, Boolean>()
                    val isFrozen: (String) -> Boolean = { key ->
                        seen.getOrPut(key) {
                            targetOf(key)?.let { looksFrozen(snapshot, it) } == true
                        }
                    }
                    val already = allowed.keys.filter { it !in skip && isFrozen(it) }
                    already to FreezeSelection.toFreeze(allowed, skip, isFrozen)
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
                FreezeBatchResult(done = done.size, failed = failed.size)
            }
        }

    /**
     * Unfreezes the whole list, not only what the applied record remembers; the list is left
     * alone. The wider net matters: a record lost to a crash or an older build would
     * otherwise leave an app suspended with no screen willing to lift it.
     */
    suspend fun unfreezeAll(): FreezeBatchResult = withContext(NonCancellable) {
        gate.withLock {
            // The first Unfreeze all after a reinstall must see the list the old install
            // froze, or it would have nothing to lift.
            loadBackupLocked()
            val appliedNow = prefs.freezeAppliedNow()
            val plan = prefs.freezeMethodsNow()
            if (appliedNow.isEmpty() && plan.isEmpty()) return@withLock FreezeBatchResult()
            val locked = lockedMainKeys()
            val snapshot = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
            val candidates = (appliedNow + plan.keys).distinct().filterNot { it in locked }
            val targets = withContext(Dispatchers.IO) {
                // The record is the answer only for HIDE, whose state cannot be read back; a
                // record left on an enabled app by a reinstall must not demand access.
                val held = candidates.mapNotNull { key ->
                    val target = targetOf(key) ?: return@mapNotNull null
                    // A package that is gone has nothing to lift, even when the old record
                    // was HIDE. Leaving it out lets a no-access unfreezeAll drop the record.
                    if (snapshot.state(target)?.installed == false || snapshot.isKnownAbsent(target)) {
                        return@mapNotNull null
                    }
                    key to looksFrozen(snapshot, target)
                }.toMap()
                FreezeSelection.toLift(held.keys, appliedNow, plan, locked) { held[it] == true }
            }
            if (targets.isEmpty()) {
                if (appliedNow.any { it !in locked }) {
                    prefs.setFreezeApplied(appliedNow - locked, applied = false)
                    _changes.value += 1
                    persistBackupLocked()
                }
                return@withLock FreezeBatchResult()
            }
            if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeBatchResult(noAccess = true)
            val done = withContext(Dispatchers.IO) {
                liftedIn(targets.map { it to plan[it] })
            }
            val failed = targets.filter { it !in done }
            // Locked apps keep their freeze record: the lock still holds them, and this run
            // deliberately did not lift them.
            prefs.setFreezeApplied(appliedNow - failed.toSet() - locked, applied = false)
            _changes.value += 1
            persistBackupLocked()
            FreezeBatchResult(done = done.size, failed = failed.size)
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
    )

    /**
     * Adds the apps from an exported list that are installed and not already listed.
     * Returns how many joined, or -1 when the file is not one of ours.
     */
    suspend fun importPlan(text: String): Int {
        val entries = FreezePlanFile.decode(text) ?: return -1
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
        _changes.value += 1
        return usable.size
    }

    private suspend fun systemAllowed(isSystem: Boolean): Boolean =
        prefs.freezeSystemAppsNow() || !isSystem

    private suspend fun systemAllowed(target: FreezeTarget, snapshot: FreezeSnapshot): Boolean =
        systemAllowed(snapshot.state(target)?.system == true)

    /** The apps no freeze may touch while the guard is on; read at most every half minute. */
    private suspend fun protectedPackages(): Set<String> {
        if (!prefs.freezeProtectNow()) return emptySet()
        val now = SystemClock.elapsedRealtime()
        if (now - protectedAt < PROTECT_TTL_MS) return protectedSet
        val packages = withContext(Dispatchers.IO) {
            FreezeProtection.packages(context, shell.protectedPackages())
        }
        protectedSet = packages
        protectedAt = now
        return packages
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
            .mapNotNull { (key, method) -> targetOf(key)?.let { key to (method to it) } }
        if (resolved.isEmpty()) return emptySet()
        val done = mutableSetOf<String>()
        val canHide = binder.available() && binder.canHide()
        val binderable = resolved.filter { (_, pair) -> pair.first != FreezeMethod.HIDE || canHide }
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
        val hiding = resolved.filter { it.first !in done && it.second.first == FreezeMethod.HIDE }
        if (hiding.isNotEmpty() && shell.rootAvailable()) shellRun(hiding, root = true)
        val left = resolved.filter { it.first !in done }
        if (left.isNotEmpty()) shellRun(left, root = null)
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
        var states = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
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
        val canHide = binder.available() && binder.canHide()
        var pending = withContext(Dispatchers.IO) {
            resolved.filter { (_, pair) ->
                val (method, target) = pair
                !freeNow(states, target) || (method == FreezeMethod.HIDE && !canHide)
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
        val failed = pending.mapTo(mutableSetOf()) { it.first }
        return resolved.map { it.first }.filterNot { it in failed }.toSet()
    }

    private suspend fun shellLift(
        pending: List<Pair<String, FreezeMethod?>>,
        perMethod: Boolean,
        root: Boolean?,
    ): List<Pair<String, FreezeMethod?>> {
        val resolved = pending.mapNotNull { (key, method) ->
            targetOf(key)?.let { key to (method to it) }
        }
        if (resolved.isEmpty()) return emptyList()
        val answers = shell.runGroups(
            resolved.map { (_, pair) ->
                val (method, target) = pair
                if (perMethod && method != null) {
                    FreezeCommands.unfreeze(target.packageName, method, target.userId)
                } else {
                    FreezeCommands.unfreezeEverything(target.packageName, target.userId)
                }
            },
            root = root,
        )
        val states = withContext(Dispatchers.IO) { FreezeSnapshot.read(context) }
        return withContext(Dispatchers.IO) {
            resolved.filterIndexed { index, (_, pair) ->
                val (method, target) = pair
                val answered = answers.getOrNull(index) == true
                // Hiding cannot be read back, and another profile's flags are read through
                // LauncherApps only: a clean command is the only answer there is.
                val verifiable = FreezeSelection.verifiable(method) && target.userSerial == 0L
                !answered || (verifiable && !freeNow(states, target))
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
    private fun looksFrozen(snapshot: FreezeSnapshot, target: FreezeTarget): Boolean {
        if (snapshot.isKnownAbsent(target)) return false
        val state = snapshot.state(target) ?: return true
        if (state.hidden || state.suspended) return true
        return !state.enabled && !parkedByPlatform(target)
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
        if (snapshot.state(target)?.installed == false) return false
        return looksFrozen(snapshot, target) ||
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
    private fun freeNow(snapshot: FreezeSnapshot, target: FreezeTarget): Boolean {
        val state = snapshot.state(target) ?: return true
        if (!state.enabled && !parkedByPlatform(target)) return false
        return if (target.userSerial == 0L) !suspendedNow(target) else !state.suspended
    }

    private fun suspendedNow(target: FreezeTarget): Boolean = target.userSerial == 0L && runCatching {
        context.packageManager.isPackageSuspended(target.packageName)
    }.getOrDefault(false)

    companion object {
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

    fun encode(plan: Map<String, FreezeMethod>, rows: Map<String, FreezeRow>): String {
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
        return org.json.JSONObject().put("version", 1).put("apps", apps).toString()
    }

    /** Null for anything that is not a file this build wrote. */
    fun decode(text: String): Map<String, Entry>? = runCatching {
        if (text.length > MAX_FILE_CHARS) return null
        val root = org.json.JSONObject(text)
        if (root.optInt("version") != 1) return null
        val apps = root.optJSONArray("apps") ?: return null
        if (apps.length() > MAX_ENTRIES) return null
        buildMap {
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
    }.getOrNull()

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
            .toString()
    }

    /** Null for anything that is not a backup this build wrote. */
    fun decode(text: String): Snapshot? = runCatching {
        if (text.length > MAX_FILE_CHARS) return null
        val root = org.json.JSONObject(text)
        if (root.optInt("version") != VERSION) return null
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
        Snapshot(
            firstInstallTime = root.optLong("firstInstall", 0L),
            enabled = root.optBoolean("enabled", true),
            systemApps = root.optBoolean("systemApps", false),
            protect = root.optBoolean("protect", true),
            methods = decodedMethods,
            applied = decodedApplied,
            rows = decodedRows,
            locked = decodedLocked,
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

    private const val VERSION = 1
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
