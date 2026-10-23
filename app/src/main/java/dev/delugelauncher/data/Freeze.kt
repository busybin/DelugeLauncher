// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
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
enum class FreezeOutcome { DONE, NO_ACCESS, SYSTEM_APPS_OFF, NOT_LISTED, FAILED, DISABLED }

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

    /** Runs in order; the force stop is what makes the state change take effect at once. */
    fun freeze(packageName: String, method: FreezeMethod): List<String> {
        if (!isPackageName(packageName)) return emptyList()
        return when (method) {
            FreezeMethod.DISABLE -> listOf(
                "am force-stop $packageName",
                "pm disable-user --user 0 $packageName",
            )
            FreezeMethod.HIDE -> listOf(
                "am force-stop $packageName",
                "pm hide $packageName",
            )
            FreezeMethod.SUSPEND -> listOf(
                "am force-stop $packageName",
                "appops set $packageName RUN_ANY_IN_BACKGROUND ignore",
                "appops set $packageName RUN_IN_BACKGROUND ignore",
                "pm suspend --user 0 $packageName",
            )
            FreezeMethod.STOP -> listOf("am force-stop $packageName")
        }
    }

    /** The exact reverse of [freeze], plus every other reverse for the unfreeze-all fallback. */
    fun unfreeze(packageName: String, method: FreezeMethod): List<String> {
        if (!isPackageName(packageName)) return emptyList()
        return when (method) {
            FreezeMethod.DISABLE -> listOf("pm enable --user 0 $packageName")
            FreezeMethod.HIDE -> listOf("pm unhide $packageName")
            FreezeMethod.SUSPEND -> listOf(
                "appops set $packageName RUN_IN_BACKGROUND allow",
                "appops set $packageName RUN_ANY_IN_BACKGROUND allow",
                "pm unsuspend --user 0 $packageName",
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
    fun unfreezeEverything(packageName: String): List<String> {
        if (!isPackageName(packageName)) return emptyList()
        return listOf(
            "pm enable --user 0 $packageName",
            "pm unhide $packageName",
            "appops set $packageName RUN_IN_BACKGROUND allow",
            "appops set $packageName RUN_ANY_IN_BACKGROUND allow",
            "pm unsuspend --user 0 $packageName",
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
     * The apps a whole-list lift has to touch: everything recorded as frozen, plus anything the
     * device still reports as held. A STOP app has nothing to undo and is left out unless it is
     * genuinely held, which saves a full round of no-op commands per listed app.
     */
    fun toLift(
        candidates: Collection<String>,
        applied: Set<String>,
        plan: Map<String, FreezeMethod>,
        isFrozen: (String) -> Boolean,
    ): List<String> = candidates.filter { packageName ->
        val frozen = isFrozen(packageName)
        if (plan[packageName] == FreezeMethod.STOP && !frozen) false
        else packageName in applied || frozen
    }

    /**
     * Whether a lift under [method] can be confirmed by reading the device afterwards.
     *
     * Hiding has no public state to read, so there a clean command is the only answer there
     * is; without this the lift was believed to have happened and its command never ran.
     */
    fun verifiable(method: FreezeMethod?): Boolean = method != FreezeMethod.HIDE
}

/** Reads the live state the package manager reports for a frozen app. */object FreezeState {

    private const val MATCH_UNINSTALLED = PackageManager.MATCH_UNINSTALLED_PACKAGES

    fun applicationInfo(context: Context, packageName: String): ApplicationInfo? = runCatching {
        context.packageManager.getApplicationInfo(packageName, MATCH_UNINSTALLED)
    }.getOrNull()

    /** A preinstalled app, including one updated in place. */
    fun isSystemApp(context: Context, packageName: String): Boolean {
        val flags = applicationInfo(context, packageName)?.flags ?: return false
        return flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

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

/** A shell that runs commands as root (if the device grants it) or through Shizuku. */
class FreezeShell(private val context: Context) {

    private data class RootState(val provider: RootProvider, val version: String?)

    private var rootState: RootState? = null

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
            val result = runProcess(arrayOf("su", "-c", ROOT_PROBE), ROOT_TIMEOUT_MS)
            if (result?.first != 0 || !result.second.contains("uid=0")) return null
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
            return RootState(provider, version).also { rootState = it }
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

    /** Whether a root shell answered the probe, regardless of which channel is in use. */
    fun rootAvailable(): Boolean = rootState() != null

    /** Whether Shizuku is running with access granted. */
    fun shizukuAvailable(): Boolean = shizukuReady()

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


/**
 * Keeps the freeze list, and freezes and unfreezes what is on it.
 *
 * Two things are stored and they are deliberately not the same thing: the list
 * ([Prefs.freezeMethods], package to method) is what the user chose, and [Prefs.freezeApplied]
 * is which of those are frozen at this moment. Choosing an app does not freeze it — only a
 * trigger does — and unfreezing leaves the app on the list, so the next trigger puts it back.
 */
class FreezeManager(private val context: Context, private val prefs: Prefs) {

    private val shell = FreezeShell(context)
    private val binder = FreezeBinder(context)

    /** One whole-list run at a time: a second tap waits instead of racing the first. */
    private val gate = Mutex()

    // Checked only when the freeze screen asks: probing root can raise a prompt, which has no
    // business happening during a cold start.
    private val _status = kotlinx.coroutines.flow.MutableStateFlow(FreezeStatus())
    val status: kotlinx.coroutines.flow.StateFlow<FreezeStatus> = _status

    private val _changes = kotlinx.coroutines.flow.MutableStateFlow(0)
    val changes: kotlinx.coroutines.flow.StateFlow<Int> = _changes

    suspend fun refreshAccess() {
        _status.value = withContext(Dispatchers.IO) {
            shell.status()
        }
        reconcile()
        upgradeSuspendDialog()
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
        val targets = prefs.freezeAppliedNow().filter { plan[it] == FreezeMethod.SUSPEND }
        if (targets.isEmpty()) {
            prefs.setFreezeDialogReady()
            return
        }
        val done = withContext(Dispatchers.IO) {
            targets.map { packageName -> async { binder.refreshSuspend(packageName) == true } }.awaitAll()
        }
        if (done.all { it }) prefs.setFreezeDialogReady()
    }

    /** Probes again before an operation that cannot work without access. */
    private suspend fun ensureAccess(): FreezeAccess {
        refreshAccess()
        return _status.value.access
    }

    /**
     * Brings the applied set back in line with the device, without touching the list.
     *
     * Two things are corrected. An app that is gone leaves the list. And an app that is
     * recorded as frozen but is not — the user re-enabled it in Settings, or a STOP did not
     * survive a reboot — stops being drawn as frozen. Only methods whose state the platform
     * reports are checked; HIDE has no public answer and SUSPEND has been seen to lie, and
     * a wrong "not frozen" there would be worse than a stale one.
     */
    private suspend fun reconcile() {
        val plan = prefs.freezeMethodsNow()
        val applied = prefs.freezeAppliedNow()
        val gone = withContext(Dispatchers.IO) {
            plan.keys.filter { packageName ->
                val info = FreezeState.applicationInfo(context, packageName)
                info == null || info.flags and ApplicationInfo.FLAG_INSTALLED == 0
            }
        }
        val noLonger = withContext(Dispatchers.IO) {
            applied.filter { packageName ->
                when (val method = plan[packageName]) {
                    null -> true
                    FreezeMethod.DISABLE, FreezeMethod.STOP ->
                        !FreezeState.isFrozen(context, packageName, method)
                    else -> false
                }
            }
        }
        if (gone.isEmpty() && noLonger.isEmpty()) return
        gone.forEach { prefs.removeFreezeTarget(it) }
        val drop = noLonger.toSet() - gone.toSet()
        if (drop.isNotEmpty()) prefs.setFreezeApplied(drop, applied = false)
        _changes.value += 1
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
            val applied = prefs.freezeAppliedNow()
            val plan = prefs.freezeMethodsNow()
            // Every listed app is restored, not only the ones the applied record remembers: a
            // crash between a freeze and its record would otherwise leave an app stuck with the
            // feature gone and no screen left to lift it.
            val targets = withContext(Dispatchers.IO) {
                // A hidden app cannot be read back, so anything listed or recorded under
                // HIDE is restored without asking whether it looks frozen first.
                (plan.keys + applied).distinct().filter {
                    looksFrozen(it) ||
                        plan[it] == FreezeMethod.HIDE ||
                        (it in applied && plan[it] != FreezeMethod.STOP)
                }
            }
            var lifted = 0
            if (targets.isNotEmpty()) {
                if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeBatchResult(noAccess = true)
                val done = withContext(Dispatchers.IO) {
                    liftedIn(targets.map { it to plan[it] })
                }
                lifted = done.size
                prefs.setFreezeApplied(done, applied = false)
                _changes.value += 1
                if (done.size != targets.size) {
                    return@withLock FreezeBatchResult(done = lifted, failed = targets.size - done.size)
                }
            }
            prefs.clearFreezePlan()
            prefs.clearFreezeActionBindings()
            prefs.setFreezeEnabled(false)
            _changes.value += 1
            FreezeBatchResult(done = lifted)
        }
    }

    /**
     * Inverts the selection within one method: listed apps leave the list, unlisted ones join
     * it. Apps listed under another method are left where they are.
     *
     * Removing a frozen app has to lift it, so no access means nothing is changed at all.
     */
    suspend fun invertPlan(packages: List<String>, method: FreezeMethod): FreezeBatchResult = gate.withLock {
        if (!prefs.freezeEnabledNow()) return@withLock FreezeBatchResult()
        val current = prefs.freezeMethodsNow()
        val unique = packages.distinct()
        val listed = unique.filter { current[it] == method }
        val unlisted = unique.filter { current[it] == null }
        if (listed.isEmpty() && unlisted.isEmpty()) return@withLock FreezeBatchResult()
        val applied = prefs.freezeAppliedNow()
        val toLift = withContext(Dispatchers.IO) {
            listed.filter { it in applied || looksFrozen(it) }
        }
        var done = 0
        if (toLift.isNotEmpty()) {
            if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeBatchResult(noAccess = true)
            val lifted = withContext(Dispatchers.IO) {
                liftedIn(toLift.map { it to current[it] })
            }
            prefs.setFreezeApplied(lifted, applied = false)
            prefs.removeFreezeTargets(lifted)
            done += lifted.size
            if (lifted.size != toLift.size) {
                _changes.value += 1
                return@withLock FreezeBatchResult(done = done, failed = toLift.size - lifted.size)
            }
        }
        val plainRemovals = listed.filterNot { it in toLift.toSet() }
        prefs.removeFreezeTargets(plainRemovals)
        done += plainRemovals.size
        if (unlisted.isNotEmpty()) {
            prefs.setFreezeMethods(unlisted.associateWith { method })
            done += unlisted.size
        }
        _changes.value += 1
        FreezeBatchResult(done = done)
    }

    /** Adds an app to the list. Nothing is frozen by this. */
    suspend fun addToPlan(packageName: String, method: FreezeMethod): FreezeOutcome {
        if (!prefs.freezeEnabledNow()) return FreezeOutcome.DISABLED
        if (!validPackage(packageName)) return FreezeOutcome.FAILED
        if (!systemAllowed(packageName)) return FreezeOutcome.SYSTEM_APPS_OFF
        prefs.setFreezeMethod(packageName, method)
        _changes.value += 1
        return FreezeOutcome.DONE
    }

    /** Adds a batch to the list with one method — Select all. Nothing is frozen by this. */
    suspend fun addManyToPlan(packages: List<String>, method: FreezeMethod): Int {
        if (!prefs.freezeEnabledNow()) return 0
        val targets = packages.filter { validPackage(it) && systemAllowed(it) }
        if (targets.isEmpty()) return 0
        prefs.setFreezeMethods(targets.associateWith { method })
        _changes.value += 1
        return targets.size
    }

    /** Freezes one listed app now, under the method the list remembers. */
    suspend fun freeze(packageName: String): FreezeOutcome = gate.withLock {
        if (!prefs.freezeEnabledNow()) return@withLock FreezeOutcome.DISABLED
        val method = prefs.freezeMethodsNow()[packageName] ?: return@withLock FreezeOutcome.NOT_LISTED
        if (!validPackage(packageName)) return@withLock FreezeOutcome.FAILED
        if (!systemAllowed(packageName)) return@withLock FreezeOutcome.SYSTEM_APPS_OFF
        if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeOutcome.NO_ACCESS
        val already = withContext(Dispatchers.IO) {
            packageName in prefs.freezeAppliedNow() && looksFrozen(packageName)
        }
        if (!already) {
            val frozen = withContext(Dispatchers.IO) {
                frozenIn(mapOf(packageName to method))
            }
            if (packageName !in frozen) return@withLock FreezeOutcome.FAILED
        }
        prefs.setFreezeApplied(setOf(packageName), applied = true)
        _changes.value += 1
        FreezeOutcome.DONE
    }

    /**
     * Freezes [packageName] with [method] right now, listing it first when it is not there yet.
     *
     * The list is what a later Freeze all works from, so freezing a single app from a row menu
     * is what puts it on the list too — one tap, no separate step to remember.
     */
    suspend fun freezeNow(packageName: String, method: FreezeMethod): FreezeOutcome {
        val added = addToPlan(packageName, method)
        if (added != FreezeOutcome.DONE) return added
        return freeze(packageName)
    }

    /** Unfreezes one app for now; it stays on the list. */
    suspend fun unfreeze(packageName: String): FreezeOutcome = gate.withLock {
        // A record or a live state either way: an app frozen by another tool has no record,
        // and a hidden one cannot be read back, so the record decides for it.
        val needsLift = withContext(Dispatchers.IO) {
            packageName in prefs.freezeAppliedNow() || looksFrozen(packageName)
        }
        if (!needsLift) return@withLock FreezeOutcome.DONE
        if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeOutcome.NO_ACCESS
        val method = prefs.freezeMethodsNow()[packageName]
        val lifted = withContext(Dispatchers.IO) {
            liftedIn(listOf(packageName to method))
        }
        if (packageName !in lifted) return@withLock FreezeOutcome.FAILED
        prefs.setFreezeApplied(setOf(packageName), applied = false)
        _changes.value += 1
        FreezeOutcome.DONE
    }

    /** Takes an app off the list for good, unfreezing it if it was frozen. */
    suspend fun removeFromPlan(packageName: String): FreezeOutcome = gate.withLock {
        val needsLift = withContext(Dispatchers.IO) {
            packageName in prefs.freezeAppliedNow() || looksFrozen(packageName)
        }
        if (needsLift) {
            if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeOutcome.NO_ACCESS
            val method = prefs.freezeMethodsNow()[packageName]
            val lifted = withContext(Dispatchers.IO) {
                liftedIn(listOf(packageName to method))
            }
            // Keeping the row is better than dropping the only record of a stuck app.
            if (packageName !in lifted) return@withLock FreezeOutcome.FAILED
            prefs.setFreezeApplied(setOf(packageName), applied = false)
        }
        prefs.removeFreezeTarget(packageName)
        _changes.value += 1
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
                // After the access check: reconciling may have dropped apps that are gone.
                val plan = prefs.freezeMethodsNow()
                if (plan.isEmpty()) return@withLock FreezeBatchResult()
                val systemEnabled = prefs.freezeSystemAppsNow()
                val skip = if (auto) withContext(Dispatchers.IO) { autoSkip() } else emptySet()
                val (frozenBefore, targets) = withContext(Dispatchers.IO) {
                    val allowed = if (systemEnabled) plan
                    else plan.filterKeys { !FreezeState.isSystemApp(context, it) }
                    // One state read per app, shared by the skip decision and the record.
                    val seen = mutableMapOf<String, Boolean>()
                    val isFrozen: (String) -> Boolean = { seen.getOrPut(it) { looksFrozen(it) } }
                    val already = allowed.keys.filter { it !in skip && isFrozen(it) }
                    already to FreezeSelection.toFreeze(allowed, skip, isFrozen).associateWith { allowed.getValue(it) }
                }
                // Only the apps that need it: after the first screen-off every screen-off reads the
                // state of the list instead of running four commands per app all over again.
                val done = withContext(Dispatchers.IO) { frozenIn(targets) }
                val failed = targets.keys - done
                prefs.setFreezeApplied(frozenBefore.toSet() + done, applied = true)
                _changes.value += 1
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
            val appliedNow = prefs.freezeAppliedNow()
            val plan = prefs.freezeMethodsNow()
            if (appliedNow.isEmpty() && plan.isEmpty()) return@withLock FreezeBatchResult()
            if (ensureAccess() == FreezeAccess.NONE) return@withLock FreezeBatchResult(noAccess = true)
            val candidates = (appliedNow + plan.keys).distinct()
            val targets = withContext(Dispatchers.IO) {
                // The record is the answer for anything the platform cannot report back, and the
                // live state catches apps frozen without one.
                FreezeSelection.toLift(candidates, appliedNow, plan, ::looksFrozen)
            }
            val done = withContext(Dispatchers.IO) {
                liftedIn(targets.map { it to plan[it] })
            }
            val failed = targets.filter { it !in done }
            prefs.setFreezeApplied(appliedNow - failed.toSet(), applied = false)
            _changes.value += 1
            FreezeBatchResult(done = done.size, failed = failed.size)
        }
    }

    /**
     * Turns the system-app section on or off. Turning it off also lifts anything system-level
     * that is frozen right now: hiding the rows and leaving those apps disabled would leave
     * the user no way back to them.
     */
    suspend fun setSystemAppsEnabled(enabled: Boolean): FreezeBatchResult = gate.withLock {
        var unfrozen = 0
        if (!enabled) {
            val plan = prefs.freezeMethodsNow()
            val systemPlan = withContext(Dispatchers.IO) {
                plan.keys.filter { FreezeState.isSystemApp(context, it) }
            }
            val frozen = withContext(Dispatchers.IO) {
                val applied = prefs.freezeAppliedNow()
                // Same as turning the feature off: a hidden app cannot be read back, so its
                // own record or its HIDE listing has to be enough.
                systemPlan.filter { it in applied || looksFrozen(it) || plan[it] == FreezeMethod.HIDE }
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
            systemPlan.forEach { prefs.removeFreezeTarget(it) }
        }
        prefs.setFreezeSystemApps(enabled)
        _changes.value += 1
        FreezeBatchResult(done = unfrozen)
    }

    private fun validPackage(packageName: String): Boolean =
        packageName != context.packageName && FreezeCommands.isPackageName(packageName)

    private suspend fun systemAllowed(packageName: String): Boolean =
        prefs.freezeSystemAppsNow() || !FreezeState.isSystemApp(context, packageName)

    /** Reads the two dumps in one elevated shell, only when the setting asks for them. */
    private suspend fun autoSkip(): Set<String> {
        val skip = mutableSetOf<String>()
        if (prefs.freezeAutoSkipForegroundNow()) {
            shell.output(FOREGROUND_DUMP)?.let { FreezeGuards.foregroundPackage(it)?.let(skip::add) }
        }
        if (prefs.freezeAutoSkipNotificationsNow()) {
            shell.output(NOTIFICATION_DUMP)?.let { skip += FreezeGuards.notifiedPackages(it) }
        }
        return skip
    }

    /**
     * Freezes [packageName] targets and answers with the set that took.
     *
     * Shizuku's binder path does one IPC per app where the shell path forks a `pm` process per
     * command. Hiding is the exception: it is a root operation, so it keeps the root shell.
     * Whatever the binder could not do falls back to the command path.
     */
    private suspend fun frozenIn(targets: Map<String, FreezeMethod>): Set<String> {
        if (targets.isEmpty()) return emptySet()
        val done = mutableSetOf<String>()
        val hiding = targets.filterValues { it == FreezeMethod.HIDE }
        val direct = targets - hiding.keys
        if (direct.isNotEmpty() && binder.available()) {
            val results = withContext(Dispatchers.IO) {
                direct.map { (packageName, method) ->
                    async { packageName to (binder.freeze(packageName, method) == true) }
                }.awaitAll()
            }
            done += results.filter { it.second }.map { it.first }
        }
        if (hiding.isNotEmpty()) {
            val entries = hiding.entries.toList()
            val answers = shell.runGroups(
                entries.map { FreezeCommands.freeze(it.key, it.value) },
                root = true,
            )
            done += entries.indices.filter { answers.getOrNull(it) == true }.map { entries[it].key }
        }
        val left = targets.filterKeys { it !in done }
        if (left.isNotEmpty()) {
            val entries = left.entries.toList()
            val answers = shell.runGroups(entries.map { FreezeCommands.freeze(it.key, it.value) })
            done += entries.indices.filter { answers.getOrNull(it) == true }.map { entries[it].key }
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
    private suspend fun liftedIn(targets: List<Pair<String, FreezeMethod?>>): Set<String> {
        if (targets.isEmpty()) return emptySet()
        val entries = targets.distinctBy { it.first }
        if (binder.available()) {
            withContext(Dispatchers.IO) {
                entries.map { (packageName, _) ->
                    async { binder.unfreeze(packageName) }
                }.awaitAll()
            }
        }
        var pending = withContext(Dispatchers.IO) {
            // Verified against every readable state, not the stored method: a record can name
            // the wrong method (an older build, another tool, a method changed on the list),
            // and then the command for that method lifts nothing while still exiting cleanly.
            entries.filter { !liftedNow(it.first) || it.second == FreezeMethod.HIDE }
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
        return entries.map { it.first }.filterNot { it in failed }.toSet()
    }

    private suspend fun shellLift(
        pending: List<Pair<String, FreezeMethod?>>,
        perMethod: Boolean,
        root: Boolean?,
    ): List<Pair<String, FreezeMethod?>> {
        val answers = shell.runGroups(
            pending.map { (packageName, method) ->
                if (perMethod && method != null) FreezeCommands.unfreeze(packageName, method)
                else FreezeCommands.unfreezeEverything(packageName)
            },
            root = root,
        )
        return withContext(Dispatchers.IO) {
            pending.filterIndexed { index, entry ->
                val answered = answers.getOrNull(index) == true
                // Hiding cannot be read back: a clean command is the only answer there is.
                !answered ||
                    (FreezeSelection.verifiable(entry.second) && !liftedNow(entry.first))
            }
        }
    }

    /**
     * Whether the device still reports this app as held by some freeze, using cheap local
     * reads only. This is what lets a whole-list run skip the apps already where they belong,
     * which on every screen-off after the first is nearly all of them.
     *
     * A missing package counts as held: a hidden one is not reliably readable and running the
     * harmless lift commands on a gone package costs nothing.
     */
    private fun looksFrozen(packageName: String): Boolean {
        val info = FreezeState.applicationInfo(context, packageName) ?: return true
        return !info.enabled || isSuspended(packageName) || FreezeState.isHidden(info)
    }

    /**
     * Whether the device reports this app as free again, on every state it can read.
     *
     * Not asked per stored method: a wrong record then answers for the wrong thing, and a
     * clean no-op command has already been counted as a lift. Hiding is left out because it
     * has no public state; the caller trusts the command there.
     */
    private fun liftedNow(packageName: String): Boolean =
        !isSuspended(packageName) &&
            FreezeState.applicationInfo(context, packageName)?.enabled != false

    private fun isSuspended(packageName: String): Boolean = runCatching {
        context.packageManager.isPackageSuspended(packageName)
    }.getOrDefault(false)

    companion object {
        private const val FOREGROUND_DUMP =
            "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|mFocusedApp'"
        private const val NOTIFICATION_DUMP =
            "dumpsys notification --noredact | grep 'pkg='"
    }
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
