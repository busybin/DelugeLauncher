// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.network

import androidx.compose.ui.platform.LocalResources
import dev.delugelauncher.ui.common.topBarInsets
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.NetworkAppState
import dev.delugelauncher.data.NetworkMode
import dev.delugelauncher.data.NetworkStatus
import dev.delugelauncher.ui.common.AppIcon
import dev.delugelauncher.ui.settings.AccessSurface
import dev.delugelauncher.ui.settings.positiveMarkColor

/** The toggle's column, shared by the row and the header so the two cannot drift. */
private val TOGGLE_COLUMN = 56.dp
private val ICON_COLUMN = 28.dp
private val STATE_COLUMN = 120.dp

/**
 * The per-app firewall, in the shape the freeze page settled on: a state card, then one
 * table of apps where the left circle is both the state and the switch.
 *
 * The list is not the truth. A row that was written but not read back says "waiting", and a
 * row whose answer could not be parsed says "unconfirmed", because a clean exit code from
 * the shell is not the same as a package being offline.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun NetworkAppsScreen(
    allApps: List<AppInfo>,
    blocked: Set<String>,
    unrestricted: Set<String>,
    applied: Set<String>,
    pending: Set<String>,
    unknown: Set<String>,
    protectedPackages: Set<String>,
    frozenPackages: Set<String>,
    status: NetworkStatus,
    mode: NetworkMode,
    blockNewApps: Boolean,
    iconSizeDp: Int,
    onBack: () -> Unit,
    onSetMode: (NetworkMode) -> Unit,
    onSetAppState: (AppInfo, NetworkAppState) -> Unit,
    onSetBlockNewApps: (Boolean) -> Unit,
    onSync: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf(NetworkFilter.ALL) }
    var stateDialogFor by remember { mutableStateOf<NetworkRow?>(null) }
    val rows = NetworkUi.rows(
        apps = allApps,
        blocked = blocked,
        applied = applied,
        pending = pending,
        unknown = unknown,
        protectedPackages = protectedPackages,
        frozenPackages = frozenPackages,
        unrestricted = unrestricted,
        filter = filter,
        query = query,
        showSystem = showSystem,
    )
    val on = mode.enabled
    val usable = status.available && on

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                windowInsets = topBarInsets,
                title = { Text(stringResource(R.string.network_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                NetworkStateCard(
                    enabled = on,
                    switchable = status.available,
                    title = stringResource(R.string.network_firewall),
                    body = when {
                        !status.supported -> stringResource(R.string.network_unsupported)
                        !status.available -> stringResource(R.string.network_needs_access)
                        !on -> stringResource(R.string.network_firewall_detail)
                        else -> statusLine(status)
                    },
                    blockNewApps = blockNewApps,
                    onToggle = { checked ->
                        onSetMode(if (checked) NetworkMode.BLACKLIST else NetworkMode.OFF)
                    },
                    onSetBlockNewApps = onSetBlockNewApps,
                    onSync = onSync,
                )
            }
            item {
                AccessSurface {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.network_search_hint)) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSystem = !showSystem }
                                .padding(top = 6.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.network_show_system),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                        }
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NetworkFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option },
                                    label = { Text(filterLabel(option)) },
                                )
                            }
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.network_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            } else {
                stickyHeader(key = "network-table-header") {
                    NetworkTableHeader()
                }
                items(rows, key = { it.packageName }) { row ->
                    NetworkRowCard(
                        row = row,
                        iconSizeDp = iconSizeDp,
                        enabled = usable && row.state != NetworkRowState.PROTECTED,
                        onClick = {
                            val next = if (row.policy == NetworkAppState.BLOCKED) {
                                NetworkAppState.FOLLOW_FREEZE
                            } else {
                                NetworkAppState.BLOCKED
                            }
                            onSetAppState(row.app, next)
                        },
                        onLongClick = { stateDialogFor = row },
                    )
                }
            }
        }
    }

    stateDialogFor?.let { row ->
        NetworkStateDialog(
            row = row,
            onDismiss = { stateDialogFor = null },
            onPick = { state ->
                stateDialogFor = null
                onSetAppState(row.app, state)
            },
        )
    }
}

@Composable
private fun NetworkStateCard(
    enabled: Boolean,
    switchable: Boolean,
    title: String,
    body: String,
    blockNewApps: Boolean,
    onToggle: (Boolean) -> Unit,
    onSetBlockNewApps: (Boolean) -> Unit,
    onSync: () -> Unit,
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
                    enabled = switchable,
                    onCheckedChange = onToggle,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 6.dp),
            )
            if (enabled) {
                PolicyRow(
                    label = stringResource(R.string.network_block_new_apps),
                    detail = stringResource(R.string.network_block_new_apps_detail),
                    checked = blockNewApps,
                    onCheckedChange = onSetBlockNewApps,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.network_reboot_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSync) {
                        Text(stringResource(R.string.network_sync))
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyRow(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NetworkTableHeader() {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 30.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.network_col_block),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(TOGGLE_COLUMN),
            )
            Spacer(Modifier.width(26.dp))
            Text(
                text = stringResource(R.string.network_col_app),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.network_col_state),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(STATE_COLUMN),
            )
        }
    }
}

@Composable
private fun NetworkRowCard(
    row: NetworkRow,
    iconSizeDp: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val protected = row.state == NetworkRowState.PROTECTED
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (protected) 0.18f else 0.35f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(TOGGLE_COLUMN), contentAlignment = Alignment.Center) {
                NetworkToggle(blocked = row.blocked, enabled = enabled, onClick = onClick)
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(ICON_COLUMN), contentAlignment = Alignment.Center) {
                AppIcon(app = row.app, sizeDp = minOf(iconSizeDp, 28))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = row.app.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (protected) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = rowLabel(row),
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    row.blocked -> MaterialTheme.colorScheme.primary
                    row.policy == NetworkAppState.PROTECTED ->
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(STATE_COLUMN),
            )
        }
    }
}

@Composable
private fun rowLabel(row: NetworkRow): String = when {
    row.policy == NetworkAppState.PROTECTED -> stringResource(R.string.network_state_protected)
    row.state == NetworkRowState.PENDING -> stringResource(R.string.network_state_pending)
    row.state == NetworkRowState.UNKNOWN -> stringResource(R.string.network_state_unknown)
    row.policy == NetworkAppState.BLOCKED -> stringResource(R.string.network_state_blocked)
    row.policy == NetworkAppState.UNRESTRICTED ->
        stringResource(R.string.network_state_unrestricted)
    row.freezeLinked -> stringResource(R.string.network_state_frozen)
    else -> stringResource(R.string.network_state_follow)
}

@Composable
private fun filterLabel(filter: NetworkFilter): String = stringResource(
    when (filter) {
        NetworkFilter.ALL -> R.string.network_filter_all
        NetworkFilter.FOLLOW_FREEZE -> R.string.network_filter_follow
        NetworkFilter.BLOCKED -> R.string.network_filter_blocked
        NetworkFilter.UNRESTRICTED -> R.string.network_filter_unrestricted
    },
)

/** The long-press answer sheet: all three states, the current one already marked. */
@Composable
private fun NetworkStateDialog(
    row: NetworkRow,
    onDismiss: () -> Unit,
    onPick: (NetworkAppState) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.app.label) },
        text = {
            Column {
                NetworkStateOption(
                    label = R.string.network_state_follow,
                    detail = R.string.network_state_follow_detail,
                    selected = row.policy == NetworkAppState.FOLLOW_FREEZE,
                    onPick = { onPick(NetworkAppState.FOLLOW_FREEZE) },
                )
                NetworkStateOption(
                    label = R.string.network_state_blocked,
                    detail = R.string.network_state_blocked_detail,
                    selected = row.policy == NetworkAppState.BLOCKED,
                    onPick = { onPick(NetworkAppState.BLOCKED) },
                )
                NetworkStateOption(
                    label = R.string.network_state_unrestricted,
                    detail = R.string.network_state_unrestricted_detail,
                    selected = row.policy == NetworkAppState.UNRESTRICTED,
                    onPick = { onPick(NetworkAppState.UNRESTRICTED) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun NetworkStateOption(
    label: Int,
    detail: Int,
    selected: Boolean,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onPick)
        Column(Modifier.padding(start = 8.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
    }
}

/**
 * The row's only switch, drawn on the state itself: a ring while the app is reachable, a
 * filled disc with a bar through it while it is cut off.
 */
@Composable
private fun NetworkToggle(blocked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val ink by animateColorAsState(
        targetValue = if (blocked) positiveMarkColor() else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(180),
        label = "network-toggle-ink",
    )
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .toggleable(
                value = blocked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (blocked) ink else Color.Transparent)
                .border(
                    width = if (blocked) 0.dp else 2.dp,
                    color = if (enabled) ink else ink.copy(alpha = 0.5f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (blocked) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(13.dp)
                        .rotate(45f)
                        .background(Color.White, RoundedCornerShape(1.5.dp)),
                )
            }
        }
    }
}

@Composable
private fun statusLine(status: NetworkStatus): String {
    val context = LocalContext.current
    val resources = LocalResources.current
    val parts = buildList {
        if (status.applied.isNotEmpty()) {
            add(resources.getString(R.string.network_status_applied, status.applied.size))
        }
        if (status.pending.isNotEmpty()) {
            add(resources.getString(R.string.network_status_pending, status.pending.size))
        }
        if (status.unknown.isNotEmpty()) {
            add(resources.getString(R.string.network_status_unknown, status.unknown.size))
        }
        if (isEmpty()) add(resources.getString(R.string.network_status_none))
    }
    return parts.joinToString(" · ")
}
