// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.LockCapabilities

/**
 * Says what a lock will and will not do before it is applied.
 *
 * Shown only when a layer is missing; a root phone with the guard installed gets the silent
 * full path. The password gate is always true, so it is listed too rather than left implied.
 */
@Composable
fun LockCapabilityDialog(
    appLabel: String,
    capabilities: LockCapabilities,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_lock_limits_title, appLabel)) },
        text = {
            Column {
                Text(stringResource(R.string.app_lock_limits_intro))
                CapabilityLine(
                    ok = true,
                    label = stringResource(R.string.app_lock_limits_gate),
                    detail = stringResource(R.string.app_lock_limits_on),
                )
                CapabilityLine(
                    ok = capabilities.canHide,
                    label = stringResource(R.string.app_lock_limits_hide),
                    detail = if (capabilities.canHide) {
                        stringResource(R.string.app_lock_limits_on)
                    } else {
                        stringResource(R.string.app_lock_limits_hide_off)
                    },
                )
                CapabilityLine(
                    ok = capabilities.canSuspend,
                    label = stringResource(R.string.app_lock_limits_suspend),
                    detail = if (capabilities.canSuspend) {
                        stringResource(R.string.app_lock_limits_on)
                    } else {
                        stringResource(R.string.app_lock_limits_suspend_off)
                    },
                )
                CapabilityLine(
                    ok = capabilities.canGuard,
                    label = stringResource(R.string.app_lock_limits_guard),
                    detail = if (capabilities.canGuard) {
                        stringResource(R.string.app_lock_limits_on)
                    } else {
                        stringResource(R.string.app_lock_limits_guard_off)
                    },
                )
                if (!capabilities.isFull) {
                    Text(
                        stringResource(R.string.app_lock_limits_how),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.app_lock_limits_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun CapabilityLine(ok: Boolean, label: String, detail: String) {
    Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (ok) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
