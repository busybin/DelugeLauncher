// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.R
import dev.delugelauncher.data.NetworkMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One tap in the shade to turn the firewall on or off.
 *
 * Active means the list is being enforced. The tile is unavailable while the feature has no
 * privileged channel, so it never looks like a button that does nothing.
 */
class NetworkTileService : TileService() {

    override fun onStartListening() {
        val app = application as? DelugeApp ?: return
        app.runInBackground {
            val mode = app.prefs.networkModeNow()
            val usable = app.networkManager.supported() && app.freezeManager.hasQuietAccess()
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    label = getString(R.string.network_title)
                    state = when {
                        !usable -> Tile.STATE_UNAVAILABLE
                        mode.enabled -> Tile.STATE_ACTIVE
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
            val on = app.prefs.networkModeNow().enabled
            val result = app.networkManager.setMode(if (on) NetworkMode.OFF else NetworkMode.BLACKLIST)
            withContext(Dispatchers.Main) {
                if (result.noAccess) {
                    Toast.makeText(
                        this@NetworkTileService,
                        getString(R.string.network_needs_access),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                onStartListening()
            }
        }
    }
}
