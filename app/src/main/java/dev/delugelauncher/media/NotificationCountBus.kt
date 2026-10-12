// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * How many notifications each package is currently showing.
 *
 * Bridges the NotificationListenerService to the UI for the same reason [NowPlayingBus] does:
 * the system owns the service and hands the app no handle to it.
 *
 * A count and nothing else. The text of a notification is the notification's business, and a
 * launcher that copied it onto the home screen would be putting whatever arrived in front of
 * whoever happened to be looking at the phone.
 */
object NotificationCountBus {
    // The last published counts, persisted: the bus is memory-only, so a widget drawn
    // before the listener has published in this process would otherwise read an empty
    // map and hide the row. Package names never contain U+0001, so the join is safe.
    const val PERSIST_FILE = "deluge_notification_counts"
    const val PERSIST_KEY = "counts"

    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts

    /** Whether anything has been published in this process. See [update]. */
    @Volatile
    var published = false
        private set

    fun update(value: Map<String, Int>) {
        _counts.value = value
        published = true
    }

    /** One package's count: collecting this recomposes only when its own number moves. */
    fun countFor(packageName: String): kotlinx.coroutines.flow.Flow<Int> =
        _counts.map { it[packageName] ?: 0 }.distinctUntilChanged()

    /** Nothing is known once the service goes, and a stale badge is worse than no badge. */
    fun clear() {
        _counts.value = emptyMap()
        published = true
    }
}
