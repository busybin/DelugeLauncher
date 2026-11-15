// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.freeze

import dev.delugelauncher.ui.common.topBarInsets
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.FreezeAccess
import dev.delugelauncher.data.FreezeChannel
import dev.delugelauncher.data.FreezeMethod
import dev.delugelauncher.data.FreezeSort
import dev.delugelauncher.data.FreezeSourceFilter
import dev.delugelauncher.data.FreezeStateFilter
import dev.delugelauncher.data.FreezeStatus
import dev.delugelauncher.data.Pinyin
import dev.delugelauncher.ui.common.FreezePinned
import dev.delugelauncher.ui.common.freezeMethodLabel
import kotlinx.coroutines.launch

/** A row whose switch was turned on before a default method existed. */
private sealed interface PendingAdd {
    data class One(val app: AppInfo) : PendingAdd
    data class Many(val apps: List<AppInfo>) : PendingAdd
}

/** Delays the automatic freeze can be set to, in seconds. */
private val AUTO_DELAYS = listOf(0, 30, 60, 300, 900, 1800)

/**
 * The freeze list, laid out the way the access page is: a state card, one card per group,
 * then the rows themselves as cards.
 *
 * Every query, filter and count lives in [FreezeUi]; this file turns state into rows and
 * rows into taps. Choosing a method puts an app on the list, the switch is the only thing
 * that freezes now, and a permanent row is skipped by every bulk lift except the two the
 * user explicitly aimed at it.
 */
@Composable
fun FreezeAppsScreen(
    allApps: List<AppInfo>,
    freezeMethods: Map<String, FreezeMethod>,
    freezeApplied: Set<String>,
    freezePermanent: Set<String> = emptySet(),
    freezePinned: Set<String> = emptySet(),
    lockedApps: Set<String> = emptySet(),
    protectedPackages: Set<String> = emptySet(),
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    status: FreezeStatus,
    systemAppsEnabled: Boolean,
    sourceFilter: FreezeSourceFilter,
    stateFilter: FreezeStateFilter,
    sort: FreezeSort,
    query: String,
    channels: Map<FreezeMethod, FreezeChannel> = emptyMap(),
    defaultMethod: FreezeMethod? = null,
    autoEnabled: Boolean,
    autoDelaySeconds: Int,
    unfreezeOnUnlock: Boolean,
    autoSkipCharging: Boolean,
    autoSkipForeground: Boolean,
    autoSkipNotifications: Boolean,
    protectEssentials: Boolean,
    freezeEnabled: Boolean = true,
    onSetFreezeEnabled: (Boolean) -> Unit = {},
    onAddToPlan: (AppInfo, FreezeMethod) -> Unit,
    onAddAndFreeze: (AppInfo, FreezeMethod) -> Unit = { _, _ -> },
    onSetApplied: (AppInfo, Boolean) -> Unit,
    onSetMethod: (AppInfo, FreezeMethod) -> Unit,
    onSetPermanent: (AppInfo, Boolean) -> Unit,
    onFreezeManySelected: (List<AppInfo>) -> Unit = {},
    onUnfreezeManySelected: (List<AppInfo>) -> Unit = {},
    onAddManySelected: (List<AppInfo>, FreezeMethod) -> Unit = { _, _ -> },
    onRemoveManySelected: (List<AppInfo>) -> Unit = {},
    onSetPermanentMany: (List<AppInfo>, Boolean) -> Unit = { _, _ -> },
    onSetPinned: ((AppInfo, Boolean) -> Unit)? = null,
    onRemoveFromPlan: (AppInfo) -> Unit,
    onFreezeAll: () -> Unit,
    onUnfreezeAll: () -> Unit,
    onSetAutoEnabled: (Boolean) -> Unit,
    onSetAutoDelay: (Int) -> Unit,
    onSetUnfreezeOnUnlock: (Boolean) -> Unit,
    onSetAutoSkipCharging: (Boolean) -> Unit,
    onSetAutoSkipForeground: (Boolean) -> Unit,
    onSetAutoSkipNotifications: (Boolean) -> Unit,
    onSetProtectEssentials: (Boolean) -> Unit,
    onSetSystemAppsEnabled: (Boolean) -> Unit,
    onExportPlan: (Uri) -> Unit,
    onImportPlan: (Uri) -> Unit,
    onSetSourceFilter: (FreezeSourceFilter) -> Unit,
    onSetStateFilter: (FreezeStateFilter) -> Unit,
    onSetSort: (FreezeSort) -> Unit,
    onSetQuery: (String) -> Unit,
    onSetChannel: (FreezeMethod, FreezeChannel) -> Unit,
    onSetDefaultMethod: (FreezeMethod?) -> Unit = {},
    onNavigateAccess: () -> Unit,
    onNavigateMethods: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) onExportPlan(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImportPlan(uri) }

    var menu by remember { mutableStateOf(false) }
    var autoDialog by rememberSaveable { mutableStateOf(false) }
    var disablingFreeze by rememberSaveable { mutableStateOf(false) }
    var confirmingFreezeAll by rememberSaveable { mutableStateOf(false) }
    var confirmingUnfreezeAll by rememberSaveable { mutableStateOf(false) }
    var confirmingSystemOn by rememberSaveable { mutableStateOf(false) }
    var confirmingSystemOff by rememberSaveable { mutableStateOf(false) }
    var systemBlocked by rememberSaveable { mutableStateOf(false) }
    var multi by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var sheetKey by remember { mutableStateOf<String?>(null) }
    var stopSwitch by remember { mutableStateOf<AppInfo?>(null) }
    var pendingAdd by remember { mutableStateOf<PendingAdd?>(null) }
    var channelsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    val workSuffix = stringResource(R.string.app_work_suffix)
    val pinyin = remember { Pinyin.ordering() }
    val rows = remember(
        allApps, freezeMethods, freezeApplied, freezePermanent, lockedApps,
        nameOverrides, protectedPackages, workSuffix, pinyin,
    ) {
        allApps.mapNotNull { app ->
            val listed = freezeMethods[app.freezeKey] != null
            if (app.kind != EntryKind.APP && !(app.kind == EntryKind.PRIVATE_SPACE && listed)) {
                return@mapNotNull null
            }
            val display = nameOverrides[app.key] ?: (app.label + if (app.work) workSuffix else "")
            FreezeAppRow(
                key = app.freezeKey,
                label = display,
                latinName = Pinyin.latin(display),
                method = freezeMethods[app.freezeKey],
                frozen = app.freezeKey in freezeApplied,
                permanent = app.freezeKey in freezePermanent,
                system = app.system,
                userSerial = app.userSerial,
                privateSpace = app.kind == EntryKind.PRIVATE_SPACE,
                locked = app.freezeKey in lockedApps,
                protectedRow = app.componentName.packageName in protectedPackages,
                installedAt = app.installedAt,
                updatedAt = app.updatedAt,
            )
        }
    }
    val byKey = remember(allApps) { allApps.associateBy { it.freezeKey } }
    val filters = FreezeFilters(
        query = query,
        source = sourceFilter,
        state = stateFilter,
        sort = sort,
        latin = pinyin,
    )
    val shown = remember(rows, filters) { FreezeUi.visible(rows, filters) }
    val counts = remember(rows, sourceFilter) { FreezeUi.counts(rows, sourceFilter) }
    val totals = remember(rows) { FreezeUi.totalCounts(rows) }
    val sheetRow = sheetKey?.let { key -> rows.firstOrNull { it.key == key } }

    fun leaveMulti() {
        multi = false
        selected = emptySet()
    }

    // A selection made under one view must not survive into another: its rows would be gone
    // from the screen while the bulk bar still counted them.
    LaunchedEffect(query, sourceFilter, stateFilter, sort) {
        if (selected.isNotEmpty()) selected = emptySet()
    }

    val selectedShown = shown.filter { it.key in selected }
    val listable = selectedShown.count { !it.listed && !it.locked && !it.protectedRow }
    val removable = selectedShown.count { it.listed }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
            windowInsets = topBarInsets,
                title = {
                    Text(
                        if (multi) {
                            stringResource(R.string.freeze_selected_count, selected.size)
                        } else {
                            stringResource(R.string.settings_freeze_apps)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (multi) leaveMulti() else onBack() }) {
                        Icon(
                            if (multi) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                if (multi) R.string.action_cancel else R.string.action_back
                            ),
                        )
                    }
                },
                actions = {
                    if (!multi) {
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
                                    onClick = { menu = false; confirmingFreezeAll = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.freeze_unfreeze_all)) },
                                    onClick = { menu = false; confirmingUnfreezeAll = true },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.freeze_export)) },
                                    onClick = { menu = false; exportLauncher.launch("deluge-freeze.json") },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.freeze_import)) },
                                    onClick = { menu = false; importLauncher.launch(arrayOf("application/json", "*/*")) },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (multi) {
                FreezeBulkBar(
                    selectedCount = selected.size,
                    allSelected = selected.isNotEmpty() && shown.all { it.key in selected },
                    listable = listable,
                    removable = removable,
                    onSelectAll = { selected = selected + FreezeUi.allKeys(shown) },
                    onAddToList = {
                        val apps = selectedShown
                            .filter { !it.listed && !it.locked && !it.protectedRow }
                            .mapNotNull { byKey[it.key] }
                        if (apps.isNotEmpty()) {
                            val method = defaultMethod
                            if (method == null || !status.supports(method)) {
                                pendingAdd = PendingAdd.Many(apps)
                            } else {
                                onAddManySelected(apps, method)
                            }
                        }
                        leaveMulti()
                    },
                    onRemoveFromList = {
                        val apps = selectedShown.filter { it.listed }.mapNotNull { byKey[it.key] }
                        if (apps.isNotEmpty()) onRemoveManySelected(apps)
                        leaveMulti()
                    },
                    onFreeze = {
                        val listedRows = selectedShown.filter { it.listed }
                        val ignored = selected.count { key -> shown.firstOrNull { it.key == key }?.listed != true }
                        if (listedRows.isNotEmpty()) onFreezeManySelected(listedRows.mapNotNull { byKey[it.key] })
                        if (ignored > 0) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.freeze_bulk_unlisted, ignored),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        leaveMulti()
                    },
                    onUnfreeze = {
                        val listedRows = selectedShown.filter { it.listed }
                        onUnfreezeManySelected(listedRows.mapNotNull { byKey[it.key] })
                        leaveMulti()
                    },
                    onPermanent = { permanent ->
                        val eligible = selectedShown.filter {
                            it.listed && it.method != FreezeMethod.STOP
                        }
                        val ignored = selected.count { key -> shown.firstOrNull { it.key == key }?.listed != true }
                        if (eligible.isNotEmpty()) {
                            onSetPermanentMany(eligible.mapNotNull { byKey[it.key] }, permanent)
                        }
                        if (ignored > 0) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.freeze_bulk_unlisted, ignored),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        leaveMulti()
                    },
                    onClose = { leaveMulti() },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("freeze-list"),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    if (freezeEnabled) {
                        FreezeStateCard(
                            enabled = true,
                            title = stringResource(R.string.freeze_state_title, counts.frozen),
                            body = buildString {
                                append(
                                    stringResource(
                                        R.string.freeze_state_body,
                                        counts.listed,
                                        counts.permanent,
                                    )
                                )
                                if (status.checked && status.access == FreezeAccess.NONE) {
                                    append('\n')
                                    append(stringResource(R.string.access_no_channel))
                                }
                            },
                            counts = counts,
                            onToggle = { if (!it) disablingFreeze = true },
                            onListed = { onSetStateFilter(FreezeStateFilter.ALL) },
                            onFrozen = { onSetStateFilter(FreezeStateFilter.FROZEN) },
                            onPermanent = { onSetStateFilter(FreezeStateFilter.PERMANENT) },
                        )
                    } else {
                        FreezeStateCard(
                            enabled = false,
                            title = stringResource(R.string.freeze_state_off_title),
                            body = stringResource(R.string.freeze_state_off_body),
                            counts = null,
                            onToggle = { if (it) onSetFreezeEnabled(true) },
                        )
                    }
                }
                if (freezeEnabled) {
                    item {
                        FreezeActionGrid(
                            defaultMethod = defaultMethod,
                            onOpenAccess = onNavigateAccess,
                            onOpenMethods = onNavigateMethods,
                            onOpenChannels = { channelsOpen = true },
                            onOpenAuto = { autoDialog = true },
                        )
                    }
                    item {
                        FreezeFilterCard(
                            filters = filters,
                            systemEnabled = systemAppsEnabled,
                            onSource = onSetSourceFilter,
                            onState = onSetStateFilter,
                            onSort = onSetSort,
                            onSystemToggle = { enabled ->
                                if (enabled) {
                                    if (rows.any { it.system && it.permanent }) {
                                        systemBlocked = true
                                    } else {
                                        confirmingSystemOn = true
                                    }
                                } else {
                                    confirmingSystemOff = true
                                }
                            },
                        )
                    }
                }
                if (freezeEnabled && shown.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(
                                if (query.isNotBlank()) R.string.freeze_no_matches else R.string.freeze_list_empty
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                if (freezeEnabled && shown.isNotEmpty()) {
                    stickyHeader(key = "freeze-table-header") {
                        FreezeTableHeader(onOpenMethods = onNavigateMethods)
                    }
                }
                items(shown, key = { it.key }) { row ->
                    val app = byKey[row.key]
                    if (app != null) {
                        FreezeAppRowCard(
                            row = row,
                            app = app,
                            iconSizeDp = iconSizeDp,
                            status = status,
                            multi = multi,
                            selected = row.key in selected,
                            onOpen = { sheetKey = row.key },
                            onToggleFrozen = {
                                if (row.listed) {
                                    onSetApplied(app, !row.frozen)
                                } else {
                                    val method = defaultMethod
                                    if (method == null || !status.supports(method)) {
                                        pendingAdd = PendingAdd.One(app)
                                    } else {
                                        onAddAndFreeze(app, method)
                                    }
                                }
                            },
                            onPickMethod = { method ->
                                if (!row.listed) {
                                    onAddToPlan(app, method)
                                } else if (method == FreezeMethod.STOP && row.permanent) {
                                    stopSwitch = app
                                } else {
                                    onSetMethod(app, method)
                                }
                            },
                            onTogglePermanent = { onSetPermanent(app, !row.permanent) },
                            onLongPress = {
                                if (!row.locked && !row.protectedRow) {
                                    multi = true
                                    selected = selected + row.key
                                }
                            },
                            onToggleSelected = {
                                selected = if (row.key in selected) {
                                    selected - row.key
                                } else {
                                    selected + row.key
                                }
                            },
                        )
                    }
                }
            }
            if (!multi && freezeEnabled) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                        SmallFloatingActionButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.freeze_scroll_top),
                            )
                        }
                    }
                    SmallFloatingActionButton(onClick = { searchOpen = true }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.freeze_search_hint),
                        )
                    }
                }
            }
        }
    }

    sheetRow?.let { row ->
        val app = byKey[row.key]
        if (app != null) {
            FreezeAppSheet(
                row = row,
                app = app,
                iconSizeDp = iconSizeDp,
                onDismiss = { sheetKey = null },
                onPin = {
                    sheetKey = null
                    if (FreezePinned.request(context, app, row.label)) {
                        onSetPinned?.invoke(app, true)
                    } else {
                        Toast.makeText(context, R.string.freeze_pin_failed, Toast.LENGTH_SHORT).show()
                    }
                },
                onRemove = {
                    sheetKey = null
                    onRemoveFromPlan(app)
                },
                onCompareMethods = {
                    sheetKey = null
                    onNavigateMethods()
                },
            )
        }
    }

    if (disablingFreeze) {
        ConfirmDialog(
            title = stringResource(R.string.freeze_disable_title),
            body = stringResource(R.string.freeze_disable_body),
            confirm = stringResource(R.string.freeze_disable_confirm),
            onConfirm = {
                disablingFreeze = false
                onSetFreezeEnabled(false)
            },
            onDismiss = { disablingFreeze = false },
        )
    }
    if (confirmingFreezeAll) {
        ConfirmDialog(
            title = stringResource(R.string.freeze_freeze_all),
            body = stringResource(R.string.freeze_all_confirm_body, totals.listed),
            onConfirm = { confirmingFreezeAll = false; onFreezeAll() },
            onDismiss = { confirmingFreezeAll = false },
        )
    }
    if (confirmingUnfreezeAll) {
        ConfirmDialog(
            title = stringResource(R.string.freeze_unfreeze_all),
            body = stringResource(R.string.unfreeze_all_confirm_body, totals.frozen),
            onConfirm = { confirmingUnfreezeAll = false; onUnfreezeAll() },
            onDismiss = { confirmingUnfreezeAll = false },
        )
    }
    if (confirmingSystemOn) {
        ConfirmDialog(
            title = stringResource(R.string.freeze_system_enable_title),
            body = stringResource(R.string.freeze_system_enable_body),
            confirm = stringResource(R.string.freeze_system_enable_confirm),
            onConfirm = {
                confirmingSystemOn = false
                onSetSystemAppsEnabled(true)
            },
            onDismiss = { confirmingSystemOn = false },
        )
    }
    if (confirmingSystemOff) {
        ConfirmDialog(
            title = stringResource(R.string.freeze_system_disable_title),
            body = stringResource(R.string.freeze_system_disable_body),
            confirm = stringResource(R.string.freeze_system_disable_confirm),
            onConfirm = {
                confirmingSystemOff = false
                onSetSystemAppsEnabled(false)
                onSetSourceFilter(FreezeSourceFilter.USER)
            },
            onDismiss = { confirmingSystemOff = false },
        )
    }
    if (systemBlocked) {
        AlertDialog(
            onDismissRequest = { systemBlocked = false },
            title = { Text(stringResource(R.string.freeze_system_disable_title)) },
            text = { Text(stringResource(R.string.freeze_system_permanent_blocked)) },
            confirmButton = {
                TextButton(onClick = { systemBlocked = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }
    if (autoDialog) {
        AutoFreezeDialog(
            enabled = autoEnabled,
            delaySeconds = autoDelaySeconds,
            unfreezeOnUnlock = unfreezeOnUnlock,
            skipCharging = autoSkipCharging,
            skipForeground = autoSkipForeground,
            skipNotifications = autoSkipNotifications,
            protectEssentials = protectEssentials,
            onSetEnabled = onSetAutoEnabled,
            onSetDelay = onSetAutoDelay,
            onSetUnfreezeOnUnlock = onSetUnfreezeOnUnlock,
            onSetSkipCharging = onSetAutoSkipCharging,
            onSetSkipForeground = onSetAutoSkipForeground,
            onSetSkipNotifications = onSetAutoSkipNotifications,
            onSetProtectEssentials = onSetProtectEssentials,
            onDismiss = { autoDialog = false },
        )
    }
    stopSwitch?.let { app ->
        ConfirmDialog(
            title = stringResource(R.string.freeze_method_stop),
            body = stringResource(R.string.freeze_method_stop_clears, app.label),
            onConfirm = {
                stopSwitch = null
                onSetMethod(app, FreezeMethod.STOP)
            },
            onDismiss = { stopSwitch = null },
        )
    }
    if (channelsOpen) {
        AlertDialog(
            onDismissRequest = { channelsOpen = false },
            title = { Text(stringResource(R.string.freeze_channels_title)) },
            text = {
                FreezeChannelsPanel(
                    status = status,
                    channels = channels,
                    defaultMethod = defaultMethod,
                    onSetChannel = onSetChannel,
                    onSetDefault = onSetDefaultMethod,
                )
            },
            confirmButton = {
                TextButton(onClick = { channelsOpen = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }
    pendingAdd?.let { pending ->
        val methods = FreezeMethod.entries.filter { status.supports(it) }
        AlertDialog(
            onDismissRequest = { pendingAdd = null },
            title = { Text(stringResource(R.string.freeze_choose_default_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.freeze_choose_default_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (methods.isEmpty()) {
                        Text(
                            text = stringResource(R.string.freeze_needs_access),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    methods.forEach { method ->
                        TextButton(
                            onClick = {
                                pendingAdd = null
                                onSetDefaultMethod(method)
                                when (pending) {
                                    is PendingAdd.One -> onAddAndFreeze(pending.app, method)
                                    is PendingAdd.Many -> onAddManySelected(pending.apps, method)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(freezeMethodLabel(method))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAdd = null
                    onNavigateMethods()
                }) {
                    Text(stringResource(R.string.freeze_choose_default_compare))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAdd = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (searchOpen) {
        var draft by remember { mutableStateOf(query) }
        AlertDialog(
            onDismissRequest = { searchOpen = false },
            title = { Text(stringResource(R.string.freeze_search_hint)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetQuery(draft)
                    searchOpen = false
                }) {
                    Text(stringResource(R.string.action_done))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onSetQuery("")
                    searchOpen = false
                }) {
                    Text(stringResource(R.string.freeze_search_clear))
                }
            },
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm ?: stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AutoFreezeDialog(
    enabled: Boolean,
    delaySeconds: Int,
    unfreezeOnUnlock: Boolean,
    skipCharging: Boolean,
    skipForeground: Boolean,
    skipNotifications: Boolean,
    protectEssentials: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onSetDelay: (Int) -> Unit,
    onSetUnfreezeOnUnlock: (Boolean) -> Unit,
    onSetSkipCharging: (Boolean) -> Unit,
    onSetSkipForeground: (Boolean) -> Unit,
    onSetSkipNotifications: (Boolean) -> Unit,
    onSetProtectEssentials: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.freeze_auto)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ToggleRow(stringResource(R.string.freeze_auto_enable), enabled, onSetEnabled)
                Text(
                    stringResource(R.string.freeze_auto_delay),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                AUTO_DELAYS.forEach { seconds ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = seconds == delaySeconds,
                            onClick = { onSetDelay(seconds) },
                        )
                        Text(
                            text = when {
                                seconds <= 0 -> stringResource(R.string.freeze_auto_delay_immediate)
                                seconds % 60 == 0 ->
                                    stringResource(R.string.freeze_auto_delay_minutes, seconds / 60)
                                else -> stringResource(R.string.freeze_auto_delay_seconds, seconds)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                ToggleRow(
                    stringResource(R.string.freeze_unfreeze_on_unlock),
                    unfreezeOnUnlock,
                    onSetUnfreezeOnUnlock,
                )
                ToggleRow(
                    stringResource(R.string.freeze_auto_skip_charging),
                    skipCharging,
                    onSetSkipCharging,
                )
                ToggleRow(
                    stringResource(R.string.freeze_auto_skip_foreground),
                    skipForeground,
                    onSetSkipForeground,
                )
                ToggleRow(
                    stringResource(R.string.freeze_auto_skip_notifications),
                    skipNotifications,
                    onSetSkipNotifications,
                )
                ToggleRow(
                    stringResource(R.string.freeze_protect),
                    protectEssentials,
                    onSetProtectEssentials,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
