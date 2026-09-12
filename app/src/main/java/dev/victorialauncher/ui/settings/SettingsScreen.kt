// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import dev.victorialauncher.BuildConfig
import dev.victorialauncher.data.AppFont
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.data.QuickLaunchSlot
import dev.victorialauncher.data.IconPackRepository
import dev.victorialauncher.data.TextColorMode
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.ui.theme.toFontFamily
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    hiddenCount: Int,
    iconPacks: List<IconPackRepository.IconPackInfo>,
    iconPackPackage: String?,
    previewApp: AppInfo?,
    iconSizeDp: Int,
    labelSizeSp: Int,
    itemSpacingDp: Int,
    font: AppFont,
    hideStatusBar: Boolean,
    dimWallpaperAlpha: Float,
    hapticsEnabled: Boolean,
    dimHomeAlpha: Float,
    showFavoriteLabels: Boolean,
    textColorMode: TextColorMode,
    doubleTapToLock: Boolean,
    edgeSide: EdgeSide,
    alwaysShowAz: Boolean,
    showAlphabet: Boolean,
    sortByUsage: Boolean,
    appListSearch: Boolean,
    appListSearchBottom: Boolean,
    swipeUpOpensList: Boolean,
    alignment: HomeAlignment,
    nowPlayingEnabled: Boolean,
    nowPlayingListenerEnabled: Boolean,
    showAppIcons: Boolean,
    onSetIconPack: (String?) -> Unit,
    onSetShowAppIcons: (Boolean) -> Unit,
    onSetIconSize: (Int) -> Unit,
    onSetLabelSize: (Int) -> Unit,
    onSetItemSpacing: (Int) -> Unit,
    onSetFont: (AppFont) -> Unit,
    statusBarPeekSeconds: Int,
    onSetHideStatusBar: (Boolean) -> Unit,
    onSetStatusBarPeekSeconds: (Int) -> Unit,
    onSetDimWallpaper: (Float) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetDimHome: (Float) -> Unit,
    onSetShowFavoriteLabels: (Boolean) -> Unit,
    onSetTextColorMode: (TextColorMode) -> Unit,
    onSetDoubleTapToLock: (Boolean) -> Unit,
    edgeZoneWidthDp: Int,
    onSetEdgeSide: (EdgeSide) -> Unit,
    onSetEdgeZoneWidth: (Int) -> Unit,
    onSetAlwaysShowAz: (Boolean) -> Unit,
    onSetShowAlphabet: (Boolean) -> Unit,
    onSetSortByUsage: (Boolean) -> Unit,
    onSetSwipeUpOpensList: (Boolean) -> Unit,
    onSetAppListSearch: (Boolean) -> Unit,
    onSetAppListSearchBottom: (Boolean) -> Unit,
    quickLaunchLeftLabel: String?,
    quickLaunchRightLabel: String?,
    onOpenQuickLaunchPicker: (QuickLaunchSlot) -> Unit,
    onSetAlignment: (HomeAlignment) -> Unit,
    onSetNowPlayingEnabled: (Boolean) -> Unit,
    shadeGestureReady: Boolean,
    lockGestureReady: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .padding(padding)
                .background(surface)
                .fillMaxWidth(),
        ) {
            item {
                Section(stringResource(R.string.settings_section_appearance)) {
                    // Live preview of exactly how a home row will render.
                    RowPreview(previewApp, iconSizeDp, labelSizeSp, font)
                    RowDivider()
                    IconPackRow(iconPacks, iconPackPackage, showAppIcons, onSetIconPack, onSetShowAppIcons)
                    RowDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_icon_size),
                        value = iconSizeDp.toFloat(),
                        range = 32f..96f,
                        valueLabel = "${iconSizeDp}dp",
                        onValueChange = { onSetIconSize(it.toInt()) },
                    )
                    RowDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_text_size),
                        value = labelSizeSp.toFloat(),
                        range = 10f..28f,
                        valueLabel = "${labelSizeSp}sp",
                        onValueChange = { onSetLabelSize(it.toInt()) },
                    )
                    RowDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_favorite_spacing),
                        value = itemSpacingDp.toFloat(),
                        range = 0f..40f,
                        valueLabel = "${itemSpacingDp}dp",
                        onValueChange = { onSetItemSpacing(it.toInt()) },
                    )
                    RowDivider()
                    FontRow(font, onSetFont)
                    RowDivider()
                    TextColorRow(textColorMode, onSetTextColorMode)
                    RowDivider()
                    AlignmentRow(alignment, onSetAlignment)
                    RowDivider()
                    SwitchRow(stringResource(R.string.settings_show_names), showFavoriteLabels, onSetShowFavoriteLabels)
                    RowDivider()
                    SwitchRow(stringResource(R.string.settings_hide_status_bar), hideStatusBar, onSetHideStatusBar)
                    if (hideStatusBar) {
                        RowDivider()
                        SliderRow(
                            label = stringResource(R.string.settings_status_bar_timeout),
                            value = statusBarPeekSeconds.toFloat(),
                            range = 1f..30f,
                            valueLabel = "${statusBarPeekSeconds}s",
                            onValueChange = { onSetStatusBarPeekSeconds(it.roundToInt()) },
                        )
                    }
                    RowDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_dim_home),
                        value = dimHomeAlpha,
                        range = 0f..0.85f,
                        valueLabel = "${(dimHomeAlpha * 100).toInt()}%",
                        onValueChange = onSetDimHome,
                        step = 0.05f,
                    )
                    RowDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_dim_applist),
                        value = dimWallpaperAlpha,
                        range = 0f..0.85f,
                        valueLabel = "${(dimWallpaperAlpha * 100).toInt()}%",
                        onValueChange = onSetDimWallpaper,
                        step = 0.05f,
                    )
                }
            }

            item {
                Section(stringResource(R.string.settings_section_behavior)) {
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_haptics),
                        detail = stringResource(R.string.settings_haptics_detail),
                        checked = hapticsEnabled,
                        onCheckedChange = onSetHaptics,
                    )
                    RowDivider()
                    EdgeSideRow(edgeSide, onSetEdgeSide)
                    RowDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_edge_zone_width),
                        value = edgeZoneWidthDp.toFloat(),
                        range = 32f..96f,
                        valueLabel = "${edgeZoneWidthDp}dp",
                        onValueChange = { onSetEdgeZoneWidth(it.roundToInt()) },
                        step = 4f,
                    )
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_double_tap_lock),
                        detail = stringResource(R.string.settings_double_tap_lock_detail),
                        checked = doubleTapToLock,
                        onCheckedChange = onSetDoubleTapToLock,
                    )
                    // Switching it on does nothing at all without the permission, so the way
                    // to grant it belongs right here rather than buried in a toast later.
                    if (doubleTapToLock && !lockGestureReady) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_lock_needs_accessibility),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f),
                            )
                            AccessibilityActions(onOpenAccessibilitySettings, onOpenAppInfo)
                        }
                    }
                    RowDivider()
                    SwitchRow(stringResource(R.string.settings_always_show_az), alwaysShowAz, onSetAlwaysShowAz)
                    RowDivider()
                    SwitchRow(stringResource(R.string.settings_show_alphabet), showAlphabet, onSetShowAlphabet)
                    RowDivider()
                    QuickLaunchRow(
                        label = stringResource(R.string.settings_quick_launch_left),
                        value = quickLaunchLeftLabel,
                        onClick = { onOpenQuickLaunchPicker(QuickLaunchSlot.LEFT) },
                    )
                    RowDivider()
                    QuickLaunchRow(
                        label = stringResource(R.string.settings_quick_launch_right),
                        value = quickLaunchRightLabel,
                        onClick = { onOpenQuickLaunchPicker(QuickLaunchSlot.RIGHT) },
                    )
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_swipe_up_list),
                        detail = stringResource(R.string.settings_swipe_up_list_detail),
                        checked = swipeUpOpensList,
                        onCheckedChange = onSetSwipeUpOpensList,
                    )
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_search_bar),
                        detail = stringResource(R.string.settings_search_bar_detail),
                        checked = appListSearch,
                        onCheckedChange = onSetAppListSearch,
                    )
                    if (appListSearch) {
                        RowDivider()
                        SwitchRow(
                            stringResource(R.string.settings_search_bar_bottom),
                            appListSearchBottom,
                            onSetAppListSearchBottom,
                        )
                    }
                    RowDivider()
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_sort_by_usage),
                        detail = stringResource(R.string.settings_sort_by_usage_detail),
                        checked = sortByUsage,
                        onCheckedChange = onSetSortByUsage,
                    )
                    RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_shade_gesture), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(
                                    if (shadeGestureReady) R.string.settings_shade_ready
                                    else R.string.settings_shade_not_ready
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        if (!shadeGestureReady) {
                            AccessibilityActions(onOpenAccessibilitySettings, onOpenAppInfo)
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.settings_section_now_playing)) {
                    SwitchRow(stringResource(R.string.settings_now_playing_show), nowPlayingEnabled, onSetNowPlayingEnabled)
                    if (nowPlayingEnabled) {
                        RowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(
                                        if (nowPlayingListenerEnabled) R.string.settings_notification_access_granted
                                        else R.string.settings_notification_access_missing
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(R.string.settings_notification_access_detail),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            FilledChip(stringResource(R.string.settings_open_settings), selected = false, onClick = onOpenNotificationSettings)
                        }
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_apps)) }
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                  Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenFavorites)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_favorites), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.settings_favorites_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                    RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenHiddenApps)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_hidden_apps), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (hiddenCount == 0) stringResource(R.string.settings_hidden_none)
                                else stringResource(R.string.settings_hidden_count, hiddenCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                  }
                }
            }

            item {
                Section(stringResource(R.string.settings_section_about)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionLabel(title)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun SwitchRowWithDetail(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

/** Slider plus a pair of steppers, since dragging to an exact value is fiddly. */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    step: Float = 1f,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onValueChange((value - step).coerceIn(range)) },
                enabled = value > range.start,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.settings_less))
            }
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(
                onClick = { onValueChange((value + step).coerceIn(range)) },
                enabled = value < range.endInclusive,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_more))
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPackRow(
    packs: List<IconPackRepository.IconPackInfo>,
    selected: String?,
    showIcons: Boolean,
    onSelect: (String?) -> Unit,
    onSetShowIcons: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_icon_pack), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledChip(stringResource(R.string.settings_icon_pack_default), showIcons && selected == null) {
                onSetShowIcons(true)
                onSelect(null)
            }
            packs.forEach { pack ->
                FilledChip(pack.label, showIcons && selected == pack.packageName) {
                    onSetShowIcons(true)
                    onSelect(pack.packageName)
                }
            }
            // Not a pack but a choice about packs: draw no icons at all.
            FilledChip(stringResource(R.string.settings_icon_pack_no_icons), !showIcons) { onSetShowIcons(false) }
        }
        if (packs.isEmpty()) {
            Text(
                stringResource(R.string.settings_icon_pack_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun FontRow(selected: AppFont, onSelect: (AppFont) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_font), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFont.entries.forEach { f ->
                // Each chip is rendered in the font it selects, so the choice previews itself.
                FilledChip(
                    label = stringResource(f.labelRes()),
                    selected = selected == f,
                    fontFamily = f.toFontFamily(),
                    onClick = { onSelect(f) },
                )
            }
        }
    }
}

@Composable
private fun RowPreview(app: AppInfo?, iconSizeDp: Int, labelSizeSp: Int, font: AppFont) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            stringResource(R.string.settings_preview),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app != null) {
                AppIcon(app = app, sizeDp = iconSizeDp)
                Spacer(Modifier.width(16.dp))
                Text(app.label, fontSize = labelSizeSp.sp, fontFamily = font.toFontFamily())
            } else {
                Text(stringResource(R.string.settings_preview_sample), fontSize = labelSizeSp.sp, fontFamily = font.toFontFamily())
            }
        }
    }
}

@Composable
private fun TextColorRow(selected: TextColorMode, onSelect: (TextColorMode) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_text_color), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_text_color_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextColorMode.entries.forEach { mode ->
                FilledChip(stringResource(mode.labelRes()), selected == mode) { onSelect(mode) }
            }
        }
    }
}

/**
 * Getting to the accessibility toggle, and to the screen that unblocks it.
 *
 * Android 13 and later refuse to let an app installed outside an app store be switched on
 * under Accessibility at all — the toggle is there but greyed, with no explanation offered at
 * the point of failure. It has to be unblocked first from the app's own info screen, under the
 * overflow menu, so that screen is one tap away here rather than something to go hunting for.
 */
@Composable
private fun AccessibilityActions(onOpenAccessibilitySettings: () -> Unit, onOpenAppInfo: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledChip(stringResource(R.string.settings_app_info), selected = false, onClick = onOpenAppInfo)
        FilledChip(stringResource(R.string.settings_enable), selected = false, onClick = onOpenAccessibilitySettings)
    }
}

@Composable
private fun QuickLaunchRow(label: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                value ?: stringResource(R.string.settings_quick_launch_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun AlignmentRow(selected: HomeAlignment, onSelect: (HomeAlignment) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_alignment), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeAlignment.entries.forEach { option ->
                FilledChip(
                    label = stringResource(option.labelRes()),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun EdgeSideRow(selected: EdgeSide, onSelect: (EdgeSide) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_edge_side), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EdgeSide.entries.forEach { side ->
                FilledChip(stringResource(side.labelRes()), selected == side) { onSelect(side) }
            }
        }
    }
}

@Composable
private fun FilledChip(
    label: String,
    selected: Boolean,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = fontFamily,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}