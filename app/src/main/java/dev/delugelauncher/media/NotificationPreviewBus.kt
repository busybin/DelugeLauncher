// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The newest notification of each app, for the one line shown under its name.
 *
 * Text is held only while the setting that displays it is on: the listener publishes nothing
 * when it is off, so a count-only launcher never has a notification's words in memory. Nothing
 * is written to disk.
 */
data class NotificationPreview(
    val packageName: String,
    val userSerial: Long,
    val title: String,
    val text: String,
    val postTime: Long,
) {
    /** One line, and nothing at all when there is nothing worth reading. */
    val line: String
        get() = when {
            title.isBlank() -> text
            text.isBlank() || text == title -> title
            else -> "$title · $text"
        }
}

object NotificationPreviewBus {
    private val _previews = MutableStateFlow<Map<String, NotificationPreview>>(emptyMap())
    val previews: StateFlow<Map<String, NotificationPreview>> = _previews

    /** The same profile split the rest of the launcher uses, so two profiles never collide. */
    fun key(packageName: String, userSerial: Long): String = "$packageName|u$userSerial"

    /** Keeps the newest of each app; the caller has already dropped summaries and ongoing ones. */
    fun update(entries: List<NotificationPreview>) {
        val newest = entries
            .groupBy { key(it.packageName, it.userSerial) }
            .mapValues { (_, list) -> list.maxBy { it.postTime } }
        if (newest != _previews.value) _previews.value = newest
    }

    fun clear() {
        if (_previews.value.isNotEmpty()) _previews.value = emptyMap()
    }
}
