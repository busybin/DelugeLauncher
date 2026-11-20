// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dev.delugelauncher.data.FreezeOutcome
import dev.delugelauncher.service.FreezeIntents
import dev.delugelauncher.data.FreezeProtection
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
        val token = intent?.getStringExtra(FreezeIntents.EXTRA_TOKEN)
        val app = application as? DelugeApp
        finish()
        if (key == null || app == null) return
        app.runInBackground {
            // Exported so the hosting launcher can start it, which also means any app can: a
            // stranger unfreezing the list would defeat the feature. The shortcut carries the
            // automation token, and nothing else is accepted.
            if (!FreezeIntents.matches(app.prefs.externalControlTokenNow(), token)) {
                return@runInBackground
            }
            // Two shortcuts launched in the same second used to run side by side, both
            // reading the record before either had written it and both going the same way.
            if (!busy.compareAndSet(false, true)) return@runInBackground
            try {
                // A shortcut can outlive its list entry, and a button that does nothing reads
                // as a broken app rather than a stale shortcut.
                if (key !in app.prefs.freezeMethodsNow()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.shortcut_unavailable),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    return@runInBackground
                }
                val label = runCatching {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(FreezeTargetKey.packageName(key), 0)
                    ).toString()
                }.getOrNull() ?: FreezeTargetKey.packageName(key)
                // Read only once the guard is held, so it sees the other run's record.
                val wasFrozen = key in app.prefs.freezeAppliedNow()
                // A pinned shortcut has no way to ask, and a phone whose screen or Settings
                // just went away cannot be fixed from the phone: that freeze stays in the
                // list, where it can be confirmed first.
                if (!wasFrozen &&
                    FreezeTargetKey.packageName(key) in FreezeProtection.loadBearing(this)
                ) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.freeze_protected, label),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    return@runInBackground
                }
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
            } finally {
                busy.set(false)
            }
        }
    }

    companion object {
        const val EXTRA_KEY = "dev.delugelauncher.extra.FREEZE_KEY"
        const val ACTION_TOGGLE = "dev.delugelauncher.action.TOGGLE_FREEZE"

        /** One toggle at a time across every instance of this activity. */
        private val busy = java.util.concurrent.atomic.AtomicBoolean(false)
    }
}
