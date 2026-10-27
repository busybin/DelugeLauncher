// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import java.util.Locale
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import dev.delugelauncher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A few tries spread over about a second, since a pin request's shortcut can take a moment
 *  to show up in the system's own pinned set after [LauncherApps.PinItemRequest.accept]
 *  returns, not before. */
private const val PIN_CONFIRM_ATTEMPTS = 5
private const val PIN_CONFIRM_DELAY_MS = 200L

class AppRepository(
    private val context: Context,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    private val freezeManager: FreezeManager,
) {

    private val pm: PackageManager get() = context.packageManager
    private val launcherApps: LauncherApps
        get() = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager
        get() = context.getSystemService(UserManager::class.java)

    /**
     * The last serial a private space positively reported, kept only so that a read which
     * fails does not un-conceal what an earlier read concealed. In memory and never written
     * down: it is a fact about this device right now, and a file recording that a private
     * space exists is itself something for someone to find on the phone.
     */
    @Volatile
    private var lastKnownPrivateSerial: Long = 0L

    /**
     * The last list handed to the UI, kept for the life of this process so a
     * recreated screen draws it at once instead of standing an empty home in
     * front of the user while the fresh enumeration runs behind it. In memory
     * only, never written down: profile membership is a fact about right now,
     * and the reload that starts alongside the seeded screen corrects anything
     * that changed while nobody was looking.
     */
    @Volatile
    private var lastPublished: Pair<PrivateSpace, List<AppInfo>>? = null

    fun cachedApps(): Pair<PrivateSpace, List<AppInfo>>? = lastPublished

    fun notePublished(state: PrivateSpace, apps: List<AppInfo>) {
        lastPublished = state to apps
    }

    /**
     * The serial to key a profile's rows by, or null when nothing from it may be listed. Asked
     * once per profile and used for apps and pinned shortcuts alike: a shortcut pinned inside
     * the private space belongs to it exactly as its apps do.
     */
    private fun listableSerial(user: UserHandle, mainUser: UserHandle): Long? {
        val isMain = user == mainUser
        val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrNull()
        // Only asked about a profile the answers could change anything for: the one we
        // run in is always listed, and below Android 15 there is no private space for
        // either answer to describe.
        val classify = !isMain && Build.VERSION.SDK_INT >= PRIVATE_SPACE_SDK
        val listed = shouldListProfile(
            isMainUser = isMain,
            sdk = Build.VERSION.SDK_INT,
            userType = if (classify) userType(user) else null,
            quietMode = if (classify) quietMode(user) else null,
            serial = serial,
        )
        if (!listed) return null
        // Null only reaches here for the profile we run in, whose serial is zero anyway; every
        // other profile without one was refused above.
        return serial ?: 0L
    }

    /**
     * Every launchable activity across every profile the launcher may show, decided profile by
     * profile before any of it is turned into rows.
     *
     * LauncherApps rather than PackageManager, because queryIntentActivities only ever sees
     * the profile we are running in — a work profile or a private space is invisible to it.
     * LauncherApps also hands back the badged icon and the per-profile label, which is what
     * marks a work app as a work app.
     *
     * The decision is made here, by UserHandle, rather than by filtering finished rows by
     * serial afterwards, because a profile whose serial could not be read produces rows whose
     * keys are indistinguishable from the main profile's — there is nothing left to filter by
     * at that point, and those keys would be stored into favorites and launch counts.
     */
    fun queryAllApps(
        privateSpace: PrivateSpace = privateSpace(),
        /**
         * Whether to read each app's first-install time, which only the "Recently installed"
         * section uses. A full `getInstalledPackages` pass is the slowest PackageManager call
         * here, so it is skipped whenever nothing on screen can show the answer.
         */
        withInstallTimes: Boolean = true,
        /** Package name to freeze method, so frozen rows stay listed and say so. */
        frozen: Map<String, FreezeMethod> = emptyMap(),
        /** Keys in the freeze list, frozen or merely paused, so rows can offer the right thing. */
        planned: Set<String> = emptySet(),
        /** What the list remembers about rows that cannot be read back, keyed like [frozen]. */
        freezeRows: Map<String, FreezeRow> = emptyMap(),
    ): List<AppInfo> {
        val profiles = runCatching { userManager.userProfiles }.getOrNull().orEmpty()
        val mainUser = Process.myUserHandle()
        // One call answers every question here about the main profile: enabled/hidden/
        // suspended/stopped flags, system state, uid and both install times. Asking per app
        // and per profile was hundreds of round trips on every reload and every cold start.
        val states = FreezeState.mainStates(context)
        val installTimes = if (withInstallTimes) {
            states.mapValues { it.value.installedAt to it.value.updatedAt }
        } else {
            emptyMap()
        }
        return profiles
            .flatMap { user ->
                val serial = listableSerial(user, mainUser) ?: return@flatMap emptyList()
                val work = isWorkProfile(user, mainUser)
                val privateProfile = userType(user) == USER_TYPE_PROFILE_PRIVATE
                // One call for the lot: asking per package turns a list build into
                // hundreds of round trips to the package manager.
                // Asking about a profile we are not the launcher for throws rather than
                // returning nothing, and one inaccessible profile must not lose the rest.
                runCatching { launcherApps.getActivityList(null, user) }
                    .getOrNull()
                    .orEmpty()
                    .map { info ->
                        val serialKey = EntryKeys.app(info.componentName.packageName, serial)
                        AppInfo(
                            componentName = info.componentName,
                            label = info.label?.toString() ?: info.componentName.packageName,
                            user = user,
                            userSerial = serial,
                            work = work,
                            privateProfile = privateProfile,
                            system = runCatching {
                                info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                            }.getOrDefault(false),
                            installedAt = installTimes[info.componentName.packageName]?.first ?: 0L,
                            updatedAt = installTimes[info.componentName.packageName]?.second ?: 0L,
                            // Read from the profile the row belongs to; the flag on a
                            // LauncherActivityInfo is not reliably carried, so it is never
                            // trusted for this.
                            suspended = if (user == mainUser) {
                                states[info.componentName.packageName]?.suspended == true
                            } else {
                                false
                            },
                            // A disabled or hidden app is not listed by LauncherApps at all,
                            // and whether it is frozen is the manager's own record rather than
                            // anything re-derived here: the platform's answer for a suspended
                            // app is not reliable, and a wrong one showed a frozen app as if it
                            // still had to be frozen.
                            planned = serialKey in planned,
                            frozen = frozen[serialKey],
                        )
                    }
            }
            // Launching ourselves through the MAIN+LAUNCHER filter starts a task that isn't
            // rooted at HOME: it shows up in the app switcher and leaves the system unsure
            // which task is home until the default launcher is set again. Nothing good comes
            // of listing the launcher inside its own app list.
            .filterNot { it.componentName.packageName == context.packageName }
            // After that filter rather than before it: the reason for it is that starting our
            // own launcher activity corrupts the HOME task, which says nothing about a
            // shortcut, and one we published ourselves is started through LauncherApps like
            // any other.
            .plus(queryPinnedShortcuts(frozen, planned))
            // Belt as well as braces, and after the shortcuts so that it covers them too. The
            // profiles above are read one call at a time, so the lock can be granted between
            // the state this list was asked for and the quiet mode read here — and it is
            // granted before the profile has actually stopped. A caller that has just locked
            // the space hands that state in, and this drops what it names however the system
            // happens to be answering at this instant.
            .filterNot { privateSpace.conceals(it.key) }
            .distinctBy { it.key }
            // After the dedupe: these are the frozen apps LauncherApps stopped returning.
            .let { rows -> rows + frozenRows(frozen, rows.mapTo(mutableSetOf()) { it.freezeKey }, freezeRows) }
            // By pinyin on a Chinese phone: this is the order every list of app names starts
            // from, and codepoint order would put ZhiFuBao where no reader looks for it.
            // The app list re-files and re-orders the same apps by its own letters.
            .sortedBy { if (Pinyin.ordering()) Pinyin.latin(it.label) else it.label.lowercase() }
            // After the own-package filter, which would otherwise drop it: the row is ours.
            .plus(privateSpaceRow(privateSpace))
    }

    /**
     * Rows for frozen apps the launcher no longer returns because they are disabled or hidden.
     *
     * Only those two methods remove an app from LauncherApps; suspended and force-stopped
     * apps are still listed there and are marked on their ordinary rows instead. The component
     * is recovered so the row keeps the same key as the app had before it was frozen, which is
     * what favorites, hidden entries and launch counts are stored under.
     */
    private fun frozenRows(
        frozen: Map<String, FreezeMethod>,
        present: Set<String>,
        rows: Map<String, FreezeRow>,
    ): List<AppInfo> = frozen.mapNotNull { (key, method) ->
        if (key in present) return@mapNotNull null
        val serial = FreezeTargetKey.userSerial(key)
        val packageName = FreezeTargetKey.packageName(key)
        val info = FreezeState.applicationInfo(context, packageName, serial) ?: return@mapNotNull null
        if (info.flags and ApplicationInfo.FLAG_INSTALLED == 0) return@mapNotNull null
        // Hidden apps are absent from the launcher by definition; a disabled one must still
        // report disabled, or the user re-enabled it themselves and this entry is stale.
        if (method == FreezeMethod.DISABLE && info.enabled) return@mapNotNull null
        val stored = rows[key]
        AppInfo(
            // The activity recorded when the app joined the list wins: another profile's
            // launcher cannot be asked, and a package with several launcher activities would
            // otherwise come back under one the favorites were not stored by.
            componentName = stored?.component
                ?.let(ComponentName::unflattenFromString)
                ?: launcherComponent(packageName)
                ?: ComponentName(packageName, packageName),
            label = stored?.label
                ?: runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(packageName),
            user = if (serial == 0L) {
                Process.myUserHandle()
            } else {
                userManager.getUserForSerialNumber(serial)
            },
            userSerial = serial,
            system = info.flags and FreezeState.SYSTEM_FLAGS != 0,
            frozen = method,
            planned = true,
        )
    }

    private fun launcherComponent(packageName: String): ComponentName? =
        FreezeState.launcherComponent(context, packageName)

    /**
     * What kind of profile this is, or null when the platform will not say — which everything
     * here reads as "it might be the private one" and treats accordingly.
     */
    private fun userType(user: UserHandle): String? =
        if (Build.VERSION.SDK_INT < PRIVATE_SPACE_SDK) null
        else runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()

    /**
     * The platform's name for the private space profile.
     *
     * Spelled out rather than referenced: the constant is API 35 and this builds against
     * it, but the string is what getLauncherUserInfo actually answers with.
     */
    private val USER_TYPE_PROFILE_PRIVATE = "android.os.usertype.profile.PRIVATE"

    /** LauncherUserInfo.PRIVATE_SPACE_ENTRYPOINT_HIDDEN, by its documented value. */
    private val PRIVATE_SPACE_ENTRYPOINT_HIDDEN = "private_space_entrypoint_hidden"

    /**
     * Whether the owner has asked for the private space's entry to be hidden while locked.
     *
     * Android 16 says so through LauncherUserInfo's user config, under a key it documents as a
     * plain string. Reached by reflection because the method arrived in API 36 and this builds
     * against 35; below 36 there is no such setting to honour, and anything that fails reads as
     * not hidden, which is how this has always behaved.
     */
    private fun entrypointHidden(user: UserHandle): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            val info = launcherApps.getLauncherUserInfo(user) ?: return false
            val config = info.javaClass.getMethod("getUserConfig").invoke(info) as? android.os.Bundle
            config?.getBoolean(PRIVATE_SPACE_ENTRYPOINT_HIDDEN, false) == true
        }.getOrDefault(false)
    }

    /**
     * Whether a profile is a work one.
     *
     * Exact from API 35, where the platform will name a profile's type. Below that it will not,
     * and any profile other than the main one is a work profile in practice — the private space
     * arrived with that same API, so there was nothing else a second profile could be.
     */
    private fun isWorkProfile(user: UserHandle, mainUser: UserHandle): Boolean {
        if (user == mainUser) return false
        val type = userType(user) ?: return true
        return type == UserManager.USER_TYPE_PROFILE_MANAGED
    }

    /** Whether the profile is switched off, or null when the platform will not say. */
    private fun quietMode(user: UserHandle): Boolean? =
        runCatching { userManager.isQuietModeEnabled(user) }.getOrNull()

    /**
     * The private space as this launcher can see it right now.
     *
     * Asked fresh every time rather than remembered, because the system locks the space on its
     * own — it re-locks when the screen goes off — and a remembered answer would go stale with
     * nothing here being called.
     *
     * Every call is runCatching-wrapped: they throw when the launcher is not the default home,
     * which is the condition Android puts on the permission, and a launcher that is not the
     * default home must behave exactly as it did before any of this existed. What a failure
     * never produces is [PrivateSpace.Absent], which conceals nothing: not being able to read
     * the space is not the same as there not being one, and only the second of those is safe
     * to act on.
     */
    fun privateSpace(): PrivateSpace {
        // getLauncherUserInfo arrived in API 35, which is also the first Android to have a
        // private space at all, so below it there is nothing to look for.
        if (Build.VERSION.SDK_INT < PRIVATE_SPACE_SDK) return PrivateSpace.Absent
        val mainUser = Process.myUserHandle()
        // LauncherApps.profiles rather than UserManager.userProfiles: without the permission
        // UserManager still lists the private profile while LauncherApps does not, and it is
        // LauncherApps that decides whether anything inside it can be read.
        val profiles = runCatching { launcherApps.profiles }.getOrNull()
            // The list itself refusing is not an answer of "there is no private space".
            ?: return uncertain(user = null)
        // A profile that would not say what it is could be the private one, so a pass that
        // found no private space but did meet one of those has not established anything.
        var unclassified = false
        for (user in profiles) {
            if (user == mainUser) continue
            val type = userType(user)
            if (type == null) {
                unclassified = true
                continue
            }
            if (type != USER_TYPE_PROFILE_PRIVATE) continue
            // Zero is the main profile's serial, so a profile reporting it means the lookup
            // failed. Concealing by a zero serial conceals nothing, which is the one outcome
            // worth refusing outright — so it becomes an uncertain space rather than an open
            // one, and falls back to the last serial this space was known by.
            val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrNull()
                ?.takeIf { it != 0L }
                ?: return uncertain(user)
            lastKnownPrivateSerial = serial
            return when (privateSpaceKind(type, quietMode(user))) {
                PrivateSpaceKind.UNLOCKED -> PrivateSpace.Unlocked(user, serial)
                // Locked, and also the unreachable case: the type was matched just above, and
                // locked is the reading to take if that ever stopped being true.
                else -> PrivateSpace.Locked(user, serial)
            }
        }
        // Nothing here said it was a private space. That is only an absence if every profile
        // did say what it was, and if no space has answered earlier in this session — a space
        // that has gone missing from a list it used to be in is a read that failed, not a
        // space that was deleted, and the difference is not one to guess in this direction.
        return if (unclassified || lastKnownPrivateSerial != 0L) uncertain(null) else PrivateSpace.Absent
    }

    /**
     * A space we know is there, or might be, and cannot describe: concealed by the last serial
     * it was known by, which is zero when it has never given one up.
     */
    private fun uncertain(user: UserHandle?): PrivateSpace =
        PrivateSpace.Uncertain(user, lastKnownPrivateSerial)

    /**
     * Locks an open space and asks for a locked one to be opened. What that takes is the
     * system's to decide: on a phone with a screen lock it puts its own authentication in
     * front of the unlock, and this answers null until that has been answered.
     *
     * Returns the state to hold from now on when a lock was granted, so whoever pressed the
     * row can conceal at that moment rather than waiting for the broadcast that follows. Null
     * when nothing was granted, and null for an unlock: nothing is exposed by an unlock being
     * a moment late, and there is nothing to list until the profile is actually up again.
     *
     * Suspending because this is several binder calls and, on a phone with a screen lock, the
     * system's own authentication — none of which belongs on the main thread, and all of which
     * starts from a press on a row.
     */
    suspend fun togglePrivateSpace(): PrivateSpace? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < PRIVATE_SPACE_SDK) return@withContext null
        val state = privateSpace()
        // Nothing to ask about without a profile to ask about it. An uncertain space with one
        // is treated as locked, like everywhere else, so pressing the row tries to open it.
        val user = state.user ?: return@withContext null
        val lock = state is PrivateSpace.Unlocked
        val granted = runCatching { userManager.requestQuietModeEnabled(lock, user) }.getOrDefault(false)
        if (!granted || !lock) return@withContext null
        // The profile takes a moment to actually stop after the lock is granted. This state is
        // what the caller holds and hands to the enumeration that follows: asked again right
        // now, the system would describe an open space and list everything inside it.
        PrivateSpace.Locked(user, state.serial)
    }

    /**
     * Opens the app store inside an open private space, the way the stock launcher's install
     * button does. Settings' own route to it can come up empty while another launcher is the
     * default. False when there is no open space or no store to open.
     */
    fun openPrivateSpaceStore(from: Context, packageName: String? = null): Boolean {
        if (Build.VERSION.SDK_INT < PRIVATE_SPACE_SDK) return false
        val user = (privateSpace() as? PrivateSpace.Unlocked)?.user ?: return false
        // The package's own page when the store has one, the store's front page otherwise:
        // a package that only exists outside the space can still be searched for by name.
        val sender = runCatching {
            (if (packageName != null) {
                launcherApps.getAppMarketActivityIntent(packageName, user)
            } else {
                null
            }) ?: launcherApps.getAppMarketActivityIntent(null, user)
        }.getOrNull() ?: return false
        return runCatching { startSystemSender(from, sender) }.getOrDefault(false)
    }

    /**
     * Whether [packageName] is installed inside the open private space. Null when there is no
     * space to ask, the space is locked, or the platform would not answer.
     *
     * Asking is safe while the space is open; while it is locked the answer belongs to a
     * profile whose apps nothing here may enumerate, so it is not asked at all.
     */
    fun privateSpaceHas(packageName: String): Boolean? {
        if (Build.VERSION.SDK_INT < PRIVATE_SPACE_SDK) return null
        val user = (privateSpace() as? PrivateSpace.Unlocked)?.user ?: return null
        return runCatching {
            launcherApps.getApplicationInfo(packageName, 0, user) != null
        }.getOrNull()
    }

    /** Whether [packageName] is installed for the launcher's own profile. */
    fun mainProfileHas(packageName: String): Boolean = runCatching {
        launcherApps.getApplicationInfo(packageName, 0, Process.myUserHandle()) != null
    }.getOrDefault(false)

    /**
     * The shell that can provision a private space without opening it, made once so its root
     * probe is answered once per process.
     */
    @Volatile
    private var provisionShell: FreezeShell? = null

    private fun shell(): FreezeShell =
        provisionShell ?: FreezeShell(context.applicationContext).also { provisionShell = it }

    /** Whether Shizuku or root can carry the private-space steps that need a shell. */
    suspend fun shellAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val shell = shell()
            // Shizuku first: asking root can raise a prompt, and a working Shizuku means the
            // shell is already answered.
            shell.shizukuAvailable() || shell.rootAvailable()
        }.getOrDefault(false)
    }

    /** The private profile's user id, readable while the space is locked. */
    suspend fun privateSpaceUser(): Int? = withContext(Dispatchers.IO) {
        privateSpace().user?.userId
    }

    /**
     * Whether [packageName] is installed for [userId], asked of the package manager instead of
     * LauncherApps so a locked private space still answers. Null when there is no shell or the
     * answer could not be read.
     */
    suspend fun profileHas(packageName: String, userId: Int): Boolean? =
        withContext(Dispatchers.IO) {
            if (userId <= 0 || !FreezeCommands.isPackageName(packageName)) return@withContext null
            val out = shell().output("pm list packages --user $userId $packageName")
                ?: return@withContext null
            out.lineSequence().any { it.trim() == "package:$packageName" }
        }

    /**
     * Installs the existing main-profile package into [userId] as a fresh copy, the way
     * `pm install-existing` does for a work profile. No store and no APK are needed, which is
     * what makes this work for a sideloaded app; its data is not copied.
     */
    suspend fun installInProfile(packageName: String, userId: Int): Boolean =
        withContext(Dispatchers.IO) {
            if (userId <= 0 || !FreezeCommands.isPackageName(packageName)) return@withContext false
            shell().run("pm install-existing --user $userId $packageName")
        }

    /** Removes the main-profile copy through the shell, keeping the private profile's copy. */
    suspend fun removeFromMainProfile(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!FreezeCommands.isPackageName(packageName)) return@withContext false
            shell().run("pm uninstall --user 0 $packageName")
        }

    /**
     * Opens the system's private space settings. The launcher-facing intent is what the stock
     * launcher uses and it runs as the main user, which its "Install available apps" page
     * needs; older builds only expose the settings action itself.
     */
    fun openPrivateSpaceSettings(from: Context): Boolean {
        if (Build.VERSION.SDK_INT < PRIVATE_SPACE_SDK) return false
        val sender = runCatching {
            LauncherApps::class.java.getMethod("getPrivateSpaceSettingsIntent").invoke(launcherApps)
        }.getOrNull() as? android.content.IntentSender
        if (sender != null) {
            return runCatching { startSystemSender(from, sender) }.getOrDefault(false)
        }
        return runCatching {
            from.startActivity(
                Intent("com.android.settings.action.OPEN_PRIVATE_SPACE_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    @androidx.annotation.RequiresApi(PRIVATE_SPACE_SDK)
    private fun startSystemSender(from: Context, sender: android.content.IntentSender): Boolean {
        // The system made this sender, so starting it rests on our being in front, which
        // Android 14 only lends to it when asked.
        val options = android.app.ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        from.startIntentSender(
            sender, null,
            Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_NEW_TASK, 0,
            options.toBundle(),
        )
        return true
    }

    /**
     * The row that opens and closes the space, or nothing when there is no space to open.
     *
     * It is not an app and has no activity of its own, so it names itself after this package
     * and says which padlock it is in its class name — which is also what tells the icon cache
     * the two apart, since that cache is keyed by the row and not by the state of the device.
     */
    fun privateSpaceRow(state: PrivateSpace): List<AppInfo> {
        // Offered for any profile this pass could name, including one it could not describe:
        // that one is treated as locked, and a locked space has to keep its way back in. And
        // for a space that named itself earlier in this session and will not now, where there
        // is no profile left to point at — see PrivateSpace.offersPadlockRow.
        if (!state.offersPadlockRow) return emptyList()
        // Hidden, if that is what its owner told Android. Holding ACCESS_HIDDEN_PROFILES is
        // what lets a launcher see a private space at all — including one its owner has asked
        // to be kept out of sight while locked, which this showed regardless. Only while it is
        // locked: once open it is in use, and the row is also the way to lock it again.
        if (state is PrivateSpace.Locked && entrypointHidden(state.user)) return emptyList()
        // Unlocked is the only state with an open padlock, and it always names its profile,
        // so a row offered without one is always the closed padlock.
        val className =
            if (state is PrivateSpace.Unlocked) PRIVATE_SPACE_UNLOCKED_CLASS else PRIVATE_SPACE_LOCKED_CLASS
        return listOf(
            AppInfo(
                componentName = ComponentName(context.packageName, className),
                label = context.getString(R.string.private_space),
                kind = EntryKind.PRIVATE_SPACE,
            )
        )
    }

    /**
     * Bumped when the pinned set changes without any package changing — the confirm screen
     * accepting one, or a menu unpinning one. Nothing else would tell the list to be built
     * again, because no app was installed, removed or updated.
     */
    private val _shortcutChanges = MutableStateFlow(0)
    val shortcutChanges: StateFlow<Int> = _shortcutChanges.asStateFlow()

    private fun noteShortcutsChanged() {
        _shortcutChanges.update { it + 1 }
    }

    private data class PinnedShortcut(val info: ShortcutInfo, val user: UserHandle, val serial: Long)

    /**
     * Only the device's current home app may ask about shortcuts at all; anyone else is
     * answered with a SecurityException rather than an empty list. So every call below asks
     * this first, and a launcher that is not the one in use simply lists no shortcuts.
     */
    private fun hasShortcutHostPermission(): Boolean =
        runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    private fun pinnedShortcuts(): List<PinnedShortcut> {
        if (!hasShortcutHostPermission()) return emptyList()
        val profiles = runCatching { userManager.userProfiles }.getOrNull().orEmpty()
        val mainUser = Process.myUserHandle()
        return profiles.flatMap { user ->
            // The same decision the app list makes, for the same reason: a shortcut read from a
            // profile whose serial could not be read would be keyed as the main profile's, and
            // nothing that conceals by serial could reach it afterwards.
            val serial = listableSerial(user, mainUser) ?: return@flatMap emptyList()
            val query = LauncherApps.ShortcutQuery()
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            runCatching { launcherApps.getShortcuts(query, user) }
                .getOrNull()
                .orEmpty()
                .map { PinnedShortcut(it, user, serial) }
        }
    }

    /** Every pinned shortcut, as an ordinary row. */
    private fun queryPinnedShortcuts(
        frozen: Map<String, FreezeMethod>,
        planned: Set<String>,
    ): List<AppInfo> = pinnedShortcuts()
        // An id that would corrupt the newline-joined favorites list (CR-4) is not something
        // this store can hold at all, so such a shortcut never becomes a row in the first
        // place — PinShortcutActivity applies the same check before it can ever be pinned.
        .filter { EntryKeys.isStorableShortcutId(it.info.id) }
        .map { pinned ->
            val info = pinned.info
            AppInfo(
                // Always the publisher's own package, never `info.activity`'s — the activity
                // names which of the publisher's screens the shortcut opens, and is not
                // guaranteed to agree with `info.package` in shape even though it always does
                // in practice. Everything downstream (AppInfo.key, AppInfo.packageName, the
                // in-list package search, the icon-pack lookup skip, unpin's own
                // `pinShortcuts(pkg, …)` call) has to agree on one package, and the publisher's
                // is the only one guaranteed to be that shortcut's.
                componentName = ComponentName(info.`package`, info.activity?.className ?: ""),
                label = info.shortLabel?.toString() ?: info.longLabel?.toString() ?: info.`package`,
                user = pinned.user,
                userSerial = pinned.serial,
                kind = EntryKind.SHORTCUT,
                shortcutId = info.id,
                disabled = !info.isEnabled,
                // A shortcut points into an app, so it is unusable while that app is frozen.
                // Carrying the state here is what lets pressing it lift the freeze first.
                frozen = frozen[info.`package`],
                planned = info.`package` in planned,
            )
        }

    /**
     * The shortcuts an app publishes about itself — what a long press on it offers.
     *
     * Both the ones declared in its manifest and the ones it adds as it runs, ordered the way
     * the app ranked them. Asked of the activity first, since an app with more than one
     * launcher icon ranks them per icon; a package that answers nothing that way is asked as a
     * whole rather than left looking as though it publishes none.
     */
    fun appShortcuts(app: AppInfo): List<ShortcutInfo> {
        if (app.kind != EntryKind.APP) return emptyList()
        val user = app.user ?: Process.myUserHandle()
        val flags = LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC

        fun query(withActivity: Boolean): List<ShortcutInfo> {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.componentName.packageName)
                .setQueryFlags(flags)
            if (withActivity) q.setActivity(app.componentName)
            // Throws rather than returning nothing when this launcher does not hold the home
            // role, which is a state it can be in for a moment after being switched to.
            return runCatching { launcherApps.getShortcuts(q, user) }.getOrNull().orEmpty()
        }

        val found = query(withActivity = true).ifEmpty { query(withActivity = false) }
        return found.filter { it.isEnabled }.sortedBy { it.rank }
    }

    /**
     * Starts one of [appShortcuts]; the publisher's own Intent is never touched.
     *
     * [app] is the row the menu was opened on: a shortcut into a frozen app cannot start until
     * that app is lifted, exactly like pressing the app itself.
     */
    fun startAppShortcut(app: AppInfo, shortcut: ShortcutInfo, origin: Rect? = null): Boolean {
        // A shortcut points into its publisher's app, and a pinned shortcut row carries that
        // app's state too, so the row kind does not matter here.
        if (app.frozen != null) {
            scope.launch {
                val lifted = freezeManager.unfreeze(app)
                withContext(Dispatchers.Main) {
                    when (lifted) {
                        FreezeOutcome.DONE -> startAppShortcutNow(shortcut, origin)
                        FreezeOutcome.NO_ACCESS -> Toast.makeText(
                            context,
                            context.getString(R.string.freeze_needs_access),
                            Toast.LENGTH_LONG,
                        ).show()
                        else -> Toast.makeText(
                            context,
                            context.getString(R.string.freeze_unfreeze_failed, app.label),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            return true
        }
        return startAppShortcutNow(shortcut, origin)
    }

    private fun startAppShortcutNow(shortcut: ShortcutInfo, origin: Rect?): Boolean = runCatching {
        launcherApps.startShortcut(shortcut, origin, null)
        true
    }.getOrDefault(false)

    /** The icon for a shortcut offered in a menu. */
    fun shortcutIcon(shortcut: ShortcutInfo, densityDpi: Int): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(shortcut, densityDpi) }.getOrNull()

    /** The live ShortcutInfo behind a row, for its icon, its reason for being off, or a pin. */
    private fun findShortcut(app: AppInfo): ShortcutInfo? {
        val id = app.shortcutId ?: return null
        if (!hasShortcutHostPermission()) return null
        val query = LauncherApps.ShortcutQuery()
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            .setPackage(app.packageName)
            .setShortcutIds(listOf(id))
        return runCatching { launcherApps.getShortcuts(query, app.user ?: Process.myUserHandle()) }
            .getOrNull()
            ?.firstOrNull()
    }

    /**
     * Hands the package back every id it still has pinned, minus this one: pinShortcuts does
     * not remove one shortcut, it replaces the whole pinned set for a package in a profile,
     * so anything left out of that list is unpinned along with it.
     *
     * Then everything stored under the key goes too, but only once that call has actually
     * gone through: if it threw — the host permission was lost, the profile went away mid-
     * call — the shortcut is still pinned with the system, and forgetting its name, icon,
     * folder, hidden flag, launch count and quick-launch slot now would strand them with
     * nothing left to reconnect them to. An app can be reinstalled and find those waiting;
     * an unpinned shortcut never comes back, so what is left behind after a real unpin is
     * only clutter nothing can reach.
     */
    /**
     * Re-pins the shortcuts named by [keys], for a settings file restored onto another
     * device.
     *
     * A shortcut is not this app's to carry: it is an entry the publishing app offered and
     * the system keeps for the launcher, and it is nowhere in the file. What the file has is
     * the favorite's key, which names the package and the shortcut id — enough to ask the
     * system for it again, where the publisher still offers it.
     *
     * pinShortcuts replaces the whole pinned set for a package in a profile, so ids already
     * pinned there go back in the same call; leaving them out would unpin them. Best effort
     * throughout: an app that is gone, or no longer offers the shortcut, is simply not
     * restored.
     *
     * Returns how many package/profile pairs were asked about.
     */
    fun repinShortcuts(keys: Collection<String>): Int {
        val wanted = keys.mapNotNull { EntryKeys.parseShortcut(it) }
        if (wanted.isEmpty()) return 0
        val existing = pinnedShortcuts().map {
            EntryKeys.ShortcutRef(it.info.`package`, it.info.id, it.serial)
        }
        var asked = 0
        wanted.groupBy { it.packageName to it.userSerial }.forEach { (group, refs) ->
            val (packageName, serial) = group
            val user = runCatching { userManager.getUserForSerialNumber(serial) }.getOrNull()
                ?: return@forEach
            val keep = (
                existing.filter { it.packageName == packageName && it.userSerial == serial }
                    .map { it.shortcutId } + refs.map { it.shortcutId }
                ).distinct()
            val done = runCatching { launcherApps.pinShortcuts(packageName, keep, user) }.isSuccess
            if (done) asked++
        }
        if (asked > 0) noteShortcutsChanged()
        return asked
    }

    fun unpin(app: AppInfo) {
        if (app.kind != EntryKind.SHORTCUT) return
        val id = app.shortcutId ?: return
        val removed = EntryKeys.ShortcutRef(app.packageName, id, app.userSerial)
        val pinned = pinnedShortcuts().map {
            EntryKeys.ShortcutRef(it.info.`package`, it.info.id, it.serial)
        }
        // pinShortcuts replaces the whole pinned set for a package in a profile. If the list
        // just read does not even contain the shortcut being removed, that profile could not
        // be read this time, and what would be handed back is an empty list — unpinning every
        // other shortcut the package has there. Better to do nothing and say so.
        if (removed !in pinned) {
            Toast.makeText(context, R.string.shortcut_unpin_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val unpinned = runCatching {
            launcherApps.pinShortcuts(
                app.packageName,
                PinnedShortcuts.remainingIds(pinned, removed),
                app.user ?: Process.myUserHandle(),
            )
        }.isSuccess
        if (!unpinned) {
            Toast.makeText(context, R.string.shortcut_unpin_failed, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch { prefs.forgetEntry(app.key) }
        noteShortcutsChanged()
    }

    /**
     * Called once [LauncherApps.PinItemRequest.accept] has returned true for a shortcut from
     * [pkg] with id [id] in profile [user] (serial [serial]): confirms the system actually
     * pinned it, then joins it to the favorites, because "add to the home screen" is what the
     * user was asked.
     *
     * Deliberately on this repository's own scope rather than the confirm screen's. That
     * screen finishes the instant it calls this, and accept() taking effect can lag its own
     * return by a moment (see [waitUntilPinned]) — if the write instead rode the screen's own
     * scope, Cancel, a tap outside, rotation, or the screen merely stopping in that window
     * would cancel it, leaving a shortcut the system has pinned but that never shows up here.
     *
     * Verifying rather than trusting accept()'s own answer is also what a forged pin request
     * cannot get past: its binder can answer accept() however it likes, but it cannot make
     * [waitUntilPinned] find a shortcut pinned that genuinely is not.
     */
    fun confirmPinnedShortcut(pkg: String, id: String, user: UserHandle, serial: Long) {
        scope.launch {
            if (waitUntilPinned(pkg, id, user)) {
                prefs.addFavorite(EntryKeys.shortcut(pkg, id, serial))
                noteShortcutsChanged()
            } else {
                // This whole coroutine runs on the repository's Default-dispatched scope, so
                // a Toast here needs Main asked for explicitly rather than however Toast.show
                // elsewhere in this class gets it for free by already being called from the UI.
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.shortcut_pin_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Polls rather than trusting one look, since the shortcut a request just accepted can take
     * a moment to reach the system's own pinned set.
     */
    private suspend fun waitUntilPinned(pkg: String, id: String, user: UserHandle): Boolean {
        val query = LauncherApps.ShortcutQuery()
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            .setPackage(pkg)
            .setShortcutIds(listOf(id))
        repeat(PIN_CONFIRM_ATTEMPTS) { attempt ->
            val pinned = runCatching { launcherApps.getShortcuts(query, user) }
                .getOrNull()
                .orEmpty()
                .any { it.id == id }
            if (pinned) return true
            if (attempt < PIN_CONFIRM_ATTEMPTS - 1) delay(PIN_CONFIRM_DELAY_MS)
        }
        return false
    }

    /** Badged by the system, so a work or private-space app is recognizable at a glance. */
    fun loadIcon(app: AppInfo): Drawable {
        // Ours, and drawn from our own resources, so there is nothing to ask the system for.
        if (app.kind == EntryKind.PRIVATE_SPACE) return privateSpaceIcon(app)
        // A shortcut's own picture, badged with the app that published it. Falling through
        // means it has none of its own, and the publisher's app icon below is the answer.
        if (app.kind == EntryKind.SHORTCUT) shortcutIcon(app)?.let { return it }
        val user = app.user
        if (user != null) {
            val activity = runCatching {
                launcherApps.getActivityList(app.componentName.packageName, user)
                    .firstOrNull { it.componentName == app.componentName }
            }.getOrNull()
            activity?.let { info ->
                runCatching { info.getBadgedIcon(0) }.getOrNull()?.let { return it }
            }
        }
        runCatching { pm.getActivityIcon(app.componentName) }.getOrNull()?.let { return it }
        runCatching { pm.getApplicationIcon(app.componentName.packageName) }.getOrNull()
            ?.let { return it }
        return hiddenIcon(app.componentName) ?: pm.defaultActivityIcon
    }

    /**
     * The icon of a hidden app — the freeze method is HIDE.
     *
     * Hiding makes the package unavailable to every ordinary query, icons included: both
     * `getActivityIcon` and `getApplicationIcon` throw for it, and LauncherApps does not list
     * it. The package is still installed, so MATCH_UNINSTALLED_PACKAGES still reads it, and
     * that is the only way its icon comes back once a restart has taken the caches that
     * happened to cover for this away.
     */
    private fun hiddenIcon(componentName: ComponentName): Drawable? {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or
            PackageManager.MATCH_DISABLED_COMPONENTS
        runCatching { pm.getActivityInfo(componentName, flags) }
            .getOrNull()
            ?.let { info -> runCatching { info.loadIcon(pm) }.getOrNull()?.let { return it } }
        val info = FreezeState.applicationInfo(context, componentName.packageName) ?: return null
        return runCatching { info.loadIcon(pm) }.getOrNull()
    }

    private fun shortcutIcon(app: AppInfo): Drawable? {
        val info = findShortcut(app) ?: return null
        val density = context.resources.configuration.densityDpi
        return runCatching { launcherApps.getShortcutBadgedIconDrawable(info, density) }.getOrNull()
            ?: runCatching { launcherApps.getShortcutIconDrawable(info, density) }.getOrNull()
    }

    // An app's label comes back in the device's language, so on a Japanese phone the English
    // name is nowhere in the list. Loading it means asking the app's own resources for the
    // label a second time through an English configuration, which is a whole resource table
    // per app — so it is only ever asked for a name that has no A-Z letter of its own, and the
    // answer is kept.
    // ConcurrentHashMap forbids null values, so a miss is stored as the empty string
    // and mapped back to null on the way out. getOrPut with a null-returning lambda would
    // throw NullPointerException instead of caching anything.
    private val englishLabels = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * The app's name in English, or null if it has none or it is the name we already have.
     *
     * Null for anything that is not an app: a shortcut is named by whoever pinned it, and
     * there are no resources of its own to ask a second time.
     */
    fun englishLabel(app: AppInfo): String? {
        if (app.kind != EntryKind.APP) return null
        englishLabels[app.key]?.let { return it.ifEmpty { null } }
        val value = runCatching {
            val info = pm.getActivityInfo(app.componentName, 0)
            val labelRes = if (info.labelRes != 0) info.labelRes else info.applicationInfo.labelRes
            if (labelRes == 0) return@runCatching null
            val res = pm.getResourcesForApplication(info.applicationInfo)
            val config = Configuration(res.configuration).apply { setLocale(Locale.ENGLISH) }
            res.getString(labelRes).takeIf { it.isNotBlank() }?.let { english ->
                // The context-adjusted resources fall back to the default language when an app
                // ships no English, which just hands the same name back.
                Resources(res.assets, res.displayMetrics, config).getString(labelRes)
            }
        }.getOrNull().orEmpty()
        // putIfAbsent: two threads asking for the same app at once must not overwrite a
        // real answer with each other's miss.
        englishLabels.putIfAbsent(app.key, value)
        return (englishLabels[app.key] ?: value).ifEmpty { null }
    }

    /** A closed padlock while the space is locked, an open one while it is not. */
    private fun privateSpaceIcon(app: AppInfo): Drawable {
        val locked = app.componentName.className == PRIVATE_SPACE_LOCKED_CLASS
        val id = if (locked) R.mipmap.ic_private_space_locked else R.mipmap.ic_private_space_unlocked
        return ContextCompat.getDrawable(context, id) ?: pm.defaultActivityIcon
    }

    /**
     * Returns false if the app could not be started, so callers can undo whatever they hid.
     */
    fun launch(app: AppInfo, origin: Rect? = null): Boolean {
        // A shortcut points into its publisher's app, so it is just as unusable while that app
        // is frozen as the app's own row is.
        val enterable = app.kind == EntryKind.APP || app.kind == EntryKind.SHORTCUT
        if (enterable && app.frozen != null) {
            // Lifting the freeze is several shell commands; the tap is answered at once and
            // the app comes forward when they finish. It stays on the freeze list, so the
            // next trigger puts it back.
            scope.launch {
                val lifted = freezeManager.unfreeze(app)
                withContext(Dispatchers.Main) {
                    when (lifted) {
                        FreezeOutcome.DONE -> launchNow(app, origin)
                        FreezeOutcome.NO_ACCESS -> Toast.makeText(
                            context,
                            context.getString(R.string.freeze_needs_access),
                            Toast.LENGTH_LONG,
                        ).show()
                        else -> Toast.makeText(
                            context,
                            context.getString(R.string.freeze_unfreeze_failed, app.label),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            return true
        }
        return launchNow(app, origin)
    }

    private fun launchNow(app: AppInfo, origin: Rect?): Boolean {
        if (app.kind == EntryKind.SHORTCUT) return launchShortcut(app, origin)
        // Only an app has an activity to start. The private space row is opened by the screen
        // that knows what it is, and arriving here means it was routed wrongly — which must not
        // launch anything and must not be counted as a launch.
        if (app.kind != EntryKind.APP) return false
        if (app.componentName.packageName == context.packageName) return false
        val started = runCatching {
            // Through LauncherApps so an app in another profile starts as that profile; a
            // plain startActivity would look for it in ours and find nothing.
            launcherApps.startMainActivity(app.componentName, app.user ?: Process.myUserHandle(), origin, null)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(app.componentName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .also { if (origin != null) it.sourceBounds = origin },
                )
                true
            }.getOrElse {
                // A row rebuilt after a hide/disable may have no exact activity name, and the
                // package's own launcher intent is then the reliable way in.
                runCatching {
                    val intent = pm.getLaunchIntentForPackage(app.packageName) ?: return@runCatching false
                    if (origin != null) intent.sourceBounds = origin
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }.getOrDefault(false)
            }
        }
        if (started) {
            // Counted whether or not the usage sort is on, so switching it on later has a
            // history to order by instead of starting from nothing.
            scope.launch { prefs.incrementLaunchCount(app.key) }
        }
        return started
    }

    private fun launchShortcut(app: AppInfo, origin: Rect?): Boolean {
        val id = app.shortcutId ?: return false
        if (app.disabled) {
            // The publisher's own wording wherever there is any: it is the only thing that
            // can say why, and "sign in again to use this" beats a shrug.
            val message = findShortcut(app)?.disabledMessage?.toString()?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.shortcut_unavailable)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return false
        }
        // Through LauncherApps and nothing else. The Intent inside a ShortcutInfo belongs to
        // the app that published it and is never handed to a launcher; rebuilding one from
        // its parts would be starting something on that app's behalf that it did not ask for.
        val started = runCatching {
            launcherApps.startShortcut(
                app.packageName,
                id,
                origin,
                null,
                app.user ?: Process.myUserHandle(),
            )
            true
        }.getOrDefault(false)
        if (started) {
            scope.launch { prefs.incrementLaunchCount(app.key) }
        }
        return started
    }

    fun openAppInfo(app: AppInfo) {
        // Only an app has one. The menus leave the item out for everything else; this is so a
        // caller that has not can do no harm.
        if (app.kind != EntryKind.APP) return
        val user = app.user
        if (user != null) {
            val shown = runCatching {
                launcherApps.startAppDetailsActivity(app.componentName, user, null, null)
                true
            }.getOrDefault(false)
            if (shown) return
        }
        openAppInfo(app.componentName.packageName)
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Best-effort un-pause, so a locked-and-paused app can open once its lock is lifted.
     *
     * Android only lets the app that paused it, a profile owner or a device owner lift a
     * suspension, so on a stock phone this is refused and the app stays paused. It is attempted
     * anyway: where the launcher does hold the privilege, it is the difference between the app
     * opening and doing nothing.
     */
    fun unsuspend(app: AppInfo) {
        if (app.kind != EntryKind.APP) return
        runCatching {
            val pm = context.packageManager
            // Found by name rather than referenced: the method is on PackageManager but is not
            // in the public SDK stubs, and it needs a permission only a profile or device owner
            // holds — so on a stock phone this is refused and the app stays paused. Attempted
            // anyway, because where the launcher does hold it, it is the difference between the
            // app opening and doing nothing.
            val method = pm.javaClass.methods.firstOrNull {
                it.name == "setPackagesSuspended" && it.parameterTypes.size == 5
            }
            method?.invoke(pm, arrayOf(app.componentName.packageName), false, null, null, null)
        }
    }

    /**
     * Hands an app to the system uninstaller.
     *
     * Never removes anything itself — the confirmation is the system's, and only the package
     * manager may run it. A work-profile app is uninstalled from its own profile, which is why
     * the user travels with the intent. Force stop is deliberately absent: a normal app cannot
     * stop another's process, that needs a system permission this app does not hold.
     */
    fun uninstall(app: AppInfo) {
        if (app.kind != EntryKind.APP) return
        val pkg = app.componentName.packageName
        val uri = Uri.fromParts("package", pkg, null)
        // The system uninstaller, not the package manager: the confirmation and the removal are
        // the system's, and only it may do them. A work-profile app is uninstalled from its own
        // profile, so the user travels with the intent — but only when it is not the main one,
        // where carrying it just confuses the installer.
        fun intent(action: String) = Intent(action, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (app.userSerial != 0L) app.user?.let { putExtra(Intent.EXTRA_USER, it) }
        }
        val started = runCatching { context.startActivity(intent(Intent.ACTION_DELETE)) }.isSuccess
        if (!started) {
            runCatching { context.startActivity(intent(Intent.ACTION_UNINSTALL_PACKAGE)) }
        }
    }
}
