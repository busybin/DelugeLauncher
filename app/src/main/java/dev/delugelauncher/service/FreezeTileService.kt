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
            val frozen = app.prefs.freezeAppliedNow().isNotEmpty()
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    label = getString(R.string.freeze_tile_label)
                    state = when {
                        !enabled || !hasPlan -> Tile.STATE_UNAVAILABLE
                        frozen -> Tile.STATE_ACTIVE
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
            val result = if (app.prefs.freezeAppliedNow().isNotEmpty()) {
                app.freezeManager.unfreezeAll()
            } else {
                app.freezeManager.freezeAll()
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
