// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The last thing a sleep rule did, so the freeze screen can say what happened and undo it.
 *
 * A rule that quietly freezes twelve apps overnight is the kind of automation that has to be
 * explainable the next morning. The log is one run, not a history: the interesting question
 * is "what did it just do", and an undo only makes sense for the latest one.
 */
data class FreezeRunLog(
    val atMillis: Long = 0L,
    /** What asked for the run: a rule change, the network, the clock, the screen. */
    val source: String = "",
    val frozen: Set<String> = emptySet(),
    val thawed: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = frozen.isEmpty() && thawed.isEmpty()

    companion object {
        fun fromJson(json: String?): FreezeRunLog? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val obj = JSONObject(json)
                val log = FreezeRunLog(
                    atMillis = obj.optLong("at", 0L),
                    source = obj.optString("source").take(40),
                    frozen = keys(obj.optJSONArray("frozen")),
                    thawed = keys(obj.optJSONArray("thawed")),
                )
                log.takeUnless { it.isEmpty }
            }.getOrNull()
        }

        fun toJson(log: FreezeRunLog): String = JSONObject().apply {
            put("at", log.atMillis)
            put("source", log.source)
            put("frozen", JSONArray().apply { log.frozen.sorted().forEach { put(it) } })
            put("thawed", JSONArray().apply { log.thawed.sorted().forEach { put(it) } })
        }.toString()

        private fun keys(array: JSONArray?): Set<String> {
            if (array == null) return emptySet()
            return (0 until array.length())
                .mapNotNull { array.optString(it).takeIf { key -> key.isNotBlank() } }
                .toSet()
        }
    }
}
