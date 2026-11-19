// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One tap in the shade to freeze the whole list, or to lift it.
 *
 * Active means something on the list is held right now; the tap then lifts everything, which
 * is the same pair of verbs the corner button and the launcher's own shortcuts offer. The tile
 * is unavailable while freezing is switched off or the list is empty, so it never looks like a
 * button that does nothing.
 */
class FreezeTileService : TileService() {

    override fun onStartListening() {
        val app = application as? DelugeApp ?: return
        app.runInBackground {
            val enabled = app.prefs.freezeEnabledNow()
            val hasPlan = app.prefs.freezeMethodsNow().isNotEmpty()
            // Read the device, not the record: an app can be gone or lifted since the last run.
            val held = app.freezeManager.heldNowCount()
            // Without a channel the tile would freeze nothing; say so instead of looking live.
            val canAct = app.freezeManager.hasQuietAccess()
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    label = getString(R.string.freeze_tile_label)
                    state = when {
                        !enabled || !hasPlan || !canAct -> Tile.STATE_UNAVAILABLE
                        held > 0 -> Tile.STATE_ACTIVE
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
            if (!app.prefs.freezeEnabledNow()) return@runInBackground
            val result = if (app.freezeManager.heldNowCount() > 0) {
                app.freezeManager.unfreezeAll()
            } else {
                app.freezeManager.freezeAll(skipBrick = true)
            }
            withContext(Dispatchers.Main) {
                if (result.noAccess) {
                    android.widget.Toast.makeText(
                        this@FreezeTileService,
                        getString(R.string.freeze_needs_access),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
                onStartListening()
            }
        }
    }
}
