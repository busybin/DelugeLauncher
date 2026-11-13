// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.freeze

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.FreezeMethod
import dev.delugelauncher.data.FreezeStatus
import dev.delugelauncher.ui.common.AppIcon
import dev.delugelauncher.ui.common.freezeMethodLabel
import dev.delugelauncher.ui.settings.positiveMarkColor

/** The row's columns, shared with the table header so the two always line up. */
internal val TOGGLE_COLUMN = 40.dp
internal val ICON_COLUMN = 28.dp
internal val METHOD_COLUMN = 96.dp
internal val PERMANENT_COLUMN = 64.dp

/**
 * One app as one line: the round toggle, the icon, the name, the method and the permanent flag.
 *
 * The list is a table, so the row is a record, not a card of its own: one line per app keeps
 * 160 rows scrollable, and the header above says what each column means. Nothing here opens a
 * second screen; the toggle is both the state and the switch.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FreezeAppRowCard(
    row: FreezeAppRow,
    app: AppInfo,
    iconSizeDp: Int,
    status: FreezeStatus,
    multi: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleFrozen: () -> Unit,
    onPickMethod: (FreezeMethod) -> Unit,
    onTogglePermanent: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelected: () -> Unit,
) {
    val dimmed = row.locked || row.protectedRow
    // A protected app may never be frozen, but a row that is somehow already frozen still
    // needs a way out: the toggle is offered for the release only.
    val canToggle = !row.locked && (!row.protectedRow || row.frozen)
    val canPermanent = row.listed && row.method != FreezeMethod.STOP && !dimmed
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dimmed) 0.18f else 0.35f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (multi) {
                            if (!dimmed) onToggleSelected()
                        } else {
                            onOpen()
                        }
                    },
                    onLongClick = onLongPress,
                )
                .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multi) {
                Box(Modifier.width(TOGGLE_COLUMN), contentAlignment = Alignment.Center) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { if (!dimmed) onToggleSelected() },
                        enabled = !dimmed,
                    )
                }
            } else {
                Box(Modifier.width(TOGGLE_COLUMN), contentAlignment = Alignment.Center) {
                    FreezeToggle(
                        frozen = row.frozen,
                        enabled = canToggle,
                        onClick = onToggleFrozen,
                        tag = "freeze-row-switch-${row.key}",
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(ICON_COLUMN), contentAlignment = Alignment.Center) {
                AppIcon(app = app, sizeDp = minOf(iconSizeDp, 28))
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (dimmed) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (dimmed) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(
                            if (row.locked) R.string.freeze_locked_app else R.string.freeze_protected_short
                        ),
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            MethodMenu(
                row = row,
                status = status,
                dimmed = dimmed,
                onPick = onPickMethod,
            )
            Box(Modifier.width(PERMANENT_COLUMN), contentAlignment = Alignment.Center) {
                Switch(
                    checked = row.permanent,
                    onCheckedChange = { onTogglePermanent() },
                    enabled = canPermanent,
                    modifier = Modifier.testTag("freeze-row-permanent-${row.key}"),
                )
            }
        }
    }
}

/**
 * The row's only switch, drawn on the state itself: a ring while the app runs, a filled
 * green disc with a pause mark while it is held.
 */
@Composable
private fun FreezeToggle(
    frozen: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    val ink by animateColorAsState(
        targetValue = if (frozen) {
            positiveMarkColor()
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(180),
        label = "freeze-toggle-ink",
    )
    val glyph by animateFloatAsState(
        targetValue = if (frozen) 1f else 0f,
        animationSpec = tween(180),
        label = "freeze-toggle-glyph",
    )
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .toggleable(
                value = frozen,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onClick() },
            )
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (frozen) ink else Color.Transparent)
                .border(
                    width = if (frozen) 0.dp else 2.dp,
                    color = if (enabled) ink else ink.copy(alpha = 0.5f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // A pause mark, drawn rather than imported: two bars, so the picture reads the
            // same in both themes and needs no extra icon dependency.
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp),
                modifier = Modifier.alpha(glyph),
            ) {
                repeat(2) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(9.dp)
                            .background(Color.White, RoundedCornerShape(1.5.dp)),
                    )
                }
            }
        }
    }
}

/**
 * The method column. An app that is not on the list has no method — it says so, and picking
 * one is what puts it on the list. Picking nothing changes nothing.
 */
@Composable
private fun MethodMenu(
    row: FreezeAppRow,
    status: FreezeStatus,
    dimmed: Boolean,
    onPick: (FreezeMethod) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val current = row.method
    Row(
        modifier = Modifier
            .width(METHOD_COLUMN)
            .clickable(enabled = !dimmed) { menu = true }
            .padding(vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = current?.let { freezeMethodLabel(it) }
                ?: stringResource(R.string.freeze_method_none),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                dimmed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                current != null -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("freeze-row-method-${row.key}"),
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (dimmed) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        if (current == null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.freeze_method_none)) },
                onClick = { menu = false },
            )
        }
        FreezeMethod.entries.forEach { method ->
            DropdownMenuItem(
                text = { Text(freezeMethodLabel(method)) },
                enabled = !dimmed && status.offered(method, row.userSerial),
                onClick = {
                    menu = false
                    if (method != current) onPick(method)
                },
            )
        }
    }
}
