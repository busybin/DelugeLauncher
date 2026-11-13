// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.R
import dev.delugelauncher.data.PrivateSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Locks or opens the private space from the shade.
 *
 * Active means the space is open and a tap locks it; the system still asks for the device
 * credential when opening one, which is the point of the space and not something to bypass.
 */
class PrivateSpaceTileService : TileService() {

    override fun onStartListening() {
        val app = application as? DelugeApp ?: return
        app.runInBackground {
            val space = runCatching { app.appRepository.privateSpace() }.getOrNull()
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    label = getString(R.string.private_space_tile_label)
                    state = when (space) {
                        null -> Tile.STATE_UNAVAILABLE
                        is PrivateSpace.Unlocked -> Tile.STATE_ACTIVE
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
            runCatching { app.appRepository.togglePrivateSpace() }
            withContext(Dispatchers.Main) { onStartListening() }
        }
    }
}
