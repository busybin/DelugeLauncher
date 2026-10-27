// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.delugelauncher.R
import dev.delugelauncher.data.ProtectStep

/**
 * The one question the lock-time protection flow asks at a time. [ProtectStep.DONE] and
 * [ProtectStep.NONE] have no dialog; the caller reports the first and never requests the second.
 */
@Composable
fun PrivateSpaceProtectDialog(
    step: ProtectStep,
    appLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, body, action, dismiss) = when (step) {
        ProtectStep.CREATE -> ProtectText(
            R.string.app_lock_space_create_title,
            R.string.app_lock_space_create_body,
            R.string.app_lock_space_create_action,
            R.string.app_lock_space_later,
        )
        ProtectStep.UNLOCK -> ProtectText(
            R.string.app_lock_space_unlock_title,
            R.string.app_lock_space_unlock_body,
            R.string.app_lock_space_unlock_action,
            R.string.app_lock_space_later,
        )
        ProtectStep.INSTALL_STORE -> ProtectText(
            R.string.app_lock_space_install_title,
            R.string.app_lock_space_install_body,
            R.string.app_lock_space_install_action,
            R.string.app_lock_space_later,
        )
        ProtectStep.INSTALL_AUTO -> ProtectText(
            R.string.app_lock_space_install_title,
            R.string.app_lock_space_install_auto_body,
            R.string.app_lock_space_install_auto_action,
            R.string.app_lock_space_later,
        )
        ProtectStep.REMOVE_MAIN -> ProtectText(
            R.string.app_lock_space_remove_title,
            R.string.app_lock_space_remove_body,
            R.string.app_lock_space_remove_action,
            R.string.app_lock_space_keep,
        )
        ProtectStep.NONE, ProtectStep.DONE -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title, appLabel)) },
        text = { Text(stringResource(body, appLabel)) },
        confirmButton = { TextButton(onClick = onAction) { Text(stringResource(action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(dismiss)) } },
    )
}

private data class ProtectText(
    val title: Int,
    val body: Int,
    val action: Int,
    val dismiss: Int,
)
