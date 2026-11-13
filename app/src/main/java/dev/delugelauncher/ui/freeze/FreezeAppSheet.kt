// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.freeze

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.ui.common.AppIcon

/**
 * The three things a row cannot carry, for the row the user tapped.
 *
 * Freezing, the method and the permanent flag are controls on the row itself; this sheet is
 * only the leftovers — the shortcut, the way to compare methods, and the way off the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezeAppSheet(
    row: FreezeAppRow,
    app: AppInfo,
    iconSizeDp: Int,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onRemove: () -> Unit,
    onCompareMethods: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app = app, sizeDp = minOf(iconSizeDp, 40))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(
                            when {
                                row.locked -> R.string.freeze_locked_app
                                row.protectedRow -> R.string.freeze_protected_short
                                !row.listed -> R.string.freeze_unmanaged
                                row.frozen -> R.string.freeze_state_frozen
                                else -> R.string.freeze_state_pending
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            SheetAction(stringResource(R.string.freeze_menu_pin), onPin)
            SheetAction(stringResource(R.string.freeze_table_open), onCompareMethods)
            if (row.listed) {
                SheetAction(
                    label = stringResource(R.string.freeze_menu_remove),
                    onClick = onRemove,
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (destructive) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}
