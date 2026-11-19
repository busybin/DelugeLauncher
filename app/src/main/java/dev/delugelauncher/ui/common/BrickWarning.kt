// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo

/**
 * The last word before a freeze that can take the phone with it.
 *
 * With the guard on, these packages are refused outright. With it off they are ordinary rows,
 * and the screen they hide behind is also the screen that would have to be opened to undo the
 * mistake — so one dialog stands between the tap and a phone with nothing to tap.
 */
interface BrickWarning {
    /** Asks first when [targets] holds a package the phone needs, and runs [run] otherwise. */
    fun guard(targets: Collection<AppInfo>, run: () -> Unit)
}

/** Used where no warning is installed (tests, previews): the action simply runs. */
object NoBrickWarning : BrickWarning {
    override fun guard(targets: Collection<AppInfo>, run: () -> Unit) = run()
}

/**
 * Supplied by the screen that can draw a dialog, so a row menu two screens deep does not have
 * to carry a callback down to it.
 */
val LocalBrickWarning = compositionLocalOf<BrickWarning> { NoBrickWarning }

class BrickWarningState(
    /** Package names whose freeze can leave no way back into the phone. */
    private val bricking: Set<String>,
) : BrickWarning {

    data class Pending(val names: String, val run: () -> Unit)

    internal var pending by mutableStateOf<Pending?>(null)
        private set

    override fun guard(targets: Collection<AppInfo>, run: () -> Unit) {
        val named = targets
            .filter { it.packageName in bricking }
            .map { it.label }
            .distinct()
        if (named.isEmpty()) run() else ask(named, run)
    }

    /** Asks for [names] whatever the package list says, for callers holding only labels. */
    fun ask(names: List<String>, run: () -> Unit) {
        if (names.isEmpty()) run() else pending = Pending(names.joinToString(", "), run)
    }

    fun dismiss() {
        pending = null
    }

    internal fun confirm() {
        val current = pending ?: return
        pending = null
        current.run()
    }
}

@Composable
fun BrickWarningDialog(state: BrickWarningState) {
    val current = state.pending ?: return
    AlertDialog(
        onDismissRequest = state::dismiss,
        title = { Text(stringResource(R.string.freeze_brick_title)) },
        text = {
            Text(
                stringResource(R.string.freeze_brick_body, current.names),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = { state.confirm() }) {
                Text(
                    stringResource(R.string.freeze_brick_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = state::dismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
