// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.R
import dev.delugelauncher.data.HandoverManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hands the phone over: one tap freezes the list, the next thaws only what this added.
 *
 * Unavailable while the freeze list is empty or freezing is off, so the tile never claims to
 * have put anything to sleep when there was nothing to put there.
 */
class HandoverTileService : TileService() {

    private fun manager(app: DelugeApp) = HandoverManager(app.prefs, app.freezeManager)

    override fun onStartListening() {
        val app = application as? DelugeApp ?: return
        app.runInBackground {
            val active = manager(app).isActive()
            val usable = app.prefs.freezeEnabledNow() && app.prefs.freezeMethodsNow().isNotEmpty()
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    label = getString(R.string.handover_tile_label)
                    state = when {
                        !usable -> Tile.STATE_UNAVAILABLE
                        active -> Tile.STATE_ACTIVE
                        else -> Tile.STATE_INACTIVE
                    }
                    updateTile()
                }
            }
        }
    }

    override fun onClick() {
        val app = application as? DelugeApp ?: return
        app.runInBackground {
            val handover = manager(app)
            val count = if (handover.isActive()) handover.exit() else handover.enter()
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    this@HandoverTileService,
                    if (handover.isActive()) {
                        getString(R.string.handover_enter_done, count)
                    } else {
                        getString(R.string.handover_exit_done, count)
                    },
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                onStartListening()
            }
        }
    }
}
