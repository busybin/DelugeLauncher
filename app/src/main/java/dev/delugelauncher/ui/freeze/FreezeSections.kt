// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.freeze

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.FreezeChannel
import dev.delugelauncher.data.FreezeMethod
import dev.delugelauncher.data.FreezeSort
import dev.delugelauncher.data.FreezeSourceFilter
import dev.delugelauncher.data.FreezeStateFilter
import dev.delugelauncher.data.FreezeStatus
import dev.delugelauncher.ui.common.freezeChannelLabel
import dev.delugelauncher.ui.common.freezeMethodLabel
import dev.delugelauncher.ui.settings.AccessSurface
import dev.delugelauncher.ui.settings.NavRow
import dev.delugelauncher.ui.settings.SectionDivider
import dev.delugelauncher.ui.settings.ChannelCard
import dev.delugelauncher.ui.settings.ListSectionLabel

/**
 * Whether a channel can run a method on this phone.
 *
 * Hiding on Shizuku is left out on purpose: it only works when the manager itself runs as
 * root, and a choice that silently fails half the time is worse than one that is not offered.
 */
fun channelOffered(channel: FreezeChannel, method: FreezeMethod, status: FreezeStatus): Boolean {
    if (!status.checked) return true
    return when (channel) {
        FreezeChannel.AUTO -> true
        FreezeChannel.SHIZUKU -> status.shizukuAuthorized && method != FreezeMethod.HIDE
        FreezeChannel.ROOT -> status.rootGranted
        FreezeChannel.OWNER ->
            status.deviceOwner && (method == FreezeMethod.SUSPEND || method == FreezeMethod.HIDE)
    }
}

/** The one sentence the access page uses as its headline, reused as the entry's summary. */
@Composable
fun accessSentence(status: FreezeStatus): String = stringResource(
    when {
        !status.checked -> R.string.access_state_checking
        status.access == dev.delugelauncher.data.FreezeAccess.DEVICE_OWNER -> R.string.access_state_owner
        status.access == dev.delugelauncher.data.FreezeAccess.ROOT -> R.string.access_state_root
        status.access == dev.delugelauncher.data.FreezeAccess.SHIZUKU -> R.string.access_state_shizuku
        else -> R.string.access_state_none
    }
)

/**
 * The four reference actions as a 2x2 grid: access, method comparison, channels and the
 * automatic freeze. They are the page's doors, so they sit together above the list.
 */
@Composable
fun FreezeActionGrid(
    defaultMethod: FreezeMethod?,
    onOpenAccess: () -> Unit,
    onOpenMethods: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenAuto: () -> Unit,
) {
    val gap = 8.dp
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            FreezeActionTile(
                title = stringResource(R.string.access_title),
                onClick = onOpenAccess,
                modifier = Modifier.weight(1f),
            )
            FreezeActionTile(
                title = stringResource(R.string.freeze_methods_title),
                onClick = onOpenMethods,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.size(gap))
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            FreezeActionTile(
                title = stringResource(R.string.freeze_channels_title),
                value = defaultMethod?.let { freezeMethodLabel(it) }
                    ?: stringResource(R.string.freeze_method_none),
                onClick = onOpenChannels,
                modifier = Modifier.weight(1f),
            )
            FreezeActionTile(
                title = stringResource(R.string.freeze_auto),
                onClick = onOpenAuto,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FreezeActionTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The opening card, with the master switch on the same line as the frozen count. */
@Composable
fun FreezeStateCard(
    enabled: Boolean,
    title: String,
    body: String,
    counts: FreezeCounts?,
    onToggle: (Boolean) -> Unit,
    onListed: () -> Unit = {},
    onFrozen: () -> Unit = {},
    onPermanent: () -> Unit = {},
) {
    AccessSurface {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("freeze-enable-switch"),
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 6.dp),
            )
            if (counts != null) {
                FreezeStats(
                    counts = counts,
                    onListed = onListed,
                    onFrozen = onFrozen,
                    onPermanent = onPermanent,
                )
            }
        }
    }
}

/**
 * The column names above the rows, pinned so a long list never loses the meaning of a
 * number. The widths come from the row, so the two cannot drift apart.
 */
@Composable
fun FreezeTableHeader(onOpenMethods: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("freeze-table-header")
                .padding(start = 26.dp, end = 30.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The switch's own header: wider than its 40dp touch target, borrowing the
            // empty icon column so the "app" label still starts where the names do.
            Text(
                text = stringResource(R.string.freeze_col_freeze),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(56.dp),
            )
            Spacer(Modifier.width(26.dp))
            Text(
                text = stringResource(R.string.freeze_col_app),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.freeze_col_method),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(METHOD_COLUMN),
            )
            // The word is the way into the methods page: "permanent" is the one column whose
            // effect people ask about, and the answer already lives on that page.
            Row(
                modifier = Modifier
                    .width(PERMANENT_COLUMN)
                    .clickable(onClick = onOpenMethods)
                    .testTag("freeze-permanent-help"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.freeze_permanent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    maxLines = 1,
                )
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/** Which channel runs each method, plus the method a new row joins with. */
@Composable
fun FreezeChannelsPanel(
    status: FreezeStatus,
    channels: Map<FreezeMethod, FreezeChannel>,
    defaultMethod: FreezeMethod?,
    onSetChannel: (FreezeMethod, FreezeChannel) -> Unit,
    onSetDefault: (FreezeMethod?) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.freeze_default_method),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            DefaultMethodMenu(defaultMethod, status, onSetDefault)
        }
        SectionDivider()
        FreezeMethod.entries.forEach { method ->
            ChannelRow(
                method = method,
                status = status,
                channel = channels[method] ?: FreezeChannel.AUTO,
                onSet = { onSetChannel(method, it) },
            )
        }
    }
}

@Composable
private fun DefaultMethodMenu(
    default: FreezeMethod?,
    status: FreezeStatus,
    onSet: (FreezeMethod?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        MenuValue(
            text = default?.let { freezeMethodLabel(it) }
                ?: stringResource(R.string.freeze_method_none),
            onClick = { menu = true },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.freeze_method_none)) },
                leadingIcon = { RadioButton(selected = default == null, onClick = null) },
                onClick = {
                    menu = false
                    if (default != null) onSet(null)
                },
            )
            FreezeMethod.entries.filter { status.supports(it) }.forEach { method ->
                DropdownMenuItem(
                    text = { Text(freezeMethodLabel(method)) },
                    leadingIcon = { RadioButton(selected = method == default, onClick = null) },
                    onClick = {
                        menu = false
                        if (method != default) onSet(method)
                    },
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    method: FreezeMethod,
    status: FreezeStatus,
    channel: FreezeChannel,
    onSet: (FreezeChannel) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val offered = channelOffered(channel, method, status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = freezeMethodLabel(method),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (!offered) {
            Text(
                text = stringResource(R.string.freeze_channel_unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(8.dp))
        }
        Box {
            MenuValue(freezeChannelLabel(channel), onClick = { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                FreezeChannel.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(freezeChannelLabel(option)) },
                        enabled = channelOffered(option, method, status),
                        onClick = {
                            menu = false
                            if (option != channel) onSet(option)
                        },
                    )
                }
            }
        }
    }
}

/** A dropdown's closed state: the current value and a quiet chevron, nothing else. */
@Composable
fun MenuValue(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The three buckets, the two orders, and the system-app switch — one card. */
@Composable
fun FreezeFilterCard(
    filters: FreezeFilters,
    systemEnabled: Boolean,
    onSource: (FreezeSourceFilter) -> Unit,
    onState: (FreezeStateFilter) -> Unit,
    onSort: (FreezeSort) -> Unit,
    onSystemToggle: (Boolean) -> Unit,
) {
    ListSectionLabel(stringResource(R.string.freeze_filter_title))
    AccessSurface {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            val sources = listOf(
                FreezeSourceFilter.USER to R.string.freeze_filter_user,
                FreezeSourceFilter.SYSTEM to R.string.freeze_filter_system,
                FreezeSourceFilter.OTHER_PROFILE to R.string.freeze_filter_other,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                sources.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = filters.source == value,
                        onClick = { onSource(value) },
                        enabled = value != FreezeSourceFilter.SYSTEM || systemEnabled,
                        shape = SegmentedButtonDefaults.itemShape(index, sources.size),
                    ) {
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StateMenu(filters.state, onState)
                SortMenu(filters.sort, onSort)
            }
            HorizontalDivider(
                Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.freeze_system_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Switch(checked = systemEnabled, onCheckedChange = onSystemToggle)
            }
        }
    }
}

@Composable
private fun StateMenu(state: FreezeStateFilter, onState: (FreezeStateFilter) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        MenuValue(stateLabel(state), onClick = { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            FreezeStateFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stateLabel(option)) },
                    onClick = {
                        menu = false
                        onState(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun stateLabel(state: FreezeStateFilter): String = stringResource(
    when (state) {
        FreezeStateFilter.ALL -> R.string.freeze_filter_all
        FreezeStateFilter.FROZEN -> R.string.freeze_filter_frozen
        FreezeStateFilter.UNFROZEN -> R.string.freeze_filter_unfrozen
        FreezeStateFilter.PERMANENT -> R.string.freeze_filter_permanent
    }
)

@Composable
private fun SortMenu(sort: FreezeSort, onSort: (FreezeSort) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        MenuValue(sortLabel(sort), onClick = { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            FreezeSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(sortLabel(option)) },
                    onClick = {
                        menu = false
                        onSort(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun sortLabel(sort: FreezeSort): String = stringResource(
    when (sort) {
        FreezeSort.NAME_ASC -> R.string.freeze_sort_name_asc
        FreezeSort.NAME_DESC -> R.string.freeze_sort_name_desc
        FreezeSort.INSTALLED -> R.string.freeze_sort_installed
        FreezeSort.UPDATED -> R.string.freeze_sort_updated
    }
)

/** The three totals, each a shortcut into the filter that shows it. */
@Composable
fun FreezeStats(
    counts: FreezeCounts,
    onListed: () -> Unit,
    onFrozen: () -> Unit,
    onPermanent: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Stat(counts.listed, stringResource(R.string.freeze_count_listed), onListed, Modifier.weight(1f))
        Stat(counts.frozen, stringResource(R.string.freeze_state_frozen), onFrozen, Modifier.weight(1f))
        Stat(counts.permanent, stringResource(R.string.freeze_permanent), onPermanent, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shown while rows are selected: every action stays inside the current view. */
@Composable
fun FreezeBulkBar(
    selectedCount: Int,
    allSelected: Boolean,
    listable: Int,
    removable: Int,
    onSelectAll: () -> Unit,
    onAddToList: () -> Unit,
    onRemoveFromList: () -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onPermanent: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.freeze_selected_count, selectedCount),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSelectAll, enabled = !allSelected) {
                    Text(stringResource(R.string.freeze_select_all))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAddToList, enabled = listable > 0) {
                    Text(stringResource(R.string.freeze_bulk_add))
                }
                TextButton(onClick = onRemoveFromList, enabled = removable > 0) {
                    Text(stringResource(R.string.freeze_bulk_remove))
                }
                TextButton(onClick = onFreeze, enabled = selectedCount > 0) {
                    Text(stringResource(R.string.freeze_menu_freeze_now))
                }
                TextButton(onClick = onUnfreeze, enabled = removable > 0) {
                    Text(stringResource(R.string.freeze_menu_unfreeze))
                }
                TextButton(onClick = { onPermanent(true) }, enabled = selectedCount > 0) {
                    Text(stringResource(R.string.freeze_permanent))
                }
                TextButton(onClick = { onPermanent(false) }, enabled = selectedCount > 0) {
                    Text(stringResource(R.string.freeze_bulk_clear))
                }
            }
        }
    }
}
