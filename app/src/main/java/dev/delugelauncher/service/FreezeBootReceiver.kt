// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.delugelauncher.data.Prefs

/**
 * Puts the screen-off watcher back after a reboot.
 *
 * The watcher is a foreground service, and a boot broadcast is one of the few moments the
 * system lets an app start one from the background. Without this, automatic freezing silently
 * stayed off until the launcher was opened once.
 */
class FreezeBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    val prefs = Prefs(appContext)
                    (appContext as? dev.delugelauncher.DelugeApp)?.appRepository
                        ?.rehideLockedApps()
                    if (prefs.freezeEnabledNow() && prefs.freezeAutoEnabledNow()) {
                        FreezeAutoService.sync(appContext, true)
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
