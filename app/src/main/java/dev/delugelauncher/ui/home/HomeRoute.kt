// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.home

import android.app.SearchManager
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.delugelauncher.TypedKey
import dev.delugelauncher.DelugeApp
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.restoreConcealed
import dev.delugelauncher.data.AzStripVisibility
import dev.delugelauncher.data.EdgeSide
import dev.delugelauncher.data.FavoritesSource
import dev.delugelauncher.data.ShortcutSwipe
import dev.delugelauncher.data.HomeAlignment
import dev.delugelauncher.data.IconSide
import dev.delugelauncher.data.Folder
import dev.delugelauncher.data.HomePaddings
import dev.delugelauncher.data.QuickLaunchSlot
import dev.delugelauncher.data.PaddingSlot
import dev.delugelauncher.data.folderToken
import dev.delugelauncher.media.NowPlayingBus
import dev.delugelauncher.media.isListenerEnabled
import dev.delugelauncher.service.SystemUi
import dev.delugelauncher.ui.applist.AppListModel
import dev.delugelauncher.ui.applist.AppListScreen
import dev.delugelauncher.ui.applist.BandEditOverlay
import dev.delugelauncher.ui.applist.EdgeScrubber
import dev.delugelauncher.ui.applist.EdgeTouchZone
import dev.delugelauncher.ui.applist.ScrubBand
import dev.delugelauncher.ui.applist.ScrubberGeometry
import dev.delugelauncher.ui.applist.ScrubState
import dev.delugelauncher.ui.common.ScreenshotDisguise
import dev.delugelauncher.ui.applist.buildAppListModel
import dev.delugelauncher.ui.common.FolderPickerDialog
import dev.delugelauncher.widget.WidgetSlotActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Immutable
import android.content.res.Configuration
import android.net.Uri
import dev.delugelauncher.R

/** Width of the invisible strip at each screen edge that opens the app list. */
/** Android only honors this much gesture exclusion per side, so spend it on the strip. */
private const val GESTURE_EXCLUSION_CAP_DP = 200

/** How long a launch is given to take us off screen before the overlay closes itself. */
private const val LAUNCH_CLOSE_TIMEOUT_MS = 2000L

/** Long enough to read as a settle, short enough not to stand between you and the icons. */
/** Room each letter wants on the strip before it stops being something anyone can hit. */
private val MIN_LETTER_HEIGHT = 15.dp

private const val HOME_FADE_MS = 220

/**
 * How far a swipe up carries the app list in. The same distance a pull collapses it over, so
 * opening is literally the closing animation run backwards.
 */
private val SWIPE_OPEN_DISTANCE = 150.dp

/**
 * The home destination: the home screen itself, the app-list overlay layered over it, and the
 * edge zones that move between them.
 *
 * The overlay is kept composed and measured even while hidden — just never placed — because
 * building the whole list from scratch on every open is what made it take a beat to appear.
 */
@Composable
fun HomeRoute(
    app: DelugeApp,
    homeIntentTick: Int,
    /**
     * The app a corner button opens, or null for no button.
     *
     * Passed beside [settings] rather than inside it: HomeSettings is at the size a data
     * class constructor will still resolve — one more field and every argument to it fails
     * to typecheck against itself — and this is a resolved row rather than a stored value.
     */
    cornerButtonApp: AppInfo?,
    /** True when the corner opens the search instead of launching an app. */
    cornerButtonIsSearch: Boolean,
    /** Whether opening the app list puts the cursor in its search box. */
    autoKeyboard: Boolean,
    /** Whether the last letters reach the same line as the first. */
    lastLetterToLine: Boolean,
    typedToSearch: List<TypedKey>,
    onTypedToSearchHandled: (List<TypedKey>) -> Unit,
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
    /** Bumped on every package event, so frozen and unfrozen providers re-read. */
    widgetTick: Int,
    launchCounts: Map<String, Int>,
    showWelcome: Boolean,
    /** Whether to offer the "make this your home screen" card, already decided by the caller. */
    showDefaultPrompt: Boolean,
    onDismissDefaultPrompt: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onWelcomeDismissed: () -> Unit,
    scrubBandFractions: Pair<Float, Float>?,
    onSetScrubBand: (Float, Float) -> Unit,
    onClearScrubBand: () -> Unit,
    onPeekStatusBar: () -> Unit,
    onAppListVisibleChange: (Boolean) -> Unit,
    /** Locks an open private space or asks for a locked one to be opened, off this thread. */
    onTogglePrivateSpace: () -> Unit,
    onNavigate: (String) -> Unit,
    /** Fake names by app key while the screenshot disguise is on; empty otherwise. */
    disguiseNames: Map<String, String>,
) {
    val context = LocalContext.current
    // Read here because the models are built off the main thread, where a string resource
    // cannot be asked for.
    val privateSectionLabel = stringResource(R.string.applist_private_space_section)
        .takeIf { settings.privateSpaceSection }
    val recentSectionArg = if (settings.recentSection) {
        stringResource(R.string.applist_recent_section) to settings.recentSectionCount
    } else {
        null
    }
    // Asked once: whether anything on this phone handles a web search at all.
    val webSearchAvailable = remember(context) {
        runCatching {
            context.packageManager.resolveActivity(Intent(Intent.ACTION_WEB_SEARCH), 0) != null
        }.getOrDefault(false)
    }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Folder cover names while disguised, by folder id. Empty otherwise.
    val folderDisguise = remember(disguiseNames, folders) {
        if (disguiseNames.isEmpty()) emptyMap()
        else folders.associate { it.id to ScreenshotDisguise.folderName(it.id) }
    }
    val appTags by app.prefs.appTags.collectAsState(initial = emptyMap())
    // Tags gathered into groups for the list's own sections. Built here rather than in the
    // background model because it is a handful of apps, not the whole alphabet.
    val tagSections = remember(appTags, appsByKey, hiddenApps) {
        val byTag = mutableMapOf<String, MutableList<AppInfo>>()
        appTags.forEach { (key, tags) ->
            val appInfo = appsByKey[key] ?: return@forEach
            if (key in hiddenApps) return@forEach
            tags.forEach { tag -> byTag.getOrPut(tag) { mutableListOf() }.add(appInfo) }
        }
        byTag.toSortedMap().map { (tag, list) -> tag to list }
    }
    val allTags = remember(appTags) { appTags.values.flatten().toSortedSet().toList() }

    // Hidden apps are left out of the model the list draws from, so reaching them by name
    // needs a model of its own. Only built when the setting asks for it, and it falls back to
    // the list's own model otherwise, so nothing is grouped and sorted twice for nothing.
    val includeHiddenInSearch = settings.appListSearchHidden && hiddenApps.isNotEmpty()

    // Grouping, sorting and flattening every installed app is too much to do in composition,
    // and it re-runs whenever a name override changes.
    val listModel by produceState(
        initialValue = AppListModel(emptyList(), emptyList()),
        appsByKey,
        hiddenApps,
        nameOverrides,
        // Empty unless the sort is on, so an ordinary launch doesn't rebuild the whole list.
        if (settings.sortByUsage) launchCounts else emptyMap(),
        settings.showListHeaders,
        tagSections,
    ) {
        val apps = appsByKey.values.toList()
        val counts = if (settings.sortByUsage) launchCounts else emptyMap()
        value = withContext(Dispatchers.Default) {
            buildAppListModel(
                apps,
                hiddenApps,
                { disguiseNames[it.key] ?: nameOverrides[it.key] ?: it.label },
                counts,
                englishName = { app.appRepository.englishLabel(it) },
                showHeaders = settings.showListHeaders,
                renamed = { it.key in nameOverrides },
                privateSectionLabel = privateSectionLabel,
                recentSection = recentSectionArg,
                tagSections = tagSections,
            )
        }
    }

    val hiddenSearchModel by produceState<AppListModel?>(
        initialValue = null,
        appsByKey,
        nameOverrides,
        includeHiddenInSearch,
        if (settings.sortByUsage) launchCounts else emptyMap(),
        settings.showListHeaders,
    ) {
        val apps = appsByKey.values.toList()
        val counts = if (settings.sortByUsage) launchCounts else emptyMap()
        value = if (!includeHiddenInSearch) {
            null
        } else {
            withContext(Dispatchers.Default) {
                buildAppListModel(
                    apps,
                    emptySet(),
                    { disguiseNames[it.key] ?: nameOverrides[it.key] ?: it.label },
                    counts,
                    englishName = { app.appRepository.englishLabel(it) },
                    showHeaders = settings.showListHeaders,
                    renamed = { it.key in nameOverrides },
                    privateSectionLabel = privateSectionLabel,
                    recentSection = recentSectionArg,
                )
            }
        }
    }
    val searchModel = hiddenSearchModel ?: listModel

    var appListVisible by remember { mutableStateOf(false) }
    val scrub = remember { ScrubState() }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var favBand by remember { mutableStateOf<ScrubBand?>(null) }
    // Enough height for every letter to be worth aiming at. Counted per letter rather than set
    // as one number, since an alphabet with accents in it has more letters to find room for.
    val minBandHeightPx = with(LocalDensity.current) {
        (listModel.letters.size * MIN_LETTER_HEIGHT.toPx())
    }
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
    val homeContentAlpha by animateFloatAsState(
        if (bandEditMode) 0f else 1f,
        label = "homeContentAlpha",
    )
    // Edit mode borrows the app list's dim. Its controls are small text over whatever the
    // wallpaper happens to be, and on a busy one they were barely readable — the home screen's
    // own dim is usually off, since nothing normally sits there needing to be read.
    val homeDim =
        if (homeEditMode) maxOf(settings.dimHomeAlpha, settings.dimWallpaperAlpha) else settings.dimHomeAlpha
    var liveBand by remember { mutableStateOf<ScrubBand?>(null) }
    // Clamped on the way back in as well as on the way out: a range stored from a bad
    // measurement would otherwise put the strip off screen for good, with no gesture left to
    // reach it and fix it.
    val storedBand = scrubBandFractions
        ?.takeIf { (top, height) -> top.isFinite() && height.isFinite() && height > 0f }
        ?.let { (top, height) ->
            val bandHeight = (viewportHeightPx * height).coerceIn(0f, viewportHeightPx.toFloat())
            val bandTop = (viewportHeightPx * top)
                .coerceIn(0f, (viewportHeightPx - bandHeight).coerceAtLeast(0f))
            ScrubBand(topPx = bandTop, heightPx = bandHeight)
        }
    // A range set by hand is honoured as set: clamping it is one thing, but growing it
    // would undo an explicit choice. Only the bands the launcher works out itself get a
    // floor, which is where the squeezed strip came from.
    val autoBand = liveBand ?: storedBand ?: favBand ?: ScrubBand.fallbackFor(viewportHeightPx)
    // Sideways the screen is short, and half of it is a strip of letters squeezed into a few
    // centimetres. The strip takes the whole height there instead, and goes back to its band
    // the moment the phone is upright again.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val band = if (landscape) {
        ScrubBand(0f, viewportHeightPx.toFloat())
    } else if (liveBand != null || storedBand != null) {
        ScrubberGeometry.capHeight(autoBand, viewportHeightPx.toFloat())
    } else {
        // A favourites list taller than the screen leaves the crop centered around its middle,
        // which lands the strip in the upper half and reads as sitting at the top. The default
        // is held down in the lower half — the fallback's own line — and only follows the
        // favourites further down from there.
        val floored = ScrubberGeometry.floorHeight(
            ScrubberGeometry.capHeight(autoBand, viewportHeightPx.toFloat()),
            viewportHeightPx.toFloat(),
        )
        val minTop = viewportHeightPx * ScrubberGeometry.FALLBACK_TOP_FRACTION
        floored.copy(topPx = floored.topPx.coerceAtLeast(minTop))
    }

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

    // How far in the overlay is: 0 sitting off the bottom, the full distance fully open. The
    // edge zones jump it straight to open, a swipe drags it there by hand.
    val openDistancePx = with(LocalDensity.current) { SWIPE_OPEN_DISTANCE.toPx() }
    val openAnim = remember { Animatable(0f) }
    val appListState = rememberLazyListState()
    // Set once a swipe has carried past fully open and started scrolling the list, so the
    // flick that ends it can be handed on rather than stopping dead.
    var swipeScrolledList by remember { mutableStateOf(false) }
    val flingDecay = rememberSplineBasedDecay<Float>()

    // Set while a launched app is expected to take over the screen; see closeAfterLaunch.
    var launchClose by remember { mutableStateOf<Job?>(null) }

    // The home screen fades back in when we put the list away ourselves, but not when the
    // system did it for us: laying our own fade over Android's home transition — or running it
    // on resume for a close that happened while we were backgrounded — just reads as noise.
    var snapHome by remember { mutableStateOf(false) }

    // Hoisted so closing the list clears it; the overlay stays composed while hidden, so a
    // query left behind would still be filtering the next time it opened.
    var appListQuery by remember { mutableStateOf("") }
    // Set when the corner button opened the list as a search: the field is shown for this
    // opening even while the app-list search setting is off, because tapping a search
    // button that opens a list with no search box is a dead end. Cleared with the query.
    var forceSearch by remember { mutableStateOf(false) }
    // Bumped to ask the list for the cursor, for the openings that are a search rather than
    // a look — the corner button set to it. A count because the same request can come twice.
    var searchFocusTick by remember { mutableIntStateOf(0) }

    // A keystroke on the home screen opens the list already searching for it. Only when there
    // is a search field to type into: without one the query filters a list showing no sign of
    // why, and there would be nothing on screen to clear it with.
    LaunchedEffect(typedToSearch) {
        if (typedToSearch.isEmpty()) return@LaunchedEffect
        val taken = typedToSearch
        onTypedToSearchHandled(taken)
        if (!settings.appListSearch || homeEditMode || bandEditMode) return@LaunchedEffect
        if (!appListVisible) {
            appListVisible = true
            openAnim.snapTo(openDistancePx)
        }
        appListQuery = taken.fold(appListQuery) { text, key ->
            if (key.char == null) text.dropLast(1) else text + key.char
        }
    }

    fun closeAppList(snap: Boolean = false) {
        appListQuery = ""
        forceSearch = false
        scope.launch { openAnim.snapTo(0f) }
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

    // The one place a row press is turned into something happening. Every row in the launcher
    // arrives here, so a row that is not an app is recognised once, here, rather than in each
    // of the four places a press is wired up.
    fun actuallyLaunch(entry: AppInfo): Boolean = when (entry.kind) {
        // Handed off rather than done here: locking or unlocking is several binder calls and
        // possibly the system's own authentication screen, which is not a press's work to do
        // on the main thread. True because the press was acted on — there is simply nothing
        // coming to the foreground to wait for.
        EntryKind.PRIVATE_SPACE -> {
            onTogglePrivateSpace()
            true
        }
        else -> app.appRepository.launch(entry)
    }

    // A locked app asks for the device's own lock screen before it opens. Unlocked once per run
    // of the launcher rather than once per tap: the point is to keep someone out who picked the
    // phone up, not to ask the owner again every time they open it.
    val lockedApps by app.prefs.lockedApps.collectAsState(initial = emptySet())
    var sessionUnlocked by remember { mutableStateOf(emptySet<String>()) }
    var pendingLaunch by remember { mutableStateOf<AppInfo?>(null) }
    val keyguard = remember(context) { context.getSystemService(KeyguardManager::class.java) }
    val confirmLaunch = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingLaunch
        pendingLaunch = null
        if (pending != null && result.resultCode == Activity.RESULT_OK) {
            sessionUnlocked = sessionUnlocked + pending.key
            // A locked app that is also paused cannot start until it is un-paused, so the two
            // are lifted together: unlocking it and then doing nothing would read as broken.
            if (pending.suspended) app.appRepository.unsuspend(pending)
            actuallyLaunch(pending)
        }
    }

    fun launchEntry(entry: AppInfo): Boolean {
        if (entry.kind == EntryKind.APP && entry.key in lockedApps && entry.key !in sessionUnlocked) {
            // No secure lock on the phone means there is nothing to ask for; the app opens as
            // usual rather than becoming unreachable.
            val prompt = runCatching {
                keyguard?.createConfirmDeviceCredentialIntent(
                    context.getString(R.string.app_lock_title),
                    context.getString(R.string.app_lock_prompt, entry.label),
                )
            }.getOrNull()
            if (prompt != null) {
                pendingLaunch = entry
                confirmLaunch.launch(prompt)
                return true
            }
        }
        return actuallyLaunch(entry)
    }

    LaunchedEffect(settings.edgeSide) { scrub.syncRestingSide(settings.edgeSide) }

    // BACK on the home screen must do nothing whatsoever.
    //
    // Without this it reaches the activity and finishes it, which is the default. A home
    // activity that finishes leaves the system to show whatever home-ish task is underneath
    // — and when the default launcher was switched *to* Deluge, the previous launcher's
    // task is still sitting there, so it comes back to the front. That looks like Deluge
    // handing control over, but it is simply this one exiting. A reboot clears the old task,
    // which is why it seemed to come and go, and why killing that launcher's process drops
    // to the system one instead.
    //
    // Registered before the handlers below so it stays the lowest priority: the dispatcher
    // runs the most recently added enabled callback first, so the overlay, edit mode and the
    // band editor all still get their turn at BACK before this swallows it.
    BackHandler(enabled = true) {}

    LaunchedEffect(appListVisible) { onAppListVisibleChange(appListVisible) }
    // Leaving the home destination entirely takes the overlay with it.
    DisposableEffect(Unit) { onDispose { onAppListVisibleChange(false) } }

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

    // Set for the length of a private space unlock that was asked to keep the list open.
    var keepListThroughPause by remember { mutableStateOf(false) }

    // Leaving the launcher (screen off, another app) should always drop us back to the home
    // screen rather than reopening onto the overlay.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // The one pause we caused ourselves is let through: unlocking the private
                // space puts the system's own lock screen in front, and that is not leaving
                // the launcher. Spent as soon as we are back, so it can never excuse the next
                // real departure.
                // Used up by the pause it was set for, so it excuses that one and never a
                // later departure.
                Lifecycle.Event.ON_PAUSE ->
                    if (keepListThroughPause) keepListThroughPause = false else closeAppList(snap = true)
                Lifecycle.Event.ON_RESUME -> keepListThroughPause = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(homeIntentTick) {
        if (homeIntentTick > 0) closeAppList(snap = true)
    }

    // The gap left for the back gesture, per side. Followed from the system when automatic,
    // since it reports how far its gesture reaches on each edge and reports nothing with
    // button navigation; the set number otherwise.
    val gestureInsets = WindowInsets.systemGestures
    val layoutDirection = LocalLayoutDirection.current
    val densityNow = LocalDensity.current
    val insetLeftDp = if (settings.edgeZoneInsetAuto) {
        with(densityNow) { gestureInsets.getLeft(this, layoutDirection).toDp().value.roundToInt() }
    } else {
        settings.edgeZoneInsetDp
    }
    val insetRightDp = if (settings.edgeZoneInsetAuto) {
        with(densityNow) { gestureInsets.getRight(this, layoutDirection).toDp().value.roundToInt() }
    } else {
        settings.edgeZoneInsetDp
    }

    LaunchedEffect(settings.edgeSide, settings.edgeZoneWidthDp, insetLeftDp, insetRightDp, view, band) {
        view.post {
            val density = view.resources.displayMetrics.density
            val widthPx = (settings.edgeZoneWidthDp * density).toInt()
            val insetLeftPx = (insetLeftDp * density).toInt()
            val insetRightPx = (insetRightDp * density).toInt()
            val h = view.height
            val w = view.width
            if (h > 0 && w > 0) {
                // Android's back-gesture claims the outer edges, and only honors 200dp of
                // exclusion per side — so spend it on the scrub band rather than spreading it
                // uselessly over the whole screen height.
                val capPx = (GESTURE_EXCLUSION_CAP_DP * density).toInt()
                val top = band.topPx.toInt().coerceIn(0, h)
                val bottom = band.bottomPx.toInt().coerceIn(top, h)
                // The band is usually taller than the cap, and whatever is left over stays
                // the system's: a touch there waits on the back-gesture detector before it
                // reaches us, which is the delay at the very edge. Centering what we are
                // given splits the unprotected remainder between the two ends rather than
                // leaving all of it below the halfway mark.
                val height = bottom - top
                val exTop = if (height > capPx) top + (height - capPx) / 2 else top
                val exBottom = if (height > capPx) exTop + capPx else bottom
                // Starting where the strip now starts. Excluding from the very edge while the
                // strip itself has been held inward is what made the setting do nothing: the
                // touch area moved, and the system was still being told to keep its back
                // gesture out of the pixels the setting had just handed back to it.
                val rects = buildList {
                    if (settings.edgeSide != EdgeSide.RIGHT) {
                        add(Rect(insetLeftPx, exTop, insetLeftPx + widthPx, exBottom))
                    }
                    if (settings.edgeSide != EdgeSide.LEFT) {
                        add(Rect(w - widthPx - insetRightPx, exTop, w - insetRightPx, exBottom))
                    }
                }
                ViewCompat.setSystemGestureExclusionRects(view, rects)
            }
        }
    }
    DisposableEffect(view) {
        // Settings and the other screens must get the system back-gesture back.
        onDispose { ViewCompat.setSystemGestureExclusionRects(view, emptyList()) }
    }

    // Worked out once, because two things need it: the strip itself, and the home content
    // that has to keep clear of a strip which is always there. A widget runs the full width,
    // so without this the strip simply sits on top of one.
    val stripAlwaysVisible = when (settings.azStripVisibility) {
        AzStripVisibility.NEVER -> false
        AzStripVisibility.ALWAYS -> true
        AzStripVisibility.LANDSCAPE ->
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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

        // One dim for the wallpaper, belonging to neither screen and drawn under both.
        //
        // Each screen used to carry its own, which meant a handover rather than a change: on
        // the way back the list's faded out with the list while the home screen's was still
        // faded in, so a home dim of 80% and a list dim of 25% went 25 to nothing to 80. One
        // layer moving between the two values has nothing to hand over.
        val wallpaperDim by animateFloatAsState(
            targetValue = when {
                // The range is set against the wallpaper itself.
                bandEditMode -> 0f
                appListVisible -> settings.dimWallpaperAlpha
                else -> homeDim
            },
            animationSpec = if (appListVisible || snapHome) {
                snap()
            } else {
                tween(HOME_FADE_MS, easing = LinearOutSlowInEasing)
            },
            label = "wallpaperDim",
        )
        if (wallpaperDim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(settings.dimColor).copy(alpha = wallpaperDim)),
            )
        }
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
                )
                // Setting the range takes the home screen out of the picture entirely, edit
                // layout's own controls with it. The band is judged against the wallpaper it
                // will sit on, not against the chrome it happens to overlap.
                .graphicsLayer { alpha = homeContentAlpha },
        ) {
            HomeScreen(
                homeIntentTick = homeIntentTick,
                closeFolderOnLaunch = settings.closeFolderOnLaunch,
                showFavoriteIcons = settings.showFavoriteIcons,
                // Only while it would still be telling someone something they do not know.
                showEmptyHint = !settings.favoritesEverSet &&
                    settings.favoritesSource == FavoritesSource.MANUAL,
                avoidStatusBar = !settings.hideStatusBar,
                markWorkApps = settings.markWorkApps,
                textShadow = settings.textShadow,
                textShadowStrength = settings.textShadowStrength,
                compactTapArea = settings.compactTapArea,
                cornerButtonApp = cornerButtonApp,
                onOpenCornerApp = { launchEntry(it) },
                cornerButtonIsSearch = cornerButtonIsSearch,
                onOpenCornerSearch = {
                    appListVisible = true
                    appListQuery = ""
                    forceSearch = true
                    // Opened the whole way, as tapping the edge does. Marking it visible is
                    // not opening it: the list is drawn against this, so without it the
                    // button produced the half-faded list of a swipe abandoned part way.
                    scope.launch { openAnim.snapTo(openDistancePx) }
                    searchFocusTick++
                },
                favoritesReorderable = settings.favoritesSource == FavoritesSource.MANUAL,
                shortcutSwipe = settings.shortcutSwipe,
                favorites = favorites,
                nameOverrides = nameOverrides,
                iconSizeDp = settings.iconSizeDp,
                labelSizeSp = settings.labelSizeSp,
                itemSpacingDp = settings.itemSpacingDp,
                sidePaddingDp = settings.sidePaddingDp,
                widgetSidePaddingDp = settings.widgetSidePaddingDp,
                widgetOffsetXDp = settings.widgetOffsetXDp,
                onSetSidePadding = { scope.launch { app.prefs.setSidePaddingDp(it) } },
                onSetWidgetSidePadding = { scope.launch { app.prefs.setWidgetSidePaddingDp(it) } },
                onSetWidgetOffsetX = { scope.launch { app.prefs.setWidgetOffsetXDp(it) } },
                paddings = homePaddings,
                widgetIds = widgetIds,
                disguiseNames = disguiseNames,
                folderDisguise = folderDisguise,
                widgetPosition = widgetPosition,
                widgetHeightDp = widgetHeightDp,
                widgetTick = widgetTick,
                hapticsEnabled = settings.hapticsEnabled,
                nowPlayingEnabled = settings.nowPlayingEnabled,
                nowPlayingHeightDp = settings.nowPlayingHeightDp,
                onResizeNowPlaying = { scope.launch { app.prefs.setNowPlayingHeightDp(it) } },
                widgetActions = widgetActions,
                onLaunch = { launchEntry(it) },
                // Remove means the same thing to whoever taps it either way: take this off my
                // home screen. Which list it writes to depends on which list is being shown —
                // against the computed favorites it used to edit the manual list nobody could
                // see, so the row stayed put and the menu item did nothing at all.
                onRemoveFavorite = {
                    scope.launch {
                        if (settings.favoritesSource == FavoritesSource.FREQUENT) {
                            app.prefs.setFrequentExcluded(it.key, excluded = true)
                        } else {
                            app.prefs.removeFavorite(it.key)
                        }
                    }
                },
                onOpenFolderApp = { launchEntry(it) },
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
                        // The home screen draws only the favorites that resolve to a row, and
                        // this hands back the order of what it drew. Written as it stands, that
                        // would replace the stored list and delete every favorite that drew
                        // nothing — which is exactly what a locked private space's favorites
                        // do. Whatever drew no row goes back where it was.
                        val drawn = newFavKeys.toSet()
                        app.prefs.setFavorites(restoreConcealed(favoriteKeys, newFavKeys) { it !in drawn })
                        app.prefs.setWidgetPosition(newWidgetPos)
                    }
                },
                onCommitPadding = { slot: PaddingSlot, value: Int ->
                    scope.launch { app.prefs.setHomePadding(slot, value) }
                },
                onFavoritesBoundsChanged = { top, bottom ->
                    // Measured in window space, so a home screen still travelling under the
                    // open overlay would drag the alphabet along with it. Whatever the band
                    // was when the list opened is what it stays.
                    //
                    // Edit mode is ignored for the same reason and a better one: it lays the
                    // favorites out differently, and the strip should match where they
                    // actually sit rather than where they sit while being rearranged.
                    if (appListVisible || homeEditMode) return@HomeScreen
                    // Not the favorites' own span when that is too short to divide between
                    // the letters — see ScrubberGeometry.bandForFavorites.
                    favBand = ScrubberGeometry.bandForFavorites(
                        topPx = top,
                        bottomPx = bottom,
                        viewportPx = viewportHeightPx.toFloat(),
                        minHeightPx = minBandHeightPx,
                    )
                },
                nowPlayingHasContent = nowPlayingHasContent,
                contentColor = settings.contentColor,
                showFavoriteLabels = settings.showFavoriteLabels,
                dimPausedApps = settings.dimPausedApps,
                pinchToResize = settings.pinchToResize,
                onSetIconSize = { scope.launch { app.prefs.setIconSizeDp(it) } },
                lockedApps = lockedApps,
                onToggleLock = { scope.launch { app.prefs.setAppLocked(it.key, it.key !in lockedApps) } },
                alignment = settings.alignment,
                iconSide = settings.iconSide,
                editMode = homeEditMode,
                onEditModeChange = { homeEditMode = it },
                onEditScrubBand = { liveBand = band; bandEditMode = true },
                centerFavorites = settings.centerFavorites,
                swipeUpOpensAppList = settings.swipeUpOpensAppList,
                onSwipeUpDrag = { total, delta ->
                    appListVisible = true
                    scope.launch { openAnim.snapTo(total.coerceAtMost(openDistancePx)) }
                    // Once it is all the way in the finger is usually still moving, so the
                    // rest of the drag goes to the list rather than stopping dead against it.
                    if (total > openDistancePx) {
                        swipeScrolledList = true
                        appListState.dispatchRawDelta(delta)
                    }
                },
                onSwipeUpEnd = { velocity ->
                    scope.launch {
                        val settled = openAnim.value > openDistancePx * 0.4f || velocity < -1200f
                        if (settled) {
                            openAnim.animateTo(
                                targetValue = openDistancePx,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                            // Asked for once it has settled, not on the way: a keyboard
                            // arriving mid-animation fights the list for the same space.
                            if (settings.swipeUpOpensSearch && !swipeScrolledList) {
                                searchFocusTick++
                            }
                        } else {
                            openAnim.animateTo(0f, tween(160, easing = FastOutLinearInEasing))
                            closeAppList()
                        }
                    }
                    // The scrolling half of this gesture was fed the drag raw, which carries
                    // no momentum of its own — so a flick that starts after the list is
                    // already moving has to have its velocity handed over explicitly, or the
                    // list stops the instant the finger leaves.
                    if (swipeScrolledList && velocity < 0f) {
                        scope.launch {
                            appListState.scroll {
                                var travelled = 0f
                                AnimationState(initialValue = 0f, initialVelocity = -velocity)
                                    .animateDecay(flingDecay) {
                                        scrollBy(value - travelled)
                                        travelled = value
                                    }
                            }
                        }
                    }
                    swipeScrolledList = false
                },
                quickLaunchEnabled = settings.quickLaunchLeft != null || settings.quickLaunchRight != null,
                onQuickLaunch = { slot ->
                    val target = when (slot) {
                        QuickLaunchSlot.LEFT -> settings.quickLaunchLeft
                        QuickLaunchSlot.RIGHT -> settings.quickLaunchRight
                    }
                    target?.let { launchEntry(it) }
                },
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
                // Same lock as the edge strip's double tap, on bare wallpaper instead.
                onDoubleTapBackground = if (settings.doubleTapToLock) ({ lockOrExplain() }) else null,
                onManageFavorites = { onNavigate("favorites") },
                onSetName = { appInfo, name -> scope.launch { app.prefs.setNameOverride(appInfo.key, name) } },
                onChangeIcon = { appInfo -> onNavigate(iconPickerRoute(appInfo.key)) },
                onAppInfo = { app.appRepository.openAppInfo(it) },
                onUninstall = { app.appRepository.uninstall(it) },
                onUnpinShortcut = { app.appRepository.unpin(it) },
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
                iconSizeDp = settings.iconSizeDp,
                labelSizeSp = settings.labelSizeSp,
                band = band,
                viewportHeightPx = viewportHeightPx,
                visible = appListVisible,
                favoriteKeys = remember(favoriteKeys) { favoriteKeys.toSet() },
                onLaunch = { appInfo ->
                    // A launch that never got off the ground leaves nothing to wait for, and
                    // the private-space row has nothing coming to the foreground either — it
                    // changes what this very list holds, so it wants the list out of the way.
                    // Unless asked to stay, in which case the list is where the space's apps
                    // are about to appear, and closing it means opening it again to see them.
                    val stay = appInfo.kind == EntryKind.PRIVATE_SPACE && settings.stayAfterPrivateUnlock
                    if (stay) {
                        keepListThroughPause = true
                        // A phone with no screen lock unlocks without showing anything, so no
                        // pause comes to use this up. Left standing it would let the next real
                        // departure keep the list open, so it lapses if the lock screen has not
                        // appeared by now — which it does at once when there is one.
                        scope.launch {
                            delay(PRIVATE_UNLOCK_PROMPT_MS)
                            keepListThroughPause = false
                        }
                    }
                    val acted = launchEntry(appInfo)
                    when {
                        stay -> Unit
                        acted && appInfo.kind == EntryKind.APP -> closeAfterLaunch()
                        else -> closeAppList()
                    }
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
                onAppInfo = { app.appRepository.openAppInfo(it) },
                onUninstall = { closeAppList(); app.appRepository.uninstall(it) },
                onUnpinShortcut = { appInfo -> closeAppList(); app.appRepository.unpin(appInfo) },
                onHideApp = { appInfo, hide -> scope.launch { app.prefs.setHidden(appInfo.key, hide) } },
                hiddenApps = hiddenApps,
                onMoveToFolder = { appInfo -> closeAppList(); folderPickerFor = appInfo },
                onOpenSettings = { closeAppList(); onNavigate("settings") },
                onDismiss = { closeAppList() },
                contentColor = settings.contentColor,
                showAlphabet = settings.showAlphabet,
                statusBarHidden = settings.hideStatusBarAppList,
                searchModel = searchModel,
                dimPausedApps = settings.dimPausedApps,
                reduceMotion = settings.reduceMotion,
                lockedApps = lockedApps,
                onToggleLock = { scope.launch { app.prefs.setAppLocked(it.key, it.key !in lockedApps) } },
                appTags = appTags,
                allTags = allTags,
                onAddTags = { apps, tags ->
                    // Replace, not merge: what is checked goes on and what is not comes off.
                    scope.launch {
                        apps.forEach { a -> app.prefs.setAppTags(a.key, tags) }
                    }
                },
                shortcutSwipe = settings.shortcutSwipe,
                favoritesEditable = settings.favoritesSource == FavoritesSource.MANUAL,
                showSettingsRow = settings.showSettingsRow,
                autoKeyboard = autoKeyboard,
                searchFocusTick = searchFocusTick,
                holdScrollSpeed = settings.holdScrollSpeed,
                dimOthers = settings.dimOthersScrubbing,
                markWorkApps = settings.markWorkApps,
                disguiseNames = disguiseNames,
                textShadow = settings.textShadow,
                textShadowStrength = settings.textShadowStrength,
                // Offered only where something can take it, or the row is a dead end.
                webSearchFallback = settings.webSearchFallback && webSearchAvailable,
                onWebSearch = { term ->
                    val intent = Intent(Intent.ACTION_WEB_SEARCH)
                        .putExtra(SearchManager.QUERY, term)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                    closeAppList()
                },
                lastLetterToLine = lastLetterToLine,
                searchEnabled = settings.appListSearch || forceSearch,
                searchAtBottom = settings.appListSearchBottom,
                sectionTopPercent = settings.sectionTopPercent,
                listState = appListState,
                // Distance still to travel, which is exactly what the collapse transform
                // takes: the list arrives scaled down and faded, and grows into place.
                enterPullPx = openDistancePx - openAnim.value,
                query = appListQuery,
                onQueryChange = { appListQuery = it },
                alignment = settings.appListAlignment,
                iconSide = settings.iconSide,
            )
        }

        // The band editor draws no letters of its own: the strip is what shows the range
        // being dragged, so it is given whether or not the always-on setting asked for it.
        // Edit layout is the other way round — its own controls sit where the strip does, and
        // the two were drawn on top of each other.
        // Sideways the strip is worth the room it takes; upright the same setting can be too
        // much, so a phone in a car mount can have it there without carrying it everywhere.
        val showIdleStripNow = when {
            appListVisible -> false
            bandEditMode -> true
            homeEditMode -> false
            else -> stripAlwaysVisible
        }
        if (showIdleStripNow) {
            EdgeScrubber(
                letters = listModel.letters,
                scrubY = { null },
                pullPx = { 0f },
                band = band,
                // Beside the empty end of the row, matching the app list: favorites aligned
                // right leave their left side free, so that is where the strip goes.
                side = if (settings.alignment == HomeAlignment.RIGHT) EdgeSide.LEFT else EdgeSide.RIGHT,
                modifier = Modifier.align(
                    if (settings.alignment == HomeAlignment.RIGHT) Alignment.CenterStart else Alignment.CenterEnd
                ),
            )
        }

        // Only ever on a home screen that is being looked at, and never over the app list:
        // this is the one card that asks for something the user has to leave the screen to
        // do, so it waits until they are actually here.
        if (showDefaultPrompt && !appListVisible && !homeEditMode && !bandEditMode) {
            DefaultLauncherPrompt(
                onSetDefault = onOpenHomeSettings,
                onDismiss = onDismissDefaultPrompt,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (showWelcome) {
            WelcomeDialog(onDismiss = onWelcomeDismissed)
        }

        folderPickerFor?.let { target ->
            FolderPickerDialog(
                appLabel = nameOverrides[target.key] ?: target.label,
                folders = folders,
                // The rows this launcher can actually draw, which is what the home screen's
                // own badge counts. A folder's stored keys include whatever a locked private
                // space is hiding.
                memberCount = { folder -> folder.apps.count { it in appsByKey } },
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
                letters = listModel.letters,
                onBandChange = { liveBand = it },
                // Stays open rather than exiting: dropping the stored range hands the
                // strip back to the favorites, and the point of a reset is watching it land
                // there. Done then leaves without storing anything, since there is nothing
                // being edited any more.
                onReset = { liveBand = null; onClearScrubBand() },
                onDone = { commitBand() },
            )
        }

        // Edge zones sit on top of everything, so one unbroken touch opens the list and then
        // scrubs it as the finger moves.
        if (!homeEditMode && !bandEditMode && appListQuery.isEmpty()) {
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
                    insetDp = (if (side == EdgeSide.LEFT) insetLeftDp else insetRightDp).dp,
                    letters = listModel.letters,
                    band = band,
                    bandOnly = settings.edgeZoneBandOnly,
                    holdScroll = settings.holdScroll,
                    hapticsEnabled = settings.hapticsEnabled,
                    state = scrub,
                    listOpen = appListVisible,
                    onDismiss = { closeAppList() },
                    onOpen = {
                        appListVisible = true
                        // Opened by touching the edge, so there is nothing to animate in.
                        scope.launch { openAnim.snapTo(openDistancePx) }
                    },
                    onDoubleTap = if (settings.doubleTapToLock) ({ lockOrExplain() }) else null,
                    // Aligned to the top when it is confined, because the offset that puts it
                    // against the band is measured from there. Centred, it would be placed by
                    // half the leftover height and then pushed down again by the whole of the
                    // band's top.
                    modifier = Modifier.align(
                        when {
                            settings.edgeZoneBandOnly && side == EdgeSide.LEFT -> Alignment.TopStart
                            settings.edgeZoneBandOnly -> Alignment.TopEnd
                            side == EdgeSide.LEFT -> Alignment.CenterStart
                            else -> Alignment.CenterEnd
                        }
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
    val azStripVisibility: AzStripVisibility,
    val shortcutSwipe: ShortcutSwipe,
    val showAlphabet: Boolean,
    val alignment: HomeAlignment,
    val appListAlignment: HomeAlignment,
    val iconSide: IconSide,
    val widgetSidePaddingDp: Int,
    val widgetOffsetXDp: Int,
    val edgeZoneWidthDp: Int,
    val swipeUpOpensAppList: Boolean,
    val appListSearch: Boolean,
    val appListSearchBottom: Boolean,
    val sectionTopPercent: Int,
    val closeFolderOnLaunch: Boolean,
    val showListHeaders: Boolean,
    val showSettingsRow: Boolean,
    val showFavoriteIcons: Boolean,
    val favoritesEverSet: Boolean,
    val edgeZoneBandOnly: Boolean,
    val holdScroll: Boolean,
    val holdScrollSpeed: Int,
    val dimOthersScrubbing: Boolean,
    val markWorkApps: Boolean,
    val textShadow: Boolean,
    val textShadowStrength: Int,
    val compactTapArea: Boolean,
    val privateSpaceSection: Boolean,
    val recentSection: Boolean,
    val recentSectionCount: Int,
    val edgeZoneInsetDp: Int,
    val edgeZoneInsetAuto: Boolean,
    val stayAfterPrivateUnlock: Boolean,
    val webSearchFallback: Boolean,
    val swipeUpOpensSearch: Boolean,
    val favoritesSource: FavoritesSource,
    val appListSearchHidden: Boolean,
    val hideStatusBarAppList: Boolean,
    /** Whether the status bar is hidden on the home screen, so the content can clear it. */
    val hideStatusBar: Boolean,
    val sortByUsage: Boolean,
    val quickLaunchLeft: AppInfo?,
    val quickLaunchRight: AppInfo?,
    /** Place the favorites by measurement, until the user sets a padding of their own. */
    val centerFavorites: Boolean,
    val dimWallpaperAlpha: Float,
    val dimHomeAlpha: Float,
    val dimColor: Int,
    val hapticsEnabled: Boolean,
    val showFavoriteLabels: Boolean,
    val doubleTapToLock: Boolean,
    val contentColor: Color,
    /** Whether a paused (suspended) app is shown greyed out. */
    val dimPausedApps: Boolean,
    /** Whether a two-finger pinch on the home screen resizes the icons. */
    val pinchToResize: Boolean,
    /** Whether the launcher's own transitions are skipped. */
    val reduceMotion: Boolean,
)

/** How long a private space unlock has to put the system's lock screen up before giving up. */
private const val PRIVATE_UNLOCK_PROMPT_MS = 1500L
