// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import dev.delugelauncher.ui.common.topBarInsets
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
    /** Whether this phone has the per-app networking switch at all. */
    networkSupported: Boolean,
    onRefresh: () -> Unit,
    onOpenShizuku: () -> Unit,
    onGetShizuku: () -> Unit,
    onRemoveDeviceOwner: () -> Unit,
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
            windowInsets = topBarInsets,
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
                CapabilityTable(status, networkSupported)
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    CAPABILITY_NOTES.forEach { note ->
                        Text(
                            stringResource(note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
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
                        SectionDivider()
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
                        SectionDivider()
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

/** One numbered note per capability that needs a caveat, in the order the rows appear. */
private val CAPABILITY_NOTES = listOf(
    R.string.access_note_hide,
    R.string.access_note_suspend,
    R.string.access_note_background,
    R.string.access_note_lock,
    R.string.access_note_network,
)

/** The three channels against the things the freeze screen can do. */
@Composable
private fun CapabilityTable(status: FreezeStatus, networkSupported: Boolean) {
    // What this phone actually has, so a column nobody granted reads grey instead of red.
    val root = status.rootGranted
    val shizuku = status.shizukuRunning && status.shizukuAuthorized
    val owner = status.deviceOwner
    val rows = listOf(
        Capability(R.string.freeze_method_stop, root = true, shizuku = true, owner = false),
        Capability(R.string.freeze_method_disable, root = true, shizuku = true, owner = false),
        Capability(R.string.access_table_hide, root = true, shizuku = false, owner = true),
        Capability(R.string.access_table_suspend, root = true, shizuku = true, owner = true),
        Capability(R.string.access_table_background, root = true, shizuku = true, owner = false),
        Capability(R.string.access_table_lock, root = true, shizuku = true, owner = true),
        // The switch lives in the system, not in the channel: on a phone that does not have
        // it, ticking a channel would promise something that never happens there.
        Capability(
            R.string.access_table_network,
            root = networkSupported,
            shizuku = networkSupported,
            owner = false,
        ),
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
                HeaderCell(R.string.access_root, root)
                HeaderCell(R.string.access_shizuku, shizuku)
                HeaderCell(R.string.access_owner, owner)
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
                    Mark(row.root, root)
                    Mark(row.shizuku, shizuku)
                    Mark(row.owner, owner)
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
)

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
private fun ownerSummary(status: FreezeStatus): String = when {
    !status.checked -> stringResource(R.string.access_state_checking)
    status.deviceOwner -> stringResource(R.string.access_owner_active)
    else -> stringResource(R.string.access_summary_not_set)
}
