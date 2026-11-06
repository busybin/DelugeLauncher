// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.json.JSONObject

/**
 * The one app the launcher is temporarily offering.
 *
 * This is a launcher-level session, not a device policy. It never freezes or hides anything:
 * the other rows simply are not shown, and returning home lands on the focus screen. That is
 * enough for handing a phone to a friend for one app, and it cannot disturb a freeze that was
 * set up separately.
 */
data class FocusSession(
    val active: Boolean = false,
    /** The app's launcher key, so the same app keeps its identity across a rebuild. */
    val targetKey: String = "",
    val packageName: String = "",
    val label: String = "",
)

internal fun focusSessionToJson(session: FocusSession): String = JSONObject().apply {
    put("active", session.active)
    put("key", session.targetKey)
    put("package", session.packageName)
    put("label", session.label)
}.toString()

internal fun focusSessionFromJson(json: String?): FocusSession {
    if (json.isNullOrBlank()) return FocusSession()
    return runCatching {
        val obj = JSONObject(json)
        FocusSession(
            active = obj.optBoolean("active", false),
            targetKey = obj.optString("key").take(512),
            packageName = obj.optString("package").take(256),
            label = obj.optString("label").take(256),
        )
    }.getOrDefault(FocusSession())
}
