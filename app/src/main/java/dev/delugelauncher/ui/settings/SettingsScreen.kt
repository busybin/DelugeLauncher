// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.settings

import dev.delugelauncher.ui.common.DelugeIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import dev.delugelauncher.data.ColorSchemeChoice
import dev.delugelauncher.data.CornerAction
import dev.delugelauncher.data.SlotKind
import dev.delugelauncher.data.SlotTarget
import dev.delugelauncher.data.UpdateError
import dev.delugelauncher.data.UpdateResult
import dev.delugelauncher.data.targetKind
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import dev.delugelauncher.ui.theme.fontFamilyOf
import dev.delugelauncher.ui.theme.rememberWallpaperPalette
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.items
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import dev.delugelauncher.service.HapticUtil
import dev.delugelauncher.data.CrashLog
import dev.delugelauncher.data.Pinyin
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import dev.delugelauncher.BuildConfig
import dev.delugelauncher.data.AppFont
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.AzStripVisibility
import dev.delugelauncher.data.IconShape
import dev.delugelauncher.data.EdgeSide
import dev.delugelauncher.data.EDGE_ZONE_INSET_RANGE
import dev.delugelauncher.data.HOLD_SCROLL_RANGE
import dev.delugelauncher.data.NOTIFICATION_TEXT_SP_RANGE
import dev.delugelauncher.data.RECENT_SECTION_RANGE
import dev.delugelauncher.data.SECTION_TOP_RANGE
import dev.delugelauncher.data.FREQUENT_RANGE
import dev.delugelauncher.data.FavoritesSource
import dev.delugelauncher.data.ShortcutSwipe
import dev.delugelauncher.data.HomeAlignment
import dev.delugelauncher.data.IconSide
import dev.delugelauncher.data.IconPackRepository
import dev.delugelauncher.data.TextColorMode
import dev.delugelauncher.data.HapticFeedback
import dev.delugelauncher.data.TwoColumnSlot
import android.content.Intent
import dev.delugelauncher.ui.common.AppIcon
import dev.delugelauncher.ui.theme.toFontFamily
import dev.delugelauncher.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.delugelauncher.ui.theme.SHADOW_STRENGTH_RANGE

// SettingsScreen's state holder. Not 186 flat parameters: the Compose compiler emits a $changed
// mask per @Composable parameter and the call site blows the JVM's 64 KB per-method limit
// ("Method too large: ...DelugeNavHost$lambda$..."). One object keeps the call site at a single
// argument while `with(model)` puts the properties back in scope. A plain class, not a data
// class, so no componentN/copy is generated.
class SettingsScreenModel(
    val hiddenCount: Int,
    val settingsAdvanced: Boolean,
    val iconPacks: List<IconPackRepository.IconPackInfo>,
    val iconPackPackage: String?,
    val previewApp: AppInfo?,
    val iconSizeDp: Int,
    val labelSizeSp: Int,
    val itemSpacingDp: Int,
    val font: AppFont,
    val hideStatusBar: Boolean,
    val hideStatusBarAppList: Boolean,
    val dimWallpaperAlpha: Float,
    val hapticFeedback: HapticFeedback,
    val dimHomeAlpha: Float,
    val showFavoriteLabels: Boolean,
    val screenshotMode: Boolean,
    val onSetScreenshotMode: (Boolean) -> Unit,
    val textColorMode: TextColorMode,
    val textColorCustom: Int,
    val dimColor: Int,
    val allowRotation: Boolean,
    val shortcutSwipe: ShortcutSwipe,
    val fontFile: String?,
    val iconShape: IconShape,
    val themedIcons: Boolean,
    val doubleTapToLock: Boolean,
    val appLockTimeout: Int,
    val onSetAppLockTimeout: (Int) -> Unit,
    val appLockScreenOff: Boolean,
    val onSetAppLockScreenOff: (Boolean) -> Unit,
    val edgeSide: EdgeSide,
    val azStripVisibility: AzStripVisibility,
    val showAlphabet: Boolean,
    val sortByUsage: Boolean,
    val autoFrequentTag: Boolean,
    val onSetAutoFrequentTag: (Boolean) -> Unit,
    val homeTwoColumnPortrait: Boolean,
    val homeTwoColumnLandscape: Boolean,
    val listTwoColumnPortrait: Boolean,
    val listTwoColumnLandscape: Boolean,
    val onSetTwoColumn: (TwoColumnSlot, Boolean) -> Unit,
    val appListSearch: Boolean,
    val appListSearchBottom: Boolean,
    val appListSearchHidden: Boolean,
    val swipeUpOpensList: Boolean,
    val alignment: HomeAlignment,
    val appListAlignment: HomeAlignment,
    val iconSide: IconSide,
    val nowPlayingEnabled: Boolean,
    val nowPlayingListenerEnabled: Boolean,
    val showAppIcons: Boolean,
    val showFavoriteIcons: Boolean,
    val onSetShowFavoriteIcons: (Boolean) -> Unit,
    val showListHeaders: Boolean,
    val onSetShowListHeaders: (Boolean) -> Unit,
    val showSettingsRow: Boolean,
    val onSetShowSettingsRow: (Boolean) -> Unit,
    val appListFolders: Boolean,
    val onSetAppListFolders: (Boolean) -> Unit,
    val edgeZoneBandOnly: Boolean,
    val onSetEdgeZoneBandOnly: (Boolean) -> Unit,
    val holdScroll: Boolean,
    val onSetHoldScroll: (Boolean) -> Unit,
    val dimOthersScrubbing: Boolean,
    val onSetDimOthersScrubbing: (Boolean) -> Unit,
    val markWorkApps: Boolean,
    val onSetMarkWorkApps: (Boolean) -> Unit,
    val textShadow: Boolean,
    val onSetTextShadow: (Boolean) -> Unit,
    val textShadowStrength: Int,
    val onSetTextShadowStrength: (Int) -> Unit,
    val compactTapArea: Boolean,
    val onSetCompactTapArea: (Boolean) -> Unit,
    val stayAfterPrivateUnlock: Boolean,
    val onSetStayAfterPrivateUnlock: (Boolean) -> Unit,
    val privateSpaceSection: Boolean,
    val onSetPrivateSpaceSection: (Boolean) -> Unit,
    val recentSection: Boolean,
    val onSetRecentSection: (Boolean) -> Unit,
    val recentSectionCount: Int,
    val onSetRecentSectionCount: (Int) -> Unit,
    val edgeZoneInsetDp: Int,
    val onSetEdgeZoneInsetDp: (Int) -> Unit,
    val edgeZoneInsetAuto: Boolean,
    val onSetEdgeZoneInsetAuto: (Boolean) -> Unit,
    val webSearchFallback: Boolean,
    val onSetWebSearchFallback: (Boolean) -> Unit,
    val swipeUpOpensSearch: Boolean,
    val onSetSwipeUpOpensSearch: (Boolean) -> Unit,
    val holdScrollSpeed: Int,
    val onSetHoldScrollSpeed: (Int) -> Unit,
    val notificationBadges: Boolean,
    val onSetNotificationBadges: (Boolean) -> Unit,
    val notificationPreview: Boolean,
    val notificationTextSp: Int,
    val onSetNotificationTextSp: (Int) -> Unit,
    val onSetNotificationPreview: (Boolean) -> Unit,
    val notificationPanel: Boolean,
    val onSetNotificationPanel: (Boolean) -> Unit,
    val onOpenNotificationPanel: () -> Unit,
    val dimPausedApps: Boolean,
    val onSetDimPausedApps: (Boolean) -> Unit,
    val autoDark: Boolean,
    val onSetAutoDark: (Boolean) -> Unit,
    val nightStartHour: Int,
    val onSetNightStartHour: (Int) -> Unit,
    val nightEndHour: Int,
    val onSetNightEndHour: (Int) -> Unit,
    val pinchToResize: Boolean,
    val onSetPinchToResize: (Boolean) -> Unit,
    val reduceMotion: Boolean,
    val onSetReduceMotion: (Boolean) -> Unit,
    val onSetSettingsAdvanced: (Boolean) -> Unit,
    val sectionTopPercent: Int,
    val onSetSectionTopPercent: (Int) -> Unit,
    val closeFolderOnLaunch: Boolean,
    val onSetCloseFolderOnLaunch: (Boolean) -> Unit,
    val favoritesSource: FavoritesSource,
    val onSetFavoritesSource: (FavoritesSource) -> Unit,
    val frequentCount: Int,
    val onSetFrequentCount: (Int) -> Unit,
    val onSetIconPack: (String?) -> Unit,
    val onSetShowAppIcons: (Boolean) -> Unit,
    val onSetIconSize: (Int) -> Unit,
    val onSetLabelSize: (Int) -> Unit,
    val onSetItemSpacing: (Int) -> Unit,
    val onSetFont: (AppFont) -> Unit,
    val colorScheme: ColorSchemeChoice,
    val onSetColorScheme: (ColorSchemeChoice) -> Unit,
    val statusBarPeekSeconds: Int,
    val onSetHideStatusBar: (Boolean) -> Unit,
    val onSetHideStatusBarAppList: (Boolean) -> Unit,
    val onSetStatusBarPeekSeconds: (Int) -> Unit,
    val onSetDimWallpaper: (Float) -> Unit,
    val wallpaperBlurDp: Int,
    val onSetWallpaperBlur: (Int) -> Unit,
    val onSetHapticFeedback: (HapticFeedback) -> Unit,
    val onSetDimHome: (Float) -> Unit,
    val onSetShowFavoriteLabels: (Boolean) -> Unit,
    val onSetTextColorMode: (TextColorMode) -> Unit,
    val onSetTextColorCustom: (Int) -> Unit,
    val onSetDimColor: (Int) -> Unit,
    val onSetAllowRotation: (Boolean) -> Unit,
    val onSetShortcutSwipe: (ShortcutSwipe) -> Unit,
    val onExportSettings: () -> Unit,
    val onImportSettings: () -> Unit,
    val onPickFontFile: (Uri) -> Unit,
    val onSetIconShape: (IconShape) -> Unit,
    val onSetThemedIcons: (Boolean) -> Unit,
    val onSetDoubleTapToLock: (Boolean) -> Unit,
    val edgeZoneWidthDp: Int,
    val onSetEdgeSide: (EdgeSide) -> Unit,
    val onSetEdgeZoneWidth: (Int) -> Unit,
    val onSetAzStripVisibility: (AzStripVisibility) -> Unit,
    val onSetShowAlphabet: (Boolean) -> Unit,
    val onSetSortByUsage: (Boolean) -> Unit,
    val onSetSwipeUpOpensList: (Boolean) -> Unit,
    val onSetAppListSearch: (Boolean) -> Unit,
    val onSetAppListSearchBottom: (Boolean) -> Unit,
    val onSetAppListSearchHidden: (Boolean) -> Unit,
    val quickLaunchLeftLabel: String?,
    val quickLaunchRightLabel: String?,
    val cornerButtonLabel: String?,
    val cornerSwipeLabel: String?,
    val onOpenSlotPicker: (SlotTarget, SlotKind) -> Unit,
    val quickLaunchLeftAction: CornerAction,
    val onSetQuickLaunchLeftAction: (CornerAction) -> Unit,
    val quickLaunchRightAction: CornerAction,
    val onSetQuickLaunchRightAction: (CornerAction) -> Unit,
    val cornerTapAction: CornerAction,
    val onSetCornerTapAction: (CornerAction) -> Unit,
    val cornerSwipeAction: CornerAction,
    val onSetCornerSwipeAction: (CornerAction) -> Unit,
    val autoKeyboard: Boolean,
    val onSetAutoKeyboard: (Boolean) -> Unit,
    val lastLetterToLine: Boolean,
    val onSetLastLetterToLine: (Boolean) -> Unit,
    val onSetAlignment: (HomeAlignment) -> Unit,
    val onSetAppListAlignment: (HomeAlignment) -> Unit,
    val onSetIconSide: (IconSide) -> Unit,
    val onSetNowPlayingEnabled: (Boolean) -> Unit,
    val shadeGestureReady: Boolean,
    val lockGestureReady: Boolean,
    val onOpenAccessibilitySettings: () -> Unit,
    val onOpenAppInfo: () -> Unit,
    val onOpenHiddenApps: () -> Unit,
    /** How many apps this device has frozen, for the count on the settings row. */
    val frozenCount: Int,
    /** Whether app freezing is offered at all. Off also means its rows and actions are gone. */
    val freezeEnabled: Boolean,
    val onOpenFreezeApps: () -> Unit,
    /** Turning this off unfreezes and empties the list; the manager refuses a half-finished job. */
    val onSetFreezeEnabled: (Boolean) -> Unit,
    /** Whether this launcher is the one the system opens on HOME. */
    val isDefaultLauncher: Boolean,
    val onOpenHomeSettings: () -> Unit,
    val frequentExcludedCount: Int,
    /** Only meaningful while the favorites are the computed ones. */
    val frequentExcludedVisible: Boolean,
    val onOpenFrequentExcluded: () -> Unit,
    val onOpenFavorites: () -> Unit,
    /** How many favorite rules are on, for the row's subtitle. */
    val favoriteRuleCount: Int,
    val onOpenFavoriteRules: () -> Unit,
    /** Opens the launcher's own language picker. */
    val onOpenLanguage: () -> Unit,
    /** Opens the release notes. */
    val onOpenChangelog: () -> Unit,
    /** Whether a look at the release page is in flight right now. */
    val updateChecking: Boolean,
    /** What the last look in this process found, or null if none has run yet. */
    val updateResult: UpdateResult?,
    /** The newest release any look has seen, however long ago that was. */
    val updateKnownVersion: Int,
    /** Whether the launcher may look on its own at startup. Off by default — see Prefs. */
    val updateAutoCheck: Boolean,
    val onSetUpdateAutoCheck: (Boolean) -> Unit,
    val onCheckForUpdates: () -> Unit,
    /** Opens the release page in the browser; the page itself, never a file. */
    val onOpenReleasePage: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(model: SettingsScreenModel) {
    with(model) {
    val surface = MaterialTheme.colorScheme.surface
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // A width in dp means nothing until you see it against the screen it is measured on, so
    // adjusting it paints the zone down the edges it would actually occupy. It fades out on
    // its own rather than needing dismissing.
    var edgePreviewShown by remember { mutableStateOf(false) }
    // Driven by the act of adjusting, not by the value. Keyed on the value it also fired on
    // first composition, and again when the stored setting arrived and replaced the initial
    // one — so opening settings flashed a preview nobody asked for.
    var edgePreviewTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(edgePreviewTick) {
        if (edgePreviewTick == 0) return@LaunchedEffect
        edgePreviewShown = true
        delay(1400)
        edgePreviewShown = false
    }
    val edgePreviewAlpha by animateFloatAsState(
        if (edgePreviewShown) 1f else 0f,
        label = "edgePreviewAlpha",
    )

    // A dim percentage means nothing against a settings screen that is painted over the
    // wallpaper it is dimming. Adjusting one fades this screen out for a moment and paints the
    // scrim it would actually paint, over the real wallpaper — the window has always shown it,
    // so there is nothing to fetch or fake.
    var dimPreviewShown by remember { mutableStateOf(false) }
    var dimPreviewTick by remember { mutableIntStateOf(0) }
    // Which of the two dims is being adjusted, since they are set separately and each is the
    // honest answer only for its own screen.
    var dimPreviewValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(dimPreviewTick) {
        if (dimPreviewTick == 0) return@LaunchedEffect
        dimPreviewShown = true
        delay(1400)
        dimPreviewShown = false
    }
    val dimPreviewReveal by animateFloatAsState(
        if (dimPreviewShown) 1f else 0f,
        label = "dimPreviewReveal",
    )

    // The same idea for the scrub line: a percentage means nothing until you see where on this
    // screen it falls, so setting it draws the line it describes.
    var sectionPreviewShown by remember { mutableStateOf(false) }
    var sectionPreviewTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(sectionPreviewTick) {
        if (sectionPreviewTick == 0) return@LaunchedEffect
        sectionPreviewShown = true
        delay(1400)
        sectionPreviewShown = false
    }
    val sectionPreviewAlpha by animateFloatAsState(
        if (sectionPreviewShown) 1f else 0f,
        label = "sectionPreviewAlpha",
    )

    // One screen, shown a section at a time. Separate destinations would mean threading every
    // one of these settings through a route of its own, for a list that is only ever reached
    // from here — so the sections stay where they are and the screen shows one of them.
    // Saveable, not merely remembered. Settings that change the window — the rotation lock is
    // the plain one — rebuild the Activity, and a plain remember goes with it, dropping whoever
    // was three rows into a section back at the top menu.
    var openCard by rememberSaveable { mutableStateOf<SettingsCard?>(null) }
    // The developer switch hides until it is unlocked: five seconds held on the app name in
    // About. Not persisted — a dead process relocks, which is the point of hiding it.
    var developerUnlocked by rememberSaveable { mutableStateOf(false) }
    // Typed across every section at once, since the whole trouble with a settings screen is
    // knowing which section the thing you want was filed under.
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = openCard != null || query.isNotBlank()) {
        if (query.isNotBlank()) query = "" else openCard = null
    }

    // The project's page: new releases live there. Opened from the app name in About.
    fun openProjectPage() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // Sideways switches do nothing while the rotation lock holds the phone upright: say so
    // on the row itself rather than letting the switch read as broken.
    val rotationNote = stringResource(R.string.settings_two_column_needs_rotation)
    fun landscapeDetail(base: String): String =
        if (allowRotation) base else "$base $rotationNote"


    // Only after a crash, so there is nothing here to explain the rest of the time.
    // Read once up front, off the composition thread: the log is up to 64KB of file.
    var crashText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crashText = withContext(Dispatchers.IO) { CrashLog.read(context) }
    }
    var crashCleared by remember { mutableStateOf(false) }
    // Turning the freeze feature off is a one-way cleanup: every frozen app is lifted and the
    // list is emptied, so it gets a confirmation rather than a switch that just flips.
    var disablingFreeze by remember { mutableStateOf(false) }
    var appLockDialog by remember { mutableStateOf(false) }
    var statusBarDialog by remember { mutableStateOf(false) }
    var dimDialog by remember { mutableStateOf(false) }
    var twoColumnDialog by remember { mutableStateOf(false) }
    var azDialog by remember { mutableStateOf(false) }

    // Every row of settings as one list: what it is called, what else it answers to, the
    // section it belongs in, and the control itself. One list rather than a block per section
    // because the rows have to serve two readers — someone walking a section, and someone
    // typing at the search box — and a row written twice is a row that drifts.
    // Resolved once, so the row that reports an update and the row that opens the page can
    // never disagree about which release they mean.
    val updateState = updateRowState(updateChecking, updateResult, updateKnownVersion)
    val entries = listOf(
        // Language first: it is the one row that changes what every other row says.
        SettingsEntry(SettingsSection.THEME, stringResource(R.string.settings_language), "language locale translate") {
            QuickLaunchRow(
                label = stringResource(R.string.settings_language),
                value = languageLabel(),
                onClick = onOpenLanguage,
                leading = DelugeIcons.Language,
            )
        },
        // ---- Appearance ----
        SettingsEntry(SettingsSection.THEME, stringResource(R.string.settings_color_scheme), "theme color palette blue") {
            ColorSchemeRow(colorScheme, onSetColorScheme)
        },
        SettingsEntry(SettingsSection.THEME, stringResource(R.string.settings_preview)) {
            RowPreview(previewApp, iconSizeDp, labelSizeSp, font, fontFile, itemSpacingDp)
        },
        SettingsEntry(SettingsSection.ICONS_TEXT, stringResource(R.string.settings_icon_pack), "icons themed pack") {
            IconPackRow(
                iconPacks, iconPackPackage, showAppIcons, showFavoriteIcons, themedIcons,
                onSetIconPack, onSetShowAppIcons, onSetShowFavoriteIcons, onSetThemedIcons,
            )
        },
        SettingsEntry(SettingsSection.ICONS_TEXT, stringResource(R.string.settings_icons_favorites), "icons") {
            SwitchRow(stringResource(R.string.settings_icons_favorites), showFavoriteIcons, onSetShowFavoriteIcons)
        },
        SettingsEntry(SettingsSection.ICONS_TEXT, stringResource(R.string.settings_icons_app_list), "icons") {
            SwitchRow(stringResource(R.string.settings_icons_app_list), showAppIcons, onSetShowAppIcons)
        },
        SettingsEntry(SettingsSection.ICONS_TEXT, stringResource(R.string.settings_icon_size), "size") {
            SliderRow(
                label = stringResource(R.string.settings_icon_size),
                // Down to where an icon is still a recognisable shape. A list of names wants
                // the icon as a marker, not a picture, and how small is the reader's call.
                value = iconSizeDp.toFloat(), range = 12f..96f,
                valueLabel = { "${it.roundToInt()}dp" },
                onValueChange = { onSetIconSize(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.ICONS_TEXT, stringResource(R.string.settings_text_size), "font label size") {
            SliderRow(
                label = stringResource(R.string.settings_text_size),
                value = labelSizeSp.toFloat(), range = 10f..28f,
                valueLabel = { "${it.roundToInt()}sp" },
                onValueChange = { onSetLabelSize(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.ICONS_TEXT, stringResource(R.string.settings_favorite_spacing), "gap spacing", advanced = true) {
            SliderRow(
                label = stringResource(R.string.settings_favorite_spacing),
                value = itemSpacingDp.toFloat(), range = 0f..40f,
                valueLabel = { "${it.roundToInt()}dp" },
                onValueChange = { onSetItemSpacing(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.ICONS_TEXT, stringResource(R.string.settings_font), "typeface") {
            FontRow(font, fontFile, onSetFont, onPickFontFile)
        },
        SettingsEntry(SettingsSection.ICONS_TEXT, stringResource(R.string.settings_text_color), "colour", advanced = true) {
            TextColorRow(textColorMode, textColorCustom, onSetTextColorMode, onSetTextColorCustom)
        },
        SettingsEntry(SettingsSection.ICONS_TEXT, stringResource(R.string.icon_shape), "shape") {
            IconShapeRow(iconShape, onSetIconShape)
        },
        SettingsEntry(SettingsSection.LAYOUT, stringResource(R.string.settings_alignment_favorites), "align left right center") {
            AlignmentRow(stringResource(R.string.settings_alignment_favorites), alignment, onSetAlignment)
        },
        SettingsEntry(SettingsSection.LAYOUT, stringResource(R.string.settings_alignment_applist), "align left right center", advanced = true) {
            AlignmentRow(stringResource(R.string.settings_alignment_applist), appListAlignment, onSetAppListAlignment)
        },
        SettingsEntry(SettingsSection.LAYOUT, stringResource(R.string.settings_icon_side), "left right", advanced = true) {
            IconSideRow(iconSide, onSetIconSide)
        },
        SettingsEntry(SettingsSection.LAYOUT, stringResource(R.string.settings_show_names), "labels") {
            SwitchRow(stringResource(R.string.settings_show_names), showFavoriteLabels, onSetShowFavoriteLabels)
        },
        SettingsEntry(SettingsSection.LAYOUT, stringResource(R.string.settings_two_columns), "two columns portrait landscape") {
            BackupRow(
                label = stringResource(R.string.settings_two_columns),
                detail = stringResource(R.string.settings_two_columns_detail),
                onClick = { twoColumnDialog = true },
            )
        },
        SettingsEntry(
            SettingsSection.EFFECTS, stringResource(R.string.settings_dim_color), "colour wallpaper",
            // Only worth offering once something is actually dimmed.
            visible = dimHomeAlpha > 0f || dimWallpaperAlpha > 0f,
         advanced = true) { DimColorRow(dimColor, onSetDimColor) },
        SettingsEntry(SettingsSection.EFFECTS, stringResource(R.string.settings_dim_wallpaper), "dim wallpaper") {
            BackupRow(
                label = stringResource(R.string.settings_dim_wallpaper),
                detail = stringResource(R.string.settings_dim_wallpaper_detail),
                onClick = { dimDialog = true },
            )
        },
        SettingsEntry(SettingsSection.EFFECTS, stringResource(R.string.settings_wallpaper_blur), "blur background frosted", advanced = true) {
            SliderRow(
                label = stringResource(R.string.settings_wallpaper_blur),
                value = wallpaperBlurDp.toFloat(),
                range = 0f..80f,
                valueLabel = { "${it.roundToInt()}dp" },
                onValueChange = { onSetWallpaperBlur(it.roundToInt()) },
                step = 1f,
            )
        },
        SettingsEntry(SettingsSection.THEME, stringResource(R.string.settings_auto_dark), "dark night theme schedule") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_auto_dark),
                detail = stringResource(R.string.settings_auto_dark_detail),
                checked = autoDark, onCheckedChange = onSetAutoDark,
            )
        },
        SettingsEntry(
            SettingsSection.THEME, stringResource(R.string.settings_night_start),
            "dark night theme schedule start evening", visible = autoDark,
         advanced = true) {
            SliderRow(
                label = stringResource(R.string.settings_night_start),
                value = nightStartHour.toFloat(),
                range = 0f..23f,
                valueLabel = { String.format("%02d:00", it.roundToInt()) },
                onValueChange = { onSetNightStartHour(it.roundToInt()) },
                step = 1f,
            )
        },
        SettingsEntry(
            SettingsSection.THEME, stringResource(R.string.settings_night_end),
            "dark night theme schedule end morning", visible = autoDark,
         advanced = true) {
            SliderRow(
                label = stringResource(R.string.settings_night_end),
                value = nightEndHour.toFloat(),
                range = 0f..23f,
                valueLabel = { String.format("%02d:00", it.roundToInt()) },
                onValueChange = { onSetNightEndHour(it.roundToInt()) },
                step = 1f,
            )
        },
        SettingsEntry(SettingsSection.EFFECTS, stringResource(R.string.settings_reduce_motion), "animation motion") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_reduce_motion),
                detail = stringResource(R.string.settings_reduce_motion_detail),
                checked = reduceMotion, onCheckedChange = onSetReduceMotion,
            )
        },

        // ---- Home screen ----
        SettingsEntry(SettingsSection.HOME, stringResource(R.string.settings_favorites_source), "frequent most used manual") {
            FavoritesSourceRow(favoritesSource, frequentCount, onSetFavoritesSource, onSetFrequentCount)
        },
        SettingsEntry(SettingsSection.HOME, stringResource(R.string.settings_close_folder), "folder", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_close_folder),
                detail = stringResource(R.string.settings_close_folder_detail),
                checked = closeFolderOnLaunch, onCheckedChange = onSetCloseFolderOnLaunch,
            )
        },
        SettingsEntry(SettingsSection.HOME, stringResource(R.string.settings_status_bar), "status bar hide peek") {
            BackupRow(
                label = stringResource(R.string.settings_status_bar),
                detail = stringResource(R.string.settings_status_bar_detail),
                onClick = { statusBarDialog = true },
            )
        },
        SettingsEntry(SettingsSection.HOME, stringResource(R.string.settings_allow_rotation), "landscape rotate", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_allow_rotation),
                detail = stringResource(R.string.settings_allow_rotation_detail),
                checked = allowRotation, onCheckedChange = onSetAllowRotation,
            )
        },

        // ---- App list ----
        SettingsEntry(SettingsSection.SEARCH, stringResource(R.string.settings_search_bar), "search") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_search_bar),
                detail = stringResource(R.string.settings_search_bar_detail),
                checked = appListSearch, onCheckedChange = onSetAppListSearch,
            )
        },
        SettingsEntry(
            SettingsSection.SEARCH, stringResource(R.string.settings_auto_keyboard), "keyboard search",
            visible = appListSearch,
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_auto_keyboard),
                detail = stringResource(R.string.settings_auto_keyboard_detail),
                checked = autoKeyboard, onCheckedChange = onSetAutoKeyboard,
            )
        },
        SettingsEntry(
            SettingsSection.SEARCH, stringResource(R.string.settings_search_bar_bottom), "search position",
            visible = appListSearch,
         advanced = true) {
            SwitchRow(stringResource(R.string.settings_search_bar_bottom), appListSearchBottom, onSetAppListSearchBottom)
        },
        SettingsEntry(
            SettingsSection.SEARCH, stringResource(R.string.settings_search_hidden), "search hidden",
            visible = appListSearch,
         advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_search_hidden),
                detail = stringResource(R.string.settings_search_hidden_detail),
                checked = appListSearchHidden, onCheckedChange = onSetAppListSearchHidden,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST_LAYOUT, stringResource(R.string.settings_list_headers), "letters headings") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_list_headers),
                detail = stringResource(R.string.settings_list_headers_detail),
                checked = showListHeaders, onCheckedChange = onSetShowListHeaders,
            )
        },
        SettingsEntry(
            SettingsSection.APP_LIST_LAYOUT,
            stringResource(R.string.settings_applist_folders),
            "folders in list alphabet",
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_applist_folders),
                detail = stringResource(R.string.settings_applist_folders_detail),
                checked = appListFolders, onCheckedChange = onSetAppListFolders,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST_LAYOUT, stringResource(R.string.settings_settings_row), "settings shortcut row") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_settings_row),
                detail = stringResource(R.string.settings_settings_row_detail),
                checked = showSettingsRow, onCheckedChange = onSetShowSettingsRow,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST_LAYOUT, stringResource(R.string.settings_sort_by_usage), "frequent order") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_sort_by_usage),
                detail = stringResource(R.string.settings_sort_by_usage_detail),
                checked = sortByUsage, onCheckedChange = onSetSortByUsage,
            )
        },
        SettingsEntry(SettingsSection.APP_LIST_LAYOUT, stringResource(R.string.settings_auto_frequent), "frequent tag auto", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_auto_frequent),
                detail = stringResource(R.string.settings_auto_frequent_detail),
                checked = autoFrequentTag, onCheckedChange = onSetAutoFrequentTag,
            )
        },
        SettingsEntry(SettingsSection.AZ_STRIP, stringResource(R.string.settings_az_strip), "alphabet strip a-z") {
            BackupRow(
                label = stringResource(R.string.settings_az_strip),
                detail = stringResource(R.string.settings_az_strip_detail),
                onClick = { azDialog = true },
            )
        },
        SettingsEntry(SettingsSection.AZ_STRIP, stringResource(R.string.settings_edge_side), "a-z strip left right", advanced = true) {
            EdgeSideRow(edgeSide) { edgePreviewTick++; onSetEdgeSide(it) }
        },
        SettingsEntry(SettingsSection.SEARCH, stringResource(R.string.settings_web_search), "search web browser", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_web_search),
                detail = stringResource(R.string.settings_web_search_detail),
                checked = webSearchFallback, onCheckedChange = onSetWebSearchFallback,
            )
        },
        SettingsEntry(
            SettingsSection.SEARCH, stringResource(R.string.settings_swipe_up_search), "swipe up search keyboard",
            visible = swipeUpOpensList,
         advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_swipe_up_search),
                detail = stringResource(R.string.settings_swipe_up_search_detail),
                checked = swipeUpOpensSearch, onCheckedChange = onSetSwipeUpOpensSearch,
            )
        },
        SettingsEntry(SettingsSection.SECTIONS, stringResource(R.string.settings_stay_private_unlock), "private space unlock stay list", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_stay_private_unlock),
                detail = stringResource(R.string.settings_stay_private_unlock_detail),
                checked = stayAfterPrivateUnlock, onCheckedChange = onSetStayAfterPrivateUnlock,
            )
        },
        SettingsEntry(SettingsSection.SECTIONS, stringResource(R.string.settings_private_space_section), "private space profile section") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_private_space_section),
                detail = stringResource(R.string.settings_private_space_section_detail),
                checked = privateSpaceSection, onCheckedChange = onSetPrivateSpaceSection,
            )
        },
        SettingsEntry(SettingsSection.SECTIONS, stringResource(R.string.settings_recent_section), "recently installed new apps") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_recent_section),
                detail = stringResource(R.string.settings_recent_section_detail),
                checked = recentSection, onCheckedChange = onSetRecentSection,
            )
        },
        SettingsEntry(
            SettingsSection.SECTIONS, stringResource(R.string.settings_recent_section_count),
            "recently installed new apps", visible = recentSection,
         advanced = true) {
            SliderRow(
                label = stringResource(R.string.settings_recent_section_count),
                value = recentSectionCount.toFloat(),
                range = RECENT_SECTION_RANGE.first.toFloat()..RECENT_SECTION_RANGE.last.toFloat(),
                valueLabel = { it.roundToInt().toString() },
                onValueChange = { onSetRecentSectionCount(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.SECTIONS, stringResource(R.string.settings_mark_work_apps), "work profile duplicate apps", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_mark_work_apps),
                detail = stringResource(R.string.settings_mark_work_apps_detail),
                checked = markWorkApps, onCheckedChange = onSetMarkWorkApps,
            )
        },
        SettingsEntry(SettingsSection.AZ_STRIP, stringResource(R.string.settings_dim_others), "dim fade a-z scrub", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_dim_others),
                detail = stringResource(R.string.settings_dim_others_detail),
                checked = dimOthersScrubbing, onCheckedChange = onSetDimOthersScrubbing,
            )
        },
        SettingsEntry(SettingsSection.AZ_STRIP, stringResource(R.string.settings_hold_scroll), "a-z hold scroll", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_hold_scroll),
                detail = stringResource(R.string.settings_hold_scroll_detail),
                checked = holdScroll, onCheckedChange = onSetHoldScroll,
            )
        },
        SettingsEntry(
            SettingsSection.AZ_STRIP, stringResource(R.string.settings_hold_scroll_speed), "a-z hold scroll",
            visible = holdScroll,
         advanced = true) {
            SliderRow(
                label = stringResource(R.string.settings_hold_scroll_speed),
                value = holdScrollSpeed.toFloat(),
                range = HOLD_SCROLL_RANGE.first.toFloat()..HOLD_SCROLL_RANGE.last.toFloat(),
                valueLabel = { it.roundToInt().toString() },
                onValueChange = { onSetHoldScrollSpeed(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.AZ_STRIP, stringResource(R.string.settings_edge_band_only), "a-z strip height touch", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_edge_band_only),
                detail = stringResource(R.string.settings_edge_band_only_detail),
                checked = edgeZoneBandOnly, onCheckedChange = onSetEdgeZoneBandOnly,
            )
        },
        SettingsEntry(SettingsSection.AZ_STRIP, stringResource(R.string.settings_edge_inset_auto), "back gesture edge conflict automatic", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_edge_inset_auto),
                detail = stringResource(R.string.settings_edge_inset_auto_detail),
                checked = edgeZoneInsetAuto, onCheckedChange = onSetEdgeZoneInsetAuto,
            )
        },
        SettingsEntry(
            SettingsSection.AZ_STRIP, stringResource(R.string.settings_edge_inset), "back gesture edge conflict",
            visible = !edgeZoneInsetAuto,
         advanced = true) {
            SliderRow(
                label = stringResource(R.string.settings_edge_inset),
                value = edgeZoneInsetDp.toFloat(),
                range = EDGE_ZONE_INSET_RANGE.first.toFloat()..EDGE_ZONE_INSET_RANGE.last.toFloat(),
                valueLabel = { it.roundToInt().toString() },
                onValueChange = { onSetEdgeZoneInsetDp(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.AZ_STRIP, stringResource(R.string.settings_edge_zone_width), "a-z strip width", advanced = true) {
            SliderRow(
                label = stringResource(R.string.settings_edge_zone_width),
                value = edgeZoneWidthDp.toFloat(), range = 32f..96f,
                valueLabel = { "${it.roundToInt()}dp" },
                onValueChange = { edgePreviewTick++; onSetEdgeZoneWidth(it.roundToInt()) },
            )
        },
        SettingsEntry(SettingsSection.AZ_STRIP, stringResource(R.string.settings_last_letter), "letter lands end z", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_last_letter),
                detail = stringResource(R.string.settings_last_letter_detail),
                checked = lastLetterToLine, onCheckedChange = onSetLastLetterToLine,
            )
        },
        SettingsEntry(SettingsSection.AZ_STRIP, stringResource(R.string.settings_section_top), "letter lands scroll position", advanced = true) {
            SliderRow(
                label = stringResource(R.string.settings_section_top),
                value = sectionTopPercent.toFloat(),
                range = SECTION_TOP_RANGE.first.toFloat()..SECTION_TOP_RANGE.last.toFloat(),
                valueLabel = { "${it.roundToInt()}%" },
                onValueChange = { sectionPreviewTick++; onSetSectionTopPercent(it.toInt()) },
            )
        },
        SettingsEntry(SettingsSection.SECTIONS, stringResource(R.string.settings_hide_status_bar_applist), "status bar", advanced = true) {
            SwitchRow(
                stringResource(R.string.settings_hide_status_bar_applist),
                hideStatusBarAppList, onSetHideStatusBarAppList,
            )
        },

        // ---- Gestures ----
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_pinch_resize), "pinch zoom icons size", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_pinch_resize),
                detail = stringResource(R.string.settings_pinch_resize_detail),
                checked = pinchToResize, onCheckedChange = onSetPinchToResize,
            )
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_haptics), "vibrate") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.settings_haptics), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_haptics_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HapticFeedback.entries.forEach { option ->
                        FilledChip(stringResource(option.labelRes()), hapticFeedback == option) {
                            onSetHapticFeedback(option)
                        }
                    }
                }
            }
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_swipe_up_list), "swipe up") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_swipe_up_list),
                detail = stringResource(R.string.settings_swipe_up_list_detail),
                checked = swipeUpOpensList, onCheckedChange = onSetSwipeUpOpensList,
            )
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_swipe_shortcuts), "swipe shortcuts") {
            ShortcutSwipeRow(shortcutSwipe, onSetShortcutSwipe)
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_quick_launch_left), "swipe launch left") {
            CornerActionRow(
                summary = stringResource(R.string.settings_quick_launch_left),
                selected = quickLaunchLeftAction,
                onSelect = { action ->
                    // An app or a shortcut needs a target: the picker comes up for it,
                    // and nothing is stored until something is picked — backing out
                    // keeps whatever was there before. Anything else stores at once.
                    action.targetKind()?.let { kind ->
                        onOpenSlotPicker(SlotTarget.QUICK_LEFT, kind)
                    } ?: onSetQuickLaunchLeftAction(action)
                },
                // What it opens, under the action's own name, once there is something picked.
                picked = quickLaunchLeftLabel,
                freezeEnabled = freezeEnabled,
            )
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_quick_launch_right), "swipe launch right") {
            CornerActionRow(
                summary = stringResource(R.string.settings_quick_launch_right),
                selected = quickLaunchRightAction,
                onSelect = { action ->
                    action.targetKind()?.let { kind ->
                        onOpenSlotPicker(SlotTarget.QUICK_RIGHT, kind)
                    } ?: onSetQuickLaunchRightAction(action)
                },
                picked = quickLaunchRightLabel,
                freezeEnabled = freezeEnabled,
            )
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_corner_tap), "button shortcut corner tap") {
            CornerActionRow(
                summary = stringResource(R.string.settings_corner_tap),
                selected = cornerTapAction,
                onSelect = { action ->
                    action.targetKind()?.let { kind ->
                        onOpenSlotPicker(SlotTarget.CORNER_TAP, kind)
                    } ?: onSetCornerTapAction(action)
                },
                picked = cornerButtonLabel,
                freezeEnabled = freezeEnabled,
            )
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_corner_swipe), "button shortcut corner slide up") {
            CornerActionRow(
                summary = stringResource(R.string.settings_corner_swipe),
                selected = cornerSwipeAction,
                onSelect = { action ->
                    action.targetKind()?.let { kind ->
                        onOpenSlotPicker(SlotTarget.CORNER_SWIPE, kind)
                    } ?: onSetCornerSwipeAction(action)
                },
                picked = cornerSwipeLabel,
                freezeEnabled = freezeEnabled,
            )
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_double_tap_lock), "lock screen") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_double_tap_lock),
                detail = stringResource(R.string.settings_double_tap_lock_detail),
                checked = doubleTapToLock, onCheckedChange = onSetDoubleTapToLock,
            )
        },
        SettingsEntry(
            SettingsSection.GESTURES, stringResource(R.string.settings_lock_setup), "lock permission",
            // Switching it on does nothing at all without a way to lock, so the way to
            // grant one belongs right here rather than buried in a toast later.
            visible = doubleTapToLock && !lockGestureReady,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                Text(
                    stringResource(R.string.settings_lock_setup_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(8.dp))
                AccessibilityActions(onOpenAccessibilitySettings, onOpenAppInfo)
            }
        },
        SettingsEntry(SettingsSection.GESTURES, stringResource(R.string.settings_shade_gesture), "notification shade pull", advanced = true) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                if (!shadeGestureReady) AccessibilityActions(onOpenAccessibilitySettings, onOpenAppInfo)
            }
        },

        // ---- Notifications ----
        SettingsEntry(SettingsSection.NOTIFICATIONS, stringResource(R.string.settings_notification_badges), "count badge icons") {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_notification_badges),
                detail = stringResource(R.string.settings_notification_badges_detail),
                checked = notificationBadges, onCheckedChange = onSetNotificationBadges,
            )
        },
        SettingsEntry(
            SettingsSection.NOTIFICATIONS,
            stringResource(R.string.settings_notification_preview),
            "notification text preview message",
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_notification_preview),
                detail = stringResource(R.string.settings_notification_preview_detail),
                checked = notificationPreview, onCheckedChange = onSetNotificationPreview,
            )
        },
        SettingsEntry(
            SettingsSection.NOTIFICATIONS,
            stringResource(R.string.settings_notification_text_size),
            "notification text preview message",
            visible = notificationPreview,
         advanced = true) {
            SliderRow(
                label = stringResource(R.string.settings_notification_text_size),
                value = notificationTextSp.toFloat(),
                range = NOTIFICATION_TEXT_SP_RANGE.first.toFloat()..
                    NOTIFICATION_TEXT_SP_RANGE.last.toFloat(),
                valueLabel = { it.roundToInt().toString() },
                onValueChange = { onSetNotificationTextSp(it.toInt()) },
            )
        },
        SettingsEntry(
            SettingsSection.NOTIFICATIONS,
            stringResource(R.string.settings_notification_panel),
            "panel shade notifications replies",
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_notification_panel),
                detail = stringResource(R.string.settings_notification_panel_detail),
                checked = notificationPanel, onCheckedChange = onSetNotificationPanel,
            )
        },
        SettingsEntry(
            SettingsSection.NOTIFICATIONS,
            stringResource(R.string.settings_notification_panel_open),
            "open panel",
            visible = notificationPanel,
        ) {
            BackupRow(
                label = stringResource(R.string.settings_notification_panel_open),
                detail = stringResource(R.string.settings_notification_panel_open_detail),
                onClick = onOpenNotificationPanel,
            )
        },
        SettingsEntry(SettingsSection.NOTIFICATIONS, stringResource(R.string.settings_now_playing_show), "media music") {
            SwitchRow(stringResource(R.string.settings_now_playing_show), nowPlayingEnabled, onSetNowPlayingEnabled)
        },
        SettingsEntry(SettingsSection.EFFECTS, stringResource(R.string.settings_dim_paused_apps), "paused suspended grey dim", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_dim_paused_apps),
                detail = stringResource(R.string.settings_dim_paused_apps_detail),
                checked = dimPausedApps, onCheckedChange = onSetDimPausedApps,
            )
        },
        SettingsEntry(
            SettingsSection.NOTIFICATIONS, stringResource(R.string.settings_notification_access_detail), "permission access",
            visible = nowPlayingEnabled || notificationBadges || notificationPreview || notificationPanel,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                FilledChip(
                    stringResource(R.string.settings_open_settings),
                    selected = false,
                    onClick = onOpenNotificationSettings,
                )
            }
        },

        // ---- Apps ----
        SettingsEntry(SettingsSection.APPS, stringResource(R.string.settings_default_launcher), "home default launcher set") {
            BackupRow(
                label = stringResource(R.string.settings_default_launcher),
                detail = if (isDefaultLauncher) {
                    stringResource(R.string.settings_default_launcher_yes)
                } else {
                    stringResource(R.string.settings_default_launcher_no)
                },
                onClick = onOpenHomeSettings,
            )
        },
        SettingsEntry(SettingsSection.APPS, stringResource(R.string.settings_favorites), "manage") {
            BackupRow(
                label = stringResource(R.string.settings_favorites),
                detail = stringResource(R.string.settings_favorites_detail),
                onClick = onOpenFavorites,
                leading = Icons.Filled.Favorite,
            )
        },
        SettingsEntry(
            SettingsSection.APPS,
            stringResource(R.string.settings_favorite_rules),
            "rules wi-fi wifi time headphones daily opens situational",
        ) {
            BackupRow(
                label = stringResource(R.string.settings_favorite_rules),
                detail = if (favoriteRuleCount > 0) {
                    pluralStringResource(
                        R.plurals.settings_favorite_rules_on,
                        favoriteRuleCount,
                        favoriteRuleCount,
                    )
                } else {
                    stringResource(R.string.settings_favorite_rules_detail)
                },
                onClick = onOpenFavoriteRules,
                leading = DelugeIcons.Tune,
            )
        },
        SettingsEntry(SettingsSection.HOME, stringResource(R.string.settings_compact_tap), "tap area accidental launch empty", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_compact_tap),
                detail = stringResource(R.string.settings_compact_tap_detail),
                checked = compactTapArea, onCheckedChange = onSetCompactTapArea,
            )
        },
        SettingsEntry(SettingsSection.EFFECTS, stringResource(R.string.settings_text_shadow), "contrast readability wallpaper", advanced = true) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_text_shadow),
                detail = stringResource(R.string.settings_text_shadow_detail),
                checked = textShadow, onCheckedChange = onSetTextShadow,
            )
        },
        SettingsEntry(
            SettingsSection.EFFECTS, stringResource(R.string.settings_text_shadow_strength),
            "contrast readability wallpaper", visible = textShadow,
         advanced = true) {
            SliderRow(
                label = stringResource(R.string.settings_text_shadow_strength),
                value = textShadowStrength.toFloat(),
                range = SHADOW_STRENGTH_RANGE.first.toFloat()..SHADOW_STRENGTH_RANGE.last.toFloat(),
                valueLabel = { it.roundToInt().toString() },
                onValueChange = { onSetTextShadowStrength(it.toInt()) },
            )
        },
        SettingsEntry(
            SettingsSection.APPS, stringResource(R.string.settings_frequent_excluded),
            "most used favorites exclude block",
            visible = frequentExcludedVisible,
         advanced = true) {
            BackupRow(
                label = stringResource(R.string.settings_frequent_excluded),
                detail = if (frequentExcludedCount == 0) {
                    stringResource(R.string.settings_frequent_excluded_none)
                } else {
                    pluralStringResource(
                        R.plurals.settings_frequent_excluded_count,
                        frequentExcludedCount,
                        frequentExcludedCount,
                    )
                },
                onClick = onOpenFrequentExcluded,
                leading = Icons.Filled.FavoriteBorder,
            )
        },
        SettingsEntry(SettingsSection.PROTECTION, stringResource(R.string.settings_hidden_apps), "hide") {
            BackupRow(
                label = stringResource(R.string.settings_hidden_apps),
                detail = if (hiddenCount == 0) stringResource(R.string.settings_hidden_none)
                    else stringResource(R.string.settings_hidden_count, hiddenCount),
                onClick = onOpenHiddenApps,
                leading = DelugeIcons.VisibilityOff,
            )
        },
        SettingsEntry(
            SettingsSection.PROTECTION, stringResource(R.string.settings_app_lock_timeout),
            "app lock unlock credential biometric fingerprint timeout minutes",
        ) {
            BackupRow(
                label = stringResource(R.string.settings_app_lock_timeout),
                detail = appLockTimeoutLabel(appLockTimeout),
                onClick = { appLockDialog = true },
                leading = Icons.Filled.Lock,
            )
        },
        SettingsEntry(
            SettingsSection.PROTECTION, stringResource(R.string.settings_app_lock_screen_off),
            "app lock screen off relock forget",
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_app_lock_screen_off),
                detail = stringResource(R.string.settings_app_lock_screen_off_detail),
                checked = appLockScreenOff,
                onCheckedChange = onSetAppLockScreenOff,
            )
        },
        // Beside hiding, which is the launcher's own idea of hidden; this one is the system's.
        // The master switch stays visible when off, since it is the only way back on.
        SettingsEntry(
            SettingsSection.PROTECTION, stringResource(R.string.settings_freeze_enable),
            "freeze frozen disable hide suspend stop root shizuku enable feature",
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_freeze_enable),
                detail = stringResource(R.string.settings_freeze_enable_detail),
                checked = freezeEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) onSetFreezeEnabled(true) else disablingFreeze = true
                },
            )
        },
        SettingsEntry(
            SettingsSection.PROTECTION, stringResource(R.string.settings_freeze_apps),
            "freeze frozen disable hide suspend stop root shizuku", visible = freezeEnabled,
        ) {
            BackupRow(
                label = stringResource(R.string.settings_freeze_apps),
                detail = if (frozenCount == 0) stringResource(R.string.settings_freeze_none)
                    else stringResource(R.string.settings_freeze_count, frozenCount),
                onClick = onOpenFreezeApps,
                leading = Icons.Filled.Lock,
            )
        },
        // These live as entries rather than as hand-drawn items in the list below, so the
        // search box can find them: a row written straight into the LazyColumn is invisible to
        // the search, which only ever looks at the entries.
        SettingsEntry(SettingsSection.BACKUP, stringResource(R.string.settings_export), "backup export save share") {
            BackupRow(
                label = stringResource(R.string.settings_export),
                detail = stringResource(R.string.settings_export_detail),
                onClick = onExportSettings,
            )
        },
        SettingsEntry(SettingsSection.BACKUP, stringResource(R.string.settings_import), "backup import restore load") {
            BackupRow(
                label = stringResource(R.string.settings_import),
                detail = stringResource(R.string.settings_import_detail),
                onClick = onImportSettings,
            )
        },
        // The app name is pinned first in About. A tap opens the project's page, where new
        // releases live; holding it for five seconds unlocks the hidden developer switch.
        SettingsEntry(SettingsSection.ABOUT, stringResource(R.string.app_name), "version about deluge launcher github") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .developerUnlock(
                        hapticFeedback = hapticFeedback,
                        onTap = { openProjectPage() },
                        onUnlock = {
                            developerUnlocked = true
                            Toast.makeText(
                                context,
                                context.getString(R.string.developer_unlocked),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        SettingsEntry(
            SettingsSection.ABOUT, stringResource(R.string.settings_developer_mode),
            "developer screenshot disguise", visible = developerUnlocked,
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_developer_mode),
                detail = stringResource(R.string.settings_developer_mode_detail),
                checked = screenshotMode, onCheckedChange = onSetScreenshotMode,
            )
        },
        SettingsEntry(SettingsSection.ABOUT, stringResource(R.string.settings_changelog), "changelog whats new release notes version history") {
            QuickLaunchRow(
                label = stringResource(R.string.settings_changelog),
                value = stringResource(R.string.settings_changelog_detail),
                onClick = onOpenChangelog,
                leading = Icons.Filled.Info,
            )
        },
        SettingsEntry(
            SettingsSection.ABOUT, stringResource(R.string.settings_update_check),
            "update upgrade version newer release download latest",
        ) {
            UpdateRow(state = updateState, onCheck = onCheckForUpdates)
        },
        // Right after the check: auto-check is opt-in and off by default.
        SettingsEntry(
            SettingsSection.ABOUT, stringResource(R.string.settings_update_auto_check),
            "update upgrade version newer release automatic check auto",
        ) {
            SwitchRowWithDetail(
                label = stringResource(R.string.settings_update_auto_check),
                detail = stringResource(R.string.settings_update_auto_check_detail),
                checked = updateAutoCheck,
                onCheckedChange = onSetUpdateAutoCheck,
            )
        },
        // Only while there is something to get: a second row that means "go and fetch it"
        // rather than leaving the check row to mean two different things depending on state.
        SettingsEntry(
            SettingsSection.ABOUT,
            stringResource(R.string.settings_update_get, updateState.versionLabel()),
            "update upgrade download release github",
            visible = updateState is UpdateRowState.Available,
        ) {
            QuickLaunchRow(
                label = stringResource(R.string.settings_update_get, updateState.versionLabel()),
                value = stringResource(R.string.settings_update_get_detail),
                onClick = onOpenReleasePage,
                leading = Icons.Filled.KeyboardArrowDown,
            )
        },
        // A launcher draws other apps' widgets with their own code, so the trace is often
        // the only thing that says whose crash it was. An entry like any other, so the
        // search box finds it too.
        SettingsEntry(
            SettingsSection.ABOUT, stringResource(R.string.settings_crash_copy),
            "crash log report bug trace",
            visible = crashText != null && !crashCleared,
         advanced = true) {
            BackupRow(
                label = stringResource(R.string.settings_crash_copy),
                detail = stringResource(R.string.settings_crash_detail),
                onClick = {
                    crashText?.let { crash ->
                        clipboard.setPrimaryClip(ClipData.newPlainText("Deluge crash", crash))
                        Toast.makeText(context, R.string.settings_crash_copied, Toast.LENGTH_SHORT).show()
                        CrashLog.clear(context)
                        crashCleared = true
                    }
                },
            )
        },
    ).filter { it.visible }

    // What the list below actually draws: the matches while something is typed, the open
    // card otherwise, and nothing at all on the top menu. Advanced rows stay out of the way
    // unless the switch is on, but a search still reaches them: hiding a row from the list is
    // not the same as pretending it does not exist.
    val entriesToShow = remember(entries, query, openCard, settingsAdvanced) {
        val matching = when {
            query.isNotBlank() -> entries.filter { it.matches(query) }
            openCard != null -> entries.filter {
                it.section.card == openCard && (settingsAdvanced || !it.advanced)
            }
            else -> emptyList()
        }
        matching.groupBy { it.section }.toList()
    }

    Box(Modifier.fillMaxSize()) {
    if (dimPreviewReveal > 0f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(dimColor).copy(alpha = dimPreviewValue * dimPreviewReveal)),
        )
    }
    Scaffold(
        containerColor = surface.copy(alpha = 1f - dimPreviewReveal),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(openCard?.labelRes ?: R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (openCard != null) openCard = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surface.copy(alpha = 1f - dimPreviewReveal),
                ),
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
                // Filled, not outlined: a launcher's settings search sits against a
                // wallpaper-tinted surface, and a floating outlined box over it reads as a
                // form field pasted in rather than as part of the screen.
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.icon_picker_clear_search),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (openCard == null && query.isBlank()) {
                SettingsCard.entries.chunked(2).forEach { line ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            line.forEach { card ->
                                SectionCard(
                                    card = card,
                                    count = entries.count { it.section.card == card },
                                    onClick = { openCard = card },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (line.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (openCard != null && query.isBlank() &&
                entries.any { it.section.card == openCard && it.advanced }
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        ),
                    ) {
                        SwitchRowWithDetail(
                            label = stringResource(R.string.settings_advanced),
                            detail = stringResource(R.string.settings_advanced_detail),
                            checked = settingsAdvanced,
                            onCheckedChange = onSetSettingsAdvanced,
                        )
                    }
                }
            }

            entriesToShow.forEach { (section, rows) ->
                item {
                    Section(section, rows.size) {
                        rows.forEachIndexed { index, entry ->
                            if (index > 0) RowDivider()
                            entry.content()
                        }
                    }
                }
            }

            if (query.isNotBlank() && entriesToShow.isEmpty()) item {
                Text(
                    stringResource(R.string.settings_search_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        }
    }
        if (sectionPreviewAlpha > 0f) {
            // Where the top of a scrubbed letter's section comes to rest, drawn across the
            // screen it is measured against.
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .fillMaxHeight(sectionTopPercent / 100f),
                contentAlignment = Alignment.BottomStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f * sectionPreviewAlpha)
                        ),
                )
            }
        }
        if (edgePreviewAlpha > 0f) {
            val stripe = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f * edgePreviewAlpha)
            if (edgeSide != EdgeSide.RIGHT) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(edgeZoneWidthDp.dp)
                        .fillMaxHeight()
                        .background(stripe),
                )
            }
            if (edgeSide != EdgeSide.LEFT) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(edgeZoneWidthDp.dp)
                        .fillMaxHeight()
                        .background(stripe),
                )
            }
        }
    }

    if (appLockDialog) {
        AlertDialog(
            onDismissRequest = { appLockDialog = false },
            title = { Text(stringResource(R.string.settings_app_lock_timeout)) },
            text = {
                Column {
                    APP_LOCK_TIMEOUTS.forEach { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetAppLockTimeout(seconds)
                                    appLockDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = appLockTimeout == seconds,
                                onClick = {
                                    onSetAppLockTimeout(seconds)
                                    appLockDialog = false
                                },
                            )
                            Text(appLockTimeoutLabel(seconds))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { appLockDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (statusBarDialog) {
        AlertDialog(
            onDismissRequest = { statusBarDialog = false },
            title = { Text(stringResource(R.string.settings_status_bar)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SwitchRow(
                        stringResource(R.string.settings_hide_status_bar),
                        hideStatusBar,
                        onSetHideStatusBar,
                    )
                    SwitchRow(
                        stringResource(R.string.settings_hide_status_bar_applist),
                        hideStatusBarAppList,
                        onSetHideStatusBarAppList,
                    )
                    if (hideStatusBar || hideStatusBarAppList) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        SliderRow(
                            label = stringResource(R.string.settings_status_bar_timeout),
                            value = statusBarPeekSeconds.toFloat(),
                            range = 1f..30f,
                            valueLabel = { "${it.roundToInt()}s" },
                            onValueChange = { onSetStatusBarPeekSeconds(it.roundToInt()) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { statusBarDialog = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }

    if (dimDialog) {
        AlertDialog(
            onDismissRequest = { dimDialog = false },
            title = { Text(stringResource(R.string.settings_dim_wallpaper)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SliderRow(
                        label = stringResource(R.string.settings_dim_home),
                        value = dimHomeAlpha,
                        range = 0f..0.85f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        onValueChange = {
                            val next = (it * 100).roundToInt() / 100f
                            dimPreviewValue = next
                            dimPreviewTick++
                            onSetDimHome(next)
                        },
                        step = 0.01f,
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    SliderRow(
                        label = stringResource(R.string.settings_dim_applist),
                        value = dimWallpaperAlpha,
                        range = 0f..0.85f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        onValueChange = {
                            val next = (it * 100).roundToInt() / 100f
                            dimPreviewValue = next
                            dimPreviewTick++
                            onSetDimWallpaper(next)
                        },
                        step = 0.01f,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dimDialog = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }

    if (twoColumnDialog) {
        AlertDialog(
            onDismissRequest = { twoColumnDialog = false },
            title = { Text(stringResource(R.string.settings_two_columns)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_two_column_home_portrait),
                        detail = stringResource(R.string.settings_two_column_home_portrait_detail),
                        checked = homeTwoColumnPortrait,
                        onCheckedChange = { onSetTwoColumn(TwoColumnSlot.HOME_PORTRAIT, it) },
                    )
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_two_column_home_landscape),
                        detail = landscapeDetail(stringResource(R.string.settings_two_column_home_landscape_detail)),
                        checked = homeTwoColumnLandscape,
                        onCheckedChange = { onSetTwoColumn(TwoColumnSlot.HOME_LANDSCAPE, it) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_two_column_list_portrait),
                        detail = stringResource(R.string.settings_two_column_list_portrait_detail),
                        checked = listTwoColumnPortrait,
                        onCheckedChange = { onSetTwoColumn(TwoColumnSlot.LIST_PORTRAIT, it) },
                    )
                    SwitchRowWithDetail(
                        label = stringResource(R.string.settings_two_column_list_landscape),
                        detail = landscapeDetail(stringResource(R.string.settings_two_column_list_landscape_detail)),
                        checked = listTwoColumnLandscape,
                        onCheckedChange = { onSetTwoColumn(TwoColumnSlot.LIST_LANDSCAPE, it) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { twoColumnDialog = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }

    if (azDialog) {
        AlertDialog(
            onDismissRequest = { azDialog = false },
            title = { Text(stringResource(R.string.settings_az_strip)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.settings_section_home),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AzStripVisibility.entries.forEach { option ->
                            FilledChip(stringResource(option.labelRes()), azStripVisibility == option) {
                                onSetAzStripVisibility(option)
                            }
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    SwitchRow(
                        stringResource(R.string.settings_show_alphabet),
                        showAlphabet,
                        onSetShowAlphabet,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { azDialog = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }

    if (disablingFreeze) {
        AlertDialog(
            onDismissRequest = { disablingFreeze = false },
            title = { Text(stringResource(R.string.freeze_disable_title)) },
            text = { Text(stringResource(R.string.freeze_disable_body)) },
            confirmButton = {
                TextButton(onClick = {
                    disablingFreeze = false
                    onSetFreezeEnabled(false)
                }) {
                    Text(stringResource(R.string.freeze_disable_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { disablingFreeze = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    }
}

/** The lock's choices, in seconds; 0 asks on every tap. */
private val APP_LOCK_TIMEOUTS = listOf(0, 60, 300, 900, 1800)

@Composable
private fun appLockTimeoutLabel(seconds: Int): String =
    if (seconds <= 0) stringResource(R.string.settings_app_lock_every_time)
    else stringResource(R.string.settings_app_lock_minutes, seconds / 60)

@Composable
private fun SectionCard(
    card: SettingsCard,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (container, content) = sectionColors(card)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
            .heightIn(min = 132.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(container, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    card.icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(card.labelRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.settings_section_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

/** One tonal pair per top-level card, so the menu reads as a set rather than six grey cards. */
@Composable
private fun sectionColors(card: SettingsCard): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (card) {
        SettingsCard.APPEARANCE, SettingsCard.APPS ->
            colors.primaryContainer to colors.onPrimaryContainer
        SettingsCard.HOME, SettingsCard.GESTURES ->
            colors.primaryContainer.copy(alpha = 0.72f) to colors.onPrimaryContainer
        SettingsCard.APP_LIST, SettingsCard.SYSTEM ->
            colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
}

@Composable
private fun Section(
    section: SettingsSection,
    count: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (container, contentColor) = sectionColors(section.card)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    section.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(section.labelRes).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.settings_section_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(horizontal = 20.dp),
    )
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
        // The row is the target, so the switch itself is not one. With both taking a tap, a
        // press on the switch toggled it and then the row toggled it back, which read as a
        // switch that would not stay where it was put.
        Switch(
            checked = checked,
            onCheckedChange = null,
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
        // See SwitchRow: the row owns the tap, so the switch does not take one of its own.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

/** Slider plus a pair of steppers, since dragging to an exact value is fiddly. */
/** Shared with the clock widget's own settings, so a size is set the same way everywhere. */
/**
 * The value under the finger belongs to the row, not to the store. Every movement of a drag
 * used to be handed straight upstream — a preferences write per frame, each one answered with
 * a fresh set of settings and a rebuild of the whole screen holding the slider. The row now
 * follows the finger itself and hands upstream at most one value per [SLIDER_COMMIT_MS], plus
 * whatever the finger lands on when it lifts. [valueLabel] follows the finger either way.
 */
@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onValueChange: (Float) -> Unit,
    step: Float = 1f,
) {
    val send = rememberUpdatedState(onValueChange)
    // What the row draws. The finger is the authority while it is down.
    var local by remember { mutableFloatStateOf(value) }
    // The last value this row handed upstream, so its echo is recognised as an answer rather
    // than as news. A value that is not the one we sent came from somewhere else — an import,
    // a reset, the stepper on another row — and is adopted.
    var written by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }

    fun hand(v: Float) {
        written = v
        local = v
        send.value(v)
    }

    LaunchedEffect(value) {
        if (value != written) local = value
    }
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        // Trailing edge only: where the finger is, never where it has been.
        while (true) {
            delay(SLIDER_COMMIT_MS)
            if (local != written) hand(local)
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { hand((local - step).coerceIn(range)) },
                enabled = local > range.start,
            ) {
                Icon(DelugeIcons.Remove, contentDescription = stringResource(R.string.settings_less))
            }
            Text(
                valueLabel(local),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(
                onClick = { hand((local + step).coerceIn(range)) },
                enabled = local < range.endInclusive,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_more))
            }
        }
        Slider(
            value = local,
            onValueChange = {
                dragging = true
                local = it
            },
            onValueChangeFinished = {
                dragging = false
                // The floor has to be the last thing stored, whatever the throttle last sent.
                if (local != written) hand(local)
            },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

/** How often a dragged slider is allowed to reach the store. */
private const val SLIDER_COMMIT_MS = 120L

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPackRow(
    packs: List<IconPackRepository.IconPackInfo>,
    selected: String?,
    showIcons: Boolean,
    showFavoriteIcons: Boolean,
    themed: Boolean,
    onSelect: (String?) -> Unit,
    onSetShowIcons: (Boolean) -> Unit,
    onSetShowFavoriteIcons: (Boolean) -> Unit,
    onSetThemed: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_icon_pack), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledChip(
                stringResource(R.string.settings_icon_pack_default),
                showIcons && !themed && selected == null,
            ) {
                onSetShowIcons(true)
                onSetShowFavoriteIcons(true)
                onSetThemed(false)
                onSelect(null)
            }
            // A chip rather than a switch: themed icons and an icon pack are two answers to
            // the same question, and a switch beside the packs would let you pick both and
            // then wonder which won.
            FilledChip(stringResource(R.string.settings_icon_pack_themed), showIcons && themed) {
                onSetShowIcons(true)
                onSelect(null)
                onSetThemed(true)
            }
            packs.forEach { pack ->
                FilledChip(pack.label, showIcons && !themed && selected == pack.packageName) {
                    onSetShowIcons(true)
                    onSetThemed(false)
                    onSelect(pack.packageName)
                }
            }
            // Not a pack but a choice about packs: no icons anywhere, in one tap. The two
            // switches below can say the same thing and more — either surface on its own —
            // but turning both off is the common answer and should not take two.
            FilledChip(
                stringResource(R.string.settings_icon_pack_no_icons),
                !showIcons && !showFavoriteIcons,
            ) {
                onSetShowIcons(false)
                onSetShowFavoriteIcons(false)
            }
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

/**
 * The palette picker: one flat swatch per scheme, in the scheme's own colour, so the choice is
 * visible before it is made.
 *
 * It wraps. Nine swatches with their names are wider than a phone, and a single row simply cuts
 * off whatever does not fit — green onward ran off the right edge and read as if those schemes
 * did not exist. Wallpaper has no colour of its own until the system hands it one, so its
 * swatch shows the palette the wallpaper is giving the launcher right now.
 */
@Composable
private fun ColorSchemeRow(selected: ColorSchemeChoice, onSelect: (ColorSchemeChoice) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_color_scheme), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_color_scheme_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        // A fixed five-column grid rather than a wrapping row. A flow packs each line to the
        // width and leaves the lines' edges not meeting, which is what read as crooked: every
        // row here has the same columns, and a short last row is padded with empty ones so its
        // swatches stay under the swatches above.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ColorSchemeChoice.entries.chunked(SWATCHES_PER_ROW).forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    line.forEach { choice ->
                        SchemeSwatch(
                            choice = choice,
                            isSelected = choice == selected,
                            onClick = { onSelect(choice) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(SWATCHES_PER_ROW - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One dot and its name, sharing the row equally so every column lines up. */
@Composable
private fun SchemeSwatch(
    choice: ColorSchemeChoice,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(schemeSwatchColor(choice), CircleShape)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(choice.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The colour a scheme's swatch is drawn in: one flat Material tone per hue.
 *
 * Flat rather than a gradient — a two-tone dot reads as a shade of its own, not as the palette,
 * and nine gradients side by side are harder to tell apart than nine solid colours. These are the
 * familiar Material hues rather than the muted tones the theme itself is built from, so the
 * swatch is recognisable at a glance.
 */
@Composable
private fun schemeSwatchColor(choice: ColorSchemeChoice): Color = when (choice) {
    ColorSchemeChoice.DELUGE_BLUE -> Color(0xFF2196F3)
    ColorSchemeChoice.DYNAMIC -> rememberWallpaperPalette()?.primary ?: Color(0xFF2196F3)
    ColorSchemeChoice.RED -> Color(0xFFF44336)
    ColorSchemeChoice.ORANGE -> Color(0xFFFF9800)
    ColorSchemeChoice.YELLOW -> Color(0xFFFBC02D)
    ColorSchemeChoice.GREEN -> Color(0xFF4CAF50)
    ColorSchemeChoice.TEAL -> Color(0xFF009688)
    ColorSchemeChoice.PURPLE -> Color(0xFF9C27B0)
    ColorSchemeChoice.PINK -> Color(0xFFE91E63)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontRow(
    selected: AppFont,
    fontFile: String?,
    onSelect: (AppFont) -> Unit,
    onPickFile: (Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPickFile(uri)
    }
    // Font files are served under a pile of inconsistent mime types — font/ttf, x-font-ttf,
    // application/octet-stream, sometimes nothing at all — so the filter would hide the file
    // as often as it helped.
    fun open() = picker.launch(arrayOf("*/*"))

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_font), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFont.entries.forEach { f ->
                // Each chip is rendered in the font it selects, so the choice previews itself.
                FilledChip(
                    label = stringResource(f.labelRes()),
                    selected = selected == f,
                    fontFamily = fontFamilyOf(f, fontFile),
                    onClick = {
                        // Nothing to select until there is a file, so the first tap asks for one.
                        if (f == AppFont.CUSTOM && fontFile == null) open() else onSelect(f)
                    },
                )
            }
        }
        if (selected == AppFont.CUSTOM) {
            Text(
                stringResource(R.string.font_custom_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = { open() }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.font_custom_pick))
            }
        }
    }
}

@Composable
private fun RowPreview(
    app: AppInfo?,
    iconSizeDp: Int,
    labelSizeSp: Int,
    font: AppFont,
    fontFile: String?,
    itemSpacingDp: Int,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            stringResource(R.string.settings_preview),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        // Two rows, because one cannot show the gap between them: spacing was the only
        // thing on this screen with no way to see what the number meant.
        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            repeat(2) { index ->
                if (index > 0) Spacer(Modifier.height(itemSpacingDp.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app != null) {
                        AppIcon(app = app, sizeDp = iconSizeDp)
                        Spacer(Modifier.width(16.dp))
                        Text(app.label, fontSize = labelSizeSp.sp, fontFamily = fontFamilyOf(font, fontFile))
                    } else {
                        Text(
                            stringResource(R.string.settings_preview_sample),
                            fontSize = labelSizeSp.sp,
                            fontFamily = fontFamilyOf(font, fontFile),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconShapeRow(selected: IconShape, onSelect: (IconShape) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.icon_shape), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.icon_shape_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconShape.entries.forEach { shape ->
                FilledChip(stringResource(shape.labelRes()), selected == shape) { onSelect(shape) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextColorRow(
    selected: TextColorMode,
    customArgb: Int,
    onSelect: (TextColorMode) -> Unit,
    onSetCustom: (Int) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_text_color), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_text_color_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        // Wraps: five chips no longer fit on one line where three did, and a plain Row
        // answered that by breaking the last label down the screen a letter at a time.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextColorMode.entries.forEach { mode ->
                FilledChip(stringResource(mode.labelRes()), selected == mode) {
                    onSelect(mode)
                    if (mode == TextColorMode.CUSTOM) picking = true
                }
            }
        }
        if (selected == TextColorMode.CUSTOM) {
            TextButton(onClick = { picking = true }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.text_color_pick))
            }
        }
    }

    if (picking) {
        ColorPickerDialog(
            initial = customArgb,
            onConfirm = { onSetCustom(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/**
 * The cards on the first screen. Six rather than one per section: a section is a group of
 * rows, not a destination of its own, and the old menu made the owner read eight headings
 * before reaching the one they wanted.
 */
private enum class SettingsCard(@StringRes val labelRes: Int, val icon: ImageVector) {
    APPEARANCE(R.string.settings_section_appearance, Icons.Filled.Star),
    HOME(R.string.settings_section_home, Icons.Filled.Home),
    APP_LIST(R.string.settings_section_app_list, Icons.Filled.List),
    GESTURES(R.string.settings_section_gestures, Icons.Filled.Build),
    APPS(R.string.settings_section_apps_privacy, DelugeIcons.Checklist),
    SYSTEM(R.string.settings_section_system, Icons.Filled.Settings),
}

/**
 * A group of rows inside a card. The card is the destination; these are the headings the
 * owner scrolls past, which is why Appearance can hold Theme, Icons & text, Layout and
 * Effects without turning each of them into a top-level card.
 */
private enum class SettingsSection(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val card: SettingsCard,
) {
    THEME(R.string.settings_group_theme, Icons.Filled.Star, SettingsCard.APPEARANCE),
    ICONS_TEXT(R.string.settings_group_icons_text, Icons.Filled.Star, SettingsCard.APPEARANCE),
    LAYOUT(R.string.settings_group_layout, Icons.Filled.Build, SettingsCard.APPEARANCE),
    EFFECTS(R.string.settings_group_effects, Icons.Filled.Star, SettingsCard.APPEARANCE),
    HOME(R.string.settings_section_home, Icons.Filled.Home, SettingsCard.HOME),
    SEARCH(R.string.settings_group_search, Icons.Filled.Search, SettingsCard.APP_LIST),
    APP_LIST_LAYOUT(R.string.settings_group_sorting, Icons.Filled.List, SettingsCard.APP_LIST),
    AZ_STRIP(R.string.settings_group_az_strip, Icons.Filled.List, SettingsCard.APP_LIST),
    SECTIONS(R.string.settings_group_sections, DelugeIcons.Checklist, SettingsCard.APP_LIST),
    GESTURES(R.string.settings_section_gestures, Icons.Filled.Build, SettingsCard.GESTURES),
    NOTIFICATIONS(R.string.settings_section_notifications, Icons.Filled.Notifications, SettingsCard.SYSTEM),
    APPS(R.string.settings_section_apps, DelugeIcons.Checklist, SettingsCard.APPS),
    PROTECTION(R.string.settings_group_protection, Icons.Filled.Lock, SettingsCard.APPS),
    BACKUP(R.string.settings_section_backup, Icons.Filled.Refresh, SettingsCard.SYSTEM),
    ABOUT(R.string.settings_section_about, Icons.Filled.Info, SettingsCard.SYSTEM),
}

/**
 * One row of settings, as data rather than as a call in a section body.
 *
 * [keywords] is what else the row answers to, for the search box: the words someone would
 * reach for who does not already know what this screen calls the thing. [advanced] rows are
 * the tuning knobs: still searchable, but not in the way when the switch is off.
 */
private class SettingsEntry(
    val section: SettingsSection,
    val title: String,
    val keywords: String = "",
    val advanced: Boolean = false,
    val visible: Boolean = true,
    val content: @Composable () -> Unit,
) {
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (title.contains(q, ignoreCase = true) || keywords.contains(q, ignoreCase = true)) {
            return true
        }
        // Pinyin too, so a latin keyboard reaches a Chinese row ("yuyan" finds it).
        return Pinyin.rank(title, keywords, q) != Pinyin.NO_MATCH
    }
}

@Composable
private fun BackupRow(label: String, detail: String, onClick: () -> Unit, leading: ImageVector? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) RowLeadingIcon(leading)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Icon(
            DelugeIcons.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.padding(4.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun DimColorRow(dimColor: Int, onSetDimColor: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { picking = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_dim_color), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.settings_dim_color_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color(dimColor), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), CircleShape)
        )
    }
    if (picking) {
        ColorPickerDialog(
            initial = dimColor,
            onConfirm = { onSetDimColor(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/**
 * What a gesture or tap does, chosen from the same list everywhere.
 *
 * Tap to the next option, or hold and release on the dialog row to read the current one;
 * the row's second line is the answer, spelled out.
 */
@Composable
internal fun CornerActionRow(
    summary: String,
    selected: CornerAction,
    onSelect: (CornerAction) -> Unit,
    /** The app or shortcut this slot opens, shown when that is what the action is. */
    picked: String? = null,
    /** Freeze actions are only offered while the freeze feature is on. */
    freezeEnabled: Boolean = true,
) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { picking = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(selected.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            // The chosen app or shortcut, only for the actions that open one.
            if (selected.targetKind() != null && !picked.isNullOrBlank()) {
                Text(
                    picked,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Icon(
            DelugeIcons.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(summary) },
            text = {
                Column {
                CornerAction.entries
                    .filter {
                        freezeEnabled || (it != CornerAction.FREEZE_ALL && it != CornerAction.UNFREEZE_ALL)
                    }
                    .forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(action); picking = false }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = action == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(action.labelRes()), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Display name of an action. */
private fun CornerAction.labelRes(): Int = when (this) {
    CornerAction.OPEN_APP -> R.string.corner_action_open_app
    CornerAction.OPEN_SHORTCUT -> R.string.corner_action_open_shortcut
    CornerAction.APP_LIST -> R.string.corner_action_app_list
    CornerAction.SEARCH -> R.string.corner_action_search
    CornerAction.CAMERA -> R.string.corner_action_camera
    CornerAction.FLASHLIGHT -> R.string.corner_action_flashlight
    CornerAction.SHADE -> R.string.corner_action_shade
    CornerAction.NOTIFICATIONS -> R.string.corner_action_notifications
    CornerAction.LOCK -> R.string.corner_action_lock
    CornerAction.FREEZE_ALL -> R.string.corner_action_freeze_all
    CornerAction.UNFREEZE_ALL -> R.string.corner_action_unfreeze_all
    CornerAction.DO_NOTHING -> R.string.corner_action_do_nothing
}

/** A swatch to tap or a hex value to type; enough for picking a text color, and no library. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorPickerDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var hex by remember { mutableStateOf(String.format("%06X", initial and 0xFFFFFF)) }
    val parsed = remember(hex) { hex.toIntOrNull(16)?.let { 0xFF000000.toInt() or it } }

    // The palette Android derived from the wallpaper, offered first: picking a color that
    // already belongs to the wallpaper is most of what anyone wants here, and typing its hex
    // is not something anyone knows off-hand.
    // The same palette the Material text color comes from, so the swatch you pick here and
    // the color that option gives you are drawn from one scheme. MaterialTheme's own follows
    // the system dark mode instead, which disagrees the moment a light wallpaper meets a dark
    // system theme.
    val scheme = rememberWallpaperPalette() ?: MaterialTheme.colorScheme
    val fromWallpaper = listOf(
        scheme.primary, scheme.secondary, scheme.tertiary,
        scheme.primaryContainer, scheme.surfaceVariant, scheme.surface,
    ).map { it.toArgb() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_color_pick)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.color_from_wallpaper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fromWallpaper.forEach { argb ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(argb), CircleShape)
                                .border(
                                    width = if (parsed == argb) 3.dp else 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                )
                                .clickable { hex = String.format("%06X", argb and 0xFFFFFF) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SWATCHES.forEach { argb ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(argb), CircleShape)
                                .border(
                                    width = if (parsed == argb) 3.dp else 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                )
                                .clickable { hex = String.format("%06X", argb and 0xFFFFFF) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it.filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }.take(6) },
                    singleLine = true,
                    label = { Text("#RRGGBB") },
                    isError = parsed == null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val SWATCHES = listOf(
    0xFFFFFFFF.toInt(), 0xFFE3E8EC.toInt(), 0xFF9AA5B1.toInt(), 0xFF10161C.toInt(),
    0xFFEF5350.toInt(), 0xFFFFA726.toInt(), 0xFFFFEE58.toInt(), 0xFF66BB6A.toInt(),
    0xFF26C6DA.toInt(), 0xFF42A5F5.toInt(), 0xFF7E57C2.toInt(), 0xFFEC407A.toInt(),
)

/** How long the app name must be held to unlock the hidden developer switch. */
private const val DEVELOPER_HOLD_MS = 5000L

/**
 * A tap does [onTap]; holding still for five seconds does [onUnlock] instead.
 *
 * Built on the same press detector every other row uses, so it cannot lose to the scroll
 * container the way a hand-rolled gesture loop did — a drift that the list claims cancels
 * the press, and a cancelled press does nothing at all.
 */
@Composable
private fun Modifier.developerUnlock(
    hapticFeedback: HapticFeedback,
    onTap: () -> Unit,
    onUnlock: () -> Unit,
): Modifier {
    // Read when the gesture ends, not when it starts: the lambdas change every composition.
    val currentTap by rememberUpdatedState(onTap)
    val currentUnlock by rememberUpdatedState(onUnlock)
    val view = LocalView.current
    // The timer lives beside the gesture the way the strip's hold timer does.
    val scope = rememberCoroutineScope()
    return pointerInput(Unit) {
        var hold: Job? = null
        var unlocked = false
        fun fire() {
            if (unlocked) return
            unlocked = true
            HapticUtil.tick(view, hapticFeedback)
            currentUnlock()
        }
        detectTapGestures(
            onPress = {
                unlocked = false
                // A stolen press (slow scroll starting on this row) cannot be observed from
                // here, so its timer still fires — accepted: it only ever reveals the owner's
                // own hidden switch to the owner. A fresh press replaces the old timer.
                hold?.cancel()
                hold = scope.launch {
                    delay(DEVELOPER_HOLD_MS)
                    fire()
                }
            },
            // A release before the timer is a tap; a release after it already unlocked, so
            // the tap must not fire as well. A press the list steals never reaches here.
            onTap = {
                hold?.cancel()
                hold = null
                if (!unlocked) currentTap()
            },
        )
    }
}

/** How many palette swatches share a line, so the picker is a grid rather than a ragged flow. */
private const val SWATCHES_PER_ROW = 5

/** Where new releases live, opened from the app name in About. */
private const val PROJECT_URL = "https://github.com/busybin/DelugeLauncher"

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

/**
 * What the language row says on its right: the chosen language's own name, or the
 * system default. Read at composition from the delegate that actually holds the choice.
 */
@Composable
private fun languageLabel(): String {
    val context = LocalContext.current
    val tag = currentLanguageTag(context) ?: return stringResource(R.string.settings_language_system)
    return dev.delugelauncher.data.LanguagePrefs.displayName(tag).ifEmpty { tag }
}

/**
 * The mark in front of a row that opens something else: same tonal circle the section
 * list uses, so navigation rows read as navigation wherever they sit.
 */
@Composable
private fun RowLeadingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
        )
    }
    Spacer(Modifier.width(14.dp))
}

@Composable
private fun QuickLaunchRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    leading: ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) RowLeadingIcon(leading)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                value ?: stringResource(R.string.settings_quick_launch_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Icon(
            DelugeIcons.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

/**
 * What the update row has to say, resolved away from the composable that says it.
 *
 * [Idle] covers both "nothing has been asked yet" and "the last answer was that there is
 * nothing newer": the row reads the same either way, and the difference is not the user's.
 */
private sealed interface UpdateRowState {
    data object Idle : UpdateRowState
    data object Checking : UpdateRowState
    data object UpToDate : UpdateRowState
    data class Available(val version: Int) : UpdateRowState

    /** @param code the HTTP status, meaningful for a server error and 0 otherwise. */
    data class Failed(
        val reason: UpdateError,
        val code: Int,
        val blockedBySystem: Boolean,
    ) : UpdateRowState

    /** "24", for the rows that name the release rather than describe it. */
    fun versionLabel(): String = if (this is Available) "$version" else ""
}

/**
 * What the row shows, from what the last check found and what is remembered from earlier ones.
 *
 * [knownVersion] is what makes this survive a restart: the check itself is throttled, so on
 * most launches nothing runs at all and the row has only what an earlier one left behind.
 * A failure is only ever reported for a check that ran in this process — an old one being
 * told about every time settings is opened would be a complaint with nothing behind it.
 */
private fun updateRowState(
    checking: Boolean,
    result: UpdateResult?,
    knownVersion: Int,
): UpdateRowState = when {
    checking -> UpdateRowState.Checking
    result is UpdateResult.Failed ->
        UpdateRowState.Failed(result.reason, result.code, result.blockedBySystem)
    knownVersion > BuildConfig.VERSION_CODE -> UpdateRowState.Available(knownVersion)
    result is UpdateResult.UpToDate -> UpdateRowState.UpToDate
    else -> UpdateRowState.Idle
}

/** The update row's own line under the label, one per state. */
@Composable
private fun updateDetail(state: UpdateRowState): String = when (state) {
    UpdateRowState.Checking -> stringResource(R.string.settings_update_checking)
    UpdateRowState.UpToDate ->
        stringResource(R.string.settings_update_up_to_date, BuildConfig.VERSION_NAME)
    is UpdateRowState.Available ->
        stringResource(R.string.settings_update_available, state.versionLabel())
    is UpdateRowState.Failed -> when {
        // Before the reason: it is the answer to "why", and the reason on its own reads as the
        // app's fault when nothing this app did or was asked is involved.
        state.blockedBySystem -> stringResource(R.string.update_error_restricted_mode)
        state.reason == UpdateError.DNS -> stringResource(R.string.update_error_dns)
        state.reason == UpdateError.TIMEOUT -> stringResource(R.string.update_error_timeout)
        state.reason == UpdateError.TLS -> stringResource(R.string.update_error_tls)
        state.reason == UpdateError.SERVER ->
            stringResource(R.string.update_error_server, state.code)
        state.reason == UpdateError.UNEXPECTED ->
            stringResource(R.string.update_error_unexpected)
        else -> stringResource(R.string.update_error_network)
    }
    UpdateRowState.Idle -> stringResource(R.string.settings_update_check_detail)
}

/**
 * Asks the release page whether there is a newer build.
 *
 * A tap checks; a check in flight is not worth a second request, so taps are ignored while
 * one runs. The spinner is the only thing that ever moves on this row.
 */
@Composable
private fun UpdateRow(state: UpdateRowState, onCheck: () -> Unit) {
    val busy = state is UpdateRowState.Checking
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onCheck)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLeadingIcon(Icons.Filled.Refresh)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_update_check), style = MaterialTheme.typography.bodyMedium)
            Text(
                updateDetail(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                DelugeIcons.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun AlignmentRow(label: String, selected: HomeAlignment, onSelect: (HomeAlignment) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
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
private fun IconSideRow(selected: IconSide, onSelect: (IconSide) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_icon_side), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconSide.entries.forEach { option ->
                FilledChip(
                    label = stringResource(option.labelRes()),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

/**
 * Where the favorites come from: the list you keep, or the apps you actually open.
 *
 * The count only appears for the computed one, since the manual list is as long as you made it.
 */
@Composable
private fun FavoritesSourceRow(
    selected: FavoritesSource,
    count: Int,
    onSelect: (FavoritesSource) -> Unit,
    onSetCount: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_favorites_source), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FavoritesSource.entries.forEach { source ->
                FilledChip(stringResource(source.labelRes()), selected == source) { onSelect(source) }
            }
        }
    }
    if (selected == FavoritesSource.FREQUENT) {
        SliderRow(
            label = stringResource(R.string.settings_frequent_count),
            value = count.toFloat(),
            range = FREQUENT_RANGE.first.toFloat()..FREQUENT_RANGE.last.toFloat(),
            valueLabel = { it.roundToInt().toString() },
            onValueChange = { onSetCount(it.toInt()) },
        )
    }
}

@Composable
private fun ShortcutSwipeRow(selected: ShortcutSwipe, onSelect: (ShortcutSwipe) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_swipe_shortcuts), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_swipe_shortcuts_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShortcutSwipe.entries.forEach { mode ->
                FilledChip(stringResource(mode.labelRes()), selected == mode) { onSelect(mode) }
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

/** Shared with the clock widget's own settings, which offers the same kind of choices. */
@Composable
internal fun FilledChip(
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