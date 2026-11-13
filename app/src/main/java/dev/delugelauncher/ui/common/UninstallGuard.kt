// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.delugelauncher.R
import dev.delugelauncher.data.AccessProvider
import dev.delugelauncher.data.AccessProviders
import dev.delugelauncher.data.AppInfo

/**
 * Asks before the system uninstaller is opened on an app that lends Deluge its freeze power.
 *
 * This is the one uninstall where the damage outlives the removal: the list may still name
 * apps held by a channel that is about to disappear, and without root or a manager there is
 * nothing left on the phone to lift them. Ordinary apps go straight through.
 */
@Composable
fun rememberUninstallGuard(onUninstall: (AppInfo) -> Unit): UninstallGuard {
    val guard = remember { UninstallGuard() }
    guard.onUninstall = onUninstall
    val pending = guard.pending
    if (pending != null) {
        UninstallProviderDialog(
            appLabel = pending.app.label,
            provider = pending.provider,
            onDismiss = guard::cancel,
            onConfirm = guard::confirm,
        )
    }
    return guard
}

@Stable
class UninstallGuard {

    internal data class Pending(val app: AppInfo, val provider: AccessProvider)

    internal var pending by mutableStateOf<Pending?>(null)
    internal var onUninstall: (AppInfo) -> Unit = {}

    /** Starts the uninstall, asking first when the target is what gives Deluge its access. */
    fun request(context: Context, app: AppInfo) {
        if (app.kind != dev.delugelauncher.data.EntryKind.APP) return
        val provider = AccessProviders.classify(context, app.componentName.packageName)
        if (provider == null) onUninstall(app) else pending = Pending(app, provider)
    }

    internal fun confirm() {
        val app = pending?.app
        pending = null
        if (app != null) onUninstall(app)
    }

    internal fun cancel() {
        pending = null
    }
}

@Composable
private fun UninstallProviderDialog(
    appLabel: String,
    provider: AccessProvider,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.uninstall_provider_title, appLabel)) },
        text = {
            Text(
                when (provider) {
                    AccessProvider.SHIZUKU -> stringResource(R.string.uninstall_provider_shizuku)
                    AccessProvider.ROOT_MANAGER -> stringResource(R.string.uninstall_provider_root)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.uninstall_provider_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
