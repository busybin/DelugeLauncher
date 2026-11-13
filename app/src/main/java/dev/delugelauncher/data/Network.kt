// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How the list is read. Off means nothing is enforced and every rule is lifted. */
enum class NetworkMode {
    OFF,
    /** Listed apps are cut off; everything else keeps the network. */
    BLACKLIST,
    /** Only listed apps keep the network; the rest are cut off. Not exposed in v1. */
    WHITELIST;

    val enabled: Boolean get() = this != OFF

    companion object {
        fun parse(raw: String?): NetworkMode =
            entries.firstOrNull { it.name == raw } ?: OFF
    }
}

/**
 * What the user asked for, which is not what the platform is doing. The two are compared in
 * [NetworkManager.sync]; a rule that was written here but not read back is pending, not applied.
 */
data class NetworkPolicy(
    val mode: NetworkMode = NetworkMode.OFF,
    /** Explicitly cut off, whatever the freeze state. */
    val blocked: Set<String> = emptySet(),
    /** Explicitly kept online. Only meaningful in [NetworkMode.WHITELIST]. */
    val allowed: Set<String> = emptySet(),
    /** A newly installed app joins the list while the firewall is on. */
    val blockNewApps: Boolean = false,
    /** Frozen apps the freeze link must leave online. */
    val unrestricted: Set<String> = emptySet(),
) {
    /** Whether this package should be offline under this policy, protected rows aside. */
    fun wantsBlocked(packageName: String): Boolean = when (mode) {
        NetworkMode.OFF -> false
        NetworkMode.BLACKLIST -> packageName in blocked
        NetworkMode.WHITELIST -> packageName !in allowed
    }

    /**
     * The packages that should be offline under this policy.
     *
     * The explicit list wins over the freeze link and over [unrestricted]: a package on both
     * the block list and the unrestricted set is still cut off, because that is the stronger
     * promise the user made. [installed] is only read by the whitelist mode.
     */
    fun desired(
        frozen: Set<String>,
        installed: Set<String> = emptySet(),
        protected: Set<String> = emptySet(),
    ): Set<String> {
        if (!mode.enabled) return emptySet()
        val base = when (mode) {
            NetworkMode.OFF -> emptySet()
            NetworkMode.BLACKLIST -> blocked
            NetworkMode.WHITELIST -> installed - allowed
        }
        return (base + (frozen - unrestricted)).filterNot { it in protected }.toSet()
    }
}

/**
 * The three answers a row can carry, plus the rows the firewall never touches.
 *
 * [BLOCKED] wins over [UNRESTRICTED] when both were stored: the explicit cut-off is the
 * stronger promise, so a package on both lists stays offline.
 */
enum class NetworkAppState {
    /** Offline while frozen, online again after a thaw. */
    FOLLOW_FREEZE,
    /** Offline whether or not it is frozen. */
    BLOCKED,
    /** Online even while it is frozen. */
    UNRESTRICTED,
    /** Never offered: the launcher, telephony, the home screen, the keyboard. */
    PROTECTED,
}

/** The platform's answer, never the user's intent. */
data class NetworkStatus(
    val supported: Boolean = false,
    val available: Boolean = false,
    val chainEnabled: Boolean? = null,
    val applied: Set<String> = emptySet(),
    val pending: Set<String> = emptySet(),
    val unknown: Set<String> = emptySet(),
    val lastSync: Long = 0L,
)

data class NetworkResult(
    val applied: Int = 0,
    val pending: Int = 0,
    val unknown: Int = 0,
    val restored: Int = 0,
    val skippedProtected: Int = 0,
    val noAccess: Boolean = false,
)

/** The shell the manager needs, so the state machine can be tested without a device. */
interface NetworkShell {
    fun quietAccess(): Boolean
    fun runCapture(command: String, timeoutMs: Long): Pair<Int, String>?
}

/** Adapts the freeze list's privileged shell to the small surface the firewall uses. */
class FreezeNetworkShell(private val shell: FreezeShell) : NetworkShell {
    override fun quietAccess(): Boolean = shell.quietAccess()
    override fun runCapture(command: String, timeoutMs: Long): Pair<Int, String>? =
        shell.runCapture(command, timeoutMs)
}

/** What the platform has confirmed, what is still waiting, and what could not be read. */
data class NetworkBuckets(
    val applied: Set<String> = emptySet(),
    val pending: Set<String> = emptySet(),
    val unknown: Set<String> = emptySet(),
) {
    fun without(packages: Collection<String>): NetworkBuckets = NetworkBuckets(
        applied = applied - packages.toSet(),
        pending = pending - packages.toSet(),
        unknown = unknown - packages.toSet(),
    )

    fun with(applied: Set<String>, pending: Set<String>, unknown: Set<String>): NetworkBuckets =
        NetworkBuckets(
            applied = this.applied + applied,
            pending = this.pending + pending,
            unknown = this.unknown + unknown,
        )
}

/**
 * The pure half of the readback state machine. A clean exit code is not proof: only a readback
 * that says "deny" moves a row into applied, and a readback that cannot be parsed leaves it
 * unknown rather than claiming success.
 */
object NetworkState {
    fun block(
        before: NetworkBuckets,
        chunk: List<String>,
        codes: Map<Int, Boolean>,
        read: Map<String, Boolean?>,
    ): NetworkBuckets {
        val applied = mutableSetOf<String>()
        val pending = mutableSetOf<String>()
        val unknown = mutableSetOf<String>()
        chunk.forEachIndexed { index, packageName ->
            when (read[packageName]) {
                true -> applied += packageName
                false -> pending += packageName
                null -> if (codes[index] == true) unknown += packageName else pending += packageName
            }
        }
        return before.without(chunk).with(applied, pending, unknown)
    }

    fun restore(
        before: NetworkBuckets,
        chunk: List<String>,
        read: Map<String, Boolean?>,
    ): NetworkBuckets {
        val still = chunk.filter { read[it] != false }
        return before.without(chunk - still.toSet()).with(emptySet(), still.toSet(), emptySet())
    }
}

/**
 * The commands, kept pure so ordering, escaping and the marker protocol are testable on the
 * JVM. A whole chunk goes through one shell and every command leaves its own exit code, so one
 * refused package cannot hide the rest.
 */
object NetworkCommands {
    private const val CHAIN = "cmd connectivity set-chain3-enabled"
    private const val PACKAGE = "cmd connectivity set-package-networking-enabled"

    fun isPackageName(name: String): Boolean = FreezeCommands.isPackageName(name)

    fun setChain(enabled: Boolean): String = "$CHAIN ${if (enabled) "true" else "false"}"

    fun readChain(): String = "cmd connectivity get-chain3-enabled"

    fun setPackage(packageName: String, blocked: Boolean): String =
        "$PACKAGE ${if (blocked) "false" else "true"} $packageName"

    fun readPackage(packageName: String): String =
        "cmd connectivity get-package-networking-enabled $packageName"

    fun applyScript(packages: Collection<String>, blocked: Boolean): String = buildString {
        packages.forEachIndexed { index, packageName ->
            if (!isPackageName(packageName)) return@forEachIndexed
            appendLine("echo __N${index}__")
            appendLine(setPackage(packageName, blocked))
            appendLine("echo __R${index}__:\$?")
        }
    }

    fun readScript(packages: List<String>): String = buildString {
        packages.forEachIndexed { index, packageName ->
            appendLine("echo __N${index}__")
            appendLine(readPackage(packageName))
        }
    }

    private val RESULT = Regex("""__R(\d+)__:(\d+)""")
    private val MARKER = Regex("""__N(\d+)__""")

    /** Index to whether the command exited cleanly. A missing index never ran. */
    fun parseApply(output: String, count: Int): Map<Int, Boolean> {
        val result = mutableMapOf<Int, Boolean>()
        RESULT.findAll(output).forEach { match ->
            val index = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (index in 0 until count) result[index] = match.groupValues[2] == "0"
        }
        return result
    }

    /**
     * Package to blocked: true is denied, false is allowed, null is unreadable.
     *
     * The command prints `<package>:allow` or `<package>:deny`; anything else, including no
     * output at all, stays unknown rather than being reported as enforced.
     */
    fun parseRead(output: String, packages: List<String>): Map<String, Boolean?> {
        val result = LinkedHashMap<String, Boolean?>()
        val markers = MARKER.findAll(output).toList()
        markers.forEachIndexed { position, marker ->
            val index = marker.groupValues[1].toIntOrNull() ?: return@forEachIndexed
            val packageName = packages.getOrNull(index) ?: return@forEachIndexed
            val end = markers.getOrNull(position + 1)?.range?.first ?: output.length
            val chunk = output.substring(marker.range.last + 1, end).lowercase()
            result[packageName] = when {
                chunk.contains(":deny") -> true
                chunk.contains(":allow") -> false
                else -> null
            }
        }
        return result
    }

    /** `chain:enabled` / `chain:disabled`; null when the channel did not answer. */
    fun parseChain(output: String): Boolean? = when {
        output.contains("chain:enabled") -> true
        output.contains("chain:disabled") -> false
        else -> null
    }
}

/**
 * Rows the firewall must never cut off: the launcher itself, telephony, the system UI, the
 * default home and the enabled input methods. Shared by the manager and the page so both
 * agree on what the greyed-out switch means.
 */
object NetworkProtection {
    val FIXED = setOf(
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.android.systemui",
    )

    fun isProtected(packageName: String, self: String, extras: Set<String> = emptySet()): Boolean =
        packageName == self || packageName in FIXED || packageName in extras
}

/**
 * Applies the per-app firewall through the same privileged shell the freeze list uses.
 *
 * The platform keeps no record of these rules: chain3 is a global switch and the per-package
 * rules vanish on reboot. Everything that has to survive a reboot is written to [Prefs] and
 * replayed by [sync]; nothing here trusts its own last write, only the readback.
 */
class NetworkManager(
    private val context: Context,
    private val prefs: Prefs,
    private val shell: NetworkShell,
) {
    private val gate = Mutex()

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 30

    suspend fun statusNow(): NetworkStatus = withContext(Dispatchers.IO) {
        val applied = prefs.networkAppliedNow()
        val pending = prefs.networkPendingNow()
        val unknown = prefs.networkUnknownNow()
        val quiet = shell.quietAccess()
        NetworkStatus(
            supported = supported(),
            available = supported() && quiet,
            chainEnabled = if (quiet) {
                shell.runCapture(NetworkCommands.readChain(), TIMEOUT)
                    ?.second?.let(NetworkCommands::parseChain)
            } else {
                null
            },
            applied = applied,
            pending = pending,
            unknown = unknown,
            lastSync = prefs.networkLastSyncNow(),
        )
    }

    suspend fun setMode(mode: NetworkMode): NetworkResult = gate.withLock {
        prefs.setNetworkMode(mode)
        if (mode == NetworkMode.OFF) restoreAllLocked() else syncLocked()
    }

    suspend fun setBlocked(packageName: String, blocked: Boolean): NetworkResult = gate.withLock {
        if (!supported()) return NetworkResult(noAccess = true)
        if (!NetworkCommands.isPackageName(packageName)) return NetworkResult()
        if (isProtected(packageName)) return NetworkResult(skippedProtected = 1)
        prefs.setNetworkBlocked(packageName, blocked)
        if (blocked) blockLocked(listOf(packageName)) else restoreLocked(listOf(packageName))
    }

    /**
     * Stores one row's answer and reconciles the whole list against it.
     *
     * A full sync rather than a single block or restore: changing the answer also changes
     * whether the freeze link applies, and the desired set is the only place that knows both.
     */
    suspend fun setAppState(packageName: String, state: NetworkAppState): NetworkResult = gate.withLock {
        if (!supported()) return NetworkResult(noAccess = true)
        if (!NetworkCommands.isPackageName(packageName)) return NetworkResult()
        if (state == NetworkAppState.PROTECTED) return NetworkResult(skippedProtected = 1)
        if (isProtected(packageName)) return NetworkResult(skippedProtected = 1)
        prefs.setNetworkAppState(packageName, state)
        syncLocked()
    }

    suspend fun sync(): NetworkResult = gate.withLock { syncLocked() }

    /** Lifts every rule this app owns, including the pending ones. */
    suspend fun restoreAll(): NetworkResult = gate.withLock {
        prefs.setNetworkMode(NetworkMode.OFF)
        restoreAllLocked()
    }

    private suspend fun syncLocked(): NetworkResult {
        if (!supported()) return NetworkResult(noAccess = true)
        val policy = prefs.networkPolicyNow()
        if (!policy.mode.enabled) return restoreAllLocked()
        val desired = desiredFor(policy)
        val ours = prefs.networkAppliedNow() + prefs.networkPendingNow() + prefs.networkUnknownNow()
        val toBlock = desired - ours
        val toRestore = ours - desired
        var result = NetworkResult()
        if (toRestore.isNotEmpty()) result = merge(result, restoreLocked(toRestore.toList()))
        if (toBlock.isNotEmpty()) result = merge(result, blockLocked(toBlock.toList()))
        prefs.setNetworkLastSync(System.currentTimeMillis())
        return result
    }

    private suspend fun restoreAllLocked(): NetworkResult {
        if (!supported()) return NetworkResult(noAccess = true)
        val ours = prefs.networkAppliedNow() + prefs.networkPendingNow() + prefs.networkUnknownNow()
        val result = if (ours.isEmpty()) NetworkResult() else restoreLocked(ours.toList())
        if (result.pending == 0 && result.unknown == 0) {
            // Nothing is left for chain3 to guard. Turning it off is what makes a device that
            // no longer uses this feature look untouched.
            shell.runCapture(NetworkCommands.setChain(false), TIMEOUT)
        }
        prefs.setNetworkLastSync(System.currentTimeMillis())
        return result
    }

    private suspend fun blockLocked(packages: List<String>): NetworkResult {
        val valid = packages.filter { NetworkCommands.isPackageName(it) }.distinct()
        if (valid.isEmpty()) return NetworkResult()
        if (!ensureChain()) return NetworkResult(noAccess = true)

        val before = NetworkBuckets(
            prefs.networkAppliedNow(),
            prefs.networkPendingNow(),
            prefs.networkUnknownNow(),
        )
        var buckets = before
        valid.chunked(BATCH).forEach { chunk ->
            val ran = shell.runCapture(NetworkCommands.applyScript(chunk, blocked = true), TIMEOUT)
            val codes = NetworkCommands.parseApply(ran?.second.orEmpty(), chunk.size)
            val read = shell.runCapture(NetworkCommands.readScript(chunk), TIMEOUT)
                ?.second?.let { NetworkCommands.parseRead(it, chunk) }
                .orEmpty()
            buckets = NetworkState.block(buckets, chunk, codes, read)
        }
        prefs.setNetworkState(buckets.applied, buckets.pending, buckets.unknown)
        return NetworkResult(
            applied = (buckets.applied - before.applied).size,
            pending = (buckets.pending - before.pending).size,
            unknown = (buckets.unknown - before.unknown).size,
        )
    }

    private suspend fun restoreLocked(packages: List<String>): NetworkResult {
        val valid = packages.filter { NetworkCommands.isPackageName(it) }.distinct()
        if (valid.isEmpty()) return NetworkResult()
        val before = NetworkBuckets(
            prefs.networkAppliedNow(),
            prefs.networkPendingNow(),
            prefs.networkUnknownNow(),
        )
        var buckets = before
        valid.chunked(BATCH).forEach { chunk ->
            shell.runCapture(NetworkCommands.applyScript(chunk, blocked = false), TIMEOUT)
            val read = shell.runCapture(NetworkCommands.readScript(chunk), TIMEOUT)
                ?.second?.let { NetworkCommands.parseRead(it, chunk) }
                .orEmpty().toMutableMap()
            // An app that has left the phone can never answer the readback, so it would stay
            // pending for good and be retried on every sync. It has nothing left to restore.
            chunk.filterNot(::installed).forEach { read[it] = false }
            buckets = NetworkState.restore(buckets, chunk, read)
        }
        prefs.setNetworkState(buckets.applied, buckets.pending, buckets.unknown)
        val restored = valid.count { it !in buckets.applied && it !in buckets.pending && it !in buckets.unknown }
        return NetworkResult(restored = restored, pending = valid.size - restored)
    }

    /** Whether the package still exists for this user; a removed one cannot be read back. */
    private fun installed(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
    }.isSuccess

    private suspend fun ensureChain(): Boolean {
        val before = shell.runCapture(NetworkCommands.readChain(), TIMEOUT)
            ?.second?.let(NetworkCommands::parseChain)
        if (before == true) return true
        shell.runCapture(NetworkCommands.setChain(true), TIMEOUT)
        val after = shell.runCapture(NetworkCommands.readChain(), TIMEOUT)
            ?.second?.let(NetworkCommands::parseChain)
        return after == true
    }

    private suspend fun desiredFor(policy: NetworkPolicy): Set<String> = policy.desired(
        frozen = prefs.freezeAppliedNow().map(FreezeTargetKey::packageName).toSet(),
        installed = if (policy.mode == NetworkMode.WHITELIST) installedPackages() else emptySet(),
        protected = protectedPackages(),
    )

    private fun isProtected(packageName: String): Boolean =
        NetworkProtection.isProtected(packageName, context.packageName, protectedExtras())

    /** The guard list, resolved on this device: launcher, telephony, home, keyboards. */
    fun protectedPackages(): Set<String> =
        (NetworkProtection.FIXED + protectedExtras() + context.packageName)

    private fun protectedExtras(): Set<String> = buildSet {
        runCatching {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName?.let { add(it) }
        }
        runCatching {
            context.getSystemService(InputMethodManager::class.java)
                ?.inputMethodList?.forEach { add(it.packageName) }
        }
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { add(it) }
        }
        runCatching {
            Telephony.Sms.getDefaultSmsPackage(context)?.let { add(it) }
        }
    }

    private fun installedPackages(): Set<String> =
        context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()

    private fun merge(a: NetworkResult, b: NetworkResult): NetworkResult = NetworkResult(
        applied = a.applied + b.applied,
        pending = a.pending + b.pending,
        unknown = a.unknown + b.unknown,
        restored = a.restored + b.restored,
        skippedProtected = a.skippedProtected + b.skippedProtected,
        noAccess = a.noAccess || b.noAccess,
    )

    private companion object {
        const val BATCH = 64
        const val TIMEOUT = 120_000L
    }
}
