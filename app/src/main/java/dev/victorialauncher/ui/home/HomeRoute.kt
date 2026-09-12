// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.data.Folder
import dev.victorialauncher.data.HomePaddings
import dev.victorialauncher.data.PaddingSlot
import dev.victorialauncher.data.folderToken
import dev.victorialauncher.media.NowPlayingBus
import dev.victorialauncher.media.isListenerEnabled
import dev.victorialauncher.service.SystemUi
import dev.victorialauncher.ui.applist.AppListModel
import dev.victorialauncher.ui.applist.AppListScreen
import dev.victorialauncher.ui.applist.BandEditOverlay
import dev.victorialauncher.ui.applist.EdgeScrubber
import dev.victorialauncher.ui.applist.EdgeTouchZone
import dev.victorialauncher.ui.applist.ScrubBand
import dev.victorialauncher.ui.applist.ScrubState
import dev.victorialauncher.ui.applist.buildAppListModel
import dev.victorialauncher.ui.common.FolderPickerDialog
import dev.victorialauncher.widget.WidgetSlotActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Immutable
import android.net.Uri
import dev.victorialauncher.R

/** Width of the invisible strip at each screen edge that opens the app list. */
/** Android only honors this much gesture exclusion per side, so spend it on the strip. */
private const val GESTURE_EXCLUSION_CAP_DP = 200

/** How long a launch is given to take us off screen before the overlay closes itself. */
private const val LAUNCH_CLOSE_TIMEOUT_MS = 2000L

/** Long enough to read as a settle, short enough not to stand between you and the icons. */
private const val HOME_FADE_MS = 220

/**
 * The home destination: the home screen itself, the app-list overlay layered over it, and the
 * edge zones that move between them.
 *
 * The overlay is kept composed and measured even while hidden — just never placed — because
 * building the whole list from scratch on every open is what made it take a beat to appear.
 */
@Composable
fun HomeRoute(
    app: VictoriaApp,
    homeIntentTick: Int,
    settings: HomeSettings,
    homePaddings: HomePaddings,
    favorites: List<FavoriteEntry>,
    appsByKey: Map<String, AppInfo>,
    folders: List<Folder>,
    favoriteKeys: List<String>,
    hiddenApps: Set<String>,
    nameOverrides: Map<String, String>,
    widgetIds: List<Int>,
    widgetPosition: Int,
    widgetHeightDp: Int,
    widgetActions: WidgetSlotActions,
    scrubBandFractions: Pair<Float, Float>?,
    onSetScrubBand: (Float, Float) -> Unit,
    onClearScrubBand: () -> Unit,
    onPeekStatusBar: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Grouping, sorting and flattening every installed app is too much to do in composition,
    // and it re-runs whenever a name override changes.
    val listModel by produceState(
        initialValue = AppListModel(emptyList(), emptyList()),
        appsByKey,
        hiddenApps,
        nameOverrides,
    ) {
        val apps = appsByKey.values.toList()
        value = withContext(Dispatchers.Default) {
            buildAppListModel(apps, hiddenApps) { nameOverrides[it.key] ?: it.label }
        }
    }

    var appListVisible by remember { mutableStateOf(false) }
    val scrub = remember { ScrubState() }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var favBand by remember { mutableStateOf<ScrubBand?>(null) }
    var homeEditMode by remember { mutableStateOf(false) }
    var folderPickerFor by remember { mutableStateOf<AppInfo?>(null) }

    val nowPlaying by NowPlayingBus.state.collectAsState()
    val listenerGranted = remember(homeIntentTick) { isListenerEnabled(context) }
    // Don't reserve the block (or its padding) unless there is something to render:
    // no live session means the whole thing collapses, padding included.
    val nowPlayingHasContent = settings.nowPlayingEnabled && (!listenerGranted || nowPlaying != null)
    // While the band is being edited the live value wins; otherwise a range the user set by
    // hand wins over the measured favorites, which is what makes it stop following them.
    var bandEditMode by remember { mutableStateOf(false) }
    var liveBand by remember { mutableStateOf<ScrubBand?>(null) }
    val storedBand = scrubBandFractions?.let { (top, height) ->
        ScrubBand(topPx = viewportHeightPx * top, heightPx = viewportHeightPx * height)
    }
    val band = liveBand ?: storedBand ?: favBand ?: ScrubBand.fallbackFor(viewportHeightPx)

    /** Locking is a strip gesture now, so the toast that explains it lives with the strip. */
    fun lockOrExplain() {
        if (!SystemUi.lockScreen()) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_enable_accessibility_lock),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // Set while a launched app is expected to take over the screen; see closeAfterLaunch.
    var launchClose by remember { mutableStateOf<Job?>(null) }

    // The home screen fades back in when we put the list away ourselves, but not when the
    // system did it for us: laying our own fade over Android's home transition — or running it
    // on resume for a close that happened while we were backgrounded — just reads as noise.
    var snapHome by remember { mutableStateOf(false) }

    fun closeAppList(snap: Boolean = false) {
        launchClose?.cancel()
        launchClose = null
        snapHome = snap
        appListVisible = false
        scrub.cancel()
    }

    // Closing the overlay the moment an app is launched puts the home screen on screen for
    // the few frames before that app's window arrives, which reads as a jolt back to home.
    // Leave the list up and let it go when we are actually backgrounded instead: by then
    // nothing of ours is visible, so the close costs nothing and can snap.
    fun closeAfterLaunch() {
        launchClose?.cancel()
        launchClose = scope.launch {
            // Nothing ever came to the foreground. Rather than leave the list stuck open,
            // put it away the ordinary animated way.
            delay(LAUNCH_CLOSE_TIMEOUT_MS)
            launchClose = null
            closeAppList()
        }
    }

    LaunchedEffect(settings.edgeSide) { scrub.syncRestingSide(settings.edgeSide) }

    BackHandler(enabled = appListVisible) { closeAppList() }

    // Every stepper commits as it is tapped, so there is nothing to save on the way out —
    // but leaving edit mode had to be done through the Done button, and BACK simply escaped
    // to the system and left the home screen stuck in it.
    BackHandler(enabled = homeEditMode) { homeEditMode = false }

    fun commitBand() {
        liveBand?.let { edited ->
            if (viewportHeightPx > 0) {
                onSetScrubBand(edited.topPx / viewportHeightPx, edited.heightPx / viewportHeightPx)
            }
        }
        liveBand = null
        bandEditMode = false
    }

    BackHandler(enabled = bandEditMode) { commitBand() }

    // Leaving the launcher (screen off, another app) should always drop us back to the home
    // screen rather than reopening onto the overlay.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) closeAppList(snap = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(homeIntentTick) {
        if (homeIntentTick > 0) closeAppList(snap = true)
    }

    LaunchedEffect(settings.edgeSide, settings.edgeZoneWidthDp, view, band) {
        view.post {
            val density = view.resources.displayMetrics.density
            val widthPx = (settings.edgeZoneWidthDp * density).toInt()
            val h = view.height
            val w = view.width
            if (h > 0 && w > 0) {
                // Android's back-gesture claims the outer edges, and only honors 200dp of
                // exclusion per side — so spend it on the scrub band rather than spreading it
                // uselessly over the whole screen height.
                val capPx = (GESTURE_EXCLUSION_CAP_DP * density).toInt()
                val top = band.topPx.toInt().coerceIn(0, h)
                val bottom = band.bottomPx.toInt().coerceIn(top, h)
                val clipped = if (bottom - top > capPx) top + capPx else bottom
                val rects = buildList {
                    if (settings.edgeSide != EdgeSide.RIGHT) add(Rect(0, top, widthPx, clipped))
                    if (settings.edgeSide != EdgeSide.LEFT) add(Rect(w - widthPx, top, w, clipped))
                }
                ViewCompat.setSystemGestureExclusionRects(view, rects)
            }
        }
    }
    DisposableEffect(view) {
        // Settings and the other screens must get the system back-gesture back.
        onDispose { ViewCompat.setSystemGestureExclusionRects(view, emptyList()) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height },
    ) {
        // Hidden entirely while the list is up, so only the wallpaper sits behind it — and
        // kept composed the same way, so coming back is instant.
        // Opening answers a finger on the edge and must not lag behind it, so it snaps. Coming
        // back gets a short decelerating fade: the wallpaper is already there, so this is only
        // the icons settling in, and cutting them in on a single frame is what read as a jolt.
        val homeAlpha by animateFloatAsState(
            targetValue = if (appListVisible) 0f else 1f,
            animationSpec = if (appListVisible || snapHome) {
                snap()
            } else {
                tween(HOME_FADE_MS, easing = LinearOutSlowInEasing)
            },
            label = "homeAlpha",
        )
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = homeAlpha }
                .then(
                    // Hidden, but still laid out: an AppWidgetHostView that is never placed
                    // loses its layout and comes back with its text collapsed.
                    if (appListVisible) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            if (settings.dimHomeAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = settings.dimHomeAlpha)),
                )
            }
            HomeScreen(
                favorites = favorites,
                nameOverrides = nameOverrides,
                iconSizeDp = settings.iconSizeDp,
                labelSizeSp = settings.labelSizeSp,
                itemSpacingDp = settings.itemSpacingDp,
                sidePaddingDp = settings.sidePaddingDp,
                widgetSidePaddingDp = settings.widgetSidePaddingDp,
                onSetSidePadding = { scope.launch { app.prefs.setSidePaddingDp(it) } },
                onSetWidgetSidePadding = { scope.launch { app.prefs.setWidgetSidePaddingDp(it) } },
                paddings = homePaddings,
                widgetIds = widgetIds,
                widgetPosition = widgetPosition,
                widgetHeightDp = widgetHeightDp,
                hapticsEnabled = settings.hapticsEnabled,
                nowPlayingEnabled = settings.nowPlayingEnabled,
                nowPlayingHeightDp = settings.nowPlayingHeightDp,
                onResizeNowPlaying = { scope.launch { app.prefs.setNowPlayingHeightDp(it) } },
                widgetActions = widgetActions,
                onLaunch = { app.appRepository.launch(it.componentName) },
                onRemoveFavorite = { scope.launch { app.prefs.removeFavorite(it.key) } },
                onOpenFolderApp = { app.appRepository.launch(it.componentName) },
                onRenameFolder = { folder, name ->
                    scope.launch { app.prefs.upsertFolder(folder.copy(name = name)) }
                },
                onDeleteFolder = { folder -> scope.launch { app.prefs.deleteFolder(folder.id) } },
                onChangeFolderIcon = { folder -> onNavigate(iconPickerRoute(folderToken(folder.id))) },
                onResetFolderIcon = { folder -> scope.launch { app.prefs.setFolderIcon(folder.id, null) } },
                onManageFolder = { folder -> onNavigate("folder/" + folder.id) },
                onRemoveFromFolder = { folder, appInfo ->
                    scope.launch { app.prefs.removeAppFromFolder(folder.id, appInfo.key) }
                },
                appsByKey = appsByKey,
                onMoveToFolder = { folderPickerFor = it },
                onReorderHome = { newFavKeys, newWidgetPos ->
                    scope.launch {
                        app.prefs.setFavorites(newFavKeys)
                        app.prefs.setWidgetPosition(newWidgetPos)
                    }
                },
                onCommitPadding = { slot: PaddingSlot, value: Int ->
                    scope.launch { app.prefs.setHomePadding(slot, value) }
                },
                onFavoritesBoundsChanged = { top, bottom ->
                    favBand = ScrubBand(topPx = top, heightPx = bottom - top)
                },
                nowPlayingHasContent = nowPlayingHasContent,
                contentColor = settings.contentColor,
                showFavoriteLabels = settings.showFavoriteLabels,
                alignment = settings.alignment,
                editMode = homeEditMode,
                onEditModeChange = { homeEditMode = it },
                onPeekStatusBar = onPeekStatusBar,
                onExpandShade = {
                    // Say so rather than failing silently — that way a pull that does nothing
                    // tells you whether the gesture or the permission is at fault.
                    if (!SystemUi.expandNotificationShade(context)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_enable_accessibility_shade),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onManageFavorites = { onNavigate("favorites") },
                onSetName = { appInfo, name -> scope.launch { app.prefs.setNameOverride(appInfo.key, name) } },
                onChangeIcon = { appInfo -> onNavigate(iconPickerRoute(appInfo.key)) },
                onAppInfo = { app.appRepository.openAppInfo(it.packageName) },
                onOpenSettings = { onNavigate("settings") },
            )
        }

        // Kept composed and measured even while hidden, just never placed. Not placing it
        // means it neither draws nor receives touches.
        val overlayAlpha = if (appListVisible) 1f else 0f
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = overlayAlpha }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    if (appListVisible) {
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    } else {
                        layout(0, 0) {}
                    }
                },
        ) {
            AppListScreen(
                model = listModel,
                nameOverrides = nameOverrides,
                scrub = scrub,
                dimAlpha = settings.dimWallpaperAlpha,
                iconSizeDp = settings.iconSizeDp,
                labelSizeSp = settings.labelSizeSp,
                band = band,
                viewportHeightPx = viewportHeightPx,
                visible = appListVisible,
                favoriteKeys = remember(favoriteKeys) { favoriteKeys.toSet() },
                onLaunch = { appInfo ->
                    // A launch that never got off the ground leaves nothing to wait for.
                    if (app.appRepository.launch(appInfo.componentName)) closeAfterLaunch() else closeAppList()
                },
                onSetFavorite = { appInfo, add ->
                    scope.launch {
                        if (add) app.prefs.addFavorite(appInfo.key) else app.prefs.removeFavorite(appInfo.key)
                    }
                },
                onSetName = { appInfo, name -> scope.launch { app.prefs.setNameOverride(appInfo.key, name) } },
                onChangeIcon = { appInfo ->
                    closeAppList()
                    onNavigate(iconPickerRoute(appInfo.key))
                },
                onAppInfo = { app.appRepository.openAppInfo(it.packageName) },
                onHideApp = { appInfo -> scope.launch { app.prefs.setHidden(appInfo.key, true) } },
                onMoveToFolder = { appInfo -> closeAppList(); folderPickerFor = appInfo },
                onOpenSettings = { closeAppList(); onNavigate("settings") },
                onDismiss = { closeAppList() },
                contentColor = settings.contentColor,
                showAlphabet = settings.showAlphabet,
                alignment = settings.alignment,
            )
        }

        if (settings.alwaysShowAz && !appListVisible) {
            EdgeScrubber(
                letters = listModel.letters,
                scrubY = { null },
                pullPx = { 0f },
                band = band,
                side = scrub.side,
                modifier = Modifier.align(
                    if (scrub.side == EdgeSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                ),
            )
        }

        folderPickerFor?.let { target ->
            FolderPickerDialog(
                appLabel = nameOverrides[target.key] ?: target.label,
                folders = folders,
                onPickFolder = { folder ->
                    scope.launch { app.prefs.addAppToFolder(folder.id, target.key) }
                    folderPickerFor = null
                },
                onCreateFolder = { name ->
                    scope.launch {
                        val folder = Folder(
                            id = System.currentTimeMillis().toString(36),
                            name = name,
                            apps = listOf(target.key),
                        )
                        app.prefs.upsertFolder(folder)
                        // Drop the app's own row and give the folder one in its place.
                        app.prefs.removeFavorite(target.key)
                        app.prefs.addFavorite(folderToken(folder.id))
                    }
                    folderPickerFor = null
                },
                onDismiss = { folderPickerFor = null },
            )
        }

        if (bandEditMode) {
            BandEditOverlay(
                band = liveBand ?: band,
                side = scrub.side,
                viewportHeightPx = viewportHeightPx,
                contentColor = settings.contentColor,
                onBandChange = { liveBand = it },
                onReset = { liveBand = null; bandEditMode = false; onClearScrubBand() },
                onDone = { commitBand() },
            )
        }

        // Edge zones sit on top of everything, so one unbroken touch opens the list and then
        // scrubs it as the finger moves.
        if (!homeEditMode && !bandEditMode) {
            val sides = remember(settings.edgeSide) {
                when (settings.edgeSide) {
                    EdgeSide.LEFT -> listOf(EdgeSide.LEFT)
                    EdgeSide.RIGHT -> listOf(EdgeSide.RIGHT)
                    EdgeSide.BOTH -> listOf(EdgeSide.LEFT, EdgeSide.RIGHT)
                }
            }
            sides.forEach { side ->
                EdgeTouchZone(
                    side = side,
                    widthDp = settings.edgeZoneWidthDp.dp,
                    letters = listModel.letters,
                    band = band,
                    hapticsEnabled = settings.hapticsEnabled,
                    state = scrub,
                    onOpen = { appListVisible = true },
                    onDoubleTap = if (settings.doubleTapToLock) ({ lockOrExplain() }) else null,
                    onLongPress = { liveBand = band; bandEditMode = true },
                    modifier = Modifier.align(
                        if (side == EdgeSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                    ),
                )
            }
        }
    }
}

private fun iconPickerRoute(key: String) = "iconpicker/" + Uri.encode(key)

/** Display and behavior settings the home destination reads, grouped so they travel as one. */
@Immutable
data class HomeSettings(
    val iconSizeDp: Int,
    val labelSizeSp: Int,
    val itemSpacingDp: Int,
    val sidePaddingDp: Int,
    val nowPlayingHeightDp: Int,
    val nowPlayingEnabled: Boolean,
    val edgeSide: EdgeSide,
    val alwaysShowAz: Boolean,
    val showAlphabet: Boolean,
    val alignment: HomeAlignment,
    val widgetSidePaddingDp: Int,
    val edgeZoneWidthDp: Int,
    val swipeUpOpensAppList: Boolean,
    val appListSearch: Boolean,
    val sortByUsage: Boolean,
    val quickLaunchLeft: AppInfo?,
    val quickLaunchRight: AppInfo?,
    /** Place the favorites by measurement, until the user sets a padding of their own. */
    val centerFavorites: Boolean,
    val dimWallpaperAlpha: Float,
    val dimHomeAlpha: Float,
    val hapticsEnabled: Boolean,
    val showFavoriteLabels: Boolean,
    val doubleTapToLock: Boolean,
    val contentColor: Color,
)