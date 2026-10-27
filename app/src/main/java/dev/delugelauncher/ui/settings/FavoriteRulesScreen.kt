// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.delugelauncher.R
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.FavoriteRule
import dev.delugelauncher.data.FavoriteRuleAction
import dev.delugelauncher.data.FavoriteRuleHeadset
import dev.delugelauncher.data.FavoriteRuleWifi
import dev.delugelauncher.data.Folder
import dev.delugelauncher.data.folderIdFromToken
import dev.delugelauncher.ui.common.AppIcon
import dev.delugelauncher.ui.common.DelugeIcons
import java.util.Locale

/**
 * Favorites that come and go with the situation: at home, at work, on headphones, at night.
 *
 * A list of rules rather than a matrix of everything at once: each rule is one situation and
 * the favorites it applies to, which is what someone setting this up is actually thinking.
 */
@Composable
fun FavoriteRulesScreen(
    rules: List<FavoriteRule>,
    /** Favorite tokens in their home-screen order, which is the order the picker offers. */
    favoriteTokens: List<String>,
    appsByKey: Map<String, AppInfo>,
    foldersById: Map<String, Folder>,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    /** The connected network, for the "use current" button. Null when unknown. */
    currentSsid: String?,
    /** Whether the location permission that reads the network name has been granted. */
    locationGranted: Boolean,
    onRequestLocation: () -> Unit,
    onUpsert: (FavoriteRule) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    var editing by remember { mutableStateOf<FavoriteRule?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (creating || editing != null) {
        RuleEditor(
            initial = editing ?: FavoriteRule(id = ""),
            favoriteTokens = favoriteTokens,
            appsByKey = appsByKey,
            foldersById = foldersById,
            nameOverrides = nameOverrides,
            iconSizeDp = iconSizeDp,
            currentSsid = currentSsid,
            locationGranted = locationGranted,
            onRequestLocation = onRequestLocation,
            onSave = { rule ->
                onUpsert(
                    if (rule.id.isBlank()) {
                        rule.copy(id = "r" + System.currentTimeMillis().toString(36))
                    } else {
                        rule
                    }
                )
                editing = null
                creating = false
            },
            onCancel = {
                editing = null
                creating = false
            },
        )
        return
    }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.rules_add))
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
            if (rules.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)) {
                        Text(
                            stringResource(R.string.rules_empty),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.rules_empty_detail),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                }
            }
            items(rules, key = { it.id }) { rule ->
                RuleRow(
                    rule = rule,
                    subtitle = summary(rule),
                    favoriteCount = rule.tokens.size,
                    onToggle = { onUpsert(rule.copy(enabled = it)) },
                    onEdit = { editing = rule },
                    onDelete = { onDelete(rule.id) },
                )
            }
        }
    }
}

@Composable
private fun summary(rule: FavoriteRule): String {
    val parts = mutableListOf<String>()
    if (rule.wifi != FavoriteRuleWifi.ANY && rule.wifiNames.isNotEmpty()) {
        val names = rule.wifiNames.sorted().joinToString(", ")
        parts += if (rule.wifi == FavoriteRuleWifi.ON) {
            stringResource(R.string.rules_summary_wifi_on, names)
        } else {
            stringResource(R.string.rules_summary_wifi_off, names)
        }
    }
    when (rule.headset) {
        FavoriteRuleHeadset.ANY -> Unit
        FavoriteRuleHeadset.CONNECTED -> parts += stringResource(R.string.rules_summary_headset)
        FavoriteRuleHeadset.DISCONNECTED -> parts += stringResource(R.string.rules_summary_no_headset)
    }
    if (rule.startMinute in 0..1439 && rule.endMinute in 0..1439) {
        parts += stringResource(
            R.string.rules_summary_time,
            clock(rule.startMinute),
            clock(rule.endMinute),
        )
    }
    if (rule.dailyLimit > 0) {
        parts += stringResource(R.string.rules_summary_limit, rule.dailyLimit)
    }
    if (parts.isEmpty()) parts += stringResource(R.string.rules_summary_always)
    val action = stringResource(
        if (rule.action == FavoriteRuleAction.SHOW) R.string.rules_action_show
        else R.string.rules_action_hide
    )
    return action + " · " + parts.joinToString(" · ")
}

private fun clock(minute: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

@Composable
private fun RuleRow(
    rule: FavoriteRule,
    subtitle: String,
    favoriteCount: Int,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = rule.name.ifBlank { stringResource(R.string.rules_untitled) },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            Text(
                text = pluralStringResource(
                    R.plurals.rules_tokens_count,
                    favoriteCount,
                    favoriteCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_remove),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(
    initial: FavoriteRule,
    favoriteTokens: List<String>,
    appsByKey: Map<String, AppInfo>,
    foldersById: Map<String, Folder>,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    currentSsid: String?,
    locationGranted: Boolean,
    onRequestLocation: () -> Unit,
    onSave: (FavoriteRule) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val surface = MaterialTheme.colorScheme.surface
    var name by remember { mutableStateOf(initial.name) }
    var action by remember { mutableStateOf(initial.action) }
    var tokens by remember { mutableStateOf(initial.tokens) }
    var wifi by remember { mutableStateOf(initial.wifi) }
    var wifiText by remember { mutableStateOf(initial.wifiNames.sorted().joinToString(", ")) }
    var headset by remember { mutableStateOf(initial.headset) }
    var useTime by remember {
        mutableStateOf(initial.startMinute in 0..1439 && initial.endMinute in 0..1439)
    }
    var start by remember {
        mutableIntStateOf(initial.startMinute.takeIf { it in 0..1439 } ?: (9 * 60))
    }
    var end by remember {
        mutableIntStateOf(initial.endMinute.takeIf { it in 0..1439 } ?: (17 * 60))
    }
    var limit by remember { mutableIntStateOf(initial.dailyLimit) }

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            current / 60,
            current % 60,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (initial.id.isBlank()) R.string.rules_new else R.string.rules_edit
                        )
                    )
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
                    TextButton(
                        onClick = {
                            onSave(
                                FavoriteRule(
                                    id = initial.id,
                                    name = name.trim(),
                                    enabled = initial.enabled,
                                    action = action,
                                    tokens = tokens,
                                    wifi = wifi,
                                    wifiNames = wifiText.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .take(16)
                                        .toSet(),
                                    headset = headset,
                                    startMinute = if (useTime) start else -1,
                                    endMinute = if (useTime) end else -1,
                                    dailyLimit = limit,
                                )
                            )
                        },
                        enabled = tokens.isNotEmpty(),
                    ) {
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
                ListSectionLabel(stringResource(R.string.rules_action_label))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledChip(
                        stringResource(R.string.rules_action_show),
                        action == FavoriteRuleAction.SHOW,
                    ) { action = FavoriteRuleAction.SHOW }
                    FilledChip(
                        stringResource(R.string.rules_action_hide),
                        action == FavoriteRuleAction.HIDE,
                    ) { action = FavoriteRuleAction.HIDE }
                }
            }

            item {
                ListSectionLabel(stringResource(R.string.rules_conditions_label))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.rules_headset), Modifier.weight(1f))
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledChip(
                        stringResource(R.string.rules_headset_any),
                        headset == FavoriteRuleHeadset.ANY,
                    ) { headset = FavoriteRuleHeadset.ANY }
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
                    FilledChip(
                        stringResource(R.string.rules_wifi_any),
                        wifi == FavoriteRuleWifi.ANY,
                    ) { wifi = FavoriteRuleWifi.ANY }
                    FilledChip(
                        stringResource(R.string.rules_wifi_on),
                        wifi == FavoriteRuleWifi.ON,
                    ) { wifi = FavoriteRuleWifi.ON }
                    FilledChip(
                        stringResource(R.string.rules_wifi_off),
                        wifi == FavoriteRuleWifi.OFF,
                    ) { wifi = FavoriteRuleWifi.OFF }
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
                                val joined = wifiText.trim()
                                wifiText = if (joined.isEmpty()) currentSsid else "$joined, $currentSsid"
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.rules_daily_limit))
                        Text(
                            stringResource(R.string.rules_daily_limit_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                    TextButton(onClick = { if (limit > 0) limit-- }) { Text("−") }
                    Text(limit.toString(), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { if (limit < 20) limit++ }) { Text("+") }
                }
            }

            item {
                ListSectionLabel(stringResource(R.string.rules_tokens_label))
            }
            items(favoriteTokens, key = { "token:$it" }) { token ->
                val folderId = folderIdFromToken(token)
                val label = if (folderId != null) {
                    foldersById[folderId]?.name ?: token
                } else {
                    appsByKey[token]?.let { nameOverrides[token] ?: it.label } ?: token
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            tokens = if (token in tokens) tokens - token else tokens + token
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (folderId != null) {
                        Icon(
                            DelugeIcons.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(iconSizeDp.dp.coerceAtMost(40.dp)),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    } else {
                        appsByKey[token]?.let {
                            AppIcon(app = it, sizeDp = minOf(iconSizeDp, 40))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = token in tokens, onCheckedChange = { on ->
                        tokens = if (on) tokens + token else tokens - token
                    })
                }
            }
        }
    }
}
