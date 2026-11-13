// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

/**
 * Apps that have not been opened for a while, for the freeze screen's "unused" filter.
 *
 * The launcher is the only thing that can answer this without usage access: it already sees
 * every launch it performs, so [Prefs] records when each key was last opened next to the
 * count it already keeps. Only keys the user already tracks are reported — an app invented
 * from a timestamp would be a row with nothing behind it.
 */
object Hibernation {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val DEFAULT_DAYS = 90

    fun staleKeys(
        lastLaunch: Map<String, Long>,
        tracked: Set<String>,
        nowMillis: Long,
        days: Int,
        /** Install time per key, used when the launch log has already evicted the key. */
        installedAt: Map<String, Long> = emptyMap(),
    ): Set<String> {
        if (days <= 0) return emptySet()
        val cutoff = nowMillis - days * DAY_MS
        return tracked.filterTo(mutableSetOf()) { key ->
            // The launch log keeps only the 500 most recent keys, so a long-unused app was
            // dropped from it and then never reported as unused — the exact app the screen
            // exists to find. Fall back to when it was installed.
            val at = lastLaunch[key] ?: installedAt[key] ?: return@filterTo false
            at > 0 && at <= cutoff
        }
    }
}
