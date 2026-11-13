// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One thing deep freeze can take away from a suspended app.
 *
 * Every lever here is an app-op and needs only a shell (Shizuku or root). Network is not one
 * of them: the per-app firewall owns that rule, and two writers to one platform state only
 * produced undo orders nobody could reason about.
 */
enum class DeepFreezeLever(val op: String?, val minSdk: Int) {
    NOTIFICATIONS("POST_NOTIFICATION", 33),
    WAKE_LOCK("WAKE_LOCK", 23),
    FOREGROUND("START_FOREGROUND", 28),
}

/** What deep freeze did to one row, enough to undo it exactly. */
data class DeepFreezeRecord(
    val packageName: String,
    val userId: Int,
    /** App-op to the mode it had before the first time this row was hardened. */
    val ops: Map<String, String>,
)

data class DeepFreezeResult(val done: Int = 0, val failed: Int = 0, val noAccess: Boolean = false)

/** The commands, kept pure so the ordering and the escaping can be tested on the JVM. */
object DeepFreezeCommands {

    fun levers(api: Int): List<DeepFreezeLever> = DeepFreezeLever.entries.filter { api >= it.minSdk }

    fun hardenScript(
        targets: Collection<FreezeTarget>,
        levers: List<DeepFreezeLever>,
        /** Per-row choice: a lever already on record has nothing left to apply. */
        apply: (FreezeTarget, DeepFreezeLever) -> Boolean = { _, _ -> true },
    ): String {
        val valid = targets.filter { FreezeCommands.isPackageName(it.packageName) }
        if (valid.isEmpty()) return ""
        return buildString {
            valid.forEach { target ->
                levers.forEach { lever ->
                    val op = lever.op ?: return@forEach
                    if (apply(target, lever)) {
                        appendLine(
                            "cmd appops set --user ${target.userId} " +
                                "${target.packageName} $op ignore"
                        )
                    }
                }
            }
        }
    }

    fun captureScript(probes: List<Probe>): String = buildString {
        probes.forEachIndexed { index, probe ->
            appendLine("echo __P${index}__")
            appendLine(
                "cmd appops get --user ${probe.userId} ${probe.packageName} ${probe.op}"
            )
        }
    }

    /** Reads the modes back from a [captureScript] run. Missing entries mean the default mode. */
    fun parseCaptures(output: String, probes: List<Probe>): Map<Pair<String, String>, String> {
        val found = mutableMapOf<Pair<String, String>, String>()
        val pattern = Regex("__P(\\d+)__([\\s\\S]*?)(?=__P\\d+__|$)")
        pattern.findAll(output).forEach { match ->
            val index = match.groupValues[1].toIntOrNull() ?: return@forEach
            val probe = probes.getOrNull(index) ?: return@forEach
            val line = match.groupValues[2].lineSequence()
                .firstOrNull { it.trimStart().startsWith("${probe.op}:") }
            val mode = line?.substringAfter(':')?.substringBefore(';')?.trim()
            found[probe.key to probe.op] = mode?.takeIf { it.isNotEmpty() } ?: "default"
        }
        return found
    }

    fun releaseScript(records: Map<String, DeepFreezeRecord>): String = buildString {
        records.values.forEachIndexed { index, record ->
            if (!FreezeCommands.isPackageName(record.packageName)) return@forEachIndexed
            // The marker is what turns one failed command into one failed row instead of a
            // failed run: every other row in the same chunk still gets its undo.
            appendLine("echo $RECORD_MARK$index")
            record.ops.forEach { (op, mode) ->
                appendLine(
                    "cmd appops set --user ${record.userId} " +
                        "${record.packageName} $op $mode || echo $FAIL_MARK"
                )
            }
        }
    }

    /** The rows whose undo failed, by the order [releaseScript] wrote their marker. */
    fun releaseFailures(output: String, size: Int): Set<Int> = buildSet {
        var current = -1
        output.lineSequence().forEach { line ->
            val text = line.trim()
            if (text.startsWith(RECORD_MARK)) {
                current = text.removePrefix(RECORD_MARK).toIntOrNull() ?: -1
            } else if (current in 0 until size && text.contains(FAIL_MARK)) {
                add(current)
            }
        }
    }

    /**
     * Printed by [releaseScript] when any undo command failed. A failed undo keeps the whole
     * record so the next run retries it instead of quietly calling the app restored.
     */
    const val FAIL_MARK = "__DF_RELEASE_FAIL__"

    /** Starts one row's block, so a [FAIL_MARK] line can be attributed to that row. */
    const val RECORD_MARK = "__DF_ROW__"

    data class Probe(
        val key: String,
        val packageName: String,
        val userId: Int,
        val op: String,
    )
}

/** Reads and writes the per-row undo record without touching the disk itself. */
object DeepFreezeJournal {

    fun decode(text: String): MutableMap<String, DeepFreezeRecord> {
        if (text.isBlank()) return mutableMapOf()
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return mutableMapOf()
        return buildMap {
            root.keys().forEach { key ->
                val item = root.optJSONObject(key) ?: return@forEach
                val opsJson = item.optJSONObject("ops")
                val ops = buildMap {
                    opsJson?.keys()?.forEach { op ->
                        val mode = opsJson.optString(op, "default")
                        if (mode.isNotBlank()) put(op, mode)
                    }
                }
                val packageName = item.optString("pkg")
                if (!FreezeCommands.isPackageName(packageName)) return@forEach
                put(
                    key,
                    DeepFreezeRecord(
                        packageName = packageName,
                        userId = item.optInt("user", 0),
                        ops = ops,
                    ),
                )
            }
        }.toMutableMap()
    }

    fun encode(journal: Map<String, DeepFreezeRecord>): String {
        if (journal.isEmpty()) return ""
        val root = JSONObject()
        journal.forEach { (key, record) ->
            val item = JSONObject()
            item.put("pkg", record.packageName)
            item.put("user", record.userId)
            val ops = JSONObject()
            record.ops.forEach { (op, mode) -> ops.put(op, mode) }
            item.put("ops", ops)
            root.put(key, item)
        }
        return root.toString()
    }

    /**
     * The next journal after hardening [targets].
     *
     * A mode already on record is kept: after the first run the app-op reads "ignore", which
     * is our own value, and recording it as the prior would make the undo permanent.
     */
    fun merge(
        journal: Map<String, DeepFreezeRecord>,
        targets: Map<String, FreezeTarget>,
        levers: List<DeepFreezeLever>,
        priors: Map<Pair<String, String>, String>,
        /** Only levers actually sent for this row are recorded; a skipped one stays unknown. */
        apply: (String, DeepFreezeLever) -> Boolean = { _, _ -> true },
    ): MutableMap<String, DeepFreezeRecord> {
        val next = journal.toMutableMap()
        targets.forEach { (key, target) ->
            val old = next[key]
            val ops = old?.ops.orEmpty().toMutableMap()
            levers.forEach { lever ->
                if (!apply(key, lever)) return@forEach
                val op = lever.op ?: return@forEach
                ops[op] = ops[op] ?: priors[key to op] ?: "default"
            }
            next[key] = DeepFreezeRecord(
                packageName = target.packageName,
                userId = target.userId,
                ops = ops,
            )
        }
        return next
    }
}

/**
 * The hardening layer that rides along with a suspend.
 *
 * It only ever touches rows held by a suspend. Stop is a one-shot kill and the people who
 * pick it want the app back on a tap; disable and hide already stop every component. Suspend
 * is the one method that leaves a running process behind, so the app-ops go there and
 * nowhere else.
 *
 * It is not stored in the freeze record: the platform state is what matters, and this class
 * only remembers what it has to put back. [harden] is idempotent; [release] is best effort and
 * keeps whatever it could not undo for the next attempt.
 */
class DeepFreeze(
    private val prefs: Prefs,
    private val shell: FreezeShell,
    private val targetOf: (String) -> FreezeTarget?,
    private val api: Int = Build.VERSION.SDK_INT,
) {

    fun available(): Boolean = api >= 26 && shell.quietAccess()

    private fun levers(): List<DeepFreezeLever> = DeepFreezeCommands.levers(api)

    /**
     * Adds every supported lever to [targets]; returns the number of rows written.
     *
     * `RUN_IN_BACKGROUND` is not in the list: a suspend already restricts background work,
     * and capturing that mode would record our own `ignore` as the user's prior, then put it
     * back after a thaw and leave a released app still background-blocked.
     */
    suspend fun harden(
        targets: Map<String, FreezeTarget>,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): DeepFreezeResult = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) return@withContext DeepFreezeResult()
        val active = levers()
        if (active.isEmpty()) return@withContext DeepFreezeResult(noAccess = true)
        var journal = readJournal()
        val total = targets.size
        var processed = 0
        for (chunk in targets.entries.chunked(CHUNK)) {
            val part = chunk.associate { it.key to it.value }
            // App-ops already on record are still in force: running them again was the bulk
            // of a second Freeze all.
            val plan = part.mapValues { (key, _) ->
                active.filter { lever -> journal[key]?.ops?.containsKey(lever.op!!) != true }
            }
            val probes = buildList {
                part.forEach { (key, target) ->
                    plan[key].orEmpty().forEach { lever ->
                        val op = lever.op ?: return@forEach
                        add(DeepFreezeCommands.Probe(key, target.packageName, target.userId, op))
                    }
                }
            }
            val priors = if (probes.isEmpty()) {
                emptyMap()
            } else {
                val output = shell.runCapture(DeepFreezeCommands.captureScript(probes))?.second
                    ?: return@withContext DeepFreezeResult(
                        done = processed,
                        failed = total - processed,
                        noAccess = true,
                    )
                DeepFreezeCommands.parseCaptures(output, probes)
            }
            val script = DeepFreezeCommands.hardenScript(part.values, active) { target, lever ->
                lever in plan[target.key].orEmpty()
            }
            if (script.isNotBlank()) {
                if (shell.runCapture(script) == null) {
                    return@withContext DeepFreezeResult(
                        done = processed,
                        failed = total - processed,
                        noAccess = true,
                    )
                }
                journal = DeepFreezeJournal.merge(journal, part, active, priors) { key, lever ->
                    lever in plan[key].orEmpty()
                }
                // Written per chunk: a crash after the commands must not leave restrictions
                // with no journal to undo them.
                writeJournal(journal)
            }
            processed += part.size
            onProgress?.invoke(processed, total)
        }
        DeepFreezeResult(done = total)
    }

    /** Puts back the app-ops for [keys]; empty means all rows. */
    suspend fun release(
        keys: Set<String> = emptySet(),
        onProgress: ((Int, Int) -> Unit)? = null,
    ): DeepFreezeResult = withContext(Dispatchers.IO) {
        val journal = readJournal()
        val chosen = if (keys.isEmpty()) {
            journal
        } else {
            // A journal lost to a wipe must not leave an app with our restrictions still on.
            // Rows with no record are released to the platform default, which is where an
            // untouched app sits; a real prior mode recorded earlier still wins.
            keys.associateWith { key ->
                journal[key] ?: targetOf(key)?.let { target ->
                    DeepFreezeRecord(
                        packageName = target.packageName,
                        userId = target.userId,
                        ops = levers().mapNotNull { it.op }.associateWith { "default" },
                    )
                }
            }.filterValues { it != null }.mapValues { it.value!! }
        }
        if (chosen.isEmpty()) return@withContext DeepFreezeResult()
        val total = chosen.size
        var processed = 0
        var doneCount = 0
        var failedCount = 0
        val next = journal.toMutableMap()
        for (chunk in chosen.entries.chunked(CHUNK)) {
            val part = chunk.associate { it.key to it.value }
            val script = DeepFreezeCommands.releaseScript(part)
            val failed = if (script.isBlank()) {
                emptySet()
            } else {
                val answer = shell.runCapture(script)
                    ?: return@withContext DeepFreezeResult(
                        done = doneCount,
                        failed = failedCount + (total - processed),
                        noAccess = true,
                    )
                DeepFreezeCommands.releaseFailures(answer.second, chunk.size)
            }
            chunk.forEachIndexed { index, (key, record) ->
                if (index in failed) {
                    // Only this row keeps its record: one uninstalled package used to abort
                    // the whole run, so every Unfreeze all retried the same wall.
                    failedCount++
                    return@forEachIndexed
                }
                doneCount++
                next.remove(key)
            }
            writeJournal(next)
            processed += chunk.size
            onProgress?.invoke(processed, total)
        }
        DeepFreezeResult(done = doneCount, failed = failedCount)
    }

    /**
     * Makes the journal match [applied].
     *
     * Only rows held by a suspend are hardened; everything else in the journal goes back.
     * A missing channel leaves the journal alone for the next attempt.
     */
    suspend fun sync(
        applied: Set<String>,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): DeepFreezeResult {
        val journal = readJournal()
        val methods = prefs.freezeMethodsNow()
        val suspended = applied.filter { methods[it] == FreezeMethod.SUSPEND }.toSet()
        val desired = suspended
        val extra = journal.keys - desired
        var done = 0
        var failed = 0
        if (extra.isNotEmpty()) {
            val result = release(extra)
            done += result.done
            if (result.noAccess) failed += extra.size
        }
        if (desired.isNotEmpty()) {
            val targets = desired.mapNotNull { key -> targetOf(key)?.let { key to it } }.toMap()
            val result = harden(targets, onProgress = onProgress)
            done += result.done
            if (result.noAccess) failed += targets.size
        }
        return DeepFreezeResult(done = done, failed = failed)
    }

    suspend fun journalSize(): Int = readJournal().size

    private suspend fun readJournal(): MutableMap<String, DeepFreezeRecord> =
        DeepFreezeJournal.decode(prefs.deepFreezeJournalNow())

    private suspend fun writeJournal(journal: Map<String, DeepFreezeRecord>) {
        prefs.setDeepFreezeJournal(DeepFreezeJournal.encode(journal))
    }

    private companion object {
        /** Rows per shell round trip: large enough that a whole list is a few calls, not forty. */
        const val CHUNK = 64
    }
}
