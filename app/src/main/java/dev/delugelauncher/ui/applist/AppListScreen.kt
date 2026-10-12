// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import dev.delugelauncher.ui.common.DelugeIcons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import dev.delugelauncher.ui.theme.launcherTextShadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EdgeSide
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.HomeAlignment
import dev.delugelauncher.data.IconSide
import dev.delugelauncher.data.ShortcutSwipe
import dev.delugelauncher.ui.common.AppIcon
import dev.delugelauncher.ui.common.AppShortcutMenu
import dev.delugelauncher.ui.common.swipeForShortcuts
import dev.delugelauncher.ui.common.LocalIconConfig
import dev.delugelauncher.ui.common.recordTouchPosition
import dev.delugelauncher.ui.common.EditAppDialog
import dev.delugelauncher.ui.common.pressPop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.delugelauncher.R
import androidx.compose.ui.res.stringResource

/**
 * Breathing room above A and below the settings row when no scrub has placed the list.
 *
 * Small, because nothing needs holding clear any more: the end fades now switch off when there
 * is nothing scrolled past them, so the first row is crisp where it rests rather than sitting
 * inside a permanent fade.
 */
private val IDLE_TOP_PADDING = 8.dp
private val IDLE_BOTTOM_PADDING = 32.dp

/** Smallest comfortable row, so a tap beside a small icon still lands on its app. */
private val MIN_ROW_HEIGHT = 48.dp

/** Sets the settings shortcut apart from the last app above it. */
private val SETTINGS_ROW_GAP = 20.dp

/**
 * Widest the list is allowed to get.
 *
 * A name is a short thing; stretched over a tablet it leaves the row mostly empty and puts the
 * A-Z strip a hand's width from what it is scrubbing. A phone held upright never reaches this.
 */
private val MAX_LIST_WIDTH = 600.dp

/** How wide the list may run sideways, where two columns share it. */
private val MAX_LIST_WIDTH_LANDSCAPE = 1000.dp

/** Sideways on a tablet or an unfolded foldable, where the phone cap strands the middle. */
private val MAX_LIST_WIDTH_TABLET = 1200.dp

/** How far the list dissolves at each end. */
private val FADE_HEIGHT = 56.dp

/**
 * Gap above and below search results.
 *
 * The idle gaps exist so a scrubbed letter can be placed on the scrub line. Search has no
 * scrub and no line, so results simply start where the list does.
 */
private val SEARCH_EDGE_PADDING = 8.dp

/** How far either end of the list may be dragged past its content. */
private val MAX_EDGE_STRETCH = 40.dp

/**
 * How much of the collapse travel commits it. Shared with the swipe that opens the list, so
 * closing takes the same push as opening rather than roughly twice it.
 */
private const val COMMIT_FRACTION = 0.4f

/**
 * Damping for the edge elastic. Under 1 so a fling into an end overshoots and comes back
 * once — a bumper, not a bounce.
 */
private const val EDGE_STRETCH_DAMPING = 0.55f

/**
 * One row's identity, section included. The long-press menu is keyed by this rather than by
 * the app alone — and so is the list itself — because the same app legitimately stands twice
 * in the list, under its tag and under its letter.
 */
private fun rowKey(row: AppListRow): String = when (row) {
    is AppListRow.Header -> "header:${row.text}"
    is AppListRow.Entry -> row.section?.let { "$it:${row.app.key}" } ?: row.app.key
}

/** One laid-out row's identity, pairing included. */
private fun cellKey(cell: CellRow): String = when (cell) {
    is CellRow.Header -> "header:${cell.text}"
    is CellRow.One -> rowKey(cell.entry)
    is CellRow.Two -> rowKey(cell.left) + "|" + (cell.right?.let { rowKey(it) } ?: "")
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppListScreen(
    model: AppListModel,
    nameOverrides: Map<String, String>,
    scrub: ScrubState,
    iconSizeDp: Int,
    labelSizeSp: Int,
    band: ScrubBand,
    viewportHeightPx: Int,
    visible: Boolean,
    favoriteKeys: Set<String>,
    onLaunch: (AppInfo) -> Unit,
    onSetFavorite: (AppInfo, Boolean) -> Unit,
    onSetName: (AppInfo, String?) -> Unit,
    onChangeIcon: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onUnpinShortcut: (AppInfo) -> Unit,
    onHideApp: (AppInfo, Boolean) -> Unit,
    /** Which apps are hidden, so a search that turns one up can say so and put it back. */
    hiddenApps: Set<String>,
    /** Whether a paused (suspended) app is shown greyed out. */
    dimPausedApps: Boolean,
    /** Whether the launcher's own transitions are collapsed to instant. */
    reduceMotion: Boolean,
    /** Keys that ask for the device lock before opening. */
    lockedApps: Set<String>,
    onToggleLock: (AppInfo) -> Unit,
    /** Every tag in use, offered by the picker. */
    allTags: List<String>,
    /** Tags by app key, so the picker can pre-check what the chosen apps already carry. */
    appTags: Map<String, Set<String>>,
    /** Tags the picker refuses by hand: the auto-tagger's words in every language it wore. */
    reservedTags: Set<String> = RESERVED_TAGS,
    /** Sets the chosen apps' tags to exactly this set — checked are applied, unchecked removed. */
    onAddTags: (List<AppInfo>, Set<String>) -> Unit,
    onMoveToFolder: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    contentColor: Color,
    showAlphabet: Boolean,
    /** Hoisted so a swipe that overshoots the opening animation can keep scrolling it. */
    listState: LazyListState,
    /** How far the list still has to travel to be fully open; 0 once it has arrived. */
    enterPullPx: Float,
    /** Whether the status bar is set to stay hidden here, so its gap is not reserved. */
    statusBarHidden: Boolean,
    /** The model a query runs against, which may carry hidden apps the list itself omits. */
    searchModel: AppListModel,
    /** Whether a sideways swipe on a row offers its app's shortcuts. */
    shortcutSwipe: ShortcutSwipe,
    /** False when the favorites are computed, so starring would write to a list nobody sees. */
    favoritesEditable: Boolean,
    /** Whether the list ends with a row that opens the launcher's own settings. */
    showSettingsRow: Boolean,
    /** Whether opening the list puts the cursor in the search box. */
    autoKeyboard: Boolean,
    /** Bumped when something has asked for the cursor to be in the search box. */
    searchFocusTick: Int,
    /** Rows a second to walk while a finger rests on a letter. */
    holdScrollSpeed: Int,
    /** Whether the rest of the list fades away while a letter is being scrubbed. */
    dimOthers: Boolean,
    /** Whether a work profile's apps say so after their name. */
    markWorkApps: Boolean,
    /** Fake names by app key while the screenshot disguise is on; empty otherwise. */
    disguiseNames: Map<String, String>,
    /** Two apps per list row, upright and sideways separately. */
    listTwoColumnPortrait: Boolean,
    listTwoColumnLandscape: Boolean,
    /** Whether the launcher's own text carries a shadow for legibility. */
    textShadow: Boolean,
    /** How strong that shadow is. */
    textShadowStrength: Int,
    /** Whether a query matching nothing offers the words to the browser. */
    webSearchFallback: Boolean,
    onWebSearch: (String) -> Unit,
    /** Whether the last letters can reach the same line as the first. */
    lastLetterToLine: Boolean,
    /** English name for filing search results like the list files them; null when unknown. */
    englishName: (AppInfo) -> String? = { null },
    /** Whether the shown name was typed by hand, which files '#' as '#' itself. */
    isRenamed: (AppInfo) -> Boolean = { false },
    searchEnabled: Boolean,
    searchAtBottom: Boolean,
    sectionTopPercent: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    alignment: HomeAlignment,
    iconSide: IconSide,
) {
    // Appended at the point of showing rather than stored in the label, so a rename still
    // wins and turning this off needs no re-reading of every app. The disguise wins over
    // everything while it is on: a rename is exactly what must not show.
    val workSuffix = stringResource(R.string.app_work_suffix)
    fun displayName(app: AppInfo): String {
        disguiseNames[app.key]?.let { return it }
        val base = nameOverrides[app.key] ?: app.label
        return if (markWorkApps && app.work) base + workSuffix else base
    }

    // The gesture handlers below outlive the composition that created them, so they must not
    // capture this frame's callbacks — a dismiss half a minute old still has to close the
    // list that is up now.
    val currentDismiss by rememberUpdatedState(onDismiss)

    // Reading these here confines the invalidation to this composable: the home screen
    // behind the overlay never sees the letter change. currentY/currentPull stay as
    // function references so their callers read them in the draw phase, not composition.
    val scrubLetter = scrub.letter
    val scrubPlacement = scrub.placement
    val scrubbing = scrub.scrubbing
    val activeSide = scrub.side
    val scrubY = remember(scrub) { scrub::currentY }
    val pullPx = remember(scrub) { scrub::currentPull }

    // Searching is a different mode from scrubbing: the letters shrink to whatever matched,
    // so the strip is hidden and placement stays out of it until the query is cleared.
    // Filtered off the main thread with a short debounce: every keystroke used to scan the
    // whole list (pinyin included) during composition, which dropped frames while typing.
    val searching = searchEnabled && query.isNotBlank()
    var displayModel by remember(model, searchModel, searchEnabled) { mutableStateOf(model) }
    LaunchedEffect(model, searchModel, query, searchEnabled) {
        if (!searching) {
            displayModel = model
            return@LaunchedEffect
        }
        delay(100)
        val term = query.trim()
        displayModel = withContext(Dispatchers.Default) {
            searchModel
                .filtered { app ->
                    // The name it shows, its pinyin (微信 answers to "wx"), and — for a phone
                    // that names everything in its own language, so Settings calls itself 設定
                    // — the package name, where the English word is still sitting.
                    PinyinSearch.rank(displayName(app), app.componentName.packageName, term) !=
                        PinyinSearch.NO_MATCH
                }
                .rankedBy(
                    rank = { PinyinSearch.rank(displayName(it), it.componentName.packageName, term) },
                    displayName = { displayName(it) },
                    renamed = { isRenamed(it) },
                    englishName = { englishName(it) },
                )
        }
    }
    // What a search result underlines: the query, and only while it is actually a search.
    val searchTerm = if (searching) query.trim() else ""
    // Sideways the rows pair up, so the two columns stay level instead of one running long;
    // upright every row holds one app, exactly as before — unless asked for two everywhere.
    // Each orientation has its own switch, so all four combinations are possible.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val twoColumn = if (landscape) listTwoColumnLandscape else listTwoColumnPortrait
    // The model as laid out. Everything below — scrub placement, spy, gaps, keys — reads
    // this rather than the model, so indices always mean laid-out rows.
    val cells = remember(displayModel, twoColumn) { displayModel.toCells(twoColumn) }
    // Staying on the same app across the 1↔2 column switch: pairing halves the row count,
    // so keeping the raw index would land somewhere unrelated. Anchored by app key instead.
    var columnAnchorKey by remember { mutableStateOf<String?>(null) }
    fun anchorOf(cell: CellRow): String? = when (cell) {
        is CellRow.Header -> null
        is CellRow.One -> cell.entry.app.key
        is CellRow.Two -> cell.left.app.key
    }
    LaunchedEffect(cells) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx -> columnAnchorKey = cells.rows.getOrNull(idx)?.let(::anchorOf) }
    }
    LaunchedEffect(twoColumn) {
        val key = columnAnchorKey ?: return@LaunchedEffect
        val idx = cells.rows.indexOfFirst { cell ->
            when (cell) {
                is CellRow.Header -> false
                is CellRow.One -> cell.entry.app.key == key
                is CellRow.Two -> cell.left.app.key == key || cell.right?.app?.key == key
            }
        }
        if (idx >= 0) listState.scrollToItem(idx)
    }
    // The launcher's own transitions, collapsed to nothing when motion is reduced.
    val motionFast = if (reduceMotion) 0 else 180
    val motionSlow = if (reduceMotion) 0 else 240
    // Animated rather than switched, so an end does not snap from crisp to faded the moment
    // the first pixel scrolls past it.
    val topFade by animateFloatAsState(
        if (listState.canScrollBackward) 1f else 0f,
        label = "topFade",
    )
    val bottomFade by animateFloatAsState(
        if (listState.canScrollForward) 1f else 0f,
        label = "bottomFade",
    )

    /** Height of the pinned search field, so list-relative offsets can be compared to taps. */
    var searchHeightPx by remember { mutableIntStateOf(0) }

    // Each query is a fresh list, so it starts at the top. Without this the offset from
    // whatever was scrolled before carries over, and a query with few matches lands the
    // results somewhere past the end of the screen.
    LaunchedEffect(query) {
        if (query.isNotBlank()) listState.scrollToItem(0)
    }

    // Rows outside the scrubbed letter fade out; the section itself never moves, because it
    // is the same list the whole time. Only ever read inside a graphicsLayer, so the fade
    // runs in the draw phase instead of recomposing every visible row 60 times a second.
    val othersAlpha by animateFloatAsState(
        // Only while a finger is travelling through the alphabet: a tap on the edge sets a
        // letter too, and fading out for it cost a quarter of a second of ghosted list on
        // every open.
        // Not while a held letter is walking the list: the whole point of that is to read what
        // goes past, and everything that went past was faded out. That is what made it look as
        // though only the held letter's own apps ever scrolled.
        targetValue = if (scrubLetter != null && scrubbing && dimOthers && !scrub.holdScrolling) {
            0f
        } else {
            1f
        },
        animationSpec = tween(durationMillis = motionFast),
        label = "othersAlpha",
    )
    // Where a scrubbed letter is parked. A setting because how far up the screen you can
    // still reach is a fact about the hand and the phone, not something to pick for anyone.
    val sectionTopPx = (viewportHeightPx * sectionTopPercent / 100f).roundToInt()

    // The LazyColumn always holds the full list — while scrubbing it's just hidden and
    // pre-scrolled, with the letter's apps drawn over the top. Filtering the rows themselves
    // meant that on release the unfiltered list was briefly parked back at A.
    // From the placement and not from the live letter: the letter is gone by the time a quick
    // tap has composed anything, and the list is placed by what was asked for, not by what is
    // still under a finger.
    val scrubRowIndex = remember(displayModel, scrubPlacement) {
        val letter = scrubPlacement?.letter ?: return@remember -1
        cells.letterIndex.firstOrNull { it.first == letter }?.second ?: -1
    }

    // Row indices of the highlighted section. Applied *after* the scroll lands, otherwise
    // the new letter lights up a frame before the list moves to it — that was the jitter.
    var highlightRange by remember { mutableStateOf(IntRange.EMPTY) }

    // Pull-to-collapse from either end, done the way pull-to-refresh is done: one
    // nested-scroll connection that actually *consumes* the drag. Held signed throughout —
    // positive is pulled down off the top, negative is pulled up off the bottom — so one
    // gesture serves both ends rather than each needing its own path.
    //
    // The previous versions watched the raw pointer stream without consuming, so the list
    // scrolled and the overlay tracked the pull at the same time, and reversing direction
    // left the two disagreeing. Consuming means the list can't scroll while there's a pull
    // outstanding, and winding back up spends the pull before the list moves again — so the
    // gesture is always in exactly one state.
    val density = LocalDensity.current
    // The travel a collapse is drawn over, and the share of it that commits — deliberately
    // the same fraction the swipe-up uses to open, so the two gestures answer alike.
    val dismissPullPx = with(density) { 150.dp.toPx() }
    val dismissCommitPx = dismissPullPx * COMMIT_FRACTION
    // Per-frame drag big enough to call a flick rather than a deliberate push.
    val fastDragPx = with(density) { 12.dp.toPx() }
    // How far a gesture may have scrolled the list and still count as starting at the end.
    val nearEndPx = with(density) { 48.dp.toPx() }
    val maxPullPx = with(density) { 320.dp.toPx() }
    val maxStretchPx = with(density) { MAX_EDGE_STRETCH.toPx() }
    // The idle gaps are there so a scrubbed letter can be placed on the scrub line with room
    // to spare. A pinned search field already separates the list from the screen edge, and the
    // full gap on top of it read as dead space — and as a gap that changed size the moment you
    // started typing. Whichever end the field is on gets the search gap either way.
    val restingTopPadding =
        if (searchEnabled && !searchAtBottom) SEARCH_EDGE_PADDING else IDLE_TOP_PADDING
    val restingBottomPadding =
        if (searchEnabled && searchAtBottom) SEARCH_EDGE_PADDING else IDLE_BOTTOM_PADDING
    val idleTopPaddingPx = with(density) { restingTopPadding.roundToPx() }
    val idleBottomPaddingPx = with(density) { restingBottomPadding.roundToPx() }

    /** Signed: positive pulled down off the top of the list, negative up off the bottom. */
    var overPull by remember { mutableFloatStateOf(0f) }
    val collapseAnim = remember { Animatable(0f) }
    var collapsing by remember { mutableStateOf(false) }
    // Entering and collapsing are the same transform in opposite directions, so the distance
    // left to travel on the way in is fed through the very same path.
    val collapseProvider: () -> Float = {
        when {
            collapsing -> collapseAnim.value
            enterPullPx > 0f -> enterPullPx
            else -> overPull
        }
    }

    // Both ends of the list share one elastic. A drag past an end stretches it, a fling into
    // an end seeds it with the leftover *velocity*, and it always springs back to rest.
    // Seeding from velocity rather than jumping to a fixed peak is what stops a second fling
    // from snapping the list: the new spring carries on from wherever the old one was.
    var stretchPx by remember { mutableFloatStateOf(0f) }
    val stretchAnim = remember { Animatable(0f) }
    var stretchSettling by remember { mutableStateOf(false) }
    val stretchProvider: () -> Float = {
        val raw = if (stretchSettling) stretchAnim.value else stretchPx
        raw.coerceIn(-maxStretchPx, maxStretchPx)
    }

    // Everything the overlay left behind has to be cleared explicitly, because it stays
    // composed while hidden: the tail padding and the collapse transform from the last scrub
    // would otherwise still be there the next time it opens.
    val focusManager = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    // Asked for after the overlay is actually placed: a focus request against a field that has
    // not been laid out yet throws, and the field arrives with the rest of the list.
    LaunchedEffect(searchFocusTick) {
        if (searchFocusTick > 0 && searchEnabled) runCatching { searchFocus.requestFocus() }
    }
    LaunchedEffect(visible, autoKeyboard, searchEnabled) {
        if (visible && autoKeyboard && searchEnabled) {
            runCatching { searchFocus.requestFocus() }
        }
    }
    LaunchedEffect(visible) {
        if (!visible) {
            focusManager.clearFocus()
            highlightRange = IntRange.EMPTY
            overPull = 0f
            stretchPx = 0f
            collapsing = false
            scrub.clearPlacement()
            return@LaunchedEffect
        }
        // Back to the top on the way in rather than on the way out, and only when nothing has
        // asked for a letter.
        //
        // Hidden, this overlay is measured but never placed, and a scroll waits for a layout
        // that is not coming — so a scroll-to-top issued on close sat pending from then until
        // the list was next placed, which is the moment it opens. It and the tap's own scroll
        // then woke on the same layout pass and raced for the scroll mutex, and when the stale
        // one landed second the list opened at A having been told to go to N. Only ever on the
        // first open after the launcher started, because only then is one left pending.
        if (scrub.placement == null) listState.scrollToItem(0)
    }

    // How much of the scrub placement padding is still sitting on screen at each end, over and
    // above the gap the list idles with. Positive means that end has further to travel.
    fun topGapRemaining(): Float {
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return 0f
        // Not the first row at the top means the gap is long gone above us.
        if (first.index > 0) return 0f
        return (first.offset - info.viewportStartOffset).toFloat() - idleTopPaddingPx
    }

    /**
     * How much further the list could still scroll forward. Large whenever the end is not even
     * on screen; zero when parked against it.
     */
    fun forwardRoom(): Float {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return Float.MAX_VALUE
        // The last row's index is size - 1: comparing against size is always true and the
        // end is never reported as reached.
        if (last.index < cells.rows.size - 1) return Float.MAX_VALUE
        val bottom = (last.offset - info.viewportStartOffset + last.size).toFloat()
        return bottom + idleBottomPaddingPx - viewportHeightPx
    }

    /**
     * Whether the placement padding can be retired without anything appearing to move.
     *
     * A list short enough to show both ends at once can never clear either gap by scrolling,
     * so it is let through rather than left holding the padding for good.
     */
    fun placementSettled(): Boolean {
        val items = listState.layoutInfo.visibleItemsInfo
        val first = items.firstOrNull() ?: return true
        if (first.index == 0 && items.last().index >= cells.rows.size - 1) return true
        return topGapRemaining() <= 0f
    }

    // A manual scroll means the scrub placement has served its purpose, so the alignment gap
    // can go — but it is ordinary scrollable space, so the finger is allowed to travel through
    // it rather than having it pulled out from underneath. That matters at A, which the scrub
    // parks against the top of the scroll range: there is nothing above to scroll back into, so
    // retiring the gap there could only shift the rows themselves and the first section would
    // snap upward. Waiting until it has scrolled off the top makes the change invisible, and
    // once gone the idle gap is all that is left, so there is no scrolling back into it unless
    // A is picked again.
    /**
     * The most of the placement gap that may still be shown, which only ever falls.
     *
     * The gap a scrub opens above the letter is ordinary scrollable space, so having ridden the
     * letter up from the line you could ride it straight back down to it. This is the ratchet
     * asked for: once the letter has been carried up to a tenth of the screen, a tenth is as
     * far back down as it goes, and the room it came from is not handed back until the letter
     * is picked again.
     *
     * Enforced by refusing the scroll rather than by shrinking the padding. Shrinking it moves
     * the content by the same amount the finger just did — the two add up and the list bolts to
     * the top, which is what the first attempt at this did.
     */
    var gapCeilingPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }

    /**
     * The most blank that may still be shown under the last row, which only ever falls.
     *
     * This is the mirror of [gapCeilingPx], and the mirroring works here where every previous
     * attempt did not, because of which direction it refuses. The blank under the last row
     * grows when you scroll forward, into nothing, and shrinks when you scroll back toward the
     * apps. Refusing it to grow therefore stops the last letter travelling up past the line —
     * and leaves scrolling back entirely free, which is the only way out of the end of a list
     * and the thing the earlier clamps took away.
     */
    var blankCeilingPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }

    /**
     * Whether the room under the last app is still being held for a placement.
     *
     * This is how the end of the list gets what the start gets from its ratchet. The room is
     * what lets the last letters reach the line, and it is taken away as soon as the last row
     * has been scrolled off — invisibly, because by then it is below the viewport. Coming back
     * afterwards, the list ends at its last app and the letter sits where its own content puts
     * it, which is the same "it cannot return to the line" the first letter has, reached
     * without refusing a single scroll.
     *
     * Held apart from [highlightRange], which cannot answer this: that range is retired the
     * moment the gap above has nothing left to give, and above the last letter it has nothing
     * to give at the outset.
     */
    var bottomRoomLive by remember { mutableStateOf(false) }

    var userDragged by remember { mutableStateOf(false) }

    /**
     * How much room is still held above the first row for the placed letter to sit on the line.
     *
     * A ratchet: it gives way as it scrolls off the top and never opens back up. Retiring it in
     * one step instead meant the gap sat there at full size through the whole scroll and then
     * vanished, and the scroll had to be compensated by exactly the amount it lost or the list
     * jumped by the difference. Letting it out by however much has already gone past the top
     * edge is the same arithmetic done continuously, and it cannot be seen for the same reason:
     * what is taken away is only ever what is no longer on screen.
     */
    // Taken away the moment the last row leaves the screen, which is the whole of the ratchet
    // at this end. Invisible when it happens: what is removed is below the viewport.
    LaunchedEffect(bottomRoomLive) {
        if (!bottomRoomLive) return@LaunchedEffect
        // Once the last row's bottom has reached the bottom of the screen, every pixel of the
        // room is below the fold and taking it away moves nothing.
        //
        // The condition was "the last row has left the viewport", which never came true: the
        // room keeps that row inside the measured viewport for as long as it is held, so the
        // list waited forever and the room was never given back. Asking where the row's bottom
        // edge is asks the question that was meant all along.
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            // Far enough past the fold to cover the gap above the first row as well, since
            // that one is let go at the same moment and the list has to be able to absorb it.
            // Waiting only for the room itself to clear the bottom edge left nothing spare, so
            // the compensating scroll came up short by whatever it could not find — which is
            // the shift felt as the last letter reached the edge.
            val slack = (sectionTopPx - idleTopPaddingPx).coerceAtLeast(0)
            last != null && (
                // Scrolled back far enough that the final row is not even on screen.
                last.index < info.totalItemsCount - 1 ||
                    // Or it is, and there is room under it for both to go at once.
                    last.offset + last.size >= info.viewportEndOffset + slack
                )
        }
            // The layout has not caught up at the moment this starts: the scroll to the letter
            // is still in flight, so the first reading describes where the list was a frame ago
            // and says the room is already spent. Waiting for it to be false once means waiting
            // for the letter to actually arrive.
            .dropWhile { it }
            .first { it }
        bottomRoomLive = false
    }

    LaunchedEffect(userDragged, scrubLetter, bottomRoomLive) {
        // Not while the room under the last row is still held. Down there the gap above the
        // first row is doing nothing — row zero is a whole alphabet away — but retiring it
        // still shortens the list by its height, and the compensation for that is capped by
        // how much room is left below, which the held room has already spoken for. What is
        // left uncovered is the jump felt on the first drag away from the last letter. Both
        // are let go together instead, once the list is back among the apps and there is room
        // to absorb it.
        if (!userDragged || scrubLetter != null || highlightRange.isEmpty() || bottomRoomLive) {
            return@LaunchedEffect
        }
        snapshotFlow { placementSettled() }.first { it }
        if (highlightRange.isEmpty()) return@LaunchedEffect

        // The top padding falls from the scrub line back to the idle gap, so that is exactly
        // how far the content would rise — compensating by anything else leaves the list
        // jumping by the difference.
        //
        // Except against the bottom, where shrinking the padding shortens the scroll range by
        // the same amount and the clamp slides us back by whatever no longer fits, doing part
        // of the job already. Compensating the full amount on top of that is itself a jump, so
        // only what the clamp cannot absorb is asked for.
        val shrinkBy = (sectionTopPx - idleTopPaddingPx).toFloat()
        val compensate = minOf(shrinkBy, forwardRoom()).coerceAtLeast(0f)
        highlightRange = IntRange.EMPTY
        // dispatchRawDelta rather than scrollBy: the drag that got us here holds the scroll
        // mutex at UserInput priority, and a scrollBy would just be canceled by it.
        if (compensate > 0f) listState.dispatchRawDelta(-compensate)
    }

    LaunchedEffect(scrubRowIndex, scrubPlacement, displayModel, scrub.active) {
        if (scrubRowIndex < 0) return@LaunchedEffect
        // The next letter's header ends this section. Walking the rows to find it copied the
        // whole tail of the list on every one of the ~26 letter changes in a gesture.
        val end = cells.letterIndex.firstOrNull { it.second > scrubRowIndex }?.second ?: cells.rows.size

        // While the finger is down, scroll at once and measure nothing. The room-measure
        // below awaits two frames per letter change, and fast movement restarts this effect
        // before the scroll even runs — the list lags the finger and the whole drag
        // stutters, then goes smooth once every row has been composed anyway. The room is
        // still opened so the last letters can travel; it is measured when the finger lifts
        // and this effect reruns with the finger gone.
        // While the finger is down, scroll at once and measure nothing. The room is
        // still opened so the last letters can travel; it is measured when the finger
        // lifts and this effect reruns with the finger gone. No throttling here on
        // purpose: delaying a scroll only lags the list behind the finger, and a lagging
        // list reads as stutter. Superseded runs die to restarts instead, mid-scroll.
        if (scrub.active) {
            if (lastLetterToLine && !bottomRoomLive) bottomRoomLive = true
            listState.scrollToItem(scrubRowIndex)
            highlightRange = scrubRowIndex until end
            // A fresh placement opens the room again; from here the ratchet only closes it.
            gapCeilingPx = Float.MAX_VALUE
            blankCeilingPx = Float.MAX_VALUE
            // This placement is fresh, so the next drag is the one that retires it.
            userDragged = false
            return@LaunchedEffect
        }

        // Room before the scroll, and a frame for it to land in. The scroll clamps against the
        // range as it stands when it runs, so asking for the room afterwards leaves the letter
        // stopped short of the line with the room arriving behind it.
        //
        // Offered to every placement rather than only to the last section. Whether a letter
        // needs any is not a question about which letter it is: it is whether the apps below it
        // are enough to push it up to the line, and the last several letters of an alphabet are
        // never enough between them. Guessing by position put the final letter on the line and
        // left the four or five before it short.
        if (lastLetterToLine && !bottomRoomLive) {
            bottomRoomLive = true
            withFrameNanos { }
        }

        listState.scrollToItem(scrubRowIndex)
        highlightRange = scrubRowIndex until end
        // A fresh placement opens the room again; from here the ratchet only closes it.
        gapCeilingPx = Float.MAX_VALUE
        blankCeilingPx = Float.MAX_VALUE
        if (bottomRoomLive) {
            // Now ask what the room was actually worth, once the scroll has landed. Blank left
            // under the last row means this letter leant on it and the ratchet has something to
            // hold; none means it reached the line on its own apps, and the room goes back
            // before it can add a screen of empty scrolling to the end of the list.
            //
            // Measured here and not left to the first drag: that reading arrives before the
            // list has settled and describes more blank than the placement left, so the ceiling
            // opened above the line and the list ran up into the difference before stopping.
            withFrameNanos { }
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val blank = if (last != null && last.index == info.totalItemsCount - 1) {
                (info.viewportEndOffset - (last.offset + last.size)).toFloat()
            } else {
                0f
            }
            if (blank > 0f) blankCeilingPx = blank else bottomRoomLive = false
        }
        // This placement is fresh, so the next drag is the one that retires it.
        userDragged = false
    }

    // Walking on through a letter while the finger rests on it. A row at a time rather than a
    // fling, because the point is to read what goes past.
    val holdScrolling = scrub.holdScrolling
    LaunchedEffect(holdScrolling, holdScrollSpeed) {
        if (!holdScrolling) return@LaunchedEffect
        val rowPx = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index > 0 }?.size?.toFloat() ?: return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            last = now
            val delta = rowPx * holdScrollSpeed * seconds
            // dispatchRawDelta for the reason given where the gap is compensated: a scrollBy
            // takes the scroll mutex, and anything else that wants it — the placement scroll,
            // an overscroll settling — cancels this one. That cancellation propagates out of
            // the loop and the walk stops for good, a letter or two in, which is exactly how
            // it behaved.
            if (delta > 0f) listState.dispatchRawDelta(delta)
        }
    }

    val scope = rememberCoroutineScope()

    val listConnection = remember(dismissPullPx, maxPullPx, maxStretchPx, fastDragPx, nearEndPx, viewportHeightPx, listState) {
        object : NestedScrollConnection {
            /** True between the first drag of a gesture and the fling that ends it. */
            private var dragging = false

            /**
             * Whether the drag in progress began with the list already parked against that
             * end, which is what makes a pull from it a collapse rather than a scroll.
             */
            private var topPullEligible = false

            /**
             * Whether this gesture may collapse the overlay.
             *
             * Requiring it to *begin* against the end meant scrolling back to the top and
             * pushing on did nothing, and a collapse only answered on a second, separate
             * drag. What actually needs guarding against is a flick slamming into the end and
             * shrinking the overlay mid-scroll — so a gesture arms if it barely scrolled the
             * list at all, or if it is travelling slowly by the time it gets there. A long
             * fast flick satisfies neither.
             */
            private var pullArmed = false

            /** How far this gesture has scrolled the list, to tell a pull from a flick. */
            private var scrolledInGesture = 0f

            /** The edge spring, held so a finger arriving mid-settle can take it over. */
            private var settleJob: Job? = null

            private fun stretch(delta: Float) {
                // Rubber band: the further it goes, the less each pixel counts.
                val resistance = 1f - (abs(stretchPx) / maxStretchPx).coerceIn(0f, 0.9f)
                stretchPx = (stretchPx + delta * resistance).coerceIn(-maxStretchPx, maxStretchPx)
            }

            /**
             * Runs the edge spring without making the fling wait for it.
             *
             * Awaited inside onPostFling it held the connection in its settling state for the
             * spring's whole duration, and every guard below turns a drag away while that is
             * true — so a flick that coasted into the end swallowed the next pull entirely and
             * the collapse only answered on the swipe after. Launched separately it can simply
             * be cancelled the moment a finger comes back down.
             */
            private fun startSettle(velocity: Float) {
                settleJob?.cancel()
                settleJob = scope.launch { settleStretch(velocity) }
            }

            /** Ends a settle in progress and hands its position back to the finger. */
            private fun takeOverSettle() {
                settleJob?.cancel()
                settleJob = null
                if (stretchSettling) {
                    stretchPx = stretchAnim.value.coerceIn(-maxStretchPx, maxStretchPx)
                    stretchSettling = false
                }
            }

            private suspend fun settleStretch(velocity: Float) {
                val headroom = (1f - abs(stretchPx) / maxStretchPx).coerceIn(0f, 1f)
                stretchSettling = true
                try {
                    stretchAnim.snapTo(stretchPx.coerceIn(-maxStretchPx, maxStretchPx))
                    stretchPx = 0f
                    stretchAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = EDGE_STRETCH_DAMPING,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        // Scaled by what headroom is left before the clamp: seeding a full
                        // fling on top of an already-stretched edge drives the spring past
                        // maxStretchPx, and the draw clamps it there for a few frames — a
                        // flat spot in the middle of the motion, which reads as a hitch.
                        initialVelocity = (velocity * headroom).coerceIn(
                            -maxStretchPx * 12f,
                            maxStretchPx * 12f,
                        ),
                    )
                } finally {
                    // Handing the live value back means an interrupted settle continues from
                    // where it was instead of snapping flat.
                    stretchPx = stretchAnim.value.coerceIn(-maxStretchPx, maxStretchPx)
                    stretchSettling = false
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && !dragging) {
                    dragging = true
                    // Cancellation is not instant, so the state is handed over here and now
                    // rather than a frame later when the coroutine notices.
                    takeOverSettle()
                    // A finger on the list, as opposed to a programmatic scrub scroll.
                    userDragged = true
                    // Scrolling means looking rather than typing, and the keyboard is covering
                    // half of what is being looked at. It stays gone until the field is tapped
                    // again, since coming back on its own is how it got in the way.
                    focusManager.clearFocus()
                    // Collapsing has to be a deliberate pull from rest. Letting a scroll that
                    // merely *arrives* at an end turn into one is what made a fast flick
                    // shrink and fade the whole list halfway through the gesture.
                    //
                    // Only the top collapses. Pulling on at the bottom used to dismiss the
                    // list, which is not what reaching the end of a list and scrolling once more
                    // means — there it just springs against the edge.
                    topPullEligible = !listState.canScrollBackward
                    pullArmed = false
                    scrolledInGesture = 0f
                }
                if (collapsing || stretchSettling) return Offset.Zero
                // The gap a placement opens above the first row closes and stays closed.
                //
                // Only ever this gap, and only while row zero is on screen. There is no
                // matching clamp at the other end: the first letter is left by its heading
                // rising, so refusing the other direction costs nothing, while the last letter
                // is left by its heading descending — refusing that leaves the end of the list
                // with no way out but further in. What the end needs instead is for the room
                // to be taken away once it is behind you, which is done in the layout rather
                // than here.
                //
                // A fling counts as much as a finger. Gated on Drag alone, letting go mid-swipe
                // handed the gap straight back, which is a ratchet that undoes itself.
                // Each half gated on what actually outlives the placement it belongs to.
                //
                // Both used to sit behind highlightRange, which is cleared within a frame of
                // placing anything but the first letter — placementSettled() calls a placement
                // spent the moment row zero is not the one on screen, and above every other
                // letter it never is. So every clamp written for the end of the list has been
                // behind a condition that was already false, which is why none of them did
                // anything at all.
                if (source == NestedScrollSource.Drag || source == NestedScrollSource.Fling) {
                    val info = listState.layoutInfo
                    val first = info.visibleItemsInfo.firstOrNull()
                    if (first != null && first.index == 0 && !highlightRange.isEmpty()) {
                        val gap = (first.offset - info.viewportStartOffset).toFloat()
                        if (gap < gapCeilingPx) gapCeilingPx = gap
                        if (available.y > 0f) {
                            val room = (gapCeilingPx - gap).coerceAtLeast(0f)
                            if (available.y > room) {
                                // Handed to the elastic rather than swallowed, the same as at
                                // the other end. Pulling on past a gap that will not open is
                                // the list being pulled off the top of itself, and that should
                                // mean here what it means down there.
                                val excess = available.y - room
                                val resistance = 1f - (abs(overPull) / maxPullPx).coerceIn(0f, 0.75f)
                                overPull = (overPull + excess * resistance)
                                    .coerceIn(-maxPullPx, maxPullPx)
                                return available
                            }
                        }
                    }
                    val last = info.visibleItemsInfo.lastOrNull()
                    if (bottomRoomLive && last != null && last.index == info.totalItemsCount - 1) {
                        val blank = (info.viewportEndOffset - (last.offset + last.size)).toFloat()
                        if (blank < blankCeilingPx) blankCeilingPx = blank
                        if (available.y < 0f) {
                            val room = (blankCeilingPx - blank).coerceAtLeast(0f)
                            if (-available.y > room) {
                                // Refused, but not swallowed: what the clamp will not spend on
                                // scrolling goes to the edge elastic, the same as running off
                                // the end any other way. Pulling on past the line springs back
                                // rather than closing the list.
                                val excess = available.y + room
                                stretch(excess)
                                return available
                            }
                        }
                    }
                }
                // Spend whatever is outstanding before the list is allowed to move again, so
                // winding a gesture back never has the two running at once.
                if (overPull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -overPull)
                    overPull = (overPull + used).coerceAtLeast(0f)
                    return Offset(0f, used)
                }
                if (overPull < 0f && available.y > 0f) {
                    val used = minOf(available.y, -overPull)
                    overPull = (overPull + used).coerceAtMost(0f)
                    return Offset(0f, used)
                }
                if (stretchPx > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -stretchPx)
                    stretchPx += used
                    return Offset(0f, used)
                }
                if (stretchPx < 0f && available.y > 0f) {
                    val used = minOf(available.y, -stretchPx)
                    stretchPx += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Only a finger stretches an end; a fling that runs out of content is dealt
                // with in onPostFling, where its velocity is still known.
                if (collapsing || stretchSettling || source != NestedScrollSource.Drag) {
                    return Offset.Zero
                }
                scrolledInGesture += abs(consumed.y)
                if (available.y == 0f) return Offset.Zero
                if (scrolledInGesture <= nearEndPx || abs(available.y) <= fastDragPx) pullArmed = true
                // Top only, and only the top: the bottom is an edge to spring against, not a
                // way to close the list. A pull there falls through to the elastic below.
                val pulling = available.y > 0f && (topPullEligible || pullArmed)
                if (pulling) {
                    // Signed: pulled down off the top is positive, pulled up off the bottom is
                    // negative, and every reader below works off the sign rather than a
                    // separate flag for which end is in play.
                    val resistance = 1f - (abs(overPull) / maxPullPx).coerceIn(0f, 0.75f)
                    overPull = (overPull + available.y * resistance).coerceIn(-maxPullPx, maxPullPx)
                    return available
                }
                stretch(available.y)
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                dragging = false
                pullArmed = false
                scrolledInGesture = 0f
                if (collapsing || stretchSettling) return Velocity.Zero
                if (overPull != 0f) {
                    val pulled = overPull
                    val away = if (pulled > 0f) 1f else -1f
                    // Flung on past the threshold counts even when the pull itself is short.
                    val flungAway = available.y * away > 800f
                    val dismissing = abs(pulled) > dismissCommitPx ||
                        (flungAway && abs(pulled) > dismissCommitPx * 0.4f)
                    collapsing = true
                    try {
                        collapseAnim.snapTo(pulled)
                        overPull = 0f
                        if (dismissing) {
                            collapseAnim.animateTo(
                                // Off whichever edge it was heading for.
                                targetValue = viewportHeightPx.toFloat() * away,
                                animationSpec = tween(motionSlow, easing = FastOutLinearInEasing),
                            )
                            currentDismiss()
                        } else {
                            collapseAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            )
                        }
                    } finally {
                        // Also on the way out of a canceled fling — the next gesture landing
                        // on top of this one — or the overlay stays parked halfway down the
                        // screen for good. Nothing of it is on screen by then either way,
                        // because a hidden overlay is measured but never placed.
                        collapsing = false
                    }
                    return available
                }
                if (stretchPx != 0f) {
                    startSettle(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (collapsing || stretchSettling || available.y == 0f) return Velocity.Zero
                startSettle(available.y)
                return available
            }
        }
    }

    // Long-press anywhere in the list (not just favorites) to edit that app.
    var menuForKey by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var editDialogFor by remember { mutableStateOf<AppInfo?>(null) }
    var hideConfirmFor by remember { mutableStateOf<AppInfo?>(null) }
    // Picking apps to tag: a mode of its own, so a tap chooses rather than launches.
    var selecting by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var showTagPicker by remember { mutableStateOf(false) }
    val touchPosition = remember { mutableStateOf(Offset.Zero) }

    // The vertical span the rows actually occupy. A tap inside it belongs to the list even
    // when it misses a label — a section header, the gap under the last app of a letter —
    // and dismissing on those is what made the list feel like it was fighting you.
    fun isOnListContent(y: Float): Boolean {
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return false
        val last = info.visibleItemsInfo.lastOrNull() ?: return false
        return y >= first.offset - info.viewportStartOffset &&
            y < last.offset + last.size - info.viewportStartOffset
    }

    // Same reason as the home screen: the rows inherit the ambient style, so one provider
    // puts the shadow behind every name rather than each call naming it.
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(
            shadow = launcherTextShadow(textShadow, contentColor, textShadowStrength),
        ),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(listConnection)
            .pointerInput(listState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val start = down.position
                    var claimed = false
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) claimed = true
                        val delta = change.position - start
                        if (delta.getDistance() > viewConfiguration.touchSlop) moved = true

                        if (!change.pressed) break
                    }

                    // A tap no row, letter or scroll claimed, landing clear of the list
                    // itself = a tap on the wallpaper.
                    // Taps are in overlay space and the list is in its own; only a field above it
                    // shifts the two apart.
                    val listOffset = if (searchEnabled && !searchAtBottom) searchHeightPx else 0
                    if (claimed || moved || isOnListContent(start.y - listOffset)) return@awaitEachGesture

                    currentDismiss()
                }
            },
    ) {
      Box(
          modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                  val pulled = collapseProvider()
                  val progress = (abs(pulled) / dismissPullPx).coerceIn(0f, 1f)
                  translationY = pulled * 0.6f
                  val scale = 1f - 0.12f * progress
                  scaleX = scale
                  scaleY = scale
                  alpha = 1f - 0.85f * progress
              },
      ) {
        // The keyboard covers the bottom of the overlay, and with a field pinned down there
        // the window used to be panned bodily up to reveal it, taking the top of the results
        // off screen with it. Insetting instead leaves the overlay where it is and gives the
        // list the space that is actually left.
        // Ignoring visibility deliberately: a plain statusBarsPadding follows the bar as it
        // fades, and the inset drops to zero the instant the fade ends, so the whole overlay
        // jumped up into the space. The gap is held by what the setting asks for instead, which
        // does not change while the list is open.
        Column(
            modifier = Modifier
                // Sideways the camera cutout runs down an edge rather than along the top, and
                // the list ran under it. Horizontal sides only, so upright is unchanged.
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                // Capped and held against the strip's own side, so on a wide screen the names
                // stay next to the letters being scrubbed instead of a screen away from them.
                // Tablets and unfolded foldables get a wider cap, or the phone-sized middle
                // strands the strip just as far away.
                .widthIn(
                    max = if (!twoColumn) {
                        MAX_LIST_WIDTH
                    } else if (LocalConfiguration.current.smallestScreenWidthDp >= 700) {
                        MAX_LIST_WIDTH_TABLET
                    } else {
                        MAX_LIST_WIDTH_LANDSCAPE
                    }
                )
                // The capped column sits on the side the rows lean to: left-aligned names on the
                // left, right-aligned on the right, centered centered. It used to follow the
                // strip's own side, which parked a left-aligned list in the middle of a wide
                // screen — the strip is on the opposite side from the alignment.
                .align(
                    when (alignment) {
                        HomeAlignment.LEFT -> Alignment.TopStart
                        HomeAlignment.RIGHT -> Alignment.TopEnd
                        HomeAlignment.CENTER -> Alignment.TopCenter
                    }
                )
                .fillMaxSize()
                .then(
                    if (statusBarHidden) {
                        Modifier
                    } else {
                        Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    }
                )
                .imePadding()
        ) {
        if (searchEnabled && !searchAtBottom) {
            // Pinned above the list rather than scrolling with it as a first item: every row
            // index the scrub placement works from would shift by one, and the field would
            // disappear the moment you scrolled.
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                contentColor = contentColor,
                onGo = {
                    cells.rows
                        .flatMap { it.entries() }
                        .firstOrNull()
                        ?.let { onLaunch(it.app) }
                },
                modifier = Modifier
                    .onSizeChanged { searchHeightPx = it.height }
                    .focusRequester(searchFocus),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = stretchProvider()
                    // The mask below blends against this layer alone, so it can take the
                    // rows' alpha away without touching anything drawn behind them.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // Dissolves the rows at both ends rather than painting black over them.
                    // A painted fade had to be opaque enough to hide text, which left a hard
                    // step wherever its edge met the overlay — glaring against a pinned search
                    // field, and darker still where two of them met. Taking the rows' own
                    // alpha instead leaves the background exactly as it was, so they fade into
                    // it however light or dark the wallpaper dim has made it.
                    val fade = (FADE_HEIGHT.toPx() / size.height).coerceIn(0f, 0.5f)
                    if (fade > 0f) {
                        // Each end fades only while something is actually scrolled past it.
                        // A fade that is always on dims the first and last rows even at rest,
                        // which is what the gap above A was there to hold them clear of.
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 1f - topFade),
                                fade to Color.Black,
                                1f - fade to Color.Black,
                                1f to Color.Black.copy(alpha = 1f - bottomFade),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
            // Room above A and below Z, so every letter lands on the same line — including the
            // last, which used to stop wherever its own content ran out because there was
            // nothing underneath to scroll it up with (#71). The cost is a screen's worth of
            // empty space past the settings row while a letter is placed, which is why it is
            // only there while one is: at rest the list ends where its content does.
            contentPadding = with(density) {
                val top = when {
                    searching -> SEARCH_EDGE_PADDING
                    scrubLetter != null || !highlightRange.isEmpty() -> sectionTopPx.toDp()
                    else -> restingTopPadding
                }
                // Ghost strips: the edge zones scrub invisibly and nothing is
                // drawn beside the rows, so no side is held clear. The price
                // is that a tap landing inside a zone opens the list instead
                // of the row — narrow the zone width in settings if the edge
                // gets grabby, rather than bringing this padding back.
                PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = top,
                    bottom = when {
                        searching -> SEARCH_EDGE_PADDING
                        // Whatever is left of the screen below the line, so the last letters
                        // have something to be scrolled up into.
                        //
                        // Held for as long as the list is open rather than only while a letter
                        // is placed. Added at the moment of placement it was a jump: the scroll
                        // range grew and the rows moved under the scroll that was already
                        // happening, so the end of the alphabet arrived with a lurch while the
                        // start slid. Steady room costs a screen of empty space under the last
                        // app, which is the whole of what this setting is agreeing to.
                        lastLetterToLine && bottomRoomLive ->
                            (viewportHeightPx - sectionTopPx).coerceAtLeast(0).toDp()
                        else -> restingBottomPadding
                    },
                )
            },
        ) {
            if (searching && webSearchFallback && cells.rows.isEmpty()) {
                item(key = "websearch") {
                    val term = query.trim()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onWebSearch(term) }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            stringResource(R.string.applist_search_web, term),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (cells.rows.isEmpty() && !(searching && webSearchFallback)) {
                item(key = "empty") {
                    Text(
                        text = stringResource(
                            if (searching) R.string.applist_empty_search
                            else R.string.applist_empty_list
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                    )
                }
            }

            itemsIndexed(
                items = cells.rows,
                // Headers, singles and pairs lay out nothing alike; telling the list so lets
                // it reuse each kind against its own pool while scrubbing. A pair with an
                // empty half lays out differently from a full one, so it gets its own.
                key = { _, cell -> cellKey(cell) },
                contentType = { _, cell ->
                    when (cell) {
                        is CellRow.Header -> 0
                        is CellRow.One -> 1
                        is CellRow.Two -> if (cell.right == null) 3 else 2
                    }
                },
            ) { index, cell ->
                @Composable
                fun EntryCell(entry: AppListRow.Entry) {
                    AppRow(
                        shortcutSwipe = shortcutSwipe,
                        favoritesEditable = favoritesEditable,
                        contentColor = contentColor,
                        alignment = alignment,
                        iconSide = iconSide,
                        app = entry.app,
                        label = displayName(entry.app),
                        highlightTerm = searchTerm,
                        iconSizeDp = iconSizeDp,
                        labelSizeSp = labelSizeSp,
                        isFavorite = favoriteKeys.contains(entry.app.key),
                        // The row's own key, not the app's: a tagged app stands twice in the
                        // list (its group and its letter), and keying the menu by the app
                        // alone opened both rows' menus stacked on each other.
                        menuExpanded = menuForKey == rowKey(entry),
                        menuOffset = menuOffset,
                        touchPosition = touchPosition,
                        onLaunch = {
                            if (selecting) {
                                selectedKeys = if (entry.app.key in selectedKeys) {
                                    selectedKeys - entry.app.key
                                } else {
                                    selectedKeys + entry.app.key
                                }
                            } else {
                                onLaunch(entry.app)
                            }
                        },
                        selected = selecting && entry.app.key in selectedKeys,
                        // No long-press while picking apps to tag: the row menu on top of the
                        // selection is what turns it into a mess.
                        onLongPress = if (selecting) {
                            null
                        } else {
                            { offset -> menuOffset = offset; menuForKey = rowKey(entry) }
                        },
                        onDismissMenu = { menuForKey = null },
                        onSetFavorite = { onSetFavorite(entry.app, it) },
                        onEdit = { editDialogFor = entry.app },
                        onAppInfo = { onAppInfo(entry.app) },
                        onUninstall = { onUninstall(entry.app) },
                        onUnpin = { onUnpinShortcut(entry.app) },
                        onHide = {
                            // Unhiding is its own confirmation — it puts the app back. Hiding
                            // asks first, because a hidden app is not in the list to be found
                            // again by accident.
                            if (entry.app.key in hiddenApps) onHideApp(entry.app, false)
                            else hideConfirmFor = entry.app
                        },
                        isHidden = entry.app.key in hiddenApps,
                        dimPausedApps = dimPausedApps,
                        isLocked = entry.app.key in lockedApps,
                        onToggleLock = { onToggleLock(entry.app) },
                        onEditTag = { selecting = true; selectedKeys = setOf(entry.app.key) },
                        onMoveToFolder = { onMoveToFolder(entry.app) },
                    )
                }
                Box(
                    // Read in the draw phase on purpose: the scrub fade would otherwise
                    // recompose every visible row on every frame of the 180ms tween.
                    // Placement animation only while searching (its original purpose, rows
                    // sliding as a filter changes): during a scrub every jump would set
                    // every visible row animating toward a position the next jump moves
                    // again, so the list perpetually chases and never lands — which reads
                    // as stutter.
                    modifier = Modifier
                        .then(if (searching) Modifier.animateItem() else Modifier)
                        .graphicsLayer {
                            alpha = if (index in highlightRange) 1f else othersAlpha
                        },
                ) {
                when (cell) {
                    is CellRow.Header -> SectionHeader(cell.text, labelSizeSp, contentColor, alignment)
                    is CellRow.One -> EntryCell(cell.entry)
                    // Sideways two apps share the row, each taking half, so the columns stay
                    // level. A group ending on an odd app leaves the half empty. Pulled
                    // together by one side inset: two rows side by side otherwise double the
                    // gutter in the middle (20 + 8 each) while the outer edges keep one.
                    is CellRow.Two -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(-28.dp),
                    ) {
                        Box(Modifier.weight(1f)) { EntryCell(cell.left) }
                        Box(Modifier.weight(1f)) {
                            cell.right?.let { EntryCell(it) }
                        }
                    }
                }
                }
            }

            // Settings shortcut, pinned after Z. Optional: it is the one row in a list of
            // apps that is not an app, and someone who knows the long press is there has no
            // use for it.
            if (showSettingsRow) item(key = "settings", contentType = "settings") {
                // Set apart from the apps above it: it is the one row here that is not one.
                Spacer(Modifier.height(SETTINGS_ROW_GAP))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = othersAlpha }
                        .clickable(onClick = onOpenSettings)
                        .heightIn(min = MIN_ROW_HEIGHT)
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = when (alignment) {
                        HomeAlignment.LEFT -> Arrangement.Start
                        HomeAlignment.CENTER -> Arrangement.Center
                        HomeAlignment.RIGHT -> Arrangement.End
                    },
                ) {
                    if (alignment == HomeAlignment.RIGHT) {
                        Text(
                            stringResource(R.string.action_open_settings),
                            color = contentColor.copy(alpha = 0.8f),
                            fontSize = labelSizeSp.sp,
                        )
                        Spacer(Modifier.width(16.dp))
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = contentColor.copy(alpha = 0.8f))
                    } else {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = contentColor.copy(alpha = 0.8f))
                        Spacer(Modifier.width(16.dp))
                        Text(
                            stringResource(R.string.action_open_settings),
                            color = contentColor.copy(alpha = 0.8f),
                            fontSize = labelSizeSp.sp,
                        )
                    }
                }
            }
        }
        }

        }
        if (searchEnabled && searchAtBottom) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                contentColor = contentColor,
                onGo = {
                    cells.rows
                        .flatMap { it.entries() }
                        .firstOrNull()
                        ?.let { onLaunch(it.app) }
                },
                atBottom = true,
                modifier = Modifier.focusRequester(searchFocus),
            )
        }
        }

        // The strip lives on the right no matter which edge the gesture came
        // from: the left edge scrubs blind, display and gesture are separate
        // things. It takes no room, so the rows run full-bleed under it.
        if (showAlphabet && !searching) {
            val stripSide = if (alignment == HomeAlignment.RIGHT) EdgeSide.LEFT else EdgeSide.RIGHT
            AlphabetSpyStrip(
                cells = cells,
                scrub = scrub,
                listState = listState,
                scrubY = scrubY,
                pullPx = pullPx,
                band = band,
                stripSide = stripSide,
                modifier = Modifier.align(
                    if (stripSide == EdgeSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                ),
            )
        }

        // While picking apps to tag, a bar says how many and offers the tag picker, and a FAB
        // does the same at the thumb's end of the screen.
        if (selecting) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                // Below the status bar rather than under it: parked at the very top it
                // collided with the clock. The inset is zero while the bar is hidden.
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selecting = false; selectedKeys = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                    Text(
                        stringResource(R.string.tag_selected_count, selectedKeys.size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showTagPicker = true }) {
                        Text(stringResource(R.string.action_set_tag))
                    }
                }
            }
            FloatingActionButton(
                onClick = { showTagPicker = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_set_tag))
            }
        }

        editDialogFor?.let { target ->
            EditAppDialog(
                currentName = displayName(target),
                onConfirmName = { name -> onSetName(target, name); editDialogFor = null },
                onChangeIcon = { name ->
                    if (name.trim() != displayName(target)) onSetName(target, name.trim())
                    onChangeIcon(target)
                    editDialogFor = null
                },
                onDismiss = { editDialogFor = null },
            )
        }

        if (showTagPicker) {
            val selectedApps = cells.rows
                .flatMap { it.entries() }
                .map { it.app }
                .filter { it.key in selectedKeys }
                .distinctBy { it.key }
            // What the chosen apps already agree on — for a single app, all of its tags. Shown
            // checked, so confirming keeps them and unchecking removes them. The auto tag is
            // never offered: it belongs to the usage counter, and handing it out by hand
            // would fight it.
            val commonTags = selectedApps
                .map { appTags[it.key] ?: emptySet<String>() }
                .reduceOrNull { a, b -> a intersect b }
                ?.minus(reservedTags)
                ?: emptySet()
            // Whatever reserved tags the chosen apps already carry stay put: the picker can
            // neither show nor remove them, so confirming must not drop them either.
            val reservedKept = selectedApps
                .flatMapTo(mutableSetOf()) { appTags[it.key] ?: emptySet() }
                .intersect(reservedTags)
            TagPickerDialog(
                allTags = allTags - reservedTags,
                initialChecked = commonTags,
                onConfirm = { tags ->
                    val blocked = tags.intersect(reservedTags)
                    if (blocked.isNotEmpty()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.tag_reserved, blocked.first()),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    onAddTags(selectedApps, tags - reservedTags + reservedKept)
                    showTagPicker = false
                    selecting = false
                    selectedKeys = emptySet()
                },
                onDismiss = { showTagPicker = false },
            )
        }

        hideConfirmFor?.let { target ->
            AlertDialog(
                onDismissRequest = { hideConfirmFor = null },
                title = { Text(stringResource(R.string.applist_hide)) },
                text = { Text(stringResource(R.string.hide_confirm_message, displayName(target))) },
                confirmButton = {
                    TextButton(onClick = { onHideApp(target, true); hideConfirmFor = null }) {
                        Text(stringResource(R.string.applist_hide))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { hideConfirmFor = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        // Bubble for the current letter, dragged out from the strip and springing back.
        if (scrubLetter != null) {
            val bubble = 72.dp
            val halfPx = with(density) { (bubble / 2).toPx() }
            val insetPx = with(density) { SCRUB_BUBBLE_INSET_DP.dp.toPx() }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .align(if (activeSide == EdgeSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .offset {
                        val x = insetPx + pullPx()
                        IntOffset(
                            x = if (activeSide == EdgeSide.LEFT) x.roundToInt() else -x.roundToInt(),
                            y = ((scrubY() ?: 0f) - halfPx).roundToInt(),
                        )
                    }
                    .size(bubble),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        scrubLetter.toString(),
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
      }
    }
    }
}

/**
 * Choose the tags to put on the selected apps: the ones already in use, plus a field for a new
 * one. Additive — an app keeps the tags it already had, and one app can carry several.
 */
@Composable
private fun TagPickerDialog(
    allTags: List<String>,
    initialChecked: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = remember { mutableStateListOf<String>().apply { addAll(initialChecked) } }
    var newTag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_set_tag)) },
        text = {
            Column {
                allTags.forEach { tag ->
                    val on = tag in chosen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (on) chosen.remove(tag) else chosen.add(tag) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = { if (on) chosen.remove(tag) else chosen.add(tag) },
                        )
                        Text(tag, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tag_new)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val extra = newTag.trim()
                onConfirm(chosen.toSet() + if (extra.isNotEmpty()) setOf(extra) else emptySet())
            }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SectionHeader(text: String, labelSizeSp: Int, contentColor: Color, alignment: HomeAlignment) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        contentAlignment = when (alignment) {
            HomeAlignment.LEFT -> Alignment.CenterStart
            HomeAlignment.CENTER -> Alignment.Center
            HomeAlignment.RIGHT -> Alignment.CenterEnd
        },
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = (labelSizeSp + 2).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun AppRow(
    shortcutSwipe: ShortcutSwipe,
    /** False when the favorites are computed, so starring would write to a list nobody sees. */
    favoritesEditable: Boolean,
    contentColor: Color,
    alignment: HomeAlignment,
    iconSide: IconSide,
    touchPosition: MutableState<Offset>,
    app: AppInfo,
    label: String,
    /** The live query, so the stretch it matched can be marked. Empty when not searching. */
    highlightTerm: String,
    iconSizeDp: Int,
    labelSizeSp: Int,
    isFavorite: Boolean,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    onLaunch: () -> Unit,
    onLongPress: ((DpOffset) -> Unit)?,
    onDismissMenu: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onUnpin: () -> Unit,
    onHide: () -> Unit,
    onMoveToFolder: () -> Unit,
    /** Hidden apps only ever reach this list through a search. */
    isHidden: Boolean,
    /** Whether a paused (suspended) app is shown greyed out. */
    dimPausedApps: Boolean,
    /** Whether this app asks for the device lock before it opens. */
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onEditTag: () -> Unit,
    /** True while picking apps to tag and this one is picked. */
    selected: Boolean,
) {
    // Same press treatment as the home screen: the stock ripple all but vanishes against a
    // wallpaper, and without any feedback a tap that did register reads as one that didn't.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current
    var shortcutMenu by remember { mutableStateOf(false) }
    // A paused app fades the way a hidden one does, when the setting asks; the icon goes with
    // the name so the whole row reads as set aside.
    val dimmed = isHidden || app.disabled || (dimPausedApps && app.suspended)

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Ahead of the inset, so the long-press menu is still placed against the
                // whole row rather than 20dp to the left of the finger.
                .recordTouchPosition(touchPosition)
                .swipeForShortcuts(shortcutSwipe, app.kind == EntryKind.APP) {
                    shortcutMenu = true
                }
                .padding(horizontal = 20.dp)
                .background(
                    color = when {
                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        pressed -> contentColor.copy(alpha = 0.15f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(18.dp),
                )
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onLaunch,
                    onLongClick = onLongPress?.let { lp ->
                        {
                            lp(
                                with(density) {
                                    DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                                }
                            )
                        }
                    },
                )
                // The whole row is the target, not the label: at small icon sizes the strip
                // left to tap was thinner than a fingertip.
                .heightIn(min = MIN_ROW_HEIGHT)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = when (alignment) {
                HomeAlignment.LEFT -> Arrangement.Start
                HomeAlignment.CENTER -> Arrangement.Center
                HomeAlignment.RIGHT -> Arrangement.End
            },
        ) {
            val gap = if (LocalIconConfig.current.showIcons) 16.dp else 0.dp
            // fill = false so the label takes only the width it needs and the icon stays
            // beside it; a plain weight spans the row and pins the icon to the far edge.
            val labelModifier = Modifier.weight(1f, fill = false)
            val matched = remember(label, highlightTerm) {
                if (highlightTerm.isEmpty()) null else PinyinSearch.highlight(label, highlightTerm)
            }
            val text: @Composable () -> Unit = {
                // The name, with whatever the query matched picked out. A pinyin hit marks the
                // characters it was read from, not the letters that were typed — typing "wx"
                // underlines 微信 rather than leaving the row looking unmatched.
                val shown = if (matched == null) {
                    AnnotatedString(label)
                } else {
                    val mark = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    buildAnnotatedString {
                        append(label.substring(0, matched.first))
                        withStyle(mark) { append(label.substring(matched.first, matched.last + 1)) }
                        append(label.substring(matched.last + 1))
                    }
                }
                Row(
                    modifier = labelModifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        shown,
                        // Dimmed, because a hidden app only ever turns up here through a search
                        // and nothing else on the row says it is one. A shortcut its publisher
                        // has switched off gets the same treatment, for the same reason: it is
                        // listed, but tapping it only explains why it will not open.
                        color = if (dimmed) contentColor.copy(alpha = 0.5f) else contentColor,
                        fontSize = labelSizeSp.sp,
                        modifier = Modifier.weight(1f, fill = false),
                        textAlign = when (alignment) {
                            HomeAlignment.LEFT -> TextAlign.Start
                            HomeAlignment.CENTER -> TextAlign.Center
                            HomeAlignment.RIGHT -> TextAlign.End
                        },
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
            // Centerd rows balance the icon with a spacer on the label's far side, so the
            // text lands on the screen's center line rather than the pair straddling it.
            val showIcons = LocalIconConfig.current.showIcons
            val balance: @Composable () -> Unit = {
                if (alignment == HomeAlignment.CENTER && showIcons) {
                    Spacer(Modifier.width(iconSizeDp.dp + gap))
                }
            }
            // A right-aligned row always puts its icon on the right, same as the home
            // screen: the name ending at the screen edge reads as the row running out
            // of room. The side setting still decides everywhere else.
            val side = if (alignment == HomeAlignment.RIGHT) IconSide.RIGHT else iconSide
            val iconModifier = Modifier
                .pressPop(interaction)
                .then(if (dimmed) Modifier.graphicsLayer { alpha = 0.5f } else Modifier)
            if (side == IconSide.RIGHT) {
                balance()
                text()
                Spacer(Modifier.width(gap))
                AppIcon(app = app, sizeDp = iconSizeDp, modifier = iconModifier)
            } else {
                AppIcon(app = app, sizeDp = iconSizeDp, modifier = iconModifier)
                Spacer(Modifier.width(gap))
                text()
                balance()
            }
        }

        AppShortcutMenu(app, shortcutMenu, labelSizeSp) { shortcutMenu = false }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu, offset = menuOffset) {
            // Not offered while the favorites keep themselves. Starring an app wrote to a list
            // nothing was drawing, so the app never appeared and there was nothing on screen to
            // say why — which is what #77 reported as favorites being broken.
            if (favoritesEditable) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (isFavorite) R.string.applist_remove_favorite else R.string.applist_add_favorite)) },
                    leadingIcon = {
                        Icon(
                            if (isFavorite) DelugeIcons.StarBorder else Icons.Filled.Star,
                            contentDescription = null,
                        )
                    },
                    onClick = { onDismissMenu(); onSetFavorite(!isFavorite) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                leadingIcon = { Icon(DelugeIcons.Tune, contentDescription = null) },
                onClick = { onDismissMenu(); onEdit() },
            )
            // Only an installed app has a settings screen to open; a row that stands for
            // something else — a shortcut, the private space — would send the system looking
            // for a package that is not there.

            if (app.kind == EntryKind.APP) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_app_info)) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = { onDismissMenu(); onAppInfo() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_uninstall)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { onDismissMenu(); onUninstall() },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (isLocked) R.string.action_unlock_app else R.string.action_lock_app
                            )
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    onClick = { onDismissMenu(); onToggleLock() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_set_tag)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { onDismissMenu(); onEditTag() },
                )
            }
            // Hands the shortcut back to the app that pinned it, which is the only way one
            // ever leaves: nothing uninstalls a shortcut.
            if (app.kind == EntryKind.SHORTCUT) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove_shortcut)) },
                    leadingIcon = { Icon(DelugeIcons.LinkOff, contentDescription = null) },
                    onClick = { onDismissMenu(); onUnpin() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move_to_folder)) },
                leadingIcon = { Icon(DelugeIcons.Folder, contentDescription = null) },
                onClick = { onDismissMenu(); onMoveToFolder() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isHidden) R.string.applist_unhide else R.string.applist_hide
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        if (isHidden) DelugeIcons.Visibility else DelugeIcons.VisibilityOff,
                        contentDescription = null,
                    )
                },
                onClick = { onDismissMenu(); onHide() },
            )
        }
    }
}

/**
 * The A-Z strip with a scroll spy: while nobody is scrubbing, every letter with a row
 * on screen is drawn enlarged — scrolling a short alphabet parks several letters in
 * view at once, and one lit letter would be the wrong answer for most of them.
 * Isolated in its own composable so following the scroll position recomposes only the
 * strip, never the list around it.
 */
@Composable
private fun AlphabetSpyStrip(
    cells: CellModel,
    scrub: ScrubState,
    listState: LazyListState,
    scrubY: () -> Float?,
    pullPx: () -> Float,
    band: ScrubBand,
    /** Which side the strip is drawn on: the one the rows are not aligned against. */
    stripSide: EdgeSide,
    modifier: Modifier = Modifier,
) {
    val scrubLetter = scrub.letter
    val end = remember(cells) { cells.alphabetEnd() }
    fun letterAt(i: Int): String? =
        if (i < 0 || i >= end) null
        else cells.letterIndex.lastOrNull { it.second <= i }?.first
    // Indices only, never offsets: offsets change per pixel and must not recompose
    // anything. The calculation re-runs per frame while scrolling but only notifies
    // when the letter set actually changes.
    val visibleLetters by remember(cells) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNullTo(mutableSetOf()) { letterAt(it.index) }
        }
    }
    EdgeScrubber(
        letters = cells.letters,
        scrubY = scrubY,
        pullPx = pullPx,
        band = band,
        // Beside the empty end of the row, not on top of the names: rows aligned right
        // leave their left side free, so that is where the strip goes.
        side = stripSide,
        spotlights = scrubLetter?.let { setOf(it) } ?: visibleLetters,
        modifier = modifier,
    )
}

/** The app list's own search box, styled against the wallpaper rather than a surface. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    contentColor: Color,
    onGo: () -> Unit,
    atBottom: Boolean = false,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        // The key is already there and already means this; asking is the long way round to the
        // one app you have just finished spelling. Nothing happens when nothing matched.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        placeholder = { Text(stringResource(R.string.applist_search), color = contentColor.copy(alpha = 0.6f)) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = contentColor.copy(alpha = 0.7f))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.icon_picker_clear_search),
                        tint = contentColor.copy(alpha = 0.7f),
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = contentColor,
            unfocusedTextColor = contentColor,
            cursorColor = contentColor,
            focusedBorderColor = contentColor.copy(alpha = 0.5f),
            unfocusedBorderColor = contentColor.copy(alpha = 0.3f),
            // No fill of its own. A fixed black tint read as a separate panel laid over the
            // overlay, and how separate depended on the wallpaper dim underneath it: barely
            // visible undimmed, a slab of black at full dim. Transparent composites to
            // exactly the overlay's own background at every dim, so the outline and the icons
            // are what say this is a field.
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        modifier = modifier
            // The overlay draws under both system bars, so without this the field sits behind
            // the clock at the top, or the gesture pill at the bottom.
            .then(
                if (atBottom) {
                    // The column's own ime padding has already cleared the keyboard, so
                    // adding the gesture bar on top of it would double the gap.
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
                } else {
                    // The status bar is the column's problem now, so that the rows clear it too.
                    Modifier
                }
            )
            .fillMaxWidth()
            .padding(
                // Ghost strips take no room, so the field just keeps a thumb
                // off either edge like the rows do.
                start = 8.dp,
                end = 8.dp,
                top = if (atBottom) 8.dp else 12.dp,
                bottom = if (atBottom) 12.dp else 8.dp,
            ),
    )
}
