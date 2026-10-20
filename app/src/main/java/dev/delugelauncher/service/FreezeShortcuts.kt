// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import dev.delugelauncher.MainActivity
import dev.delugelauncher.R

/**
 * The two freeze verbs offered when the launcher's own icon is held down.
 *
 * Dynamic rather than a static shortcuts.xml, because turning the feature off has to remove
 * them too: a menu entry that still freezes apps after the user switched freezing off is worse
 * than no entry at all. [sync] is safe to call on every preference change.
 */
object FreezeShortcuts {

    private const val FREEZE_ALL = "freeze_all"
    private const val UNFREEZE_ALL = "unfreeze_all"

    fun sync(context: Context, enabled: Boolean) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        runCatching {
            if (!enabled) {
                manager.removeAllDynamicShortcuts()
                return
            }
            if (manager.dynamicShortcuts.any { it.id == FREEZE_ALL }) return
            manager.dynamicShortcuts = listOf(
                shortcut(
                    context,
                    FREEZE_ALL,
                    R.string.freeze_shortcut_freeze_short,
                    R.string.freeze_shortcut_freeze_long,
                    MainActivity.ACTION_FREEZE_ALL,
                    0,
                ),
                shortcut(
                    context,
                    UNFREEZE_ALL,
                    R.string.freeze_shortcut_unfreeze_short,
                    R.string.freeze_shortcut_unfreeze_long,
                    MainActivity.ACTION_UNFREEZE_ALL,
                    1,
                ),
            )
        }
    }

    private fun shortcut(
        context: Context,
        id: String,
        shortLabel: Int,
        longLabel: Int,
        action: String,
        rank: Int,
    ): ShortcutInfo = ShortcutInfo.Builder(context, id)
        .setShortLabel(context.getString(shortLabel))
        .setLongLabel(context.getString(longLabel))
        .setIcon(Icon.createWithResource(context, R.drawable.ic_freeze_notification))
        .setIntent(
            Intent(context, MainActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        .setRank(rank)
        .build()
}
