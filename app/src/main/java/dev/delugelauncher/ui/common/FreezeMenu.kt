// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.FreezeMethod
import dev.delugelauncher.ui.settings.methodName

/**
 * The four things a row can ask of the freeze list.
 *
 * Held as one object rather than four parameters because every row menu takes the same set,
 * and the rows that can be frozen are already carrying a dozen callbacks apiece.
 */
@Immutable
class FreezeActions(
    /** Adds an app to the freeze list with a method. Nothing is frozen by this. */
    val add: (AppInfo, FreezeMethod) -> Unit,
    /** Freezes a listed app on the spot, under the method the list remembers. */
    val freezeNow: (AppInfo) -> Unit,
    /** Unfreezes it for now; it stays on the list. */
    val unfreeze: (AppInfo) -> Unit,
    /** Takes it off the list for good. */
    val remove: (AppInfo) -> Unit,
)

/**
 * The first-level entries a long-press menu adds for one app.
 *
 * Which one it is depends on where the app sits: not on the list offers to add it, on the list
 * and frozen offers to unfreeze it, on the list and not frozen offers to freeze it. Taking it
 * off the list is deliberately one level in, with the rest of the row's management.
 */
@Composable
fun FreezeMenuItems(
    app: AppInfo,
    actions: FreezeActions?,
    onDismissMenu: () -> Unit,
    /** The row's own state says a method still has to be picked; the dialog lives with it. */
    onChooseMethod: () -> Unit,
) {
    if (actions == null) return
    if (app.kind != EntryKind.APP) return
    if (!app.planned) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.freeze_menu_add)) },
            leadingIcon = { Icon(DelugeIcons.Freeze, contentDescription = null) },
            onClick = onChooseMethod,
        )
        return
    }
    val frozen = app.frozen != null
    DropdownMenuItem(
        text = {
            Text(
                stringResource(
                    if (frozen) R.string.freeze_menu_unfreeze
                    else R.string.freeze_menu_freeze_now
                )
            )
        },
        leadingIcon = {
            Icon(if (frozen) DelugeIcons.Thaw else DelugeIcons.Freeze, contentDescription = null)
        },
        onClick = {
            onDismissMenu()
            if (app.frozen != null) actions.unfreeze(app) else actions.freezeNow(app)
        },
    )
}

/** The second-level action that takes a listed app off the freeze list for good. */
@Composable
fun FreezeRemoveMenuItem(
    app: AppInfo,
    actions: FreezeActions?,
    onDismissMenu: () -> Unit,
) {
    if (actions == null) return
    if (app.kind != EntryKind.APP) return
    if (!app.planned) return
    DropdownMenuItem(
        text = { Text(stringResource(R.string.freeze_menu_remove)) },
        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        onClick = { onDismissMenu(); actions.remove(app) },
    )
}

/** The method an app is listed under, asked when it joins the freeze list from a row menu. */
@Composable
fun FreezeMethodDialog(
    /** The app being listed, or null when no dialog is showing. */
    app: AppInfo?,
    label: String,
    onDismiss: () -> Unit,
    onPick: (FreezeMethod) -> Unit,
) {
    if (app == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.freeze_pick_title, label)) },
        text = {
            Column {
                FreezeMethod.entries.forEach { method ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(method) }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = false, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(methodName(method), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
