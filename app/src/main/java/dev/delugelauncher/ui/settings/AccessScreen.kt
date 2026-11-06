// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import dev.delugelauncher.ui.common.joinMiddleDot

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.DeviceOwnerPolicy
import dev.delugelauncher.data.FreezeAccess
import dev.delugelauncher.data.FreezeStatus
import dev.delugelauncher.data.RootProvider

/**
 * What can freeze apps on this phone, and how to set up the rest of the ways.
 *
 * The page opens with a capability table first: the channels differ in what they can do,
 * and that is the question behind "why can't I hide?" Each channel then folds into one card,
 * so the page is a status board instead of a wall of instructions.
 */
@Composable
fun AccessScreen(
    status: FreezeStatus,
    onRefresh: () -> Unit,
    onOpenShizuku: () -> Unit,
    onGetShizuku: () -> Unit,
    onRemoveDeviceOwner: () -> Unit,
    /** Asks Dhizuku for permission and the package-access scope it needs. */
    onGrantDhizuku: () -> Unit,
    /** Permission is there; this only takes the package-access scope. */
    onEnableDhizuku: () -> Unit,
    onOpenDhizuku: () -> Unit,
    onGetDhizuku: () -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val known = status.checked
    val ready = known && status.access != FreezeAccess.NONE
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val ownerCommand = remember(context) {
        DeviceOwnerPolicy(context.applicationContext).provisioningCommand()
    }
    // Saveable: a rotation must not drop the removal confirmation the user is reading.
    var copied by rememberSaveable { mutableStateOf(false) }
    var confirmingRemove by rememberSaveable { mutableStateOf(false) }
    // The screen is the manual fallback, not the only probe: opening it must always ask.
    LaunchedEffect(Unit) { onRefresh() }
    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.access_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onRefresh) {
                        Text(stringResource(R.string.access_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.padding(padding),
        ) {
            item(key = "state") {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(
                                when {
                                    !known -> R.string.access_state_checking
                                    status.access == FreezeAccess.DEVICE_OWNER ->
                                        R.string.access_state_owner
                                    status.access == FreezeAccess.DHIZUKU ->
                                        R.string.access_state_dhizuku
                                    status.access == FreezeAccess.ROOT -> R.string.access_state_root
                                    status.access == FreezeAccess.SHIZUKU -> R.string.access_state_shizuku
                                    else -> R.string.access_state_none
                                }
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(
                                when {
                                    !known -> R.string.access_state_checking
                                    !ready -> R.string.access_no_channel
                                    else -> R.string.access_ready_body
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            item(key = "table") {
                ListSectionLabel(stringResource(R.string.access_table_title))
                CapabilityTable()
                Text(
                    stringResource(R.string.access_table_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            item(key = "root") {
                ChannelCard(
                    title = stringResource(R.string.access_root),
                    summary = rootSummary(status),
                    active = status.rootGranted,
                ) {
                    StatusRow(
                        stringResource(R.string.access_root_granted),
                        status.rootGranted,
                        known,
                    )
                    if (known && status.rootGranted) {
                        ValueRow(
                            stringResource(R.string.access_root_manager),
                            listOfNotNull(
                                when (status.rootProvider) {
                                    RootProvider.MAGISK -> "Magisk"
                                    RootProvider.KERNELSU -> "KernelSU"
                                    RootProvider.APATCH -> "APatch"
                                    RootProvider.SUPERSU -> "SuperSU"
                                    RootProvider.OTHER -> "su"
                                    RootProvider.NONE -> "su"
                                },
                                status.rootVersion,
                            ).joinToString(" "),
                        )
                    }
                }
            }
            item(key = "shizuku") {
                ChannelCard(
                    title = stringResource(R.string.access_shizuku),
                    summary = shizukuSummary(status),
                    active = status.shizukuAuthorized,
                ) {
                    StatusRow(
                        stringResource(R.string.access_shizuku_installed),
                        status.shizukuInstalled,
                        known,
                    )
                    StatusRow(
                        stringResource(R.string.access_shizuku_running),
                        status.shizukuRunning,
                        known,
                    )
                    StatusRow(
                        stringResource(R.string.access_shizuku_authorized),
                        status.shizukuAuthorized,
                        known,
                    )
                    if (known && status.shizukuInstalled) {
                        ValueRow(
                            stringResource(R.string.access_manager),
                            joinMiddleDot(
                                listOfNotNull(
                                    status.shizukuManagerPackage,
                                    status.shizukuManagerVersion,
                                )
                            ),
                        )
                    }
                    if (known && status.shizukuRunning) {
                        ValueRow(
                            stringResource(R.string.access_version),
                            status.shizukuVersion.toString(),
                        )
                    }
                    if (known && !status.shizukuAuthorized) {
                        Divider()
                        ListSectionLabel(stringResource(R.string.access_steps_title))
                        Step(stringResource(R.string.access_step_1))
                        Step(stringResource(R.string.access_step_2))
                        Step(stringResource(R.string.access_step_3))
                        Step(stringResource(R.string.access_step_4))
                        TextButton(
                            onClick = if (status.shizukuInstalled) onOpenShizuku else onGetShizuku,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (status.shizukuInstalled) R.string.access_open_shizuku
                                    else R.string.access_get_shizuku
                                )
                            )
                        }
                    }
                }
            }
            item(key = "dhizuku") {
                ChannelCard(
                    title = stringResource(R.string.access_dhizuku),
                    summary = dhizukuSummary(status),
                    active = status.dhizukuDelegated,
                ) {
                    StatusRow(
                        stringResource(R.string.access_shizuku_installed),
                        status.dhizukuInstalled,
                        known,
                    )
                    StatusRow(
                        stringResource(R.string.access_dhizuku_permission),
                        status.dhizukuPermission,
                        known,
                    )
                    StatusRow(
                        stringResource(R.string.access_dhizuku_scope),
                        status.dhizukuDelegated,
                        known,
                    )
                    Text(
                        stringResource(R.string.access_dhizuku_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (known && !status.dhizukuDelegated) {
                        Divider()
                        ListSectionLabel(stringResource(R.string.access_dhizuku_setup))
                        Step(stringResource(R.string.access_dhizuku_step_1))
                        Step(stringResource(R.string.access_dhizuku_step_2))
                        Step(stringResource(R.string.access_dhizuku_step_3))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = when {
                                    !status.dhizukuInstalled -> onGetDhizuku
                                    !status.dhizukuPermission -> onGrantDhizuku
                                    else -> onEnableDhizuku
                                },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            ) {
                                Text(
                                    stringResource(
                                        when {
                                            !status.dhizukuInstalled ->
                                                R.string.access_get_dhizuku
                                            !status.dhizukuPermission ->
                                                R.string.access_dhizuku_grant
                                            else -> R.string.access_dhizuku_scope_action
                                        }
                                    )
                                )
                            }
                            if (status.dhizukuInstalled) {
                                TextButton(
                                    onClick = onOpenDhizuku,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                ) {
                                    Text(stringResource(R.string.access_open_dhizuku))
                                }
                            }
                        }
                    }
                }
            }
            item(key = "owner") {
                ChannelCard(
                    title = stringResource(R.string.access_owner),
                    summary = ownerSummary(status),
                    active = status.deviceOwner,
                    action = if (known && status.deviceOwner) {
                        {
                            TextButton(
                                onClick = { confirmingRemove = true },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            ) {
                                Text(stringResource(R.string.access_owner_remove))
                            }
                        }
                    } else {
                        null
                    },
                ) {
                    StatusRow(
                        stringResource(R.string.access_owner_admin),
                        status.deviceAdmin,
                        known,
                    )
                    StatusRow(
                        stringResource(R.string.access_owner_active),
                        status.deviceOwner,
                        known,
                    )
                    if (known && status.deviceOwner) {
                        Text(
                            stringResource(R.string.access_owner_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else if (known) {
                        Divider()
                        ListSectionLabel(stringResource(R.string.access_owner_setup))
                        Step(stringResource(R.string.access_owner_step_1))
                        Step(stringResource(R.string.access_owner_step_2))
                        Text(
                            stringResource(R.string.access_owner_command),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        SelectionContainer {
                            Text(
                                ownerCommand,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(ownerCommand))
                                copied = true
                            },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (copied) R.string.access_owner_copied
                                    else R.string.access_owner_copy
                                )
                            )
                        }
                        Step(stringResource(R.string.access_owner_step_3))
                        Step(stringResource(R.string.access_owner_step_4))
                        Text(
                            stringResource(R.string.access_owner_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        )
                    }
                }
            }
        }
    }
    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text(stringResource(R.string.access_owner_remove_title)) },
            text = { Text(stringResource(R.string.access_owner_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingRemove = false
                        onRemoveDeviceOwner()
                    },
                ) {
                    Text(stringResource(R.string.access_owner_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRemove = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** One channel, folded to its name and state until the details are asked for. */
@Composable
private fun ChannelCard(
    title: String,
    summary: String,
    active: Boolean,
    /** Shown under the header, always: a way out must not hide behind a tap. */
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val positive = if (isSystemInDarkTheme()) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (active) positive else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.access_collapse else R.string.access_expand
                    ),
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            if (action != null) {
                Box(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 4.dp)) { action() }
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    content()
                }
            }
        }
    }
}

/** The three channels against the things the freeze screen can do. */
@Composable
private fun CapabilityTable() {
    // Dhizuku is its own column: it is somebody else's device owner, so it shares suspend
    // and hide with the owner column but not the screen lock, and the difference is the
    // whole reason the column exists.
    val rows = listOf(
        Capability(R.string.freeze_method_stop, root = true, shizuku = true, owner = false),
        Capability(R.string.freeze_method_disable, root = true, shizuku = true, owner = false),
        Capability(R.string.freeze_method_hide, root = true, shizuku = false, owner = true,
            dhizuku = true),
        Capability(R.string.freeze_method_suspend, root = true, shizuku = true, owner = true,
            dhizuku = true),
        Capability(R.string.access_table_background, root = true, shizuku = true, owner = false),
        Capability(R.string.access_table_lock, root = true, shizuku = true, owner = true),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1.6f))
                HeaderCell(R.string.access_root)
                HeaderCell(R.string.access_shizuku)
                HeaderCell(R.string.access_owner)
                HeaderCell(R.string.access_dhizuku)
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            rows.forEachIndexed { index, row ->
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(row.label),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1.6f),
                    )
                    Mark(row.root)
                    Mark(row.shizuku)
                    Mark(row.owner)
                    Mark(row.dhizuku)
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    )
                }
            }
        }
    }
}

private data class Capability(
    @StringRes val label: Int,
    val root: Boolean,
    val shizuku: Boolean,
    val owner: Boolean,
    /** Somebody else's device owner, sharing package access only. */
    val dhizuku: Boolean = false,
)

@Composable
private fun RowScope.HeaderCell(@StringRes label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(0.8f),
    )
}

@Composable
private fun RowScope.Mark(ok: Boolean) {
    val positive = if (isSystemInDarkTheme()) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    Box(Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
        Icon(
            if (ok) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = stringResource(
                if (ok) R.string.access_yes else R.string.access_no
            ),
            tint = if (ok) positive else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun rootSummary(status: FreezeStatus): String = when {
    !status.checked -> stringResource(R.string.access_state_checking)
    !status.rootGranted -> stringResource(R.string.access_summary_not_granted)
    else -> listOfNotNull(
        when (status.rootProvider) {
            RootProvider.MAGISK -> "Magisk"
            RootProvider.KERNELSU -> "KernelSU"
            RootProvider.APATCH -> "APatch"
            RootProvider.SUPERSU -> "SuperSU"
            RootProvider.OTHER -> "su"
            RootProvider.NONE -> "su"
        },
        status.rootVersion,
    ).joinToString(" ")
}

@Composable
private fun shizukuSummary(status: FreezeStatus): String = when {
    !status.checked -> stringResource(R.string.access_state_checking)
    !status.shizukuInstalled -> stringResource(R.string.freeze_shizuku_not_installed)
    !status.shizukuRunning -> stringResource(R.string.freeze_shizuku_not_running)
    !status.shizukuAuthorized -> stringResource(R.string.freeze_shizuku_denied)
    status.shizukuManagerVersion != null ->
        stringResource(R.string.freeze_shizuku_ready_version, status.shizukuManagerVersion)
    else -> stringResource(R.string.freeze_shizuku_ready)
}

@Composable
private fun dhizukuSummary(status: FreezeStatus): String = when {
    !status.checked -> stringResource(R.string.access_state_checking)
    status.dhizukuDelegated -> stringResource(R.string.access_summary_ready)
    !status.dhizukuInstalled -> stringResource(R.string.access_summary_not_installed)
    !status.dhizukuPermission -> stringResource(R.string.access_summary_not_granted)
    else -> stringResource(R.string.access_summary_not_set)
}

@Composable
private fun ownerSummary(status: FreezeStatus): String = when {
    !status.checked -> stringResource(R.string.access_state_checking)
    status.deviceOwner -> stringResource(R.string.access_owner_active)
    else -> stringResource(R.string.access_summary_not_set)
}

@Composable
private fun Divider() {
    HorizontalDivider(
        Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun StatusRow(label: String, ok: Boolean, known: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (!known) {
                stringResource(R.string.access_unknown)
            } else {
                stringResource(if (ok) R.string.access_yes else R.string.access_no)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (known && ok) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun Step(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}
