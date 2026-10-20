// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.FreezeAccess
import dev.delugelauncher.data.FreezeListing
import dev.delugelauncher.data.FreezeMethod
import dev.delugelauncher.data.FreezeSort
import dev.delugelauncher.data.FreezeSourceFilter
import dev.delugelauncher.data.FreezeStateFilter
import dev.delugelauncher.data.FreezeStatus
import dev.delugelauncher.data.Pinyin
import dev.delugelauncher.data.RootProvider
import dev.delugelauncher.ui.common.AppIcon
import dev.delugelauncher.ui.common.DelugeIcons

/** Delays offered for the automatic freeze, all in seconds. Zero freezes at once. */
private val AUTO_DELAYS = listOf(0, 30, 60, 300, 900, 1800)

/**
 * Builds a freeze list, one method per tab.
 *
 * Picking an app here only puts it on the list; nothing is frozen until a trigger runs — the
 * Freeze all action, the corner button, or the automatic freeze. That is what makes the list
 * worth keeping: an app unfrozen for the moment stays on it and is put back by the next
 * trigger, and every row says out loud which of the two states it is in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FreezeAppsScreen(
    allApps: List<AppInfo>,
    freezeMethods: Map<String, FreezeMethod>,
    /** Packages on the list that are actually frozen right now. */
    freezeApplied: Set<String>,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    status: FreezeStatus,
    systemAppsEnabled: Boolean,
    sourceFilter: FreezeSourceFilter,
    stateFilter: FreezeStateFilter,
    sort: FreezeSort,
    query: String,
    autoEnabled: Boolean,
    autoDelaySeconds: Int,
    unfreezeOnUnlock: Boolean,
    autoSkipCharging: Boolean,
    autoSkipForeground: Boolean,
    autoSkipNotifications: Boolean,
    onRefreshAccess: () -> Unit,
    onOpenShizuku: () -> Unit,
    onAddToPlan: (AppInfo, FreezeMethod) -> Unit,
    onAddManyToPlan: (List<AppInfo>, FreezeMethod) -> Unit,
    /** The apps joining and leaving the current method's list, in one inverted selection. */
    onInvertSelection: (List<AppInfo>, List<AppInfo>, FreezeMethod) -> Unit,
    onSetApplied: (AppInfo, Boolean) -> Unit,
    onRemoveFromPlan: (AppInfo) -> Unit,
    onFreezeAll: () -> Unit,
    onUnfreezeAll: () -> Unit,
    onSetAutoEnabled: (Boolean) -> Unit,
    onSetAutoDelay: (Int) -> Unit,
    onSetUnfreezeOnUnlock: (Boolean) -> Unit,
    onSetAutoSkipCharging: (Boolean) -> Unit,
    onSetAutoSkipForeground: (Boolean) -> Unit,
    onSetAutoSkipNotifications: (Boolean) -> Unit,
    onSetSystemAppsEnabled: (Boolean) -> Unit,
    onSetSourceFilter: (FreezeSourceFilter) -> Unit,
    onSetStateFilter: (FreezeStateFilter) -> Unit,
    onSetSort: (FreezeSort) -> Unit,
    onSetQuery: (String) -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val methods = FreezeMethod.entries
    val method = methods[tabIndex.coerceIn(methods.indices)]
    val access = status.access
    val canOperate = access != FreezeAccess.NONE && (method != FreezeMethod.HIDE || status.rootGranted)
    val pinyin = remember { Pinyin.ordering() }
    // Turning system apps off while their filter is selected would leave an empty screen, so
    // the filter falls back to user apps until they are turned on again.
    val effectiveSource = if (systemAppsEnabled) sourceFilter else FreezeSourceFilter.USER
    val apps = remember(allApps, systemAppsEnabled, effectiveSource) {
        val wantsSystem = effectiveSource == FreezeSourceFilter.SYSTEM
        allApps.filter {
            it.kind == EntryKind.APP && it.userSerial == 0L &&
                (systemAppsEnabled || !it.system) && it.system == wantsSystem
        }
    }
    val name = { app: AppInfo -> nameOverrides[app.key] ?: app.label }
    val term = query.trim()
    val matchesQuery = { app: AppInfo ->
        term.isEmpty() ||
            name(app).contains(term, ignoreCase = true) ||
            Pinyin.latin(name(app)).contains(term, ignoreCase = true)
    }
    // What is frozen overall, whichever method holds it, so a state filter means the same
    // thing on every tab.
    val frozenNow = { app: AppInfo -> app.packageName in freezeApplied }
    val shown = remember(
        apps, freezeMethods, method, stateFilter, term, sort, freezeApplied, nameOverrides, pinyin,
    ) {
        val matching = apps.filter(matchesQuery)
        val listed = when (stateFilter) {
            FreezeStateFilter.ALL -> matching.filter { freezeMethods[it.packageName] == method }
            FreezeStateFilter.FROZEN -> matching.filter {
                freezeMethods[it.packageName] == method && frozenNow(it)
            }
            FreezeStateFilter.UNFROZEN -> matching.filter {
                freezeMethods[it.packageName] == method && !frozenNow(it)
            }
        }
        val rest = when (stateFilter) {
            FreezeStateFilter.ALL -> matching.filterNot { freezeMethods[it.packageName] == method }
            FreezeStateFilter.FROZEN -> emptyList()
            FreezeStateFilter.UNFROZEN -> matching.filterNot {
                freezeMethods[it.packageName] == method || frozenNow(it)
            }
        }
        FreezeListing.sorted(listed, sort, name, pinyin) to
            FreezeListing.sorted(rest, sort, name, pinyin)
    }
    val listed = shown.first
    val rest = shown.second
    val filtered = term.isNotEmpty()
    // What this tab may still take in. An app on another method's list stays visible so a
    // search still finds it, but this switch would move it — and moving the method of an app
    // that is frozen right now leaves the record naming a method that did not freeze it.
    // It is unchecked in the tab that owns it instead.
    val unlistedInRest = remember(rest, freezeMethods) {
        rest.filter { freezeMethods[it.packageName] == null }
    }
    // Inverting only touches apps this tab could hold: unlisted ones join, listed ones leave.
    // An app on another method's list is left alone rather than silently moved.
    val assignable = remember(apps, freezeMethods, method, term, nameOverrides) {
        apps.filter(matchesQuery).filter {
            freezeMethods[it.packageName] == null || freezeMethods[it.packageName] == method
        }
    }
    val invertJoining = assignable.filter { freezeMethods[it.packageName] == null }
    val invertLeaving = assignable.filter { freezeMethods[it.packageName] == method }

    var menu by remember { mutableStateOf(false) }
    var confirmingSelectAll by remember { mutableStateOf(false) }
    var confirmingInvert by remember { mutableStateOf(false) }
    var autoDialog by remember { mutableStateOf(false) }
    var enablingSystemApps by remember { mutableStateOf(false) }
    var disablingSystemApps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onRefreshAccess() }

    // Shizuku tells the app when permission was granted or refused; without this the banner
    // stays on "needs Shizuku" until the screen is opened again.
    DisposableEffect(Unit) {
        val listener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, _ ->
            onRefreshAccess()
        }
        runCatching { rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener) }
        onDispose {
            runCatching { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(listener) }
        }
    }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_freeze_apps)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.settings_more),
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.freeze_freeze_all)) },
                                leadingIcon = { Icon(DelugeIcons.Freeze, contentDescription = null) },
                                enabled = freezeMethods.isNotEmpty() && access != FreezeAccess.NONE,
                                onClick = { menu = false; onFreezeAll() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.freeze_unfreeze_all)) },
                                leadingIcon = { Icon(DelugeIcons.Thaw, contentDescription = null) },
                                enabled = freezeMethods.isNotEmpty() && access != FreezeAccess.NONE,
                                onClick = { menu = false; onUnfreezeAll() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.freeze_auto)) },
                                onClick = { menu = false; autoDialog = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            AccessBanner(
                status = status,
                onRequestShizuku = {
                    if (runCatching { rikka.shizuku.Shizuku.pingBinder() }.getOrDefault(false)) {
                        runCatching { rikka.shizuku.Shizuku.requestPermission(0) }
                    } else {
                        onOpenShizuku()
                    }
                },
                onOpenShizuku = onOpenShizuku,
            )

            TabRow(selectedTabIndex = tabIndex) {
                methods.forEachIndexed { index, m ->
                    val count = apps.count { freezeMethods[it.packageName] == m }
                    Tab(
                        selected = index == tabIndex,
                        onClick = { tabIndex = index },
                        text = {
                            // The count keeps its width; a long method name shrinks instead,
                            // so the number is not the part that gets clipped.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    methodName(m),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (count > 0) {
                                    Text(" ($count)", maxLines = 1)
                                }
                            }
                        },
                    )
                }
            }

            MethodBalance(method)

            if (method == FreezeMethod.HIDE && !status.rootGranted) {
                InfoLine(stringResource(R.string.freeze_hide_needs_root))
            }
            if (access == FreezeAccess.NONE) {
                InfoLine(stringResource(R.string.freeze_needs_access))
            }

            SystemAppsRow(
                checked = systemAppsEnabled,
                onCheckedChange = { wanted ->
                    if (wanted) enablingSystemApps = true else disablingSystemApps = true
                },
            )

            FilterControls(
                query = query,
                onQuery = onSetQuery,
                sourceFilter = effectiveSource,
                onSource = onSetSourceFilter,
                stateFilter = stateFilter,
                onState = onSetStateFilter,
                sort = sort,
                onSort = onSetSort,
                systemAppsEnabled = systemAppsEnabled,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { confirmingSelectAll = true },
                    enabled = canOperate && unlistedInRest.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.freeze_select_all))
                }
                FilledTonalButton(
                    onClick = { confirmingInvert = true },
                    enabled = canOperate && (invertJoining.isNotEmpty() || invertLeaving.isNotEmpty()),
                ) {
                    Text(stringResource(R.string.freeze_invert))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.freeze_selected_count, listed.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (listed.isEmpty() && rest.isEmpty()) {
                    item {
                        InfoLine(
                            stringResource(
                                if (filtered) R.string.freeze_no_matches else R.string.freeze_tab_empty
                            )
                        )
                    }
                }
                if (listed.isNotEmpty()) {
                    item { ListSectionLabel(stringResource(R.string.freeze_list_section, listed.size)) }
                    items(listed, key = { "listed:" + it.key }) { app ->
                        FreezeRow(
                            app = app,
                            label = name(app),
                            iconSizeDp = iconSizeDp,
                            checked = true,
                            enabled = canOperate,
                            subtitle = stringResource(
                                if (app.packageName in freezeApplied) R.string.freeze_state_frozen
                                else R.string.freeze_state_pending
                            ),
                            onToggle = { onRemoveFromPlan(app) },
                            onSetApplied = { freeze -> onSetApplied(app, freeze) },
                            onRemove = { onRemoveFromPlan(app) },
                        )
                    }
                }
                if (rest.isNotEmpty()) {
                    item { ListSectionLabel(stringResource(R.string.freeze_add_section)) }
                    items(rest, key = { it.key }) { app ->
                        val other = freezeMethods[app.packageName]
                        val elsewhere = other != null && other != method
                        FreezeRow(
                            app = app,
                            label = name(app),
                            iconSizeDp = iconSizeDp,
                            checked = false,
                            enabled = canOperate && !elsewhere,
                            subtitle = other?.takeIf { it != method }
                                ?.let { stringResource(R.string.freeze_listed_with, methodName(it)) },
                            onToggle = { onAddToPlan(app, method) },
                            onSetApplied = null,
                            onRemove = null,
                        )
                    }
                }
                if (systemAppsEnabled) {
                    item {
                        Text(
                            stringResource(R.string.freeze_system_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmingSelectAll) {
        val candidates = unlistedInRest
        AlertDialog(
            onDismissRequest = { confirmingSelectAll = false },
            title = { Text(stringResource(R.string.freeze_select_all_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.freeze_select_all_body,
                        candidates.size,
                        methodName(method),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingSelectAll = false
                    onAddManyToPlan(candidates, method)
                }) {
                    Text(stringResource(R.string.freeze_select_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSelectAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmingInvert) {
        AlertDialog(
            onDismissRequest = { confirmingInvert = false },
            title = { Text(stringResource(R.string.freeze_invert_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.freeze_invert_body,
                        invertJoining.size,
                        invertLeaving.size,
                        methodName(method),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingInvert = false
                    onInvertSelection(invertJoining, invertLeaving, method)
                }) {
                    Text(stringResource(R.string.freeze_invert))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingInvert = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (autoDialog) {
        AlertDialog(
            onDismissRequest = { autoDialog = false },
            title = { Text(stringResource(R.string.freeze_auto)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.freeze_auto_enable),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.freeze_auto_enable_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Switch(checked = autoEnabled, onCheckedChange = onSetAutoEnabled)
                    }
                    Text(
                        stringResource(R.string.freeze_auto_delay),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    AUTO_DELAYS.forEach { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable { onSetAutoDelay(seconds) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = seconds == autoDelaySeconds, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(delayLabel(seconds), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.freeze_unfreeze_on_unlock),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = unfreezeOnUnlock, onCheckedChange = onSetUnfreezeOnUnlock)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SwitchLine(
                        label = stringResource(R.string.freeze_auto_skip_charging),
                        detail = null,
                        checked = autoSkipCharging,
                        onCheckedChange = onSetAutoSkipCharging,
                    )
                    SwitchLine(
                        label = stringResource(R.string.freeze_auto_skip_foreground),
                        detail = stringResource(R.string.freeze_auto_skip_foreground_detail),
                        checked = autoSkipForeground,
                        onCheckedChange = onSetAutoSkipForeground,
                    )
                    SwitchLine(
                        label = stringResource(R.string.freeze_auto_skip_notifications),
                        detail = stringResource(R.string.freeze_auto_skip_notifications_detail),
                        checked = autoSkipNotifications,
                        onCheckedChange = onSetAutoSkipNotifications,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { autoDialog = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }

    if (enablingSystemApps) {
        AlertDialog(
            onDismissRequest = { enablingSystemApps = false },
            title = { Text(stringResource(R.string.freeze_system_enable_title)) },
            text = { Text(stringResource(R.string.freeze_system_enable_body)) },
            confirmButton = {
                TextButton(onClick = {
                    enablingSystemApps = false
                    onSetSystemAppsEnabled(true)
                }) {
                    Text(stringResource(R.string.freeze_system_enable_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { enablingSystemApps = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (disablingSystemApps) {
        AlertDialog(
            onDismissRequest = { disablingSystemApps = false },
            title = { Text(stringResource(R.string.freeze_system_disable_title)) },
            text = { Text(stringResource(R.string.freeze_system_disable_body)) },
            confirmButton = {
                TextButton(onClick = {
                    disablingSystemApps = false
                    onSetSystemAppsEnabled(false)
                }) {
                    Text(stringResource(R.string.freeze_system_disable_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { disablingSystemApps = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun delayLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.freeze_auto_delay_immediate)
    seconds % 60 == 0 -> stringResource(R.string.freeze_auto_delay_minutes, seconds / 60)
    else -> stringResource(R.string.freeze_auto_delay_seconds, seconds)
}

@Composable
private fun AccessBanner(
    status: FreezeStatus,
    onRequestShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Surface(color = surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(rootStatusText(status), style = MaterialTheme.typography.bodyMedium)
            Text(shizukuStatusText(status), style = MaterialTheme.typography.bodyMedium)
            // Shizuku is the recommended route: no root needed, and it is the faster of the
            // two. Root still answers every method, so this is a nudge rather than a blocker.
            if (!status.shizukuAuthorized) {
                Text(
                    stringResource(R.string.freeze_recommend_shizuku),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            if (status.access != FreezeAccess.NONE && !status.rootGranted) {
                Text(
                    stringResource(R.string.freeze_hide_needs_root),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            if (status.access == FreezeAccess.NONE) {
                Text(
                    stringResource(R.string.freeze_needs_access),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onRequestShizuku) {
                        Text(
                            stringResource(
                                if (status.shizukuInstalled) R.string.freeze_grant_shizuku
                                else R.string.freeze_open_shizuku
                            )
                        )
                    }
                    if (status.shizukuInstalled) {
                        TextButton(onClick = onOpenShizuku) {
                            Text(stringResource(R.string.freeze_open_shizuku))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rootStatusText(status: FreezeStatus): String {
    if (status.rootProvider == RootProvider.NONE) {
        return stringResource(R.string.freeze_root_none)
    }
    val provider = when (status.rootProvider) {
        RootProvider.MAGISK -> "Magisk"
        RootProvider.KERNELSU -> "KernelSU"
        RootProvider.APATCH -> "APatch"
        RootProvider.SUPERSU -> "SuperSU"
        else -> stringResource(R.string.freeze_root_unknown_provider)
    }
    val version = status.rootVersion?.takeIf { it.isNotBlank() }
    val label = listOfNotNull(provider, version).joinToString(" ")
    return stringResource(R.string.freeze_root_status, label)
}

@Composable
private fun shizukuStatusText(status: FreezeStatus): String {
    val detail = when {
        !status.shizukuInstalled -> stringResource(R.string.freeze_shizuku_not_installed)
        !status.shizukuRunning -> stringResource(R.string.freeze_shizuku_not_running)
        !status.shizukuAuthorized -> stringResource(R.string.freeze_shizuku_denied)
        status.shizukuVersion > 0 ->
            stringResource(R.string.freeze_shizuku_ready_version, status.shizukuVersion)
        else -> stringResource(R.string.freeze_shizuku_ready)
    }
    return stringResource(R.string.freeze_shizuku_status, detail)
}

/** The one-line trade-off of the tab in front, so choosing a method is not a guess. */
@Composable
private fun MethodBalance(method: FreezeMethod) {
    val pros = when (method) {
        FreezeMethod.DISABLE -> R.string.freeze_disable_pros
        FreezeMethod.HIDE -> R.string.freeze_hide_pros
        FreezeMethod.SUSPEND -> R.string.freeze_suspend_pros
        FreezeMethod.STOP -> R.string.freeze_stop_pros
    }
    val cons = when (method) {
        FreezeMethod.DISABLE -> R.string.freeze_disable_cons
        FreezeMethod.HIDE -> R.string.freeze_hide_cons
        FreezeMethod.SUSPEND -> R.string.freeze_suspend_cons
        FreezeMethod.STOP -> R.string.freeze_stop_cons
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.freeze_method_pros, stringResource(pros)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.freeze_method_cons, stringResource(cons)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

/** Search, the two filters and the order, kept together above the list they narrow. */
@Composable
private fun FilterControls(
    query: String,
    onQuery: (String) -> Unit,
    sourceFilter: FreezeSourceFilter,
    onSource: (FreezeSourceFilter) -> Unit,
    stateFilter: FreezeStateFilter,
    onState: (FreezeStateFilter) -> Unit,
    sort: FreezeSort,
    onSort: (FreezeSort) -> Unit,
    systemAppsEnabled: Boolean,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.icon_picker_clear_search),
                        )
                    }
                }
            },
            placeholder = { Text(stringResource(R.string.freeze_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            FilledChip(
                label = stringResource(R.string.freeze_filter_user),
                selected = sourceFilter == FreezeSourceFilter.USER,
                onClick = { onSource(FreezeSourceFilter.USER) },
            )
            if (systemAppsEnabled) {
                FilledChip(
                    label = stringResource(R.string.freeze_filter_system),
                    selected = sourceFilter == FreezeSourceFilter.SYSTEM,
                    onClick = { onSource(FreezeSourceFilter.SYSTEM) },
                )
            }
            FilledChip(
                label = stringResource(R.string.freeze_filter_all),
                selected = stateFilter == FreezeStateFilter.ALL,
                onClick = { onState(FreezeStateFilter.ALL) },
            )
            FilledChip(
                label = stringResource(R.string.freeze_filter_frozen),
                selected = stateFilter == FreezeStateFilter.FROZEN,
                onClick = { onState(FreezeStateFilter.FROZEN) },
            )
            FilledChip(
                label = stringResource(R.string.freeze_filter_unfrozen),
                selected = stateFilter == FreezeStateFilter.UNFROZEN,
                onClick = { onState(FreezeStateFilter.UNFROZEN) },
            )
            Box {
                FilledChip(
                    label = sortLabel(sort),
                    selected = false,
                    onClick = { sortMenu = true },
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    FreezeSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(option)) },
                            onClick = { sortMenu = false; onSort(option) },
                        )
                    }
                }
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
    },
)

@Composable
private fun SystemAppsRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.freeze_system_apps),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.freeze_system_apps_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SwitchLine(
    label: String,
    detail: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FreezeRow(
    app: AppInfo,
    label: String,
    iconSizeDp: Int,
    checked: Boolean,
    enabled: Boolean,
    subtitle: String?,
    /** The switch: on the list, or not. Checking adds, unchecking takes it off the list. */
    onToggle: () -> Unit,
    /** Non-null on a listed app: the long-press menu freezes or unfreezes it on the spot. */
    onSetApplied: ((Boolean) -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    var rowMenu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = enabled,
                    onClick = onToggle,
                    onLongClick = onSetApplied?.let { { rowMenu = true } },
                )
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app = app, sizeDp = minOf(iconSizeDp, 44))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        }
        if (onSetApplied != null) {
            // Both directions are always offered, whatever the record says: a state this app
            // read wrongly must never be the reason an app cannot be let out again.
            DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.freeze_menu_freeze_now)) },
                    leadingIcon = { Icon(DelugeIcons.Freeze, contentDescription = null) },
                    onClick = { rowMenu = false; onSetApplied(true) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.freeze_menu_unfreeze)) },
                    leadingIcon = { Icon(DelugeIcons.Thaw, contentDescription = null) },
                    onClick = { rowMenu = false; onSetApplied(false) },
                )
                if (onRemove != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.freeze_menu_remove)) },
                        onClick = { rowMenu = false; onRemove() },
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

/** Tab and subtitle wording for a method; all four name the same action Hail does. */
@Composable
fun methodName(method: FreezeMethod): String = stringResource(
    when (method) {
        FreezeMethod.DISABLE -> R.string.freeze_method_disable
        FreezeMethod.HIDE -> R.string.freeze_method_hide
        FreezeMethod.SUSPEND -> R.string.freeze_method_suspend
        FreezeMethod.STOP -> R.string.freeze_method_stop
    },
)
