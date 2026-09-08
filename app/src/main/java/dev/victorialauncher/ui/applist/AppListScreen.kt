// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.ui.common.recordTouchPosition
import dev.victorialauncher.ui.common.EditAppDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

/** Where the selected letter's section sits, as a fraction down the screen. */
private const val SECTION_TOP_FRACTION = 0.26f

/** How long a background tap waits for a second one before it dismisses the list. */
private const val DOUBLE_TAP_WINDOW_MS = 280L

/** Breathing room above A and below the settings row when no scrub has placed the list. */
private val IDLE_TOP_PADDING = 64.dp
private val IDLE_BOTTOM_PADDING = 32.dp

/** Smallest comfortable row, so a tap beside a small icon still lands on its app. */
private val MIN_ROW_HEIGHT = 48.dp

/** How far either end of the list may be dragged past its content. */
private val MAX_EDGE_STRETCH = 40.dp

/**
 * Damping for the edge elastic. Under 1 so a fling into an end overshoots and comes back
 * once — a bumper, not a bounce.
 */
private const val EDGE_STRETCH_DAMPING = 0.55f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListScreen(
    model: AppListModel,
    nameOverrides: Map<String, String>,
    scrub: ScrubState,
    dimAlpha: Float,
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
    onHideApp: (AppInfo) -> Unit,
    onMoveToFolder: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    contentColor: Color,
    showAlphabet: Boolean,
    alignRight: Boolean,
    doubleTapToLock: Boolean,
    onDoubleTapLock: () -> Unit,
) {
    fun displayName(app: AppInfo) = nameOverrides[app.key] ?: app.label

    // The gesture handlers below outlive the composition that created them, so they must not
    // capture this frame's callbacks — a dismiss half a minute old still has to close the
    // list that is up now.
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentDoubleTapLock by rememberUpdatedState(onDoubleTapLock)

    // Reading these here confines the invalidation to this composable: the home screen
    // behind the overlay never sees the letter change. currentY/currentPull stay as
    // function references so their callers read them in the draw phase, not composition.
    val scrubLetter = scrub.letter
    val scrubbing = scrub.scrubbing
    val activeSide = scrub.side
    val scrubY = remember(scrub) { scrub::currentY }
    val pullPx = remember(scrub) { scrub::currentPull }

    val listState = rememberLazyListState()
    // Rows outside the scrubbed letter fade out; the section itself never moves, because it
    // is the same list the whole time. Only ever read inside a graphicsLayer, so the fade
    // runs in the draw phase instead of recomposing every visible row 60 times a second.
    val othersAlpha by animateFloatAsState(
        // Only while a finger is travelling through the alphabet: a tap on the edge sets a
        // letter too, and fading out for it cost a quarter of a second of ghosted list on
        // every open.
        targetValue = if (scrubLetter != null && scrubbing) 0f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "othersAlpha",
    )
    val sectionTopPx = (viewportHeightPx * SECTION_TOP_FRACTION).roundToInt()

    // The LazyColumn always holds the full list — while scrubbing it's just hidden and
    // pre-scrolled, with the letter's apps drawn over the top. Filtering the rows themselves
    // meant that on release the unfiltered list was briefly parked back at A.
    val scrubRowIndex = remember(model, scrubLetter) {
        val letter = scrubLetter ?: return@remember -1
        model.letterIndex.firstOrNull { it.first == letter }?.second ?: -1
    }

    // Row indices of the highlighted section. Applied *after* the scroll lands, otherwise
    // the new letter lights up a frame before the list moves to it — that was the jitter.
    var highlightRange by remember { mutableStateOf(IntRange.EMPTY) }
    // Height of the highlighted section, so the tail below the list is only as long as that
    // section needs to reach the line — not a blanket screenful.
    var highlightHeightPx by remember { mutableIntStateOf(0) }

    // Dismissing on the first tap is what made double-tap-to-lock unreachable: the overlay
    // stops receiving touches the moment it hides, so the second tap never arrived. With the
    // setting on, the dismiss waits out the double-tap window and a second tap cancels it.
    var pendingDismiss by remember { mutableStateOf<Job?>(null) }

    // Pull-to-collapse, done the way pull-to-refresh is done: one nested-scroll connection
    // that actually *consumes* the drag.
    //
    // The previous versions watched the raw pointer stream without consuming, so the list
    // scrolled and the overlay tracked the pull at the same time, and reversing direction
    // left the two disagreeing. Consuming means the list can't scroll while there's a pull
    // outstanding, and winding back up spends the pull before the list moves again — so the
    // gesture is always in exactly one state.
    val density = LocalDensity.current
    val dismissPullPx = with(density) { 150.dp.toPx() }
    val maxPullPx = with(density) { 320.dp.toPx() }
    val maxStretchPx = with(density) { MAX_EDGE_STRETCH.toPx() }
    val idleTopPaddingPx = with(density) { IDLE_TOP_PADDING.roundToPx() }

    var overPull by remember { mutableFloatStateOf(0f) }
    val collapseAnim = remember { Animatable(0f) }
    var collapsing by remember { mutableStateOf(false) }
    val collapseProvider: () -> Float = { if (collapsing) collapseAnim.value else overPull }

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
    LaunchedEffect(visible) {
        if (!visible) {
            pendingDismiss?.cancel()
            pendingDismiss = null
            highlightRange = IntRange.EMPTY
            highlightHeightPx = 0
            overPull = 0f
            stretchPx = 0f
            collapsing = false
            listState.scrollToItem(0)
        }
    }

    // A manual scroll means the scrub placement has served its purpose, so the alignment
    // padding can go. It is retired at the *start* of that first drag, while the list is
    // still parked where the scrub left it and so has room in both directions to absorb the
    // change. Waiting for the scroll to settle instead means doing it against whichever end
    // the flick landed on, where the compensating scroll has nowhere to go and the shift
    // lands on screen as a hitch; deferring past that end leaves the placement padding in
    // place, which reads as the list overscrolling into a screenful of nothing.
    var userDragged by remember { mutableStateOf(false) }
    LaunchedEffect(userDragged, scrubLetter) {
        if (!userDragged || scrubLetter != null || highlightRange.isEmpty()) {
            return@LaunchedEffect
        }
        // The top padding falls from the scrub line back to the idle gap, so that is exactly
        // how far the content rises — compensating by anything else (this used to use a bare
        // 8dp) leaves the list jumping by the difference.
        val shrinkBy = (sectionTopPx - idleTopPaddingPx).toFloat()
        highlightRange = IntRange.EMPTY
        highlightHeightPx = 0
        // dispatchRawDelta rather than scrollBy: the drag that triggered this holds the
        // scroll mutex at UserInput priority, and a scrollBy would just be cancelled by it.
        if (shrinkBy > 0f) listState.dispatchRawDelta(-shrinkBy)
    }

    LaunchedEffect(scrubRowIndex, model) {
        if (scrubRowIndex < 0) return@LaunchedEffect
        listState.scrollToItem(scrubRowIndex)
        // The next letter's header ends this section. Walking the rows to find it copied the
        // whole tail of the list on every one of the ~26 letter changes in a gesture.
        val end = model.letterIndex.firstOrNull { it.second > scrubRowIndex }?.second ?: model.rows.size
        highlightRange = scrubRowIndex until end
        highlightHeightPx = listState.layoutInfo.visibleItemsInfo
            .sumOf { if (it.index in highlightRange) it.size else 0 }
        // This placement is fresh, so the next drag is the one that retires it.
        userDragged = false
    }

    val scope = rememberCoroutineScope()

    val listConnection = remember(dismissPullPx, maxPullPx, maxStretchPx, viewportHeightPx, listState) {
        object : NestedScrollConnection {
            /** True between the first drag of a gesture and the fling that ends it. */
            private var dragging = false

            /** Whether the drag in progress began with the list already parked at the top. */
            private var pullEligible = false

            private fun stretch(delta: Float) {
                // Rubber band: the further it goes, the less each pixel counts.
                val resistance = 1f - (abs(stretchPx) / maxStretchPx).coerceIn(0f, 0.9f)
                stretchPx = (stretchPx + delta * resistance).coerceIn(-maxStretchPx, maxStretchPx)
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
                    // A finger on the list, as opposed to a programmatic scrub scroll.
                    userDragged = true
                    // Collapsing has to be a deliberate pull from rest. Letting a scroll that
                    // merely *arrives* at the top turn into one is what made a fast flick
                    // shrink and fade the whole list halfway through the gesture.
                    pullEligible = !listState.canScrollBackward
                }
                if (collapsing || stretchSettling) return Offset.Zero
                // Spend whatever is outstanding before the list is allowed to move again, so
                // winding a gesture back never has the two running at once.
                if (overPull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -overPull)
                    overPull = (overPull + used).coerceAtLeast(0f)
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
                if (available.y == 0f) return Offset.Zero
                if (available.y > 0f && pullEligible) {
                    val resistance = 1f - (overPull / maxPullPx).coerceIn(0f, 0.75f)
                    overPull = (overPull + available.y * resistance).coerceIn(0f, maxPullPx)
                    return available
                }
                stretch(available.y)
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                dragging = false
                if (collapsing || stretchSettling) return Velocity.Zero
                if (overPull > 0f) {
                    val pulled = overPull
                    val flungDown = available.y > 800f
                    val dismissing = pulled > dismissPullPx ||
                        (flungDown && pulled > dismissPullPx / 3f)
                    collapsing = true
                    try {
                        collapseAnim.snapTo(pulled)
                        overPull = 0f
                        if (dismissing) {
                            collapseAnim.animateTo(
                                targetValue = viewportHeightPx.toFloat(),
                                animationSpec = tween(240, easing = FastOutLinearInEasing),
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
                        // Also on the way out of a cancelled fling — the next gesture landing
                        // on top of this one — or the overlay stays parked halfway down the
                        // screen for good. Nothing of it is on screen by then either way,
                        // because a hidden overlay is measured but never placed.
                        collapsing = false
                    }
                    return available
                }
                if (stretchPx != 0f) {
                    settleStretch(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (collapsing || stretchSettling || available.y == 0f) return Velocity.Zero
                settleStretch(available.y)
                return available
            }
        }
    }

    // Long-press anywhere in the list (not just favorites) to edit that app.
    var menuForKey by remember { mutableStateOf<String?>(null) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var editDialogFor by remember { mutableStateOf<AppInfo?>(null) }
    val touchPosition = remember { mutableStateOf(Offset.Zero) }

    // The vertical span the rows actually occupy. A tap inside it belongs to the list even
    // when it misses a label — a section header, the gap under the last app of a letter —
    // and dismissing on those is what made the list feel like it was fighting you.
    fun isOnListContent(y: Float): Boolean {
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return false
        val last = info.visibleItemsInfo.last()
        return y >= first.offset - info.viewportStartOffset &&
            y < last.offset + last.size - info.viewportStartOffset
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(listConnection)
            .pointerInput(doubleTapToLock, listState) {
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
                    if (claimed || moved || isOnListContent(start.y)) return@awaitEachGesture

                    val pending = pendingDismiss
                    when {
                        pending != null -> {
                            pending.cancel()
                            pendingDismiss = null
                            currentDoubleTapLock()
                        }

                        doubleTapToLock -> {
                            pendingDismiss = scope.launch {
                                delay(DOUBLE_TAP_WINDOW_MS)
                                pendingDismiss = null
                                currentDismiss()
                            }
                        }

                        // Off by default, so the common case keeps dismissing instantly.
                        else -> currentDismiss()
                    }
                }
            },
    ) {
      Box(
          modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                  val pulled = collapseProvider()
                  val progress = (pulled / dismissPullPx).coerceIn(0f, 1f)
                  translationY = pulled * 0.6f
                  val scale = 1f - 0.12f * progress
                  scaleX = scale
                  scaleY = scale
                  alpha = 1f - 0.85f * progress
              }
              .background(Color.Black.copy(alpha = dimAlpha)),
      ) {
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = stretchProvider() },
            // Room above A and below Z so any letter can sit on the same line; without it
            // the ends clamp and land somewhere else entirely.
            contentPadding = with(density) {
                val needsTail = scrubLetter != null || !highlightRange.isEmpty()
                if (needsTail) {
                    val tailPx = (viewportHeightPx - sectionTopPx - highlightHeightPx).coerceAtLeast(96)
                    PaddingValues(top = sectionTopPx.toDp(), bottom = tailPx.toDp())
                } else {
                    PaddingValues(top = IDLE_TOP_PADDING, bottom = IDLE_BOTTOM_PADDING)
                }
            },
        ) {
            itemsIndexed(
                items = model.rows,
                key = { _, row ->
                    when (row) {
                        is AppListRow.Header -> "header:${row.text}"
                        is AppListRow.Entry -> row.app.key
                    }
                },
                // Headers and app rows are laid out nothing alike; telling the list so lets
                // it reuse each kind against its own pool while scrubbing.
                contentType = { _, row -> row is AppListRow.Header },
            ) { index, row ->
                Box(
                    // Read in the draw phase on purpose: the scrub fade would otherwise
                    // recompose every visible row on every frame of the 180ms tween.
                    modifier = Modifier.graphicsLayer {
                        alpha = if (index in highlightRange) 1f else othersAlpha
                    },
                ) {
                when (row) {
                    is AppListRow.Header -> SectionHeader(row.text, labelSizeSp, contentColor, alignRight)
                    is AppListRow.Entry -> AppRow(
                        contentColor = contentColor,
                        alignRight = alignRight,
                        app = row.app,
                        label = displayName(row.app),
                        iconSizeDp = iconSizeDp,
                        labelSizeSp = labelSizeSp,
                        isFavorite = favoriteKeys.contains(row.app.key),
                        menuExpanded = menuForKey == row.app.key,
                        menuOffset = menuOffset,
                        touchPosition = touchPosition,
                        onLaunch = { onLaunch(row.app) },
                        onLongPress = { offset -> menuOffset = offset; menuForKey = row.app.key },
                        onDismissMenu = { menuForKey = null },
                        onSetFavorite = { onSetFavorite(row.app, it) },
                        onEdit = { editDialogFor = row.app },
                        onAppInfo = { onAppInfo(row.app) },
                        onHide = { onHideApp(row.app) },
                        onMoveToFolder = { onMoveToFolder(row.app) },
                    )
                }
                }
            }

            // Settings shortcut, pinned after Z.
            item(key = "settings", contentType = "settings") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = othersAlpha }
                        .clickable(onClick = onOpenSettings)
                        .heightIn(min = MIN_ROW_HEIGHT)
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (alignRight) Arrangement.End else Arrangement.Start,
                ) {
                    if (alignRight) {
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

        // Fade the list out as it scrolls off the top.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                    )
                ),
        )

        if (showAlphabet) {
            EdgeScrubber(
                letters = model.letters,
                scrubY = scrubY,
                pullPx = pullPx,
                band = band,
                side = activeSide,
                modifier = Modifier.align(
                    if (activeSide == EdgeSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                ),
            )
        }

        editDialogFor?.let { target ->
            EditAppDialog(
                currentName = displayName(target),
                onConfirmName = { name -> onSetName(target, name); editDialogFor = null },
                onChangeIcon = { onChangeIcon(target); editDialogFor = null },
                onDismiss = { editDialogFor = null },
            )
        }

        // Bubble for the current letter, dragged out from the strip and springing back.
        if (scrubLetter != null) {
            val bubble = 72.dp
            val halfPx = with(density) { (bubble / 2).toPx() }
            val insetPx = with(density) { 122.dp.toPx() }
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

@Composable
private fun SectionHeader(text: String, labelSizeSp: Int, contentColor: Color, alignRight: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        contentAlignment = if (alignRight) Alignment.CenterEnd else Alignment.CenterStart,
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
    contentColor: Color,
    alignRight: Boolean,
    touchPosition: MutableState<Offset>,
    app: AppInfo,
    label: String,
    iconSizeDp: Int,
    labelSizeSp: Int,
    isFavorite: Boolean,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    onLaunch: () -> Unit,
    onLongPress: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onAppInfo: () -> Unit,
    onHide: () -> Unit,
    onMoveToFolder: () -> Unit,
) {
    // Same press treatment as the home screen: the stock ripple all but vanishes against a
    // wallpaper, and without any feedback a tap that did register reads as one that didn't.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Ahead of the inset, so the long-press menu is still placed against the
                // whole row rather than 20dp to the left of the finger.
                .recordTouchPosition(touchPosition)
                .padding(horizontal = 20.dp)
                .background(
                    color = if (pressed) contentColor.copy(alpha = 0.15f) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                )
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onLaunch,
                    onLongClick = {
                        onLongPress(
                            with(density) {
                                DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                            }
                        )
                    },
                )
                // The whole row is the target, not the label: at small icon sizes the strip
                // left to tap was thinner than a fingertip.
                .heightIn(min = MIN_ROW_HEIGHT)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (alignRight) {
                Text(
                    label,
                    color = contentColor,
                    fontSize = labelSizeSp.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
                Spacer(Modifier.width(16.dp))
                AppIcon(app = app, sizeDp = iconSizeDp)
            } else {
                AppIcon(app = app, sizeDp = iconSizeDp)
                Spacer(Modifier.width(16.dp))
                Text(label, color = contentColor, fontSize = labelSizeSp.sp, modifier = Modifier.weight(1f))
            }
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu, offset = menuOffset) {
            DropdownMenuItem(
                text = { Text(stringResource(if (isFavorite) R.string.applist_remove_favorite else R.string.applist_add_favorite)) },
                leadingIcon = {
                    Icon(
                        if (isFavorite) Icons.Filled.StarBorder else Icons.Filled.Star,
                        contentDescription = null,
                    )
                },
                onClick = { onDismissMenu(); onSetFavorite(!isFavorite) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                onClick = { onDismissMenu(); onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_app_info)) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = { onDismissMenu(); onAppInfo() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move_to_folder)) },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                onClick = { onDismissMenu(); onMoveToFolder() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.applist_hide)) },
                leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                onClick = { onDismissMenu(); onHide() },
            )
        }
    }
}
