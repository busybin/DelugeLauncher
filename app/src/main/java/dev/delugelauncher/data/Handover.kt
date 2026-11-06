// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a handover changed, so leaving it can put the phone back.
 *
 * Handover freezes everything on the freeze list and locks the apps the owner picked for it.
 * The two sets it started from are kept here; leaving restores exactly those, not "unfreeze
 * all", which would also wake apps the owner had asleep before handing the phone over.
 */
data class HandoverState(
    val active: Boolean = false,
    val frozenBefore: Set<String> = emptySet(),
    val lockedBefore: Set<String> = emptySet(),
) {
    companion object {
        fun fromJson(json: String?): HandoverState {
            if (json.isNullOrBlank()) return HandoverState()
            return runCatching {
                val obj = JSONObject(json)
                HandoverState(
                    active = obj.optBoolean("active", false),
                    frozenBefore = keys(obj.optJSONArray("frozen")),
                    lockedBefore = keys(obj.optJSONArray("locked")),
                )
            }.getOrDefault(HandoverState())
        }

        fun toJson(state: HandoverState): String = JSONObject().apply {
            put("active", state.active)
            put("frozen", JSONArray().apply { state.frozenBefore.sorted().forEach { put(it) } })
            put("locked", JSONArray().apply { state.lockedBefore.sorted().forEach { put(it) } })
        }.toString()

        private fun keys(array: JSONArray?): Set<String> {
            if (array == null) return emptySet()
            return (0 until array.length())
                .mapNotNull { array.optString(it).takeIf { key -> key.isNotBlank() } }
                .toSet()
        }
    }
}

/** What leaving a handover should do to each layer, given what it found on the way in. */
data class HandoverExit(val thaw: Set<String>, val keepFrozen: Set<String>, val unlock: Set<String>, val keepLocked: Set<String>)

/**
 * Splits the freeze and lock lists into "restore" and "leave as it is now".
 *
 * Handover freezes the whole list, so frozen apps at entry are the ones that stay asleep on
 * exit; everything else is thawed. Locks are the other way round: the apps locked before stay
 * locked, the rest are released.
 */
fun handoverExit(state: HandoverState, frozenNow: Set<String>, lockedNow: Set<String>): HandoverExit =
    HandoverExit(
        thaw = frozenNow - state.frozenBefore,
        keepFrozen = frozenNow intersect state.frozenBefore,
        unlock = lockedNow - state.lockedBefore,
        keepLocked = lockedNow intersect state.lockedBefore,
    )
