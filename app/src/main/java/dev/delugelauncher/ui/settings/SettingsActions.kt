package dev.delugelauncher.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.delugelauncher.BuildConfig
import dev.delugelauncher.R
import dev.delugelauncher.data.AppFont
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.AzStripVisibility
import dev.delugelauncher.data.ColorSchemeChoice
import dev.delugelauncher.data.CornerAction
import dev.delugelauncher.data.CrashLog
import dev.delugelauncher.data.EDGE_ZONE_INSET_RANGE
import dev.delugelauncher.data.EdgeSide
import dev.delugelauncher.data.FREQUENT_RANGE
import dev.delugelauncher.data.FavoritesSource
import dev.delugelauncher.data.HOLD_SCROLL_RANGE
import dev.delugelauncher.data.HapticFeedback
import dev.delugelauncher.data.HomeAlignment
import dev.delugelauncher.data.IconPackRepository
import dev.delugelauncher.data.IconShape
import dev.delugelauncher.data.IconSide
import dev.delugelauncher.data.RECENT_SECTION_RANGE
import dev.delugelauncher.data.SECTION_TOP_RANGE
import dev.delugelauncher.data.ShortcutSwipe
import dev.delugelauncher.data.SlotKind
import dev.delugelauncher.data.SlotTarget
import dev.delugelauncher.data.TextColorMode
import dev.delugelauncher.data.TwoColumnSlot
import dev.delugelauncher.data.UpdateError
import dev.delugelauncher.data.UpdateResult
import dev.delugelauncher.data.targetKind
import dev.delugelauncher.service.HapticUtil
import dev.delugelauncher.ui.common.AppIcon
import dev.delugelauncher.ui.common.DelugeIcons
import dev.delugelauncher.ui.theme.SHADOW_STRENGTH_RANGE
import dev.delugelauncher.ui.theme.fontFamilyOf
import dev.delugelauncher.ui.theme.rememberWallpaperPalette
import dev.delugelauncher.ui.theme.toFontFamily
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// SettingsScreen's callbacks, deliberately split in two. The call site is a composable lambda,
// and Compose emits ~600 bytes of memoisation bookkeeping per lambda argument: ~90 of them
// would exceed the JVM's 64 KB per-method limit. Parameter types mirror SettingsScreenModel,
// still the single source of truth.

class SettingsActionsA(
    val onSetShowFavoriteIcons: (Boolean) -> Unit,
    val onSetTextShadowStrength: (Int) -> Unit,
    val onSetStayAfterPrivateUnlock: (Boolean) -> Unit,
    val onSetCompactTapArea: (Boolean) -> Unit,
    val onSetPrivateSpaceSection: (Boolean) -> Unit,
    val onSetRecentSection: (Boolean) -> Unit,
    val onSetRecentSectionCount: (Int) -> Unit,
    val onSetTextShadow: (Boolean) -> Unit,
    val onSetEdgeZoneInsetAuto: (Boolean) -> Unit,
    val onSetEdgeZoneInsetDp: (Int) -> Unit,
    val onSetMarkWorkApps: (Boolean) -> Unit,
    val onSetDimOthersScrubbing: (Boolean) -> Unit,
    val onSetWebSearchFallback: (Boolean) -> Unit,
    val onSetSwipeUpOpensSearch: (Boolean) -> Unit,
    val onSetHoldScroll: (Boolean) -> Unit,
    val onSetHoldScrollSpeed: (Int) -> Unit,
    val onSetEdgeZoneBandOnly: (Boolean) -> Unit,
    val onSetShowSettingsRow: (Boolean) -> Unit,
    val onSetShowListHeaders: (Boolean) -> Unit,
    val onSetNotificationBadges: (Boolean) -> Unit,
    val onSetNotificationPreview: (Boolean) -> Unit,
    val onSetDimPausedApps: (Boolean) -> Unit,
    val onSetAutoDark: (Boolean) -> Unit,
    val onSetCornerTapAction: (CornerAction) -> Unit,
    val onSetCornerSwipeAction: (CornerAction) -> Unit,
    val onSetNightStartHour: (Int) -> Unit,
    val onSetNightEndHour: (Int) -> Unit,
    val onSetPinchToResize: (Boolean) -> Unit,
    val onSetReduceMotion: (Boolean) -> Unit,
    val onSetSectionTopPercent: (Int) -> Unit,
    val onSetCloseFolderOnLaunch: (Boolean) -> Unit,
    val onSetFavoritesSource: (FavoritesSource) -> Unit,
    val onSetFrequentCount: (Int) -> Unit,
    val onSetScreenshotMode: (Boolean) -> Unit,
    val onSetIconPack: (String?) -> Unit,
    val onSetShowAppIcons: (Boolean) -> Unit,
    val onSetIconSize: (Int) -> Unit,
    val onSetLabelSize: (Int) -> Unit,
    val onSetItemSpacing: (Int) -> Unit,
    val onSetFont: (AppFont) -> Unit,
    val onSetColorScheme: (ColorSchemeChoice) -> Unit,
    val onSetHideStatusBar: (Boolean) -> Unit,
    val onSetHideStatusBarAppList: (Boolean) -> Unit,
    val onSetStatusBarPeekSeconds: (Int) -> Unit,
    val onSetDimWallpaper: (Float) -> Unit,
    val onSetWallpaperBlur: (Int) -> Unit,
)

class SettingsActionsB(
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
    val onSetIconShape: (IconShape) -> Unit,
    val onSetThemedIcons: (Boolean) -> Unit,
    val onPickFontFile: (Uri) -> Unit,
    val onSetDoubleTapToLock: (Boolean) -> Unit,
    val onSetEdgeSide: (EdgeSide) -> Unit,
    val onSetEdgeZoneWidth: (Int) -> Unit,
    val onSetAzStripVisibility: (AzStripVisibility) -> Unit,
    val onSetShowAlphabet: (Boolean) -> Unit,
    val onSetSortByUsage: (Boolean) -> Unit,
    val onSetTwoColumn: (TwoColumnSlot, Boolean) -> Unit,
    val onSetAutoFrequentTag: (Boolean) -> Unit,
    val onSetAppListSearch: (Boolean) -> Unit,
    val onSetAppListSearchBottom: (Boolean) -> Unit,
    val onSetAppListSearchHidden: (Boolean) -> Unit,
    val onSetSwipeUpOpensList: (Boolean) -> Unit,
    val onOpenSlotPicker: (SlotTarget, SlotKind) -> Unit,
    val onSetQuickLaunchLeftAction: (CornerAction) -> Unit,
    val onSetQuickLaunchRightAction: (CornerAction) -> Unit,
    val onSetLastLetterToLine: (Boolean) -> Unit,
    val onSetAutoKeyboard: (Boolean) -> Unit,
    val onSetAlignment: (HomeAlignment) -> Unit,
    val onSetAppListAlignment: (HomeAlignment) -> Unit,
    val onSetIconSide: (IconSide) -> Unit,
    val onSetNowPlayingEnabled: (Boolean) -> Unit,
    val onOpenAccessibilitySettings: () -> Unit,
    val onOpenAppInfo: () -> Unit,
    val onOpenHiddenApps: () -> Unit,
    val onOpenFreezeApps: () -> Unit,
    val onOpenHomeSettings: () -> Unit,
    val onOpenFrequentExcluded: () -> Unit,
    val onOpenFavorites: () -> Unit,
    val onOpenLanguage: () -> Unit,
    val onOpenChangelog: () -> Unit,
    val onCheckForUpdates: () -> Unit,
    val onOpenReleasePage: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onBack: () -> Unit,
)
