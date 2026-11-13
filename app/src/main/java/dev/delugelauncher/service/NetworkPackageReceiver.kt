// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.data.NetworkMode
import dev.delugelauncher.data.Prefs

/**
 * Keeps the firewall's list honest across installs.
 *
 * A package that arrives while "block new apps" is on joins the list, and one that leaves it
 * is dropped so a reinstall does not come back already cut off by a stale row. The rules
 * themselves are written by the manager, never here.
 */
class NetworkPackageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return
        val appContext = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    val prefs = Prefs(appContext)
                    val mode = prefs.networkModeNow()
                    val manager = (appContext as? DelugeApp)?.networkManager
                    when (intent.action) {
                        Intent.ACTION_PACKAGE_ADDED -> {
                            if (manager == null || !mode.enabled) return@runBlocking
                            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                            if (replacing) return@runBlocking
                            if (prefs.networkBlockNewAppsNow() && mode == NetworkMode.BLACKLIST) {
                                manager.setBlocked(packageName, true)
                            }
                        }
                        Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                            prefs.setNetworkBlocked(packageName, false)
                            manager?.sync()
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
