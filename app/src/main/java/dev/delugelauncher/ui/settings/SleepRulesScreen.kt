// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import dev.delugelauncher.ui.common.joinMiddleDot

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.FavoriteRuleHeadset
import dev.delugelauncher.data.FavoriteRuleWifi
import dev.delugelauncher.data.FreezeRunLog
import dev.delugelauncher.data.SleepAction
import dev.delugelauncher.data.SleepRule
import java.util.Locale

/**
 * The sleep rules: what the freeze list does while the phone is on a network, in a time
 * window, on headphones, charging or low on battery.
 *
 * One list, one editor per rule. The scope is always the whole freeze list — picking apps per
 * rule turns a small automation into a matrix nobody finishes filling in, and the freeze list
 * is already the set of apps the owner chose to manage.
 */
@Composable
fun SleepRulesScreen(
    rules: List<SleepRule>,
    runLog: FreezeRunLog?,
    /** The most rules the store keeps; the add button refuses past it. */
    ruleLimit: Int,
    /** How many apps the rules act on, for the empty-state line. */
    listedCount: Int,
    currentSsid: String?,
    locationGranted: Boolean,
    /** Whether root or Shizuku answered; without one a rule can be written but not applied. */
    hasAccess: Boolean,
    /** The freeze list, in the order its screen shows it, as (key, label). */
    listedApps: List<Pair<String, String>>,
    openLimits: Map<String, Int>,
    /** Apps on no list that have not been opened in a while, as (key, label). */
    unusedApps: List<Pair<String, String>>,
    onRequestLocation: () -> Unit,
    onSave: (SleepRule) -> Unit,
    onDelete: (String) -> Unit,
    onUndoRun: (FreezeRunLog) -> Unit,
    onSetLimit: (String, Int) -> Unit,
    onUnusedAdd: (String) -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    // The id, not the rule, so the draft survives rotation: a saved SleepRule object would
    // have to be parcelled, and the rule itself is already in the list this screen was given.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf<SleepRule?>(null) }
    val editing = editingId?.let { id -> rules.firstOrNull { it.id == id } }
    val atLimit = rules.size >= ruleLimit
    // A stepper tapped three times quickly used to write "stored + 1" each time and end at
    // one; the local value compounds until the store catches up.
    var liveLimits by remember { mutableStateOf(emptyMap<String, Int>()) }
    LaunchedEffect(openLimits) {
        liveLimits = liveLimits.filterKeys { key -> (openLimits[key] ?: 0) != liveLimits[key] }
    }

    if (creating || editing != null) {
        // BACK closes the editor, matching the arrow in its bar, instead of popping the whole
        // rules screen and losing the draft with no warning.
        BackHandler {
            editingId = null
            creating = false
        }
        SleepRuleEditor(
            initial = editing ?: SleepRule(id = ""),
            currentSsid = currentSsid,
            locationGranted = locationGranted,
            onRequestLocation = onRequestLocation,
            onSave = { rule ->
                onSave(
                    if (rule.id.isBlank()) {
                        rule.copy(id = "s" + System.currentTimeMillis().toString(36))
                    } else {
                        rule
                    }
                )
                editingId = null
                creating = false
            },
            onCancel = {
                editingId = null
                creating = false
            },
        )
        return
    }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sleep_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { if (!atLimit) creating = true }, enabled = !atLimit) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sleep_add))
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
            if (!hasAccess) {
                item(key = "access") {
                    Text(
                        stringResource(R.string.sleep_needs_access),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            if (runLog != null) {
                item(key = "run") {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.sleep_run_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(
                                    R.string.sleep_run_line,
                                    runLog.frozen.size,
                                    runLog.thawed.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (runLog.frozen.isNotEmpty()) {
                                TextButton(onClick = { onUndoRun(runLog) }) {
                                    Text(stringResource(R.string.sleep_undo))
                                }
                            }
                        }
                    }
                }
            }
            if (listedCount == 0) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.sleep_empty_list),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            if (atLimit) {
                item(key = "limit") {
                    Text(
                        stringResource(R.string.sleep_rules_full, ruleLimit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            if (rules.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.sleep_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            items(rules, key = { it.id }) { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingId = rule.id }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            rule.name.ifBlank { stringResource(R.string.sleep_untitled) },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            summary(rule),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { on -> onSave(rule.copy(enabled = on)) },
                    )
                    IconButton(onClick = { confirmingDelete = rule }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.sleep_delete))
                    }
                }
            }
            if (listedApps.isNotEmpty()) {
                item(key = "limits_title") {
                    ListSectionLabel(stringResource(R.string.sleep_limits_title))
                    Text(
                        stringResource(R.string.sleep_limits_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                items(listedApps, key = { "limit:" + it.first }) { (key, label) ->
                    val limit = liveLimits[key] ?: (openLimits[key] ?: 0)
                    fun setLimit(next: Int) {
                        val value = next.coerceAtLeast(0)
                        liveLimits = liveLimits + (key to value)
                        onSetLimit(key, value)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        val decrease = stringResource(R.string.sleep_limit_decrease, label)
                        TextButton(
                            onClick = { setLimit(limit - 1) },
                            modifier = Modifier.semantics { contentDescription = decrease },
                        ) {
                            Text(stringResource(R.string.sleep_limit_minus))
                        }
                        Text(
                            if (limit == 0) stringResource(R.string.sleep_limit_off) else limit.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val increase = stringResource(R.string.sleep_limit_increase, label)
                        TextButton(
                            onClick = { setLimit(limit + 1) },
                            modifier = Modifier.semantics { contentDescription = increase },
                        ) {
                            Text(stringResource(R.string.sleep_limit_plus))
                        }
                    }
                }
            }
            if (unusedApps.isNotEmpty()) {
                item(key = "unused_title") {
                    ListSectionLabel(stringResource(R.string.sleep_unused_title))
                    Text(
                        stringResource(R.string.sleep_unused_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                items(unusedApps, key = { "unused:" + it.first }) { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onUnusedAdd(key) }) {
                            Text(stringResource(R.string.sleep_unused_add))
                        }
                    }
                }
            }
        }
    }

    val doomed = confirmingDelete
    if (doomed != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text(stringResource(R.string.sleep_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.sleep_delete_body,
                        doomed.name.ifBlank { stringResource(R.string.sleep_untitled) },
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = null
                    onDelete(doomed.id)
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepRuleEditor(
    initial: SleepRule,
    currentSsid: String?,
    locationGranted: Boolean,
    onRequestLocation: () -> Unit,
    onSave: (SleepRule) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val surface = MaterialTheme.colorScheme.surface
    // Saveable, so rotating the phone while the editor is open keeps the draft. Keyed on the
    // rule id, so opening another rule starts from that rule rather than the previous draft.
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var action by rememberSaveable(initial.id) { mutableStateOf(initial.action) }
    var wifi by rememberSaveable(initial.id) { mutableStateOf(initial.wifi) }
    var wifiText by rememberSaveable(initial.id) {
        mutableStateOf(initial.wifiNames.sorted().joinToString(", "))
    }
    var headset by rememberSaveable(initial.id) { mutableStateOf(initial.headset) }
    var useTime by rememberSaveable(initial.id) {
        mutableStateOf(initial.startMinute in 0..1439 && initial.endMinute in 0..1439)
    }
    var start by rememberSaveable(initial.id) {
        mutableIntStateOf(initial.startMinute.takeIf { it in 0..1439 } ?: (23 * 60))
    }
    var end by rememberSaveable(initial.id) {
        mutableIntStateOf(initial.endMinute.takeIf { it in 0..1439 } ?: (7 * 60))
    }
    var charging by rememberSaveable(initial.id) { mutableStateOf(initial.charging) }
    var battery by rememberSaveable(initial.id) { mutableIntStateOf(initial.batteryBelow) }

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            current / 60,
            current % 60,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    fun draft() = SleepRule(
        id = initial.id,
        name = name.trim(),
        enabled = initial.enabled,
        action = action,
        tokens = initial.tokens,
        wifi = wifi,
        wifiNames = wifiText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(16).toSet(),
        headset = headset,
        startMinute = if (useTime) start else -1,
        endMinute = if (useTime) end else -1,
        charging = charging,
        batteryBelow = battery,
    )

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (initial.id.isBlank()) R.string.sleep_new else R.string.sleep_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(draft()) }, enabled = draft().conditions > 0) {
                        Text(stringResource(R.string.action_save))
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
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.rules_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
            }
            item {
                ListSectionLabel(stringResource(R.string.sleep_action_label))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledChip(stringResource(R.string.sleep_action_freeze), action == SleepAction.FREEZE) {
                        action = SleepAction.FREEZE
                    }
                    FilledChip(stringResource(R.string.sleep_action_thaw), action == SleepAction.THAW) {
                        action = SleepAction.THAW
                    }
                }
            }
            item {
                ListSectionLabel(stringResource(R.string.rules_conditions_label))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledChip(stringResource(R.string.rules_headset_any), headset == FavoriteRuleHeadset.ANY) {
                        headset = FavoriteRuleHeadset.ANY
                    }
                    FilledChip(
                        stringResource(R.string.rules_headset_connected),
                        headset == FavoriteRuleHeadset.CONNECTED,
                    ) { headset = FavoriteRuleHeadset.CONNECTED }
                    FilledChip(
                        stringResource(R.string.rules_headset_disconnected),
                        headset == FavoriteRuleHeadset.DISCONNECTED,
                    ) { headset = FavoriteRuleHeadset.DISCONNECTED }
                }
            }
            item {
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.rules_wifi),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledChip(stringResource(R.string.rules_wifi_any), wifi == FavoriteRuleWifi.ANY) {
                        wifi = FavoriteRuleWifi.ANY
                    }
                    FilledChip(stringResource(R.string.rules_wifi_on), wifi == FavoriteRuleWifi.ON) {
                        wifi = FavoriteRuleWifi.ON
                    }
                    FilledChip(stringResource(R.string.rules_wifi_off), wifi == FavoriteRuleWifi.OFF) {
                        wifi = FavoriteRuleWifi.OFF
                    }
                }
                if (wifi != FavoriteRuleWifi.ANY) {
                    OutlinedTextField(
                        value = wifiText,
                        onValueChange = { wifiText = it.take(400) },
                        label = { Text(stringResource(R.string.rules_wifi_names)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    if (!locationGranted) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.rules_wifi_permission),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRequestLocation) {
                                Text(stringResource(R.string.rules_grant))
                            }
                        }
                    } else if (currentSsid != null && currentSsid !in wifiText) {
                        TextButton(
                            onClick = {
                                wifiText = wifiText.trim().let { if (it.isEmpty()) currentSsid else "$it, $currentSsid" }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text(stringResource(R.string.rules_wifi_current, currentSsid))
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.size(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.rules_time), Modifier.weight(1f))
                    Switch(checked = useTime, onCheckedChange = { useTime = it })
                }
                if (useTime) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { pickTime(start) { start = it } }) {
                            Text(stringResource(R.string.rules_time_from, clock(start)))
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = { pickTime(end) { end = it } }) {
                            Text(stringResource(R.string.rules_time_to, clock(end)))
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.size(8.dp))
                ListSectionLabel(stringResource(R.string.sleep_charging))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledChip(stringResource(R.string.sleep_charging_any), charging == null) { charging = null }
                    FilledChip(stringResource(R.string.sleep_charging_on), charging == true) { charging = true }
                    FilledChip(stringResource(R.string.sleep_charging_off), charging == false) { charging = false }
                }
            }
            item {
                Spacer(Modifier.size(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sleep_battery))
                        Text(
                            if (battery == 0) stringResource(R.string.sleep_battery_off)
                            else stringResource(R.string.sleep_battery_detail, battery),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                }
                Slider(
                    value = battery.toFloat(),
                    onValueChange = { battery = (it / 5).toInt() * 5 },
                    valueRange = 0f..100f,
                    steps = 19,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                Text(
                    stringResource(R.string.sleep_scope_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

private fun clock(minutes: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

@Composable
private fun summary(rule: SleepRule): String {
    val parts = mutableListOf<String>()
    if (rule.action == SleepAction.THAW) parts += stringResource(R.string.sleep_action_thaw)
    when {
        rule.wifi == FavoriteRuleWifi.ON && rule.wifiNames.isNotEmpty() ->
            parts += stringResource(R.string.rules_summary_wifi_on, rule.wifiNames.sorted().joinToString(", "))
        rule.wifi == FavoriteRuleWifi.OFF && rule.wifiNames.isNotEmpty() ->
            parts += stringResource(R.string.rules_summary_wifi_off, rule.wifiNames.sorted().joinToString(", "))
    }
    when (rule.headset) {
        FavoriteRuleHeadset.CONNECTED -> parts += stringResource(R.string.rules_summary_headset)
        FavoriteRuleHeadset.DISCONNECTED -> parts += stringResource(R.string.rules_summary_no_headset)
        FavoriteRuleHeadset.ANY -> Unit
    }
    if (rule.startMinute in 0..1439 && rule.endMinute in 0..1439) {
        parts += "${clock(rule.startMinute)}–${clock(rule.endMinute)}"
    }
    rule.charging?.let {
        parts += stringResource(if (it) R.string.sleep_charging_on else R.string.sleep_charging_off)
    }
    if (rule.batteryBelow > 0) parts += stringResource(R.string.sleep_battery_detail, rule.batteryBelow)
    return if (parts.isEmpty()) stringResource(R.string.sleep_always) else joinMiddleDot(parts)
}
