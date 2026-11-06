// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

/** The largest per-app daily limit the editor will store. */
const val MAX_DAILY_OPEN_LIMIT = 999

/**
 * Whether an app has used up the opens its owner allows for today.
 *
 * Zero is "no limit", not "never open": a key that has never been given a limit must keep
 * behaving like every other app. The check runs before a launch, so a limit of 3 allows
 * exactly three.
 */
fun openLimitReached(limit: Int, opensToday: Int): Boolean = limit > 0 && opensToday >= limit

/** A stored limit, kept inside what the editor can draw. */
fun sanitizeOpenLimit(limit: Int): Int = limit.coerceIn(0, MAX_DAILY_OPEN_LIMIT)

/** The apps a limit froze today, and the local day that belongs to. */
data class LimitFrozen(val day: String, val keys: Set<String> = emptySet()) {
    /** The keys to wake now: the day rolling over is what lifts a limit. */
    fun dueOn(today: String): Set<String> = if (day == today) emptySet() else keys
}

