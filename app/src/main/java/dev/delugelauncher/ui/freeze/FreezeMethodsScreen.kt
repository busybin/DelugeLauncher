// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.freeze

import dev.delugelauncher.ui.common.topBarInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.FreezeMethod
import dev.delugelauncher.ui.common.freezeMethodLabel
import dev.delugelauncher.ui.settings.AccessSurface
import dev.delugelauncher.ui.settings.ChannelCard
import dev.delugelauncher.ui.settings.HeaderCell
import dev.delugelauncher.ui.settings.ListSectionLabel
import dev.delugelauncher.ui.settings.Mark

/**
 * What the four freeze methods actually differ in.
 *
 * The page is a copy of the access page's shape: one table for the mechanical differences,
 * then one fold-out card per method for the long version. Same gutters, same cards, so the
 * two reference screens read as one set.
 */
@Composable
fun FreezeMethodsScreen(onBack: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
            windowInsets = topBarInsets,
                title = { Text(stringResource(R.string.freeze_methods_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                ListSectionLabel(stringResource(R.string.freeze_methods_table_title))
                MethodTable()
                Text(
                    text = stringResource(R.string.freeze_methods_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            item {
                ListSectionLabel(stringResource(R.string.freeze_permanent))
                AccessSurface {
                    Text(
                        text = stringResource(R.string.freeze_permanent_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            items(FreezeMethod.entries.size) { index ->
                val method = FreezeMethod.entries[index]
                ChannelCard(
                    title = freezeMethodLabel(method),
                    summary = needLabel(method),
                ) {
                    Text(
                        text = stringResource(prosOf(method)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Text(
                        text = stringResource(consOf(method)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

private data class MethodRow(val method: FreezeMethod, val reboot: Boolean, val icon: Boolean, val background: Boolean)

private val ROWS = listOf(
    MethodRow(FreezeMethod.STOP, reboot = false, icon = true, background = false),
    MethodRow(FreezeMethod.SUSPEND, reboot = true, icon = true, background = true),
    MethodRow(FreezeMethod.DISABLE, reboot = true, icon = false, background = true),
    MethodRow(FreezeMethod.HIDE, reboot = true, icon = false, background = true),
)

@Composable
private fun MethodTable() {
    AccessSurface {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1.6f))
            HeaderCell(R.string.freeze_table_reboot)
            HeaderCell(R.string.freeze_table_icon)
            HeaderCell(R.string.freeze_table_background)
        }
        HorizontalDivider(
            Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        ROWS.forEachIndexed { index, row ->
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = freezeMethodLabel(row.method),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1.6f),
                )
                Mark(row.reboot)
                Mark(row.icon)
                Mark(row.background)
            }
            if (index != ROWS.lastIndex) {
                HorizontalDivider(
                    Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                )
            }
        }
        Spacer(Modifier.padding(bottom = 6.dp))
    }
}


@Composable
private fun needLabel(method: FreezeMethod): String = stringResource(
    when (method) {
        FreezeMethod.STOP, FreezeMethod.DISABLE -> R.string.freeze_needs_shell
        FreezeMethod.HIDE -> R.string.freeze_needs_root_or_owner
        FreezeMethod.SUSPEND -> R.string.freeze_needs_any
    }
)

private fun prosOf(method: FreezeMethod): Int = when (method) {
    FreezeMethod.STOP -> R.string.freeze_stop_pros
    FreezeMethod.SUSPEND -> R.string.freeze_suspend_pros
    FreezeMethod.DISABLE -> R.string.freeze_disable_pros
    FreezeMethod.HIDE -> R.string.freeze_hide_pros
}

private fun consOf(method: FreezeMethod): Int = when (method) {
    FreezeMethod.STOP -> R.string.freeze_stop_cons
    FreezeMethod.SUSPEND -> R.string.freeze_suspend_cons
    FreezeMethod.DISABLE -> R.string.freeze_disable_cons
    FreezeMethod.HIDE -> R.string.freeze_hide_cons
}
