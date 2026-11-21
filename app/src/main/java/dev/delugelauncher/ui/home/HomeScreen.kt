// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.home

import dev.delugelauncher.ui.common.topBarInsets
import dev.delugelauncher.ui.common.DelugeIcons
import dev.delugelauncher.ui.focus.FocusMenuItem

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalTextStyle
import dev.delugelauncher.ui.theme.launcherTextShadow
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import android.content.res.Configuration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.CornerAction
import dev.delugelauncher.data.targetKind
import dev.delugelauncher.data.ShortcutSwipe
import dev.delugelauncher.data.HapticFeedback
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.Folder
import dev.delugelauncher.data.FrozenLook
import dev.delugelauncher.data.FolderWidget
import dev.delugelauncher.media.NotificationEntry
import dev.delugelauncher.media.NotificationGateway
import dev.delugelauncher.media.NotificationPreviewBus
import dev.delugelauncher.data.QuickLaunchSlot
import dev.delugelauncher.data.HomeAlignment
import dev.delugelauncher.data.TwoColumnLabels
import dev.delugelauncher.data.AzStripSide
import dev.delugelauncher.ui.applist.STRIP_LETTER_REACH_DP
import dev.delugelauncher.data.IconSide
import dev.delugelauncher.data.HomePaddings
import dev.delugelauncher.data.folderToken
import dev.delugelauncher.data.PaddingSlot
import dev.delugelauncher.media.NowPlayingBus
import dev.delugelauncher.media.NowPlayingWidget
import dev.delugelauncher.service.HapticUtil
import androidx.compose.runtime.CompositionLocalProvider
import dev.delugelauncher.ui.common.AppIcon
import dev.delugelauncher.ui.common.swipeForShortcuts
import dev.delugelauncher.ui.common.AppShortcutMenu
import dev.delugelauncher.ui.common.FreezeActions
import dev.delugelauncher.ui.common.FreezeMenuItems
import dev.delugelauncher.ui.common.LocalNetworkActions
import dev.delugelauncher.ui.common.NetworkMenuItems
import dev.delugelauncher.ui.common.NetworkSubmenu
import dev.delugelauncher.ui.common.FreezeRemoveMenuItem
import dev.delugelauncher.ui.common.FreezeMethodDialog
import dev.delugelauncher.ui.common.TouchAnchoredMenu
import dev.delugelauncher.ui.common.LocalIconConfig
import dev.delugelauncher.ui.common.EditAppDialog
import dev.delugelauncher.ui.common.FolderIcon
import dev.delugelauncher.ui.common.recordTouchPosition
import dev.delugelauncher.ui.common.pressPop
import dev.delugelauncher.widget.WidgetPage
import dev.delugelauncher.widget.WidgetSlot
import dev.delugelauncher.widget.isDelugeClock
import androidx.compose.ui.semantics.Role
import dev.delugelauncher.widget.WidgetSlotActions
import dev.delugelauncher.DelugeApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.delugelauncher.R
import androidx.compose.ui.res.stringResource

private sealed interface HomeItem {
    data object Widget : HomeItem
    data class Favorite(val app: AppInfo) : HomeItem
    data class FolderItem(val folder: Folder) : HomeItem
}

/** A favorites row is either an app or a folder; both reorder through the same list. */
sealed interface FavoriteEntry {
    val token: String

    data class App(val app: AppInfo) : FavoriteEntry {
        override val token: String get() = app.key
    }

    data class FolderRef(val folder: Folder) : FavoriteEntry {
        override val token: String get() = folderToken(folder.id)
    }
}

private fun buildHomeItems(
    favorites: List<FavoriteEntry>,
    widgetPosition: Int,
    hasWidget: Boolean,
): List<HomeItem> {
    val pos = widgetPosition.coerceIn(0, favorites.size)
    val list = mutableListOf<HomeItem>()
    favorites.forEachIndexed { i, entry ->
        if (hasWidget && i == pos) list += HomeItem.Widget
        list += when (entry) {
            is FavoriteEntry.App -> HomeItem.Favorite(entry.app)
            is FavoriteEntry.FolderRef -> HomeItem.FolderItem(entry.folder)
        }
    }
    if (hasWidget && pos >= favorites.size) list += HomeItem.Widget
    return list
}

@Composable
fun HomeScreen(
    /** Bumped when HOME is pressed on a home screen already showing. */
    homeIntentTick: Int,
    /** Whether opening an app from inside a folder closes the folder behind it. */
    closeFolderOnLaunch: Boolean,
    /** Whether the favorites draw icons, which is asked separately from the app list. */
    showFavoriteIcons: Boolean,
    /** Whether the empty home screen should still explain itself. */
    showEmptyHint: Boolean,
    /**
     * Whether the status bar is on screen, in which case the first row has to clear it.
     *
     * The window draws edge to edge and shows the wallpaper, so nothing here is padded by
     * the system; without this the top favorite sits under the clock.
     */
    avoidStatusBar: Boolean,
    /** Whether a work profile's apps say so after their name. */
    markWorkApps: Boolean,
    /** Whether the launcher's own text carries a shadow for legibility. */
    textShadow: Boolean,
    /** Whether a favorite answers a tap only on its icon and name. */
    compactTapArea: Boolean,
    /** How strong that shadow is. */
    textShadowStrength: Int,
    /** False when the favorites are computed from usage, so there is no order to drag. */
    favoritesReorderable: Boolean,
    /** Whether a sideways swipe on a row offers its app's shortcuts. */
    shortcutSwipe: ShortcutSwipe,
    favorites: List<FavoriteEntry>,
    nameOverrides: Map<String, String>,
    /** Newest notifications per app, for the lines under its name; empty unless that is on. */
    notificationPreviews: Map<String, List<NotificationEntry>> = emptyMap(),
    /** Point size for those lines. */
    notificationTextSp: Int = 12,
    notificationColor: Int = 0,
    iconSizeDp: Int,
    labelSizeSp: Int,
    itemSpacingDp: Int,
    sidePaddingDp: Int,
    widgetSidePaddingDp: Int,
    widgetOffsetXDp: Int,
    /** How wide the edge zone's live strip is on each side, so folder controls stay clear. */
    edgeZoneStartDp: Dp = 0.dp,
    edgeZoneEndDp: Dp = 0.dp,
    /** The side an always-on A-Z strip is showing on, or null while it is hidden. */
    visibleStripSide: AzStripSide? = null,
    onSetSidePadding: (Int) -> Unit,
    onSetWidgetSidePadding: (Int) -> Unit,
    onSetWidgetOffsetX: (Int) -> Unit,
    paddings: HomePaddings,
    widgetIds: List<Int>,
    /** Two favorites per home row, upright and sideways separately. */
    homeTwoColumnPortrait: Boolean,
    homeTwoColumnLandscape: Boolean,
    /** Fake names by app key while the screenshot disguise is on; empty otherwise. */
    disguiseNames: Map<String, String>,
    /** Folder cover names by folder id while the screenshot disguise is on; empty otherwise. */
    folderDisguise: Map<String, String>,
    widgetPosition: Int,
    widgetHeightDp: Int,
    widgetsStacked: Boolean,
    /** Bumped on every package event, so frozen and unfrozen providers re-read. */
    widgetTick: Int,
    hapticFeedback: HapticFeedback,
    nowPlayingEnabled: Boolean,
    nowPlayingHeightDp: Int,
    onResizeNowPlaying: (Int) -> Unit,
    widgetActions: WidgetSlotActions,
    onLaunch: (AppInfo) -> Unit,
    /** Starts whatever is playing, through the app lock gate when it is a locked app. */
    onOpenNowPlaying: () -> Boolean = { false },
    onRemoveFavorite: (AppInfo) -> Unit,
    /** Hides a favorite from the home screen and the app list. */
    onHideFavorite: (AppInfo) -> Unit,
    onOpenFolderApp: (AppInfo) -> Unit,
    onRemoveFromFolder: (Folder, AppInfo) -> Unit,
    onRenameFolder: (Folder, String) -> Unit,
    onChangeFolderIcon: (Folder) -> Unit,
    onResetFolderIcon: (Folder) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    /** Set when folders are also in the app list, where one taken off here still lives. */
    onRemoveFolderFromHome: ((Folder) -> Unit)? = null,
    onManageFolder: (Folder) -> Unit,
    /**
     * Freezes the listed members of a folder and releases the frozen ones; members outside
     * the freeze list are left alone. Null when freezing is switched off.
     */
    onFreezeFolder: ((List<AppInfo>) -> Unit)? = null,
    onUnfreezeFolder: ((List<AppInfo>) -> Unit)? = null,
    onMoveToFolder: (AppInfo) -> Unit,
    appsByKey: Map<String, AppInfo>,
    onReorderHome: (newFavoriteKeys: List<String>, newWidgetPosition: Int) -> Unit,
    onCommitPadding: (PaddingSlot, Int) -> Unit,
    onFavoritesBoundsChanged: (topPx: Float, bottomPx: Float) -> Unit,
    /** The favorites emptied out, so the strip has nothing to point at any more. */
    onFavoritesCleared: () -> Unit,
    nowPlayingHasContent: Boolean,
    contentColor: Color,
    showFavoriteLabels: Boolean,
    frozenLook: FrozenLook,
    /** Whether a two-finger pinch resizes the icons. */
    pinchToResize: Boolean,
    /** Commits a size chosen by pinching; the settings slider writes the same value. */
    onSetIconSize: (Int) -> Unit,
    /** Keys that ask for the device lock before opening. */
    lockedApps: Set<String>,
    onToggleLock: (AppInfo) -> Unit,
    /** Opens the private-space flow for an app; null where the space cannot be reached. */
    onAddToPrivateSpace: ((AppInfo) -> Unit)? = null,
    alignment: HomeAlignment,
    iconSide: IconSide,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onEditScrubBand: () -> Unit,
    centerFavorites: Boolean,
    swipeUpOpensAppList: Boolean,
    /** Total distance dragged up past the end, and this frame's share of it. */
    onSwipeUpDrag: (total: Float, delta: Float) -> Unit,
    onSwipeUpEnd: (velocity: Float) -> Unit,
    quickLaunchEnabled: Boolean,
    onQuickLaunch: (QuickLaunchSlot) -> Unit,
    onPeekStatusBar: () -> Unit,
    onExpandShade: () -> Unit,
    /**
     * Double-tap on bare wallpaper, or null when the gesture is off.
     *
     * Null rather than a flag beside a callback: without it there is nothing to detect,
     * and tap detection that waits on a second tap for no reason only delays the long
     * press that shares this detector.
     */
    /** Keys hidden from the list: hidden everywhere, folders included. */
    hiddenApps: Set<String> = emptySet(),
    /** While true the list overlay covers home: BACK belongs to it, not to open folders. */
    overlayOpen: Boolean = false,
    onDoubleTapBackground: (() -> Unit)?,
    onManageFavorites: () -> Unit,
    /** Hands the wallpaper to the system's own picker; nothing is drawn or stored here. */
    onChangeWallpaper: () -> Unit,
    onSetName: (AppInfo, String?) -> Unit,
    onChangeIcon: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onUnpinShortcut: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    /** The app a button in the bottom corner opens, or null when there is no button. */
    cornerButtonApp: AppInfo?,
    /** True when the corner opens the app list's search rather than launching anything. */
    cornerButtonIsSearch: Boolean,
    /** What a tap on the corner button does. */
    cornerTapAction: CornerAction,
    /** What hold-slide-up on the corner button does. */
    cornerSwipeAction: CornerAction,
    /** Runs the tap's chosen action; everything the tap can do funnels through here. */
    onCornerAction: (CornerAction) -> Unit,
    /** Runs the slide-up's chosen action, against its own target. */
    onCornerSwipeAction: (CornerAction) -> Unit,
    /** Where the button sits, or null until measured; stored values win over the default. */
    cornerPos: Pair<Float, Float>?,
    /** Persists a dragged position, in fractions over the room the button can move in. */
    onSetCornerPos: (Float, Float) -> Unit,
    /** The button's bounds in root pixels, so the edge zone can leave it alone. */
    onCornerBoundsChanged: (Rect?) -> Unit,
    /** Whether the slide-up has a target bound (an app, a shortcut, or the old search). */
    cornerSwipeBound: Boolean,
    /** The hold elapsed and the finger owns the button: the home swipe-up stands down. */
    onCornerOwnershipChanged: (Boolean) -> Unit,
    /** Freeze-list actions a row's long-press menu offers; null where freezing is not set up. */
    freezeActions: FreezeActions? = null,
    /** Whether the freeze feature is on; its entries disappear from the background menu too. */
    freezeEnabled: Boolean = true,
    /** Freezes every app on the list; runs from the background long-press menu. */
    onFreezeAll: () -> Unit = {},
    /** Thaws every app on the list; runs from the same menu. */
    onUnfreezeAll: () -> Unit = {},
    /** Opens the freeze screen itself from the same menu. */
    onOpenFreezeList: () -> Unit = {},
) {
    val networkActions = LocalNetworkActions.current
    // Appended at the point of showing rather than stored in the label, so a rename still
    // wins and turning this off needs no re-reading of every app. The disguise wins over
    // everything while it is on: a rename is exactly what must not show.
    val workSuffix = stringResource(R.string.app_work_suffix)
    fun displayName(app: AppInfo): String {
        disguiseNames[app.key]?.let { return it }
        val base = nameOverrides[app.key] ?: app.label
        return if (markWorkApps && app.work) base + workSuffix else base
    }

    // What a folder holds right now, computed the same way the row draws it.
    fun folderMembers(folder: Folder): List<AppInfo> =
        folder.apps.mapNotNull { appsByKey[it] }.filterNot { it.key in hiddenApps }

    var menuForKey by remember { mutableStateOf<String?>(null) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var renameDialogFor by remember { mutableStateOf<AppInfo?>(null) }
    var folderMenuFor by remember { mutableStateOf<String?>(null) }
    var folderRenameFor by remember { mutableStateOf<Folder?>(null) }
    var nowPlayingMenu by remember { mutableStateOf(false) }
    var nowPlayingMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }
    // BACK folds open folders before it leaves anything: popups dismiss themselves, an
    // in-place expansion does not. Not while the list overlay covers home — BACK is its.
    BackHandler(enabled = expandedFolders.isNotEmpty() && !overlayOpen) {
        expandedFolders = emptySet()
    }
    val touchPosition = remember { mutableStateOf(Offset.Zero) }
    // Live icon size while a pinch is in progress; the stored one until then, committed on
    // release so a continuous gesture is one write rather than one per frame.
    var liveIconSize by remember(iconSizeDp) { mutableIntStateOf(iconSizeDp) }
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Read here rather than passed through HomeRoute: its composable is already at the JVM
    // method limit, and this is one setting with two readers on this screen.
    val context = LocalContext.current
    // Read here rather than passed in: HomeRoute is already at the JVM method size limit
    // (adding an argument to it failed the build with "Method too large").
    val twoColumnLabels = rememberTwoColumnLabels()
    val widgetHeightAuto by (context.applicationContext as DelugeApp).prefs.widgetHeightAuto
        .collectAsState(initial = false)

    var backgroundMenu by remember { mutableStateOf(false) }
    var backgroundMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var customizeMenu by remember { mutableStateOf(false) }

    // A stepped value shows immediately and is written at the same time; holding them
    // locally as well means repeated taps compound instead of each one reading back the
    // stale stored value while DataStore catches up. One entry per slot: two steppers
    // tapped in turn used to share a single slot, and the first one's uncommitted value
    // flashed back to the stored one the moment the second was touched.
    var livePaddings by remember { mutableStateOf(emptyMap<PaddingSlot, Int>()) }

    // On a fresh install the favorites are placed by measurement rather than by a stored
    // number: centered, which is where a thumb and the A-Z strip both want them. Landing at
    // the top of an empty screen is what new users kept reporting as the strip being too
    // high. Setting any padding by hand retires this for good.
    var centeredFavTopDp by remember { mutableIntStateOf(0) }
    var rootY by remember { mutableFloatStateOf(0f) }

    fun padOf(slot: PaddingSlot): Int {
        livePaddings[slot]?.let { return it }
        return if (centerFavorites && slot == PaddingSlot.FAVORITES_TOP && centeredFavTopDp > 0) {
            centeredFavTopDp
        } else {
            paddings[slot]
        }
    }

    fun setPadding(slot: PaddingSlot, value: Int) {
        // Nothing to write out first. Only setting the favorites' own top gap retires the
        // computed centre now, so adjusting anything else leaves the centre to recompute
        // around it — which is what "centered" should have meant all along. Writing the
        // computed top out on every other slider is what used to send them up the screen.
        livePaddings = livePaddings + (slot to value)
        onCommitPadding(slot, value)
    }


    // The background drag is disabled while a padding is being adjusted; leaving edit mode
    // has to release that or the home screen stops scrolling entirely.
    LaunchedEffect(editMode) { if (!editMode) livePaddings = emptyMap() }

    // Show the widget slot when a widget exists, and also in edit mode when there isn't one —
    // that placeholder is the only way back to the picker once a widget has been removed.
    val hasWidget = widgetIds.isNotEmpty()
    val showWidgetSlot = hasWidget || editMode

    // Reordering runs against a local copy and is committed once on release; going through
    // storage on every swap would lag behind the finger.
    var dragOrder by remember { mutableStateOf<List<HomeItem>?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // A drag only ever ends through its own release, and a gesture can be taken away before
    // that — when the row holding it is disposed, nothing is told, and the moved order, the
    // held row and its offset are all left behind. None of it belongs to edit mode, so it
    // stayed on the home screen as a row drawn across its neighbour, and only another
    // finished drag would clear it. Leaving edit mode drops anything a drag left standing,
    // back to the order that was actually saved.
    LaunchedEffect(editMode) {
        if (!editMode) {
            dragOrder = null
            draggingIndex = null
            dragOffset = 0f
        }
    }
    // Plain maps, deliberately. Every row writes its size and position here on every layout
    // pass; as snapshot state, read back during composition, each of those writes asked for a
    // recomposition that laid the rows out again and wrote again — which never settles, and
    // showed up as the favorites shivering in place.
    // Keyed by which row, not by where it sits. Positions are what a reorder changes, so a
    // height stored against one was read back for whichever row had since moved into it — and
    // the drag arithmetic below is all neighbour heights. Among rows of one kind every height
    // is the same and the mix-up is invisible; a folder and an app are different heights, which
    // is why reordering the two together threw them about and reordering either alone did not.
    val itemHeights = remember { HashMap<String, Int>() }
    val itemTops = remember { HashMap<String, Float>() }

    /** What a row is called in the measurement maps, the widget slot included. */
    fun measureKey(item: HomeItem): String = when (item) {
        is HomeItem.Favorite -> item.app.key
        is HomeItem.FolderItem -> folderToken(item.folder.id)
        HomeItem.Widget -> "widget"
    }

    fun tokensOf(items: List<HomeItem>) = items.mapNotNull {
        when (it) {
            is HomeItem.Favorite -> it.app.key
            is HomeItem.FolderItem -> folderToken(it.folder.id)
            HomeItem.Widget -> null
        }
    }

    val homeItems = remember(favorites, widgetPosition, showWidgetSlot) {
        buildHomeItems(favorites, widgetPosition, showWidgetSlot)
    }
    // A dirty stored list can name the same row twice; two nodes under one key crash the
    // list, so the second naming is dropped. Committing the drag writes back what is drawn,
    // which heals the stored list as a side effect.
    val displayItems = remember(dragOrder ?: homeItems) {
        val src = dragOrder ?: homeItems
        val seen = HashSet<String>()
        src.filter { seen.add(measureKey(it)) }
    }
    // Read by the drag handles rather than captured by them. A handle's pointerInput is keyed
    // on its index and the list's size, so a reorder — same size, new order — never restarts
    // it, and the block went on holding the list as it was when the handle was last built.
    // Starting a second drag from that stale order moved a row other than the one under the
    // finger and threw the rest around, which is exactly what #79 describes.
    val currentItems = rememberUpdatedState(displayItems)

    // Folders sit alongside apps in the favorites block, so both bound its padding.
    val firstRowIndex = displayItems.indexOfFirst { it !is HomeItem.Widget }
    val lastRowIndex = displayItems.indexOfLast { it !is HomeItem.Widget }

    fun commitDragOrder() {
        val order = dragOrder
        if (order != null) {
            val widgetPos = order.indexOfFirst { it is HomeItem.Widget }
                .let { if (it < 0) widgetPosition else it }
            onReorderHome(tokensOf(order), widgetPos)
        }
        dragOrder = null
        draggingIndex = null
        dragOffset = 0f
    }

    /** Swap with the neighbor once the held row has traveled past its midpoint. */
    fun onDragBy(amount: Float) {
        val order = dragOrder ?: return
        val from = draggingIndex ?: return
        dragOffset += amount

        val below = order.getOrNull(from + 1)?.let { itemHeights[measureKey(it)] }
        if (below != null && from + 1 <= order.lastIndex && dragOffset > below / 2f) {
            dragOrder = order.toMutableList().apply { add(from + 1, removeAt(from)) }
            draggingIndex = from + 1
            dragOffset -= below
            return
        }
        val above = order.getOrNull(from - 1)?.let { itemHeights[measureKey(it)] }
        if (above != null && from - 1 >= 0 && dragOffset < -above / 2f) {
            dragOrder = order.toMutableList().apply { add(from - 1, removeAt(from)) }
            draggingIndex = from - 1
            dragOffset += above
        }
    }

    // Both maps are keyed by position in the list, so an entry for a position that no longer
    // exists is a lie about a row that is gone. Removing a widget is exactly that: every row
    // below it shifts up a place and the list ends one place shorter. Same for a folder that
    // was deleted while expanded: its id must not keep the expansion standing.
    LaunchedEffect(displayItems) {
        val present = displayItems.map { measureKey(it) }.toSet()
        itemHeights.keys.retainAll { it in present }
        itemTops.keys.retainAll { it in present }
        val folderIds = displayItems
            .mapNotNull { (it as? HomeItem.FolderItem)?.folder?.id }.toSet()
        if (expandedFolders.any { it !in folderIds }) {
            expandedFolders = expandedFolders.intersect(folderIds)
        }
    }

    // Measured span of the favorites, handed up so the A-Z strip can match it.
    //
    // The only piece of the measurements that reaches composition, and only when it has really
    // moved — so a layout pass that lands everything where it already was ends there instead
    // of asking for another one.
    var favBounds by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    /** Recomputed after each row reports, against the list as it is now rather than as it was. */
    fun refreshFavBounds() {
        val first = displayItems.firstOrNull { it !is HomeItem.Widget } ?: return
        val last = displayItems.lastOrNull { it !is HomeItem.Widget } ?: return
        val top = itemTops[measureKey(first)] ?: return
        val bottom = (itemTops[measureKey(last)] ?: return) +
            (itemHeights[measureKey(last)] ?: return)
        if (bottom <= top) return
        val current = favBounds
        if (current == null || abs(current.first - top) > 0.5f || abs(current.second - bottom) > 0.5f) {
            favBounds = top to bottom
        }
    }

    LaunchedEffect(favBounds) {
        val (top, bottom) = favBounds ?: return@LaunchedEffect
        onFavoritesBoundsChanged(top, bottom)
    }

    // An emptied list reports no rows, so the refresh above never runs for it: clear the
    // bounds here or the strip keeps pointing at favorites that are gone.
    LaunchedEffect(displayItems) {
        if (displayItems.none { it !is HomeItem.Widget }) {
            favBounds = null
            onFavoritesCleared()
        }
    }

    val peekPullPx = with(density) { 30.dp.toPx() }
    val deepPullPx = with(density) { 200.dp.toPx() }
    val quickLaunchPx = with(density) { 80.dp.toPx() }
    var rawPullDown by remember { mutableFloatStateOf(0f) }
    var rawPullUp by remember { mutableFloatStateOf(0f) }
    var pullActionFired by remember { mutableStateOf(false) }
    var peekFired by remember { mutableStateOf(false) }


    val baseIconConfig = LocalIconConfig.current
    val favoriteIconConfig = remember(baseIconConfig, showFavoriteIcons) {
        baseIconConfig.copy(showIcons = showFavoriteIcons)
    }

    // No inset is held for the strip or the edge zone: the rows run full-bleed under
    // both, exactly like the app list. An inset here is what kept left-aligned favorites
    // 56dp off the left edge (and right-aligned off the right) whenever the strip was
    // shown, while the list beside it went edge to edge. Taps landing inside the zone
    // still open the list rather than the row — the same trade the list itself makes.

    // Free vertical drag with spring bounce at both ends, outside edit mode.
    val offsetY = remember { Animatable(0f) }
    // Survives rotation and trips to settings: without it every return lands back on top,
    // which on a long favorites list is losing your place for nothing.
    var savedOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    // Not saveable on purpose: it records "this composition has already run the restore",
    // and a restored true would skip the very restore it is meant to gate, leaving the
    // freshly created Animatable at zero after a rotation.
    var offsetRestored by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { savedOffsetY = offsetY.value }
    }
    // One drag event per frame is already more than the animation can apply, so every event
    // launching its own snap meant each computed its landing from a value an earlier snap
    // had not written yet — a fast fling collapsed into roughly one delta, and no fast
    // gesture ever reached the end of a long favorites list. Slow drags worked, which is
    // why short lists never showed it. Serialized, every delta lands exactly once.
    val dragMutex = remember { Mutex() }
    // Bumps whenever a gesture ends or a new one lands: snaps queued by an earlier gesture
    // check it under the lock and skip, so a lifted finger stops moving the list.
    var dragGeneration by remember { mutableIntStateOf(0) }
    // The scroll a folder's expansion asks for. Held so a finger that lands on the list
    // while it runs cancels it, rather than that gesture's first step waiting behind
    // the animation the way it used to wait behind a fling.
    var revealScroll by remember { mutableStateOf<Job?>(null) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    // What the system bars keep, so the button never slides under them. Read once per
    // composition rather than per frame: bars do not move mid-gesture.
    val layoutDir = LocalLayoutDirection.current
    val systemInsetLeftPx = maxOf(
        WindowInsets.statusBars.getLeft(density, layoutDir),
        WindowInsets.navigationBars.getLeft(density, layoutDir),
    ).toFloat()
    val systemInsetTopPx = maxOf(
        WindowInsets.statusBars.getTop(density),
        WindowInsets.navigationBars.getTop(density),
    ).toFloat()
    val systemInsetRightPx = maxOf(
        WindowInsets.statusBars.getRight(density, layoutDir),
        WindowInsets.navigationBars.getRight(density, layoutDir),
    ).toFloat()
    val systemInsetBottomPx = maxOf(
        WindowInsets.statusBars.getBottom(density),
        WindowInsets.navigationBars.getBottom(density),
    ).toFloat()
    // Just the navigation bar's own share of the window: where the home list has to
    // stop at the bottom of the screen. The max above folds in the status bar too,
    // which says nothing about the bottom edge an expanded folder would hang past.
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    // Android's back gesture owns the outer edge of the screen: a drag that starts inside it
    // is claimed by the system and never reaches this app. The A-Z strip already steps inside
    // this same measure (see edgeZoneInsetAuto); the edit-mode drag handle has to as well, or
    // a reorder drag simply never starts on a gesture-navigated phone. Zero on the buttons.
    val gestureInsetRightPx = WindowInsets.systemGestures.getRight(density, layoutDir).toFloat()
    /** Where a grab handle at the end of a row sits: clear of the back gesture. */
    val dragHandleInsetDp = with(density) { gestureInsetRightPx.toDp() } + EDIT_CONTROL_END_INSET
    // Controls inside an open folder — the Now Playing card's buttons, a widget's height
    // stepper — run to the far end, which is where the edge zone that opens the app list
    // lives. They make up that width so the zone does not take their taps.
    val folderControlsInset = if (edgeZoneStartDp == 0.dp && edgeZoneEndDp == 0.dp) {
        Modifier
    } else {
        Modifier.padding(start = edgeZoneStartDp, end = edgeZoneEndDp)
    }
    // An always-on strip reaches this far in from its edge. Rows and widgets already inset
    // themselves — a row by one padding less its 8dp inner gutter — so the one that starts
    // closest to the edge decides how much the whole column still has to move.
    val stripExtraDp = (STRIP_LETTER_REACH_DP -
        minOf((sidePaddingDp - 8).coerceAtLeast(0), widgetSidePaddingDp)).coerceAtLeast(0f).dp
    val stripInsetStartDp = if (
        visibleStripSide == AzStripSide.LEFT || visibleStripSide == AzStripSide.BOTH
    ) stripExtraDp else 0.dp
    val stripInsetEndDp = if (
        visibleStripSide == AzStripSide.RIGHT || visibleStripSide == AzStripSide.BOTH
    ) stripExtraDp else 0.dp
    // The button's placement frame and the spot it should be at: the stored value once
    // there is one, otherwise the default. Computed, never stored, so a fresh install and
    // a rotation both land somewhere sane.
    val cornerFrame = if (viewportWidth > 0 && viewportHeight > 0) {
        CornerFrame(
            viewportW = viewportWidth.toFloat(),
            viewportH = viewportHeight.toFloat(),
            buttonPx = with(density) { CORNER_BUTTON_DP.dp.toPx() },
            marginPx = with(density) { CORNER_EDGE_MARGIN_DP.dp.toPx() },
            insetLeft = systemInsetLeftPx,
            insetTop = systemInsetTopPx,
            insetRight = systemInsetRightPx,
            insetBottom = systemInsetBottomPx,
        )
    } else {
        null
    }
    val cornerPosEffective = cornerPos ?: cornerFrame?.let {
        CornerGeometry.defaultFractions(it, with(density) { CORNER_DEFAULT_MARGIN_DP.dp.toPx() })
    }
    var contentHeight by remember { mutableIntStateOf(0) }
    // Where the drag stops. The old range ended where the content did, which put the last
    // favorite's bottom flush with the display edge and left it under the gesture bar. Three
    // rows of travel past that is the difference between the last few icons being readable
    // and not — this is the same extra room the doubled range was standing in for, now the
    // measured amount instead of twice everything.
    val bottomRoomPx = with(density) { (BOTTOM_ROOM_ROWS * (iconSizeDp + 8 + itemSpacingDp)).dp.toPx() }
    val minOffset = remember(viewportHeight, contentHeight, bottomRoomPx) {
        minOf(0f, viewportHeight - contentHeight - bottomRoomPx)
    }
    // Back to the top on HOME, for the same reason the app list closes on it: the home screen
    // you get back is the one you arranged, not the one you last dragged out of the way.
    LaunchedEffect(homeIntentTick) {
        if (homeIntentTick > 0 && offsetY.value != 0f) {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    // Turning the phone changes how much there is to scroll through. A stack dragged up in
    // landscape kept that offset when the screen went tall again, which left it sitting above
    // the top of the display with nothing on screen to drag it back down by.
    LaunchedEffect(minOffset) {
        // First run restores where the list was before the rebuild; after that the range
        // only clamps whatever the finger left behind.
        if (!offsetRestored) {
            offsetRestored = true
            if (savedOffsetY != 0f) offsetY.snapTo(savedOffsetY.coerceIn(minOffset, 0f))
        } else {
            val settled = offsetY.value.coerceIn(minOffset, 0f)
            if (settled != offsetY.value) offsetY.snapTo(settled)
        }
    }
    val editScrollState = rememberScrollState()

    LaunchedEffect(centerFavorites, favBounds, viewportHeight, rootY, paddings) {
        val (top, bottom) = favBounds ?: return@LaunchedEffect
        if (!centerFavorites || viewportHeight <= 0 || bottom <= top) {
            return@LaunchedEffect
        }
        val blockHeight = bottom - top
        val appliedTopPx = with(density) { padOf(PaddingSlot.FAVORITES_TOP).dp.toPx() }
        // Everything stacked above the favorites' own gap — a widget, Now Playing, their
        // paddings — measured rather than assumed, so this works whatever is up there.
        val above = (top - rootY - offsetY.value) - appliedTopPx
        val desiredPx = ((viewportHeight - blockHeight) / 2f - above).coerceAtLeast(0f)
        val desiredDp = with(density) { desiredPx.toDp().value.roundToInt() }
        // Subtracting the applied gap makes this idempotent, so it settles in one pass; the
        // guard only stops a rounding wobble from looping.
        if (abs(desiredDp - centeredFavTopDp) > 1) centeredFavTopDp = desiredDp
    }

    // The rows take their style from the ambient one rather than naming it, so the shadow can
    // be put behind all of them at once instead of at every call that draws a name.
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(
            shadow = launcherTextShadow(textShadow, contentColor, textShadowStrength),
        ),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewportHeight = it.height
                viewportWidth = it.width
            }
            .onGloballyPositioned { rootY = it.positionInWindow().y }
            // A finger landing on the list takes it from a settling fling at once, not only
            // once it has travelled past the touch slop. Watched, never consumed.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) {
                            scope.launch { offsetY.stop() }
                        }
                    }
                }
            }
            // Two fingers resize the icons; one finger is left entirely to the drag below,
            // which is why nothing is consumed until a second pointer is actually down.
            .then(
                if (pinchToResize) {
                    Modifier.pointerInput(iconSizeDp) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            // A tap is not a resize: only store when two fingers actually
                            // moved the size, or every touch on the wallpaper writes the
                            // store and restarts this very detector.
                            var zoomed = false
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (zoom > 0f && zoom != 1f) {
                                        liveIconSize = (liveIconSize * zoom)
                                            .roundToInt()
                                            .coerceIn(MIN_ICON_DP, MAX_ICON_DP)
                                        zoomed = true
                                    }
                                    event.changes.forEach { if (it.pressed) it.consume() }
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                            if (zoomed) onSetIconSize(liveIconSize)
                        }
                    }
                } else {
                    Modifier
                }
            )
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !editMode && livePaddings.isEmpty(),
                onDragStarted = {
                    // A settling fling from the last gesture must not still own the value
                    // when a new finger lands, or the fresh drags queue behind it.
                    dragGeneration++
                    // Backstop for a corner hold that never released ownership (the screen
                    // went away mid-gesture): a new finger is a new gesture either way.
                    onCornerOwnershipChanged(false)
                    offsetY.stop()
                    revealScroll?.cancel()
                    revealScroll = null
                    rawPullDown = 0f
                    rawPullUp = 0f
                    pullActionFired = false
                    peekFired = false
                },
                state = rememberDraggableState { delta ->
                    // Undamped downward travel once already at the top drives the status-bar
                    // gestures; the damped offset would need more than a screen of dragging.
                    if (delta > 0f && offsetY.value >= -2f) {
                        rawPullDown += delta
                        if (!pullActionFired && rawPullDown > deepPullPx) {
                            pullActionFired = true
                            onExpandShade()
                        } else if (!peekFired && rawPullDown > peekPullPx) {
                            peekFired = true
                            onPeekStatusBar()
                        }
                    }
                    // The mirror of the pull-down, measured from the bottom of the stack.
                    // Reported as it happens rather than fired once at a threshold, so the
                    // list can come in under the finger instead of appearing fully formed.
                    if (swipeUpOpensAppList && delta < 0f && offsetY.value <= minOffset + 2f) {
                        rawPullUp -= delta
                        onSwipeUpDrag(rawPullUp, -delta)
                    }
                    val generation = dragGeneration
                    scope.launch {
                        dragMutex.withLock {
                            if (generation != dragGeneration) return@withLock
                            val next = offsetY.value + delta
                            val outOfBounds = next > 0f || next < minOffset
                            val applied = if (outOfBounds) offsetY.value + delta * 0.4f else next
                            offsetY.snapTo(applied)
                        }
                    }
                },
                onDragStopped = { velocity ->
                    if (!pullActionFired && rawPullDown > peekPullPx && velocity > 2200f) {
                        onExpandShade()
                    }
                    if (swipeUpOpensAppList && rawPullUp > 0f) onSwipeUpEnd(velocity)
                    rawPullDown = 0f
                    rawPullUp = 0f
                    pullActionFired = false
                    peekFired = false

                    // Queued per-increment snaps must not keep landing after the finger lifts,
                    // and the fling must not race them on the same value.
                    dragGeneration++
                    val generation = dragGeneration
                    offsetY.stop()
                    dragMutex.withLock {
                        // A finger that landed while this gesture was still queued for the
                        // lock owns the list now. Running the fling anyway would hold the
                        // lock for its whole spring and swallow that drag until it settled.
                        if (generation != dragGeneration) return@withLock
                        val decay = exponentialDecay<Float>(frictionMultiplier = 1.6f)
                        val target = decay.calculateTargetValue(offsetY.value, velocity)
                            .coerceIn(minOffset, 0f)
                        offsetY.animateTo(
                            targetValue = target,
                            initialVelocity = velocity,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }
                },
            )
            .then(
                // Only worth a pointer handler when something is actually bound to it.
                if (quickLaunchEnabled) {
                    Modifier.pointerInput(editMode, contentHeight) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val slop = viewConfiguration.touchSlop
                            var accX = 0f
                            var accY = 0f
                            var travelled = 0f
                            var claimed = false
                            // The empty space under the whole stack, and the bottom of the
                            // screen even when the favorites reach it, for a list too long
                            // to leave any space. A row that swipes for its shortcuts
                            // still claims the gesture first.
                            val eligible = !editMode && (
                                down.position.y > offsetY.value + contentHeight ||
                                    down.position.y > size.height * (1f - QUICK_LAUNCH_BOTTOM_FRACTION)
                                )
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y
                                if (!claimed) {
                                    accX += dx
                                    accY += dy
                                    // Mostly up-and-down is the list's drag, and the stock
                                    // horizontal detector took it anyway: it spent the slop
                                    // the moment sideways crossed it, and the list stayed
                                    // stuck until the finger lifted and started again.
                                    if (abs(accY) > slop && abs(accY) > abs(accX)) break
                                    // Clearly sideways: only now is the swipe ours.
                                    if (
                                        abs(accX) > slop &&
                                        abs(accX) > QUICK_LAUNCH_DOMINANCE * abs(accY)
                                    ) {
                                        claimed = true
                                        travelled = accX
                                        change.consume()
                                    }
                                } else {
                                    travelled += dx
                                    change.consume()
                                }
                            }
                            if (claimed && eligible && abs(travelled) > quickLaunchPx) {
                                onQuickLaunch(
                                    if (travelled < 0f) QuickLaunchSlot.LEFT else QuickLaunchSlot.RIGHT
                                )
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            // Rows, the widget and the edge zones all claim their own presses, so the only
            // thing that reaches this is bare wallpaper — which until now was the one part of
            // the home screen that answered nothing at all.
            .pointerInput(editMode, onDoubleTapBackground) {
                if (editMode) return@pointerInput
                val doubleTap = onDoubleTapBackground
                detectTapGestures(
                    onLongPress = { offset ->
                        backgroundMenuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                        backgroundMenu = true
                    },
                    onDoubleTap = doubleTap?.let { lock -> { _: Offset -> lock() } },
                )
            },
    ) {
        Column(
            modifier = Modifier
                // Sideways the camera cutout is down one edge rather than along the top, and
                // it sat over the favorites. Only the horizontal sides are taken, so a phone
                // held upright — where the cutout is above everything anyway — is unchanged.
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .then(
                    // With the status bar showing, the first row sat under the clock. The
                    // window draws edge to edge on purpose, so edge to edge includes the
                    // system bar until something says otherwise. Hidden, there is nothing to
                    // clear. Edit mode pads its own header and is left alone.
                    if (avoidStatusBar && !editMode) Modifier.windowInsetsPadding(topBarInsets.only(WindowInsetsSides.Top)) else Modifier
                )
                // The mirror at the bottom: without it the last row slides under the gesture
                // bar and no amount of dragging brings it fully into view — the scroll range
                // is measured on this same column, so the inset is part of what can be
                // reached rather than decoration outside it.
                .navigationBarsPadding()
                // A row of one short name stretched over a tablet is mostly empty space, and
                // the A-Z strip ends up a hand's width from the names it is scrubbing. The cap
                // only bites on a screen wider than a phone held upright, so nothing moves on
                // one that is not. Tablets and unfolded foldables get a wider one, or the
                // phone-sized middle strands the strip just as far away.
                .widthIn(
                    max = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        if (LocalConfiguration.current.smallestScreenWidthDp >= 700) {
                            MAX_CONTENT_WIDTH_TABLET
                        } else {
                            MAX_CONTENT_WIDTH_LANDSCAPE
                        }
                    } else {
                        MAX_CONTENT_WIDTH
                    }
                )
                .align(alignment.wideScreenAlignment())
                .fillMaxWidth()
                // Keeps the rows, widgets and folder cards out from under an always-on strip.
                .padding(start = stripInsetStartDp, end = stripInsetEndDp)
                .then(
                    // Edit mode roughly doubles the stack's height and its handles are
                    // themselves drag targets, so it needs ordinary scrolling.
                    if (editMode) {
                        Modifier.verticalScroll(editScrollState)
                    } else {
                        // Unbounded: the stack is dragged rather than scrolled here, so it
                        // has to be allowed to measure taller than the screen. Without this
                        // anything past the bottom edge is squashed into the leftover space.
                        Modifier
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    }
                )
                .onSizeChanged { contentHeight = it.height },
        ) {
            if (editMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The overlay draws under the status bar, so without this the Done
                        // button sits behind the clock.
                        .windowInsetsPadding(topBarInsets.only(WindowInsetsSides.Top))
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Outlined rather than bare text: it opens a mode of its own, and as a
                    // plain label beside Done it read as a caption rather than something to
                    // press.
                    // On their own surface for the same reason the steppers are: over a
                    // wallpaper that is dark on one side and light on the other, one flat
                    // content color leaves them unreadable on whichever half matches it.
                    OutlinedButton(
                        onClick = onEditScrubBand,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            DelugeIcons.Height,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.handle_az_range))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { onEditModeChange(false) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.Filled.Done, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_done))
                    }
                }
                // Up here only while there are no favorites to put it beside; otherwise it
                // sits with them, since they are all it moves.
                if (firstRowIndex < 0) {
                    StepperRow(
                        label = stringResource(R.string.handle_side_padding),
                        value = sidePaddingDp,
                        range = 0..96,
                        step = PADDING_STEP_DP,
                        onChange = onSetSidePadding,
                        contentColor = contentColor,
                    )
                }
                if (showWidgetSlot) {
                    if (firstRowIndex < 0) Spacer(Modifier.height(6.dp))
                    StepperRow(
                        label = stringResource(R.string.handle_widget_side_padding),
                        value = widgetSidePaddingDp,
                        range = 0..96,
                        step = PADDING_STEP_DP,
                        onChange = onSetWidgetSidePadding,
                        contentColor = contentColor,
                    )
                    Spacer(Modifier.height(6.dp))
                    StepperRow(
                        label = stringResource(R.string.handle_widget_offset_x),
                        value = widgetOffsetXDp,
                        range = -200..200,
                        step = PADDING_STEP_DP,
                        onChange = onSetWidgetOffsetX,
                        contentColor = contentColor,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            // With no widget on screen there is nothing above the favorites, so Now Playing
            // takes that slot instead. Outside edit mode only: while editing, the widget
            // placeholder below draws this same block with its own handles, and drawing it
            // here too would show it twice sharing one menu.
            if (!hasWidget && !editMode && nowPlayingHasContent) {
                NowPlayingBlock(
                    editMode = editMode,
                    showPlaceholder = editMode,
                    heightDp = nowPlayingHeightDp,
                    contentColor = contentColor,
                    alignment = alignment,
                    iconSide = iconSide,
                    sidePaddingDp = sidePaddingDp,
                    padTop = padOf(PaddingSlot.NOW_PLAYING_TOP),
                    padBottom = padOf(PaddingSlot.NOW_PLAYING_BOTTOM),
                    touchPosition = touchPosition,
                    menuExpanded = nowPlayingMenu,
                    menuOffset = nowPlayingMenuOffset,
                    onOpenNowPlaying = onOpenNowPlaying,
                    onOpenMenu = { offset -> nowPlayingMenuOffset = offset; nowPlayingMenu = true },
                    onDismissMenu = { nowPlayingMenu = false },
                    onEditLayout = { nowPlayingMenu = false; onEditModeChange(true) },
                    onOpenSettings = { nowPlayingMenu = false; onOpenSettings() },
                    onResize = onResizeNowPlaying,
                    onSetPadding = { slot, v -> setPadding(slot, v) },
                )
            }

            if (displayItems.isEmpty() && showEmptyHint) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.home_no_favorites_title), color = contentColor, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.home_no_favorites_body),
                        color = contentColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (favoritesReorderable) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onManageFavorites) { Text(stringResource(R.string.home_choose_favorites)) }
                    }
                }
            }

            // The favorites answer to their own icon setting, so a list of bare names here can
            // sit above an app list that still has icons. Remembered because LocalIconConfig is
            // a static local: a fresh instance each composition would invalidate every row
            // under it on every frame.
            CompositionLocalProvider(LocalIconConfig provides favoriteIconConfig) {
            // Two favorites share a row when asked, each taking half, so the columns stay
            // level. Widgets always stand alone, and edit mode pairs nothing: reordering
            // across pairs would fight the swap logic, so it edits single-column.
            val homeLandscape =
                LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val homeTwoColumn =
                (if (homeLandscape) homeTwoColumnLandscape else homeTwoColumnPortrait) && !editMode
            // A narrow half cell stacks names the way this launcher always did, unless the
            // setting asks otherwise.
            val halfCellDp = if (homeTwoColumn && viewportWidth > 0) {
                with(density) { (viewportWidth / 2f).toDp().value }
            } else {
                null
            }
            val (stackFolderNames, stackAppNames) =
                stackHalfCellNames(twoColumnLabels, halfCellDp)
            val homeCells: List<List<Pair<HomeItem, Int>>> =
                remember(displayItems, homeTwoColumn) {
                    if (!homeTwoColumn) {
                        displayItems.mapIndexed { i, item -> listOf(item to i) }
                    } else {
                        buildList {
                            // What needs the whole row: a widget always, and a folder that
                            // opens in place, because the rows it grows into need the width.
                            // A folder that opens in a card is an ordinary cell, so it pairs
                            // like an app — the two-column switch has to mean two columns.
                            fun spansRow(item: HomeItem): Boolean = when (item) {
                                is HomeItem.Widget -> true
                                is HomeItem.FolderItem ->
                                    item.folder.widgets.isNotEmpty() || item.folder.nowPlaying
                                else -> false
                            }
                            var i = 0
                            while (i < displayItems.size) {
                                val item = displayItems[i]
                                val next = displayItems.getOrNull(i + 1)
                                if (!spansRow(item) && next != null && !spansRow(next)) {
                                    add(listOf(item to i, next to (i + 1)))
                                    i += 2
                                } else {
                                    add(listOf(item to i))
                                    i++
                                }
                            }
                        }
                    }
                }
            // Row chrome lives outside the cell: inside a two-column pair's Box the spacer
            // would be measured as an overlay sibling and inflate the row instead of moving
            // it — which is what split the favorites between the top and bottom edges.
            val beforeRow: @Composable (Int, HomeItem) -> Unit = { index, item ->
                // Spacing handles live outside the draggable wrapper: inside it they would
                // travel with a dragged row and skew the height the swap threshold uses.
                if (item is HomeItem.Widget) {
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_widget_top,
                        value = padOf(PaddingSlot.WIDGET_TOP),
                        onChange = { setPadding(PaddingSlot.WIDGET_TOP, it) },
                        contentColor = contentColor,
                    )
                } else if (index == firstRowIndex) {
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_favorites_top,
                        value = padOf(PaddingSlot.FAVORITES_TOP),
                        onChange = { setPadding(PaddingSlot.FAVORITES_TOP, it) },
                        contentColor = contentColor,
                    )
                    // Beside the favorites it moves, rather than up with the widget controls.
                    if (editMode) {
                        StepperRow(
                            label = stringResource(R.string.handle_side_padding),
                            value = sidePaddingDp,
                            range = 0..96,
                            step = PADDING_STEP_DP,
                            onChange = onSetSidePadding,
                            contentColor = contentColor,
                        )
                    }
                }
            }
            val afterRow: @Composable (Int, HomeItem) -> Unit = { index, item ->
                if (item is HomeItem.Widget) {
                    if (editMode) {
                        Spacer(Modifier.height(6.dp))
                        StepperRow(
                            label = stringResource(R.string.handle_widget_height),
                            value = widgetHeightDp,
                            range = 80..900,
                            step = HEIGHT_STEP_DP,
                            onChange = { widgetActions.onResize(it) },
                            contentColor = contentColor,
                        )
                        // Only where it can do something: no other widget says how tall its
                        // content is, so for those the box would do nothing.
                        if (widgetIds.any { isDelugeClock(context, it) }) {
                            Spacer(Modifier.height(6.dp))
                            CheckRow(
                                label = stringResource(R.string.handle_widget_height_auto),
                                checked = widgetHeightAuto,
                                onCheckedChange = { widgetActions.onSetAutoHeight(it) },
                            )
                        }
                    }
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_widget_bottom,
                        value = padOf(PaddingSlot.WIDGET_BOTTOM),
                        onChange = { setPadding(PaddingSlot.WIDGET_BOTTOM, it) },
                        contentColor = contentColor,
                    )
                    // Now Playing sits between the widget and the favorites, and stays there
                    // while editing whether or not anything is playing — see the other branch.
                    if (nowPlayingHasContent || (editMode && nowPlayingEnabled)) {
                        NowPlayingBlock(
                            editMode = editMode,
                            showPlaceholder = editMode,
                            heightDp = nowPlayingHeightDp,
                            contentColor = contentColor,
                            alignment = alignment,
                            iconSide = iconSide,
                            sidePaddingDp = sidePaddingDp,
                            padTop = padOf(PaddingSlot.NOW_PLAYING_TOP),
                            padBottom = padOf(PaddingSlot.NOW_PLAYING_BOTTOM),
                            touchPosition = touchPosition,
                            menuExpanded = nowPlayingMenu,
                            menuOffset = nowPlayingMenuOffset,
                            onOpenNowPlaying = onOpenNowPlaying,
                            onOpenMenu = { offset -> nowPlayingMenuOffset = offset; nowPlayingMenu = true },
                            onDismissMenu = { nowPlayingMenu = false },
                            onEditLayout = { nowPlayingMenu = false; onEditModeChange(true) },
                            onOpenSettings = { nowPlayingMenu = false; onOpenSettings() },
                            onResize = onResizeNowPlaying,
                            onSetPadding = { slot, v -> setPadding(slot, v) },
                        )
                    }
                } else if (index == lastRowIndex) {
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_favorites_bottom,
                        value = padOf(PaddingSlot.FAVORITES_BOTTOM),
                        onChange = { setPadding(PaddingSlot.FAVORITES_BOTTOM, it) },
                        contentColor = contentColor,
                    )
                } else {
                    Spacer(Modifier.height(itemSpacingDp.dp))
                }
            }
            // One row, drawn wherever its caller has keyed it. The key belongs to the loop
            // below, which is where a row's identity has to live — see what it says about
            // branching over the keyed groups.
            val renderItem: @Composable (HomeItem) -> Unit = { item ->
                // Reordering hangs off a visible grab handle now. It used to be a long press
                // anywhere on the row, which nothing on screen advertised and which fought
                // every other thing a long press could mean.
                // No handle on a computed list: its order is the count of openings, and a row
                // dragged somewhere would be back where it was the next time anything launched.
                val itemKey = measureKey(item)
                val dragHandle = if (editMode && favoritesReorderable) {
                    // Keyed by which row this is, never by where it sits. Now that the rows
                    // are identified, a row that moves takes its node with it — so a key of
                    // the position restarts this the moment the held row swaps with its
                    // neighbour, which tears down the gesture half way through: the order is
                    // left shown but never committed, and the row keeps the offset it had
                    // when the finger was taken away from it.
                    Modifier.pointerInput(itemKey) {
                        try {
                        detectDragGestures(
                            onDragStart = {
                                // Where this row is now, rather than where it was when the
                                // handle was built, for the same reason.
                                val items = currentItems.value
                                val at = items.indexOfFirst { measureKey(it) == itemKey }
                                if (at >= 0) {
                                    // The handle is small and the row does not move until the
                                    // finger does, so a tick is the only confirmation that the
                                    // grab took.
                                    HapticUtil.tick(view, hapticFeedback)
                                    dragOrder = items
                                    draggingIndex = at
                                    dragOffset = 0f
                                }
                            },
                            onDragEnd = { commitDragOrder() },
                            onDragCancel = { commitDragOrder() },
                        ) { change, amount ->
                            change.consume()
                            onDragBy(amount.y)
                        }
                        } finally {
                            // detectDragGestures never returns, so this runs only when the
                            // gesture is taken away without a release. Whatever the row had
                            // been dragged to is what is on screen, so that is what gets
                            // saved: dropping it here is what made a row snap back to where
                            // it started the instant it swapped with its neighbour. The
                            // highlight and offset go with the order either way, so nothing
                            // is left standing on the screen.
                            commitDragOrder()
                        }
                    }
                } else {
                    null
                }

                        // Lifted by which row it is, not by where it sits: after a swap the
                        // held row's index has already moved on to the neighbour.
                        val heldKey = draggingIndex?.let { dragOrder?.getOrNull(it) }
                            ?.let(::measureKey)
                        val isHeld = heldKey != null && heldKey == itemKey
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    val measured = measureKey(item)
                                    itemHeights[measured] = coords.size.height
                                    itemTops[measured] = coords.positionInWindow().y
                                    refreshFavBounds()
                                }
                                .zIndex(if (isHeld) 1f else 0f)
                                .graphicsLayer {
                                    if (isHeld) {
                                        translationY = dragOffset
                                        scaleX = 1.03f
                                        scaleY = 1.03f
                                        alpha = 0.9f
                                    }
                                },
                        ) {
                    when (item) {
                        HomeItem.Widget -> Box {
                            // A widget lays out its own contents and most clocks center
                            // theirs, which nothing out here can reach inside. Narrowing the
                            // slot and putting it against a side moves what it draws with it.
                            WidgetSlot(
                                widgetIds = widgetIds,
                                heightDp = widgetHeightDp,
                                stacked = widgetsStacked,
                                autoHeight = widgetHeightAuto,
                                refreshTick = widgetTick,
                                onEditLayout = { onEditModeChange(true) },
                                actions = widgetActions,
                                homeIntentTick = homeIntentTick,
                                modifier = Modifier
                                    .offset(x = widgetOffsetXDp.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = widgetSidePaddingDp.dp),
                            )
                            // The widget is a row in the order like any other, so it needs a
                            // handle of its own to be moved among them.
                            if (dragHandle != null) {
                                DragHandle(
                                    contentColor = contentColor,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(end = dragHandleInsetDp)
                                        .then(dragHandle),
                                )
                            }
                        }

                        is HomeItem.FolderItem -> FolderRow(
                            compactTapArea = compactTapArea,
                            shortcutSwipe = shortcutSwipe,
                            folder = item.folder,
                            members = folderMembers(item.folder),
                            expanded = item.folder.id in expandedFolders,
                            editMode = editMode,
                            popupContents = homeTwoColumn && item.folder.widgets.isEmpty() &&
                                !item.folder.nowPlaying,
                            stackedHalfCell = stackFolderNames,
                            dragHandle = dragHandle,
                            handleInsetDp = dragHandleInsetDp,
                            iconSizeDp = liveIconSize,
                            labelSizeSp = labelSizeSp,
                            sidePaddingDp = sidePaddingDp,
                            controlsInset = folderControlsInset,
                            contentColor = contentColor,
                            showLabels = showFavoriteLabels,
                            alignment = alignment,
                            iconSide = iconSide,
                            onOpenNowPlaying = onOpenNowPlaying,
                            menuExpanded = folderMenuFor == item.folder.id,
                            menuOffset = menuOffset,
                            touchPosition = touchPosition,
                            displayName = { displayName(it) },
                            folderDisguise = folderDisguise,
                            widgetActions = widgetActions,
                            widgetTick = widgetTick,
                            onRemoveFromHome = onRemoveFolderFromHome?.let { remove ->
                                { folderMenuFor = null; remove(item.folder) }
                            },
                            onToggleExpanded = {
                                expandedFolders = if (item.folder.id in expandedFolders) {
                                    expandedFolders - item.folder.id
                                } else {
                                    expandedFolders + item.folder.id
                                }
                            },
                            onExpandedContentBottom = { bottomWindowPx ->
                                // A folder near the bottom of the screen would otherwise hang
                                // its members off the edge, where dragging them into view is
                                // a chore. The list scrolls itself up by whatever sticks
                                // out past the gesture bar — the move a finger makes, only
                                // animated and clamped the same way.
                                // Followed frame by frame while the folder opens, so the list
                                // moves with what it is revealing rather than after it. A
                                // snap, because the growth is already the animation.
                                val limit = rootY + viewportHeight - navBarBottomPx
                                val overshoot = bottomWindowPx - limit
                                if (overshoot > 1f) {
                                    val target = (offsetY.value - overshoot).coerceIn(minOffset, 0f)
                                    if (target != offsetY.value) {
                                        revealScroll?.cancel()
                                        revealScroll = scope.launch {
                                            try {
                                                offsetY.stop()
                                                offsetY.snapTo(target)
                                            } finally {
                                                revealScroll = null
                                            }
                                        }
                                    }
                                }
                            },
                            onOpenMenu = { offset -> menuOffset = offset; folderMenuFor = item.folder.id },
                            onDismissMenu = { folderMenuFor = null },
                            onManage = { folderMenuFor = null; onManageFolder(item.folder) },
                            onFreeze = onFreezeFolder?.let { freeze ->
                                { folderMenuFor = null; freeze(folderMembers(item.folder)) }
                            },
                            onUnfreeze = onUnfreezeFolder?.let { release ->
                                { folderMenuFor = null; release(folderMembers(item.folder)) }
                            },
                            onEdit = { folderMenuFor = null; folderRenameFor = item.folder },
                            onDelete = { folderMenuFor = null; onDeleteFolder(item.folder) },
                            onOpenApp = { member ->
                                // Closed as the app starts rather than on the way back: there
                                // is nothing to come back to yet, and a folder that shuts while
                                // you are still looking at it is the launcher fidgeting.
                                if (closeFolderOnLaunch) {
                                    expandedFolders = expandedFolders - item.folder.id
                                }
                                onOpenFolderApp(member)
                            },
                            onRemoveApp = { member -> onRemoveFromFolder(item.folder, member) },
                            onMemberAppInfo = onAppInfo,
                            onMemberUnpin = onUnpinShortcut,
                            onMemberEditIconName = { member -> renameDialogFor = member },
                            onOpenSettings = onOpenSettings,
                            frozenLook = frozenLook,
                            notificationPreviews = notificationPreviews,
                            notificationTextSp = notificationTextSp,
                            notificationColor = notificationColor,
                            lockedApps = lockedApps,
                            freezeActions = freezeActions,
                        )

                        is HomeItem.Favorite -> FavoriteRow(
                            compactTapArea = compactTapArea,
                            shortcutSwipe = shortcutSwipe,
                            app = item.app,
                            stackedHalfCell = stackAppNames,
                            label = displayName(item.app),
                            notificationLines = notificationPreviews[
                                NotificationPreviewBus.key(item.app.packageName, item.app.userSerial)
                            ].orEmpty(),
                            notificationTextSp = notificationTextSp,
                            notificationColor = notificationColor,
                            editMode = editMode,
                            dragHandle = dragHandle,
                            handleInsetDp = dragHandleInsetDp,
                            iconSizeDp = liveIconSize,
                            labelSizeSp = labelSizeSp,
                            sidePaddingDp = sidePaddingDp,
                            contentColor = contentColor,
                            showLabels = showFavoriteLabels,
                            frozenLook = frozenLook,
                            alignment = alignment,
                            iconSide = iconSide,
                            menuExpanded = menuForKey == item.app.key,
                            menuOffset = menuOffset,
                            touchPosition = touchPosition,
                            onLaunch = { onLaunch(item.app) },
                            onOpenMenu = { offset -> menuOffset = offset; menuForKey = item.app.key },
                            onDismissMenu = { menuForKey = null },
                            onMoveToFolder = { menuForKey = null; onMoveToFolder(item.app) },
                            onAppInfo = { menuForKey = null; onAppInfo(item.app) },
                            onUninstall = { menuForKey = null; onUninstall(item.app) },
                            onToggleLock = { menuForKey = null; onToggleLock(item.app) },
                            onAddToPrivateSpace = onAddToPrivateSpace?.let { add ->
                                { menuForKey = null; add(item.app) }
                            },
                            isLocked = item.app.lockedBy(lockedApps),
                            onUnpin = { menuForKey = null; onUnpinShortcut(item.app) },
                            onRemove = { menuForKey = null; onRemoveFavorite(item.app) },
                            onHide = { menuForKey = null; onHideFavorite(item.app) },
                            onEditIconName = { menuForKey = null; renameDialogFor = item.app },
                            freezeActions = freezeActions,
                        )
                    }
                }

            }
            // Each row is identified by which row it is, not by where it sits: without the
            // key, a loop is matched up by position from one composition to the next, so
            // reordering feeds different data into the same node rather than moving the node —
            // two apps swapping is invisible that way, and a folder and an app swapping reuses
            // each other's node, keeping the size the previous occupant had.
            //
            // And nothing branches over those keyed groups. A keyed group built inside a
            // conditional cannot be moved when the order changes, so the row is built again
            // instead of relocated — and a reorder drag running in that row goes with the
            // rebuild, the instant it should swap with its neighbour: the order it had reached
            // is left uncommitted and the row snaps back to where it started. One column (and
            // edit layout is always one column) therefore has a loop of its own, with the key
            // in it and no branch; two-column mode never reorders, so its branch can stay.
            if (!homeTwoColumn) {
                homeCells.forEach { pair ->
                    val item = pair[0].first
                    val at = pair[0].second
                    beforeRow(at, item)
                    key(measureKey(item)) { renderItem(item) }
                    afterRow(at, item)
                }
            } else {
                homeCells.forEach { pair ->
                    // A widget, and an odd favorite left at the tail, have no neighbour to
                    // take the other half: they stand alone at the full width. Dropping this
                    // branch is how the two-column switches came to crash — a one-element
                    // pair reached `pair[1]`. (Two columns never reorder, so branching around
                    // the keys is safe here; see the note above.)
                    beforeRow(pair[0].second, pair[0].first)
                    if (pair.size == 1) {
                        key(measureKey(pair[0].first)) { renderItem(pair[0].first) }
                    } else {
                    // Pulled together by one side padding: two rows side by side otherwise
                    // double the gutter in the middle while the outer edges keep one. Mirrors
                    // the (sidePadding - 8) the rows pad themselves with, so the middle gutter
                    // lands on exactly one padding, never less and never overlapping.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((-(sidePaddingDp - 8).coerceAtLeast(0)).dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            key(measureKey(pair[0].first)) { renderItem(pair[0].first) }
                        }
                        Box(Modifier.weight(1f)) {
                            key(measureKey(pair[1].first)) { renderItem(pair[1].first) }
                        }
                    }
                    }
                    val tail = pair.last()
                    afterRow(tail.second, tail.first)
                }
            }
            }
        }

        // The corner button: visible for any gesture with something to run, and always in
        // edit layout, where it is the thing being moved. Placement lives in CornerGeometry.
        val cornerTapLive = cornerTapAction != CornerAction.DO_NOTHING &&
            (cornerTapAction.targetKind() == null || cornerButtonApp != null || cornerButtonIsSearch)
        val cornerSwipeLive = cornerSwipeAction != CornerAction.DO_NOTHING &&
            (cornerSwipeAction.targetKind() == null || cornerSwipeBound)
        CornerButton(
            visible = editMode || cornerTapLive || cornerSwipeLive,
            storedPos = cornerPosEffective,
            frame = cornerFrame,
            editMode = editMode,
            tapAction = cornerTapAction,
            swipeAction = cornerSwipeAction,
            hapticFeedback = hapticFeedback,
            onTap = { onCornerAction(cornerTapAction) },
            onSwipe = onCornerSwipeAction,
            onOwnershipChanged = onCornerOwnershipChanged,
            onCommitPos = onSetCornerPos,
            onBoundsChanged = onCornerBoundsChanged,
        )

        LaunchedEffect(backgroundMenu) { if (backgroundMenu) customizeMenu = false }
        TouchAnchoredMenu(
            expanded = backgroundMenu,
            offset = backgroundMenuOffset,
            onDismissRequest = { backgroundMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_customize)) },
                leadingIcon = { Icon(DelugeIcons.Tune, contentDescription = null) },
                onClick = { backgroundMenu = false; customizeMenu = true },
            )
            if (freezeEnabled) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.freeze_open_list)) },
                    leadingIcon = { Icon(DelugeIcons.Freeze, contentDescription = null) },
                    onClick = { backgroundMenu = false; onOpenFreezeList() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.freeze_freeze_all)) },
                    leadingIcon = { Icon(DelugeIcons.Freeze, contentDescription = null) },
                    onClick = { backgroundMenu = false; onFreezeAll() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.freeze_unfreeze_all)) },
                    leadingIcon = { Icon(DelugeIcons.Thaw, contentDescription = null) },
                    onClick = { backgroundMenu = false; onUnfreezeAll() },
                )
                HorizontalDivider()
            }
            if (networkActions != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.network_open)) },
                    leadingIcon = { Icon(DelugeIcons.LinkOff, contentDescription = null) },
                    onClick = { backgroundMenu = false; networkActions.open() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = { backgroundMenu = false; onOpenSettings() },
            )
        }

        if (customizeMenu) {
            TouchAnchoredMenu(
                expanded = true,
                offset = backgroundMenuOffset,
                onDismissRequest = { customizeMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_layout)) },
                    leadingIcon = { Icon(DelugeIcons.Tune, contentDescription = null) },
                    onClick = { customizeMenu = false; onEditModeChange(true) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_add)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { customizeMenu = false; widgetActions.onAddWidget() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_choose_favorites)) },
                    leadingIcon = { Icon(DelugeIcons.Checklist, contentDescription = null) },
                    onClick = { customizeMenu = false; onManageFavorites() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_change_wallpaper)) },
                    leadingIcon = { Icon(DelugeIcons.Wallpaper, contentDescription = null) },
                    onClick = { customizeMenu = false; onChangeWallpaper() },
                )
            }
        }
    }

    folderRenameFor?.let { folder ->
        FolderEditDialog(
            currentName = folder.name,
            hasCustomIcon = folder.icon != null,
            onConfirm = { name -> onRenameFolder(folder, name); folderRenameFor = null },
            onChangeIcon = { name ->
                // Picking an icon leaves this dialog for another screen, taking the half-typed
                // name with it unless it is saved on the way out.
                if (name.isNotBlank() && name.trim() != folder.name) onRenameFolder(folder, name.trim())
                onChangeFolderIcon(folder)
                folderRenameFor = null
            },
            onResetIcon = { onResetFolderIcon(folder); folderRenameFor = null },
            onDismiss = { folderRenameFor = null },
        )
    }

    renameDialogFor?.let { app ->
        EditAppDialog(
            currentName = displayName(app),
            onConfirmName = { name -> onSetName(app, name); renameDialogFor = null },
            onChangeIcon = { name ->
                if (name.trim() != displayName(app)) onSetName(app, name.trim())
                onChangeIcon(app)
                renameDialogFor = null
            },
            onDismiss = { renameDialogFor = null },
        )
    }
    }
}

/** One favorite: icon, optional name, press highlight and its context menu. */
@Composable
private fun FavoriteRow(
    /** Whether only the icon and name answer a tap, rather than the whole line. */
    compactTapArea: Boolean,
    shortcutSwipe: ShortcutSwipe,
    /** Whether a half cell is too narrow to keep the icon and the name side by side. */
    stackedHalfCell: Boolean = false,
    app: AppInfo,
    label: String,
    /** The newest notifications' words, newest first; empty when there is nothing to show. */
    notificationLines: List<NotificationEntry>,
    /** Point size for those lines. */
    notificationTextSp: Int,
    notificationColor: Int = 0,
    editMode: Boolean,
    /** Non-null in edit mode: the gesture that reorders, attached to this row's grab handle. */
    dragHandle: Modifier?,
    /** How far in from the end the handle sits, clear of the system back gesture. */
    handleInsetDp: Dp,
    iconSizeDp: Int,
    labelSizeSp: Int,
    sidePaddingDp: Int,
    contentColor: Color,
    showLabels: Boolean,
    frozenLook: FrozenLook,
    alignment: HomeAlignment,
    iconSide: IconSide,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    touchPosition: MutableState<Offset>,
    onLaunch: () -> Unit,
    onOpenMenu: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onMoveToFolder: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onToggleLock: () -> Unit,
    onAddToPrivateSpace: (() -> Unit)?,
    isLocked: Boolean,
    onUnpin: () -> Unit,
    onRemove: () -> Unit,
    onHide: () -> Unit,
    onEditIconName: () -> Unit,
    freezeActions: FreezeActions?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current
    var shortcutMenu by remember { mutableStateOf(false) }
    // The freeze entry has to pick a method before it can do anything, and the dialog has to
    // outlive the menu it was opened from, so the state sits with the row.
    var freezeChoosing by remember { mutableStateOf(false) }

    // The notification line sits below the row rather than inside its label column, so a long
    // one cannot widen the label and push a centered icon off its mark.
    val iconLeft = iconSide == IconSide.LEFT && alignment != HomeAlignment.RIGHT
    val showIconsHere = LocalIconConfig.current.showIcons
    val previewStart = when {
        alignment == HomeAlignment.CENTER -> sidePaddingDp
        iconLeft && showIconsHere && showLabels -> sidePaddingDp + 8 + iconSizeDp + 16
        else -> sidePaddingDp
    }

    Column(Modifier.fillMaxWidth()) {
    Box(Modifier.fillMaxWidth()) {
        // Frozen and paused apps are drawn by the look setting: faded, colourless, or as any
        // other row. A hidden or disabled shortcut still fades, since the look is about
        // freezing and saying so in daylight, not about hiding an app from being seen.
        val setAside = app.frozen != null || app.suspended || app.quietProfile
        val dimmed = app.disabled || (frozenLook == FrozenLook.DIMMED && setAside)
        val greyscale = frozenLook == FrozenLook.GREYSCALE && setAside
        val appIcon: @Composable () -> Unit = {
            AppIcon(
                app = app,
                sizeDp = iconSizeDp,
                desaturate = greyscale,
                modifier = Modifier
                    .pressPop(interaction)
                    .then(if (dimmed) Modifier.graphicsLayer { alpha = 0.5f } else Modifier),
            )
        }
        val appLabel: @Composable (Modifier) -> Unit = { labelModifier ->
            Row(
                modifier = labelModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    // Same dimming a hidden app gets in the A-Z list: a shortcut its
                    // publisher has switched off is still listed, but tapping it only
                    // explains why it will not open.
                    color = if (dimmed || greyscale) contentColor.copy(alpha = 0.5f) else contentColor,
                    fontSize = labelSizeSp.sp,
                    textAlign = alignment.textAlign(),
                    // A name in a half row has a letter's width before it starts stacking
                    // downwards; two lines then a cut reads like a name, not a column.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // A lock beside the name, so which apps ask for the device lock is visible
                // without opening each one's menu.
                if (isLocked) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.action_lock_app),
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        val rowModifier = Modifier
            // Only the icon and name answer a tap when the row is narrowed to them; the
            // rest of the line is the home screen again, not a button nobody can see.
            .then(if (compactTapArea) Modifier.align(alignment.boxAlignment()) else Modifier.fillMaxWidth())
            .padding(horizontal = (sidePaddingDp - 8).coerceAtLeast(0).dp)
            .background(
                color = if (pressed) contentColor.copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            )
            .then(
                // In edit mode the row must not claim the long press, or the reorder
                // drag above it never starts.
                if (editMode) {
                    Modifier
                } else {
                    Modifier
                        .recordTouchPosition(touchPosition)
                        .swipeForShortcuts(shortcutSwipe, app.kind == EntryKind.APP) {
                            shortcutMenu = true
                        }
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onLaunch,
                            onLongClick = {
                                onOpenMenu(
                                    with(density) {
                                        DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                                    }
                                )
                            },
                        )
                }
            )
            .padding(start = 8.dp, end = handleReserve(dragHandle, sidePaddingDp, handleInsetDp), top = 4.dp, bottom = 4.dp)
        // Icon over name, the same way the folder next to it draws, so a pair never has one
        // name beside its icon and the other under it.
        if (stackedHalfCell) {
            Column(modifier = rowModifier, horizontalAlignment = alignment.columnAlignment()) {
                appIcon()
                if (showLabels) {
                    appLabel(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp))
                }
            }
        } else {
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = alignment.arrangement(),
            ) {
                AlignedIconLabel(
                    alignment = alignment,
                    showLabel = showLabels,
                    iconSide = iconSide,
                    iconWidth = iconSizeDp.dp,
                    icon = appIcon,
                    label = { appLabel(it) },
                )
            }
        }

        // Outside the row's own layout so it sits against the screen edge rather than
        // wherever the alignment happens to leave the text, and so it never shifts the
        // icon and label it belongs to. Held further in than the steppers by the back
        // gesture's own width: a drag that starts in the system's edge zone is claimed
        // by the system, and this handle would never hear of it (see HomeScreen's inset).
        if (dragHandle != null) {
            DragHandle(
                contentColor,
                Modifier.align(Alignment.CenterEnd).padding(end = handleInsetDp).then(dragHandle),
            )
        }

        AppShortcutMenu(app, shortcutMenu, labelSizeSp) { shortcutMenu = false }

        var moreMenu by remember { mutableStateOf(false) }
        // The network answers open one level down, from a single row in this menu.
        var networkMenu by remember { mutableStateOf(false) }
        // The favorite's first level is about the row in front of you: lock it, put it in
        // the private space, take it off the home screen, or go one step deeper. Editing
        // and app management are not what a press on a favorite usually means.
        LaunchedEffect(menuExpanded) {
            if (menuExpanded) {
                moreMenu = false
                networkMenu = false
            }
        }
        TouchAnchoredMenu(expanded = menuExpanded, offset = menuOffset, onDismissRequest = onDismissMenu) {
            if (app.kind == EntryKind.APP) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (isLocked) R.string.action_unlock_app else R.string.action_lock_app
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (isLocked) DelugeIcons.LockOpen else Icons.Filled.Lock,
                            contentDescription = null,
                        )
                    },
                    onClick = onToggleLock,
                )
                FocusMenuItem(app = app, onDismiss = onDismissMenu)
                FreezeMenuItems(
                    app = app,
                    actions = freezeActions,
                    onDismissMenu = onDismissMenu,
                    locked = isLocked,
                    onChooseMethod = { onDismissMenu(); freezeChoosing = true },
                )
                NetworkMenuItems(
                    app = app,
                    actions = LocalNetworkActions.current,
                    onDismissMenu = onDismissMenu,
                    onOpenSubmenu = { networkMenu = true },
                )
                // A locked app must be unlocked before it can be hidden, as in the app list:
                // hiding is a launcher action and the lock still owns the package state.
                if (!isLocked) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.applist_hide)) },
                        leadingIcon = { Icon(DelugeIcons.VisibilityOff, contentDescription = null) },
                        onClick = { onDismissMenu(); onHide() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_app_info)) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = { onDismissMenu(); onAppInfo() },
                )
            }
            // Distinct from unfavoriting below, which only takes it off the home screen: this
            // hands it back to the app that pinned it, and it leaves the launcher entirely.
            if (app.kind == EntryKind.SHORTCUT) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove_shortcut)) },
                    leadingIcon = { Icon(DelugeIcons.LinkOff, contentDescription = null) },
                    onClick = onUnpin,
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.applist_remove_favorite)) },
                leadingIcon = { Icon(DelugeIcons.StarBorder, contentDescription = null) },
                onClick = onRemove,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_more)) },
                leadingIcon = { Icon(Icons.Filled.MoreVert, contentDescription = null) },
                onClick = { onDismissMenu(); moreMenu = true },
            )
        }

        if (moreMenu) {
            TouchAnchoredMenu(expanded = true, offset = menuOffset, onDismissRequest = { moreMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                    leadingIcon = { Icon(DelugeIcons.Tune, contentDescription = null) },
                    onClick = { moreMenu = false; onEditIconName() },
                )
                if (app.kind != EntryKind.PRIVATE_SPACE) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_to_folder)) },
                        leadingIcon = { Icon(DelugeIcons.Folder, contentDescription = null) },
                        onClick = { moreMenu = false; onMoveToFolder() },
                    )
                }
                if (app.kind == EntryKind.APP) {
                    if (onAddToPrivateSpace != null && app.userSerial == 0L && !app.privateProfile) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_to_private_space)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = { moreMenu = false; onAddToPrivateSpace() },
                        )
                    }
                    FreezeRemoveMenuItem(
                        app = app,
                        actions = freezeActions,
                        onDismissMenu = { moreMenu = false },
                        locked = isLocked,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.action_uninstall),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { moreMenu = false; onUninstall() },
                    )
                }
            }
        }

        if (networkMenu) {
            TouchAnchoredMenu(
                expanded = true,
                offset = menuOffset,
                onDismissRequest = { networkMenu = false },
            ) {
                NetworkSubmenu(
                    app = app,
                    actions = LocalNetworkActions.current,
                    onDismissMenu = { networkMenu = false },
                )
            }
        }

        FreezeMethodDialog(
            app = if (freezeChoosing) app else null,
            label = label,
            counts = freezeActions?.methodCounts ?: emptyMap(),
            supports = { method, serial -> freezeActions?.supports?.invoke(method, serial) ?: true },
            onDismiss = { freezeChoosing = false },
            onPick = { method ->
                freezeChoosing = false
                freezeActions?.add(app, method)
            },
        )
    }
    // A locked app keeps its notifications to itself: the text would be readable on the home
    // screen, and the line's tap reaches the app's own activity without the lock.
    if (!editMode && !isLocked && notificationLines.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = previewStart.dp,
                    end = sidePaddingDp.dp,
                    top = 1.dp,
                    bottom = 2.dp,
                )
        ) {
            NotificationLines(
                lines = notificationLines,
                alignment = alignment,
                contentColor = notificationTint(contentColor, notificationColor),
                textSizeSp = notificationTextSp,
                interactive = true,
                onFallback = onLaunch,
            )
        }
    }
    }
}

/** A folder row, which expands in place to show its apps. */
@Composable
private fun FolderRow(
    /** Whether only the icon and name answer a tap, rather than the whole line. */
    compactTapArea: Boolean,
    shortcutSwipe: ShortcutSwipe,
    folder: Folder,
    members: List<AppInfo>,
    expanded: Boolean,
    editMode: Boolean,
    /** Whether its contents open as a card over the screen instead of growing in place. */
    popupContents: Boolean,
    /** Whether a half cell is too narrow to keep the icon and the name side by side. */
    stackedHalfCell: Boolean = false,
    /** Non-null in edit mode: the gesture that reorders, attached to this row's grab handle. */
    dragHandle: Modifier?,
    /** How far in from the end the handle sits, clear of the system back gesture. */
    handleInsetDp: Dp,
    iconSizeDp: Int,
    labelSizeSp: Int,
    sidePaddingDp: Int,
    /** Keeps this folder's own controls out from under the edge zone. */
    controlsInset: Modifier,
    contentColor: Color,
    showLabels: Boolean,
    alignment: HomeAlignment,
    iconSide: IconSide,
    /** Starts whatever is playing, through the app lock gate when it is a locked app. */
    onOpenNowPlaying: () -> Boolean,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    touchPosition: MutableState<Offset>,
    displayName: (AppInfo) -> String,
    /** Folder cover names by folder id while the screenshot disguise is on; empty otherwise. */
    folderDisguise: Map<String, String>,
    onToggleExpanded: () -> Unit,
    /** Reports where the expanded members end, in window pixels, once they have settled. */
    onExpandedContentBottom: (Float) -> Unit,
    onOpenMenu: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onManage: () -> Unit,
    onFreeze: (() -> Unit)?,
    onUnfreeze: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromHome: (() -> Unit)?,
    widgetActions: WidgetSlotActions,
    /** Bumped on every package event, so a folder's frozen widget providers re-read. */
    widgetTick: Int,
    onOpenApp: (AppInfo) -> Unit,
    onRemoveApp: (AppInfo) -> Unit,
    onMemberAppInfo: (AppInfo) -> Unit,
    onMemberUnpin: (AppInfo) -> Unit,
    onMemberEditIconName: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    /** Whether a paused (suspended) app is shown greyed out, like outside folders. */
    frozenLook: FrozenLook,
    /** Newest notifications per app, for the lines under its name. */
    notificationPreviews: Map<String, List<NotificationEntry>>,
    notificationTextSp: Int,
    notificationColor: Int = 0,
    /** Keys that ask for the device lock before opening. */
    lockedApps: Set<String>,
    /**
     * Freeze-list actions a member's long-press menu offers, the same set the rows outside a
     * folder get. Null where freezing is not set up, and then the entries simply do not appear.
     */
    freezeActions: FreezeActions? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current
    // Which member is showing its menu, and where the press that opened it landed.
    var memberMenuFor by remember { mutableStateOf<String?>(null) }
    // Which member has its network answers open, one level below that menu.
    var memberNetworkFor by remember { mutableStateOf<String?>(null) }
    // Which member is picking a freeze method. By key rather than a flag: the dialog has to
    // outlive the menu it came from, and the member it belongs to has to survive that too.
    var memberFreezeChoosing by remember { mutableStateOf<String?>(null) }
    var memberMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val memberTouch = remember { mutableStateOf(Offset.Zero) }
    var memberShortcutFor by remember { mutableStateOf<String?>(null) }
    // Where the expanded members currently end, in window pixels like HomeScreen's own
    // row measures. Written every layout pass and read only by the wait at the bottom
    // of this composable, so no recomposition rides on it.
    var membersBottomPx by remember { mutableFloatStateOf(Float.NaN) }

    Column {
        // An emptied folder keeps its row (deleting it silently would surprise), but there
        // is nothing to open: no chevron, and a tap does nothing.
        val canExpand = members.isNotEmpty() || folder.widgets.isNotEmpty() || folder.nowPlaying
        Box(Modifier.fillMaxWidth()) {
            val folderIcon: @Composable () -> Unit = {
                Box(Modifier.pressPop(interaction)) {
                    FolderIcon(
                        members = members,
                        sizeDp = iconSizeDp,
                        contentColor = contentColor,
                        iconOverride = folder.icon,
                        // A frozen member's square follows the look setting like its row does,
                        // so a folder of greyed apps does not preview them in colour.
                        memberDesaturate = { app ->
                            frozenLook == FrozenLook.GREYSCALE &&
                                (app.frozen != null || app.suspended || app.quietProfile)
                        },
                    )
                }
            }
            val folderName = folderDisguise[folder.id] ?: folder.name
            // An unnamed folder is allowed: its preview icon is its face. Only when icons are
            // off is there nothing left to read, so the row says "Folder".
            val drawn = when {
                folderName.isNotBlank() -> folderName
                !LocalIconConfig.current.showIcons -> stringResource(R.string.folder_unnamed)
                else -> null
            }
            val rowModifier = Modifier
                .then(if (compactTapArea) Modifier.align(alignment.boxAlignment()) else Modifier.fillMaxWidth())
                .padding(horizontal = (sidePaddingDp - 8).coerceAtLeast(0).dp)
                .background(
                    color = if (pressed) contentColor.copy(alpha = 0.15f) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                )
                .then(
                    if (editMode) {
                        Modifier
                    } else {
                        Modifier
                            .recordTouchPosition(touchPosition)
                            .combinedClickable(
                                interactionSource = interaction,
                                indication = null,
                                // The chevron is hidden in a half cell, so the row itself has
                                // to say that a tap opens it and what is inside.
                                onClickLabel = if (canExpand) {
                                    stringResource(R.string.folder_open_label, members.size)
                                } else {
                                    null
                                },
                                onClick = { if (canExpand) onToggleExpanded() },
                                onLongClick = {
                                    onOpenMenu(
                                        with(density) {
                                            DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                                        }
                                    )
                                },
                            )
                    }
                )
                .padding(start = 8.dp, end = handleReserve(dragHandle, sidePaddingDp, handleInsetDp), top = 4.dp, bottom = 4.dp)
            if (stackedHalfCell && popupContents) {
                // Icon over name. A half cell this narrow leaves a name beside the icon a few
                // characters, and under the icon the name gets the whole cell instead. The app
                // in the other half is drawn the same way, so the pair reads as one row.
                Column(modifier = rowModifier, horizontalAlignment = alignment.columnAlignment()) {
                    folderIcon()
                    if (showLabels && drawn != null) {
                        Text(
                            drawn,
                            color = contentColor,
                            fontSize = labelSizeSp.sp,
                            textAlign = alignment.textAlign(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                        )
                    }
                }
            } else {
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = alignment.arrangement(),
            ) {
                AlignedIconLabel(
                    alignment = alignment,
                    showLabel = showLabels,
                    iconSide = iconSide,
                    iconWidth = iconSizeDp.dp,
                    icon = folderIcon,
                ) { labelModifier ->
                    if (drawn != null) {
                        Text(
                            drawn,
                            color = contentColor,
                            fontSize = labelSizeSp.sp,
                            modifier = labelModifier,
                            textAlign = alignment.textAlign(),
                            // Two lines then a cut, like the app row beside it. Left to wrap
                            // freely, a narrow cell turned a folder name into a column of
                            // single letters.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // What a bare row never says: whether tapping opens anything, and how
                // much is inside. The chevron turns with the expansion so the state is
                // visible without reading anything. Nothing inside, nothing to open.
                // A folder that opens in a card does not need it either: in the half row
                // it is the chevron's ~45dp that leaves the name a couple of letters, and
                // the preview icon already says what the row is.
                if (!editMode && canExpand && !popupContents) {
                    FolderChevron(expanded = expanded, count = members.size, contentColor = contentColor)
                }
            }
            }

            // Outside the row's own layout so it sits against the screen edge rather than
            // wherever the alignment happens to leave the text. Held in by the back
            // gesture's own width as well — see HomeScreen's inset.
            if (dragHandle != null) {
                DragHandle(
                contentColor,
                Modifier.align(Alignment.CenterEnd).padding(end = handleInsetDp).then(dragHandle),
            )
            }

            TouchAnchoredMenu(expanded = menuExpanded, offset = menuOffset, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_folder_choose_apps)) },
                    leadingIcon = { Icon(DelugeIcons.Checklist, contentDescription = null) },
                    onClick = onManage,
                )
                onFreeze?.let { freeze ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_freeze)) },
                        leadingIcon = { Icon(DelugeIcons.Freeze, contentDescription = null) },
                        enabled = members.isNotEmpty(),
                        onClick = freeze,
                    )
                }
                onUnfreeze?.let { release ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_unfreeze)) },
                        leadingIcon = { Icon(DelugeIcons.Thaw, contentDescription = null) },
                        enabled = members.isNotEmpty(),
                        onClick = release,
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_add)) },
                    leadingIcon = { Icon(DelugeIcons.Height, contentDescription = null) },
                    onClick = { onDismissMenu(); widgetActions.onAddWidgetToFolder(folder.id) },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (folder.nowPlaying) R.string.folder_remove_now_playing
                                else R.string.folder_add_now_playing
                            )
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = {
                        onDismissMenu()
                        widgetActions.onSetFolderNowPlaying(folder.id, !folder.nowPlaying)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = onEdit,
                )
                HorizontalDivider()
                onRemoveFromHome?.let { remove ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_remove_from_home)) },
                        leadingIcon = { Icon(DelugeIcons.StarBorder, contentDescription = null) },
                        onClick = remove,
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_folder_delete)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = onDelete,
                )
            }
        }

        // Members expand in place rather than opening a separate screen. The
        // transition is held here rather than derived from `visible` so the wait
        // below can ask it when it has finished.
        val expansion = remember { MutableTransitionState(false) }
        // Half a row has no width to open a grid into, so there the folder opens as a card
        // over the home screen instead. A folder holding widgets or the playing card keeps
        // the in-place expansion: those belong under the folder's own row.
        expansion.targetState = expanded && !editMode && canExpand && !popupContents
        @Composable
        fun Contents() {
            // Inside the card the wallpaper is hidden, so labels follow the card instead of
            // the colour chosen to sit on the wallpaper.
            val contentsColor = if (popupContents) LocalContentColor.current else contentColor
            Column(
                modifier = Modifier.onGloballyPositioned { coords ->
                    membersBottomPx = coords.positionInWindow().y + coords.size.height
                },
            ) {
                // Above the apps, so a folder's widget is what it opens onto.
                folder.widgets.forEach { widget ->
                    key(widget.id) {
                        FolderWidgetBlock(folder.id, widget, widgetTick, widgetActions, controlsInset)
                    }
                }
                if (folder.nowPlaying) {
                    // Shown even when silent: it was put here on purpose, and a folder that
                    // opens without it looks like it lost it. Only a playing card is a way in;
                    // an empty one is a placeholder, so it does not pretend to be tappable.
                    val context = LocalContext.current
                    val playing by NowPlayingBus.state.collectAsState()
                    NowPlayingWidget(
                        contentColor = contentsColor,
                        alignment = alignment,
                        showPlaceholder = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(controlsInset)
                            .padding(horizontal = sidePaddingDp.dp)
                            .then(
                                if (playing == null) {
                                    Modifier
                                } else {
                                    Modifier.clickable {
                                        if (!onOpenNowPlaying()) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.now_playing_open_failed),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                            ),
                    )
                }
                members.forEach { member ->
                    // Keyed so each member keeps its own press state: an unkeyed remember in
                    // a loop would hand one row's press to whichever row moved into its slot.
                    key(member.key) {
                    val memberPress = remember { MutableInteractionSource() }
                    Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Members sit one indent inside their folder, on whichever side
                            // the folder's own name is on.
                            .padding(
                                start = if (alignment == HomeAlignment.RIGHT) sidePaddingDp.dp else (sidePaddingDp + 24).dp,
                                end = if (alignment == HomeAlignment.RIGHT) (sidePaddingDp + 24).dp else sidePaddingDp.dp,
                            )
                            // The edge strip sits over the last few dp of every row, and a
                            // member row is the one place a tap near the name could open the
                            // app list instead of the app. Kept out the same way the folder's
                            // own widgets are.
                            .then(controlsInset)
                            .recordTouchPosition(memberTouch)
                            .swipeForShortcuts(shortcutSwipe, member.kind == EntryKind.APP) {
                                memberShortcutFor = member.key
                            }
                            // A long press used to throw the app straight out of the folder,
                            // with no warning and no undo, while everywhere else on this
                            // screen it opens a menu. It opens one here too now, and removing
                            // is an item in it.
                            .combinedClickable(
                                interactionSource = memberPress,
                                indication = null,
                                onClick = { onOpenApp(member) },
                                onLongClick = {
                                    memberMenuOffset = with(density) {
                                        DpOffset(memberTouch.value.x.toDp(), memberTouch.value.y.toDp())
                                    }
                                    memberMenuFor = member.key
                                },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = alignment.arrangement(),
                    ) {
                        // Same look as outside a folder: the member row wears the setting,
                        // not a second style of its own.
                        val setAside = member.frozen != null || member.suspended ||
                            member.quietProfile
                        val dimmed = member.disabled ||
                            (frozenLook == FrozenLook.DIMMED && setAside)
                        val greyscale = frozenLook == FrozenLook.GREYSCALE && setAside
                        AlignedIconLabel(
                            alignment = alignment,
                            iconSide = iconSide,
                            showLabel = showLabels,
                            iconWidth = (iconSizeDp * 0.8f).dp,
                            icon = {
                                Box(
                                    Modifier
                                        .pressPop(memberPress)
                                        .then(if (dimmed) Modifier.graphicsLayer { alpha = 0.5f } else Modifier),
                                ) {
                                    AppIcon(
                                        app = member,
                                        sizeDp = (iconSizeDp * 0.8f).toInt(),
                                        desaturate = greyscale,
                                    )
                                }
                            },
                        ) { labelModifier ->
                            Column(
                                labelModifier,
                                horizontalAlignment = alignment.columnAlignment(),
                            ) {
                                Text(
                                    displayName(member),
                                    color = if (dimmed || greyscale) {
                                        contentsColor.copy(alpha = 0.5f)
                                    } else {
                                        contentsColor
                                    },
                                    fontSize = labelSizeSp.sp,
                                    textAlign = alignment.textAlign(),
                                    // Two lines at most: a folder opened inside a narrow
                                    // column used to break a name mid-word down the row.
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // A locked app keeps its notifications to itself, as it does
                                // outside a folder: the text would be readable here.
                                if (!member.lockedBy(lockedApps)) {
                                    NotificationLines(
                                        lines = notificationPreviews[
                                            NotificationPreviewBus.key(member.packageName, member.userSerial)
                                        ].orEmpty(),
                                        alignment = alignment,
                                        textSizeSp = notificationTextSp,
                                        contentColor = notificationTint(contentsColor, notificationColor),
                                        interactive = true,
                                        onFallback = { onOpenApp(member) },
                                    )
                                }
                            }
                        }
                    }

                    AppShortcutMenu(
                        member,
                        memberShortcutFor == member.key,
                        labelSizeSp,
                    ) { memberShortcutFor = null }

                    TouchAnchoredMenu(
                        expanded = memberMenuFor == member.key,
                        offset = memberMenuOffset,
                        onDismissRequest = { memberMenuFor = null },
                    ) {
                        // A folder member is an app like any other row, so it offers what the
                        // app publishes the same way the rows outside a folder do — freezing
                        // included, since putting an app in a folder says nothing about
                        // whether it should be running.
                        if (member.kind == EntryKind.APP) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_app_info)) },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                onClick = { memberMenuFor = null; onMemberAppInfo(member) },
                            )
                            // Same entries as outside a folder, and the same bargain: adding
                            // an app here is adding it to the freeze list, which freezes it.
                            FreezeMenuItems(
                                app = member,
                                actions = freezeActions,
                                onDismissMenu = { memberMenuFor = null },
                                locked = member.lockedBy(lockedApps),
                                onChooseMethod = {
                                    memberMenuFor = null
                                    memberFreezeChoosing = member.key
                                },
                            )
                            NetworkMenuItems(
                                app = member,
                                actions = LocalNetworkActions.current,
                                onDismissMenu = { memberMenuFor = null },
                                onOpenSubmenu = {
                                    memberMenuFor = null
                                    memberNetworkFor = member.key
                                },
                            )
                        }
                        if (member.kind == EntryKind.SHORTCUT) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_remove_shortcut)) },
                                leadingIcon = { Icon(DelugeIcons.LinkOff, contentDescription = null) },
                                onClick = { memberMenuFor = null; onMemberUnpin(member) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                            leadingIcon = { Icon(DelugeIcons.Tune, contentDescription = null) },
                            onClick = { memberMenuFor = null; onMemberEditIconName(member) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_folder_choose_apps)) },
                            leadingIcon = { Icon(DelugeIcons.Checklist, contentDescription = null) },
                            onClick = { memberMenuFor = null; onManage() },
                        )
                        HorizontalDivider()
                        // Taking the app off the freeze list lives one level in, with the rest
                        // of the row's management, exactly as it does outside a folder.
                        if (member.kind == EntryKind.APP) {
                            FreezeRemoveMenuItem(
                                app = member,
                                actions = freezeActions,
                                onDismissMenu = { memberMenuFor = null },
                                locked = member.lockedBy(lockedApps),
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_folder_remove_app)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { memberMenuFor = null; onRemoveApp(member) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_settings)) },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = { memberMenuFor = null; onOpenSettings() },
                        )
                    }
                    }
                }
                }
                if (members.isEmpty() && (expanded || editMode)) {
                    Text(
                        stringResource(R.string.home_folder_empty),
                        color = contentsColor.copy(alpha = 0.6f),
                        fontSize = (labelSizeSp - 3).coerceAtLeast(10).sp,
                        modifier = Modifier.padding(start = (sidePaddingDp + 24).dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
        }
        if (popupContents) {
            if (expanded && !editMode && canExpand) {
                FolderContentsDialog(
                    title = (folderDisguise[folder.id] ?: folder.name)
                        .ifBlank { stringResource(R.string.folder_unnamed) },
                    onDismiss = onToggleExpanded,
                    content = { Contents() },
                )
            }
        } else {
            AnimatedVisibility(visibleState = expansion) { Contents() }
        }

        // A member's network answers, resolved by key so one removed by a package event in
        // the meantime simply closes the menu.
        val networkTarget = members.firstOrNull { it.key == memberNetworkFor }
        if (networkTarget != null) {
            TouchAnchoredMenu(
                expanded = true,
                offset = memberMenuOffset,
                onDismissRequest = { memberNetworkFor = null },
            ) {
                NetworkSubmenu(
                    app = networkTarget,
                    actions = LocalNetworkActions.current,
                    onDismissMenu = { memberNetworkFor = null },
                )
            }
        }

        // Held here rather than inside the member row: the menu that opens it is dismissed on
        // the same press, and a dialog remembered inside a row keyed by member key would be
        // torn down with the menu's own composition. Resolved by key so a member removed
        // while the dialog was up simply closes it.
        val freezeTarget = members.firstOrNull { it.key == memberFreezeChoosing }
        FreezeMethodDialog(
            app = freezeTarget,
            label = freezeTarget?.let(displayName).orEmpty(),
            counts = freezeActions?.methodCounts ?: emptyMap(),
            supports = { method, serial -> freezeActions?.supports?.invoke(method, serial) ?: true },
            onDismiss = { memberFreezeChoosing = null },
            onPick = { method ->
                memberFreezeChoosing = null
                if (freezeTarget != null) freezeActions?.add(freezeTarget, method)
            },
        )

        // The bottom is reported as the folder grows, not once it has landed: moving the
        // list after the animation looked done meant a finger already on its way down
        // landed on the app next door. The screen follows the growth frame by frame.
        LaunchedEffect(expanded) {
            if (!expanded || popupContents) return@LaunchedEffect
            while (!expansion.isIdle) {
                withFrameNanos {}
                if (!membersBottomPx.isNaN()) onExpandedContentBottom(membersBottomPx)
            }
            // One further frame lets the final layout land before the bottom is read.
            withFrameNanos {}
            if (!membersBottomPx.isNaN()) onExpandedContentBottom(membersBottomPx)
        }
    }
}

/** Now Playing with its own padding handles and height handle. */
@Composable
private fun NowPlayingBlock(
    editMode: Boolean,
    showPlaceholder: Boolean,
    heightDp: Int,
    contentColor: Color,
    alignment: HomeAlignment,
    iconSide: IconSide,
    sidePaddingDp: Int,
    padTop: Int,
    padBottom: Int,
    touchPosition: MutableState<Offset>,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    onOpenNowPlaying: () -> Boolean,
    onOpenMenu: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onEditLayout: () -> Unit,
    onOpenSettings: () -> Unit,
    onResize: (Int) -> Unit,
    onSetPadding: (PaddingSlot, Int) -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    PaddingHandle(
        editMode = editMode,
        label = R.string.handle_now_playing_top,
        value = padTop,
        onChange = { onSetPadding(PaddingSlot.NOW_PLAYING_TOP, it) },
        contentColor = contentColor,
    )
    Box {
        NowPlayingWidget(
            heightDp = heightDp,
            contentColor = contentColor,
            alignment = alignment,
            showPlaceholder = showPlaceholder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sidePaddingDp.dp)
                .recordTouchPosition(touchPosition)
                .combinedClickable(
                    // The transport buttons consume their own taps, so this is only ever the
                    // card itself — which should get you to what is playing. Say so when it
                    // can't, rather than leaving a tap that looks ignored.
                    onClick = {
                        if (!onOpenNowPlaying()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.now_playing_open_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onLongClick = {
                        onOpenMenu(
                            with(density) {
                                DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                            }
                        )
                    },
                ),
        )
        TouchAnchoredMenu(expanded = menuExpanded, offset = menuOffset, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_layout)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = onEditLayout,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = onOpenSettings,
            )
        }
    }
    if (editMode) {
        Spacer(Modifier.height(6.dp))
        StepperRow(
            label = stringResource(R.string.handle_now_playing_height),
            value = heightDp,
            range = 48..220,
            step = HEIGHT_STEP_DP,
            onChange = onResize,
            contentColor = contentColor,
        )
    }
    PaddingHandle(
        editMode = editMode,
        label = R.string.handle_now_playing_bottom,
        value = padBottom,
        onChange = { onSetPadding(PaddingSlot.NOW_PLAYING_BOTTOM, it) },
        contentColor = contentColor,
    )
}
/**
 * One widget inside an open folder, with its own menu. The widget itself is the same hosted
 * view the home screen's widget area draws, so a widget that fails is contained the same way.
 */
@Composable
private fun FolderWidgetBlock(
    folderId: String,
    widget: FolderWidget,
    refreshTick: Int,
    actions: WidgetSlotActions,
    controlsInset: Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var resizing by remember { mutableStateOf(false) }
    // Followed while the stepper is held, and stored once the change is done.
    var liveHeight by remember(widget.heightDp) { mutableIntStateOf(widget.heightDp) }
    val density = LocalDensity.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Box(Modifier.fillMaxWidth().height(liveHeight.dp)) {
            WidgetPage(
                widgetId = widget.id,
                refreshTick = refreshTick,
                onPressAndHold = { x, y ->
                    menuOffset = with(density) { DpOffset(x.toDp(), y.toDp()) }
                    menuExpanded = true
                },
            )
            TouchAnchoredMenu(
                expanded = menuExpanded,
                offset = menuOffset,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.handle_widget_height)) },
                    leadingIcon = { Icon(DelugeIcons.Height, contentDescription = null) },
                    onClick = { menuExpanded = false; resizing = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_change_settings)) },
                    leadingIcon = { Icon(DelugeIcons.Tune, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onWidgetSettings(widget.id) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_app_info)) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onAppInfo(widget.id) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_remove_this)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; actions.onRemoveFolderWidget(folderId, widget.id) },
                )
            }
        }
        if (resizing) {
            Row(modifier = controlsInset, verticalAlignment = Alignment.CenterVertically) {
                StepperRow(
                    label = stringResource(R.string.handle_widget_height),
                    value = liveHeight,
                    range = 80..900,
                    step = HEIGHT_STEP_DP,
                    onChange = { liveHeight = it },
                    contentColor = LocalContentColor.current,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    resizing = false
                    if (liveHeight != widget.heightDp) {
                        actions.onResizeFolderWidget(folderId, widget.id, liveHeight)
                    }
                }) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

/** Chevron and member count on a folder row, so expansion has a visible state. */
@Composable
private fun FolderChevron(expanded: Boolean, count: Int, contentColor: Color) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "folderChevron",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (count > 0) {
            Text(
                text = count.toString(),
                color = contentColor.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.7f),
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * End padding a row needs so its label cannot run under the grab handle.
 *
 * The handle is placed against the screen edge while this padding sits inside the row's own
 * side padding, so the side padding has to come back out of it.
 */
private fun handleReserve(dragHandle: Modifier?, sidePaddingDp: Int, handleInset: Dp): Dp {
    if (dragHandle == null) return 8.dp
    val outerInset = (sidePaddingDp - 8).coerceAtLeast(0).dp
    return (handleInset + EDIT_CONTROL_SIZE - outerInset).coerceAtLeast(8.dp)
}

/**
 * Widest the home stack is allowed to get.
 *
 * Roughly a large phone held upright, so a phone in portrait never reaches it and a tablet or
 * a phone on its side keeps rows that are readable rather than a name at one edge and its icon
 * at the other.
 */
/** How wide the home stack may run sideways, where two favorites share a row. */
private val MAX_CONTENT_WIDTH_LANDSCAPE = 1000.dp

/** Sideways on a tablet or an unfolded foldable, where the phone cap strands the middle. */
private val MAX_CONTENT_WIDTH_TABLET = 1200.dp

private val MAX_CONTENT_WIDTH = 600.dp

/**
 * Rows of clearance under the last favorite when the stack is dragged to its end.
 *
 * Enough that the final rows sit clear of the gesture bar, and the ask that settled the
 * range: three icons' worth of length past where the content stops.
 */
private const val BOTTOM_ROOM_ROWS = 3

/** How much of the bottom of the home screen always answers the quick-launch swipes. */
private const val QUICK_LAUNCH_BOTTOM_FRACTION = 0.2f

/**
 * How many times further sideways than up-and-down a drag must be before the quick-launch
 * swipes call it theirs. Past the slop alone is not enough: a scroll that starts a little
 * crooked crosses the sideways slop first, and taking it left the list stuck until the
 * finger lifted.
 */
private const val QUICK_LAUNCH_DOMINANCE = 2f

/** The same span the icon-size slider offers, so a pinch cannot go where a drag cannot. */
private const val MIN_ICON_DP = 12
private const val MAX_ICON_DP = 96

/** Which side a capped stack sits against, so it stays where the rows are aligned. */
private fun HomeAlignment.wideScreenAlignment(): Alignment = when (this) {
    HomeAlignment.LEFT -> Alignment.TopStart
    HomeAlignment.CENTER -> Alignment.TopCenter
    HomeAlignment.RIGHT -> Alignment.TopEnd
}

/** The grab handle that reorders a row in edit mode. */
@Composable
private fun DragHandle(contentColor: Color, modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.Menu,
        contentDescription = stringResource(R.string.home_drag_handle),
        tint = contentColor.copy(alpha = 0.6f),
        // Same box and same glyph inset as a stepper button, so the two line up.
        modifier = modifier.size(EDIT_CONTROL_SIZE).padding(6.dp),
    )
}

/**
 * Lays an icon and its label against whichever edge the user picked.
 *
 * Left and right hang the label off a weight so it fills the row and pushes the icon to the
 * far side. Centering cannot: a weighted label would still span the row and leave the pair
 * pinned apart, so nothing is weighted and the Row's own Center arrangement packs them
 * together in the middle.
 */
@Composable
private fun RowScope.AlignedIconLabel(
    alignment: HomeAlignment,
    iconSide: IconSide,
    showLabel: Boolean,
    /** Width of [icon], so a centered label can be balanced against it. */
    iconWidth: Dp,
    icon: @Composable () -> Unit,
    label: @Composable RowScope.(Modifier) -> Unit,
) {
    val showIcons = LocalIconConfig.current.showIcons
    // No icon drawn means no gap to leave for one.
    val gap = if (showIcons) 16.dp else 0.dp
    val centered = alignment == HomeAlignment.CENTER
    // A right-aligned row always puts its icon on the right: the name ending at the screen
    // edge reads as the row running out of room, while the icon is the full stop. The side
    // setting still decides everywhere else.
    val side = if (alignment == HomeAlignment.RIGHT) IconSide.RIGHT else iconSide
    // fill = false is what keeps the icon beside its label. A plain weight makes the label
    // span the whole row, which pins the icon to the opposite edge — so a right-aligned row
    // with its icon on the left put the two at opposite ends of the screen. This way the
    // label takes only the width it needs, still yielding when a long name would overflow,
    // and the Row's arrangement moves icon and label together as one.
    val labelModifier = Modifier.weight(1f, fill = false)
    // Centering measures the pair, so the text would sit off the middle by half an icon;
    // balancing the icon on the label's far side puts the text itself on the center line.
    val balance: @Composable () -> Unit = {
        if (centered && showLabel && showIcons) Spacer(Modifier.width(iconWidth + gap))
    }

    if (side == IconSide.RIGHT) {
        balance()
        if (showLabel) {
            label(labelModifier)
            Spacer(Modifier.width(gap))
        }
        icon()
    } else {
        icon()
        if (showLabel) {
            Spacer(Modifier.width(gap))
            label(labelModifier)
        }
        balance()
    }
}

private fun HomeAlignment.boxAlignment() = when (this) {
    HomeAlignment.LEFT -> Alignment.CenterStart
    HomeAlignment.CENTER -> Alignment.Center
    HomeAlignment.RIGHT -> Alignment.CenterEnd
}

private fun HomeAlignment.textAlign() = when (this) {
    HomeAlignment.LEFT -> TextAlign.Start
    HomeAlignment.CENTER -> TextAlign.Center
    HomeAlignment.RIGHT -> TextAlign.End
}

private fun HomeAlignment.columnAlignment() = when (this) {
    HomeAlignment.LEFT -> Alignment.Start
    HomeAlignment.CENTER -> Alignment.CenterHorizontally
    HomeAlignment.RIGHT -> Alignment.End
}

/**
 * An app's notifications, a line each. A tap opens that one and a sideways swipe clears it,
 * as in the shade.
 */
@Composable
private fun NotificationLines(
    lines: List<NotificationEntry>,
    alignment: HomeAlignment,
    contentColor: Color,
    textSizeSp: Int,
    interactive: Boolean,
    onFallback: () -> Unit,
) {
    val context = LocalContext.current
    lines.forEach { line ->
        key(line.notificationKey) {
            val offset = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            var width by remember { mutableStateOf(1) }
            Text(
                line.line,
                color = contentColor.copy(
                    alpha = 0.65f * (1f - (abs(offset.value) / width).coerceIn(0f, 1f))
                ),
                fontSize = textSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = alignment.textAlign(),
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { width = it.width.coerceAtLeast(1) }
                    .graphicsLayer { translationX = offset.value }
                    .then(
                        if (!interactive) Modifier else Modifier
                            // Each line opens its own notification, as the shade would; an
                            // app that gave us nothing to open still opens.
                            .clickable {
                                if (!NotificationGateway.open(context, line)) {
                                    onFallback()
                                } else if (line.dismissOnOpen) {
                                    NotificationGateway.dismiss(line.notificationKey)
                                }
                            }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    scope.launch { offset.snapTo(offset.value + delta) }
                                },
                                onDragStopped = {
                                    if (abs(offset.value) > width / 3f) {
                                        offset.animateTo(
                                            if (offset.value > 0) width.toFloat() else -width.toFloat()
                                        )
                                        NotificationGateway.dismiss(line.notificationKey)
                                    } else {
                                        offset.animateTo(0f)
                                    }
                                },
                            )
                    ),
            )
        }
    }
}

private fun HomeAlignment.arrangement() = when (this) {
    HomeAlignment.LEFT -> Arrangement.Start
    HomeAlignment.CENTER -> Arrangement.Center
    HomeAlignment.RIGHT -> Arrangement.End
}

/**
 * A folder's contents as a card over the home screen, for a row that has no width to open
 * into. The same rows as in place, so everything a member offers there still works; the card
 * is the one thing that keeps a folder from pushing the rest of the screen around.
 */
@Composable
private fun FolderContentsDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(vertical = 48.dp),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Column(
                    Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) { content() }
            }
        }
    }
}

/** Folders get the same treatment as apps: their own name and their own icon. */
@Composable
private fun FolderEditDialog(
    currentName: String,
    hasCustomIcon: Boolean,
    onConfirm: (String) -> Unit,
    /** Receives whatever is typed, because picking an icon navigates away from this dialog. */
    onChangeIcon: (String) -> Unit,
    onResetIcon: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_edit_icon_and_name)) },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.home_folder_name_label)) })
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onChangeIcon(text) }) { Text(stringResource(R.string.action_change_icon)) }
                if (hasCustomIcon) {
                    TextButton(onClick = onResetIcon) { Text(stringResource(R.string.home_folder_use_previews)) }
                }
            }
        },
        confirmButton = {
            // An empty name is a choice, not a mistake: the folder then shows only its
            // preview icon. Trimmed either way, so a field full of spaces clears it.
            TextButton(onClick = { onConfirm(text.trim()) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A notification preview follows the theme unless the owner picked a colour for it. */
private fun notificationTint(base: Color, argb: Int): Color =
    if (argb == 0) base else Color(argb)

/**
 * The two-column label setting, read where it is used.
 *
 * Passed down as an argument would be the usual way, but HomeRoute is already at the JVM's
 * method size limit: one more parameter there fails the build with "Method too large". So the
 * preference is read here, the way the app list reads the ones only it uses.
 */
@Composable
private fun rememberTwoColumnLabels(): TwoColumnLabels {
    val app = LocalContext.current.applicationContext as DelugeApp
    val labels by app.prefs.twoColumnLabels.collectAsState(initial = TwoColumnLabels.AUTO)
    return labels
}
