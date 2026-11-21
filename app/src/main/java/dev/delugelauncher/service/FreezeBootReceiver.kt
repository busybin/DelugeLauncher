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
                    // chain3 keeps nothing across a reboot: put the firewall's rules back
                    // before the user sees an app that was meant to be offline. Nothing to do
                    // when the feature was never on.
                    val app = appContext as? dev.delugelauncher.DelugeApp
                    // The record can name a pause the system lifted while the phone was off —
                    // the "unpause and open" dialog is not this app's call. Correct it first,
                    // or the sync below would cut off an app that is already open.
                    runCatching { app?.freezeManager?.reconcileApplied() }
                    val network = app?.networkManager
                    if (network != null &&
                        (prefs.networkModeNow().enabled || prefs.networkAppliedNow().isNotEmpty())
                    ) {
                        // sync re-reads every row it records as blocked and applies the ones
                        // the platform no longer denies; that is what replays a reboot.
                        network.sync()
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
