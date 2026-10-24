// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dev.delugelauncher.data.FreezeOutcome
import dev.delugelauncher.data.FreezeTargetKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The transparent target behind a pinned freeze shortcut.
 *
 * It has no UI and finishes at once; the work continues in the application's own scope, so a
 * freezer that takes a second is not a window the user has to dismiss.
 */
class FreezeToggleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent?.getStringExtra(EXTRA_KEY)
        val app = application as? DelugeApp
        finish()
        if (key == null || app == null) return
        app.runInBackground {
            // A shortcut can outlive its list entry; nothing to toggle then.
            if (key !in app.prefs.freezeMethodsNow()) return@runInBackground
            val label = runCatching {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(FreezeTargetKey.packageName(key), 0)
                ).toString()
            }.getOrNull() ?: FreezeTargetKey.packageName(key)
            val wasFrozen = key in app.prefs.freezeAppliedNow()
            val outcome = if (wasFrozen) {
                app.freezeManager.unfreezeKey(key)
            } else {
                app.freezeManager.freezeKey(key)
            }
            val message = when (outcome) {
                FreezeOutcome.DONE, FreezeOutcome.DISABLED -> null
                FreezeOutcome.NO_ACCESS -> getString(R.string.freeze_needs_access)
                FreezeOutcome.PROTECTED -> getString(R.string.freeze_protected, label)
                else -> getString(
                    if (wasFrozen) R.string.freeze_unfreeze_failed else R.string.freeze_failed,
                    label,
                )
            }
            if (message != null) withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_KEY = "dev.delugelauncher.extra.FREEZE_KEY"
        const val ACTION_TOGGLE = "dev.delugelauncher.action.TOGGLE_FREEZE"
    }
}
