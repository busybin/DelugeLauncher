// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.media

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One button a notification offers.
 *
 * [replyKey] is set when the button carries a free-form text field as well as an intent: the
 * panel then shows the field instead of treating the button as a plain tap.
 */
data class NotificationAction(
    val title: String,
    val pendingIntent: PendingIntent?,
    val inputs: Array<RemoteInput> = emptyArray(),
    val replyKey: String? = null,
)

/**
 * One notification as the panel shows it.
 *
 * Held in memory only, and only while the panel setting is on: a launcher that never reads
 * notifications must never have their words in memory either.
 */
data class NotificationEntry(
    /** The listener's own identity for this notification, used to cancel it. */
    val notificationKey: String,
    val packageName: String,
    val userSerial: Long,
    val title: String,
    val text: String,
    val postTime: Long,
    val canDismiss: Boolean,
    val actions: List<NotificationAction> = emptyList(),
    val contentIntent: PendingIntent? = null,
) {
    /** The same profile split the rest of the launcher uses, so two profiles never collide. */
    val appKey: String get() = "$packageName|u$userSerial"

    /** One line for a row, and nothing at all when there is nothing worth reading. */
    val line: String
        get() = when {
            title.isBlank() -> text
            text.isBlank() || text == title -> title
            else -> "$title · $text"
        }
}

/**
 * What is on the shade right now, newest first.
 *
 * Written by the notification listener, read by the panel. A plain StateFlow rather than a
 * database: this is a view of a system service, and it should disappear with that service
 * instead of outliving it.
 */
object NotificationPanelBus {
    private val _entries = MutableStateFlow<List<NotificationEntry>>(emptyList())
    val entries: StateFlow<List<NotificationEntry>> = _entries

    fun update(entries: List<NotificationEntry>) {
        if (entries != _entries.value) _entries.value = entries
    }

    fun clear() {
        if (_entries.value.isNotEmpty()) _entries.value = emptyList()
    }
}

/**
 * The two things the panel asks the system to do that only a bound listener can.
 *
 * Kept here rather than in the screen so the screen never holds the service itself: the
 * binding can drop at any moment, and a screen with a dead reference would keep offering
 * buttons that do nothing.
 */
object NotificationGateway {
    @Volatile
    private var listener: NotificationListenerService? = null

    fun attach(service: NotificationListenerService) {
        listener = service
    }

    fun detach(service: NotificationListenerService) {
        if (listener === service) listener = null
    }

    /** Whether the system is bound right now, so the panel can say why it is empty. */
    val connected: Boolean get() = listener != null

    fun dismiss(notificationKey: String) {
        runCatching { listener?.cancelNotification(notificationKey) }
    }

    fun dismissAll() {
        runCatching { listener?.cancelAllNotifications() }
    }

    /**
     * Runs a notification's own pending intent, optionally with a reply attached.
     *
     * The intent is the app's own, posted through the system; adding results to a copy is how
     * an inline reply is supposed to arrive, and is also why the panel never builds one.
     */
    fun send(
        context: Context,
        action: NotificationAction,
        reply: String? = null,
    ): Boolean {
        val pending = action.pendingIntent ?: return false
        val intent = android.content.Intent()
        if (action.replyKey != null && reply != null) {
            val results = Bundle().apply { putCharSequence(action.replyKey, reply) }
            if (action.inputs.isNotEmpty()) {
                RemoteInput.addResultsToIntent(action.inputs, intent, results)
            }
        }
        return runCatching {
            pending.send(context, 0, intent)
            true
        }.getOrDefault(false)
    }

    fun open(context: Context, entry: NotificationEntry): Boolean {
        val pending = entry.contentIntent ?: return false
        return runCatching {
            pending.send(context, 0, android.content.Intent())
            true
        }.getOrDefault(false)
    }
}
