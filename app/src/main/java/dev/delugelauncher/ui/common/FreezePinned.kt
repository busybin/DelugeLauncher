// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.FreezeToggleActivity
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.service.FreezeIntents

/**
 * A home-screen shortcut that freezes or unfreezes one listed app.
 *
 * Pinned rather than a dynamic shortcut: the system only keeps a handful of dynamic ones, and
 * a user may want ten of these. The intent is a transparent activity of ours, so the launcher
 * starts it the same way it starts any publisher's shortcut.
 */
object FreezePinned {

    suspend fun request(context: Context, app: AppInfo, label: String): Boolean = runCatching {
        // The target is exported, so the shortcut carries the same secret as the broadcast
        // door: a launcher other than this one starts it under its own package.
        val token = (context.applicationContext as? DelugeApp)?.prefs?.ensureExternalControlToken()
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId(app.freezeKey))
            .setShortLabel(label.take(MAX_LABEL))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_freeze_notification))
            .setIntent(
                Intent(context, FreezeToggleActivity::class.java)
                    .setAction(FreezeToggleActivity.ACTION_TOGGLE)
                    .putExtra(FreezeToggleActivity.EXTRA_KEY, app.freezeKey)
                    .putExtra(FreezeIntents.EXTRA_TOKEN, token)
            )
            .build()
        // Null callback: the system shows its own confirmation and there is nothing to undo.
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }.getOrDefault(false)

    /** Stable per key, and safe as a shortcut id. */
    private fun shortcutId(key: String): String =
        "freeze-" + Integer.toHexString(key.hashCode())

    private const val MAX_LABEL = 40
}
