// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The newest notifications of each app, for the lines shown under its name.
 *
 * Text is held only while the setting that displays it is on: the listener publishes nothing
 * when it is off, so a count-only launcher never has a notification's words in memory. Nothing
 * is written to disk.
 */
object NotificationPreviewBus {
    /** How many of an app's notifications the home screen lists under it, newest first. */
    const val MAX_LINES = 3

    private val _previews = MutableStateFlow<Map<String, List<NotificationEntry>>>(emptyMap())
    val previews: StateFlow<Map<String, List<NotificationEntry>>> = _previews

    /** The same profile split the rest of the launcher uses, so two profiles never collide. */
    fun key(packageName: String, userSerial: Long): String = "$packageName|u$userSerial"

    /**
     * Keeps the newest few of each app; the caller has already dropped summaries. Ongoing ones
     * stay a panel-only thing: a row's lines are about something waiting to be read.
     */
    fun update(entries: List<NotificationEntry>) {
        val lines = entries
            .filter { it.canDismiss }
            .sortedByDescending { it.postTime }
            .groupBy { it.appKey }
            .mapValues { (_, list) -> list.take(MAX_LINES) }
        if (lines != _previews.value) _previews.value = lines
    }

    fun clear() {
        if (_previews.value.isNotEmpty()) _previews.value = emptyMap()
    }
}
