// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.Folder
import dev.victorialauncher.data.QuickLaunchSlot
import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.data.HomePaddings
import dev.victorialauncher.data.folderToken
import dev.victorialauncher.data.PaddingSlot
import dev.victorialauncher.media.NowPlayingWidget
import dev.victorialauncher.media.openNowPlayingApp
import dev.victorialauncher.service.HapticUtil
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.ui.common.TouchAnchoredMenu
import dev.victorialauncher.ui.common.LocalIconConfig
import dev.victorialauncher.ui.common.EditAppDialog
import dev.victorialauncher.ui.common.FolderIconImage
import dev.victorialauncher.ui.common.recordTouchPosition
import dev.victorialauncher.widget.WidgetSlot
import dev.victorialauncher.widget.WidgetSlotActions
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.victorialauncher.R
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
    favorites: List<FavoriteEntry>,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    labelSizeSp: Int,
    itemSpacingDp: Int,
    sidePaddingDp: Int,
    widgetSidePaddingDp: Int,
    onSetSidePadding: (Int) -> Unit,
    onSetWidgetSidePadding: (Int) -> Unit,
    paddings: HomePaddings,
    widgetIds: List<Int>,
    widgetPosition: Int,
    widgetHeightDp: Int,
    hapticsEnabled: Boolean,
    nowPlayingEnabled: Boolean,
    nowPlayingHeightDp: Int,
    onResizeNowPlaying: (Int) -> Unit,
    widgetActions: WidgetSlotActions,
    onLaunch: (AppInfo) -> Unit,
    onRemoveFavorite: (AppInfo) -> Unit,
    onOpenFolderApp: (AppInfo) -> Unit,
    onRenameFolder: (Folder, String) -> Unit,
    onChangeFolderIcon: (Folder) -> Unit,
    onResetFolderIcon: (Folder) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onManageFolder: (Folder) -> Unit,
    onMoveToFolder: (AppInfo) -> Unit,
    onRemoveFromFolder: (Folder, AppInfo) -> Unit,
    appsByKey: Map<String, AppInfo>,
    onReorderHome: (newFavoriteKeys: List<String>, newWidgetPosition: Int) -> Unit,
    onCommitPadding: (PaddingSlot, Int) -> Unit,
    onFavoritesBoundsChanged: (topPx: Float, bottomPx: Float) -> Unit,
    nowPlayingHasContent: Boolean,
    contentColor: Color,
    showFavoriteLabels: Boolean,
    alignment: HomeAlignment,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    centerFavorites: Boolean,
    swipeUpOpensAppList: Boolean,
    onSwipeUp: () -> Unit,
    quickLaunchEnabled: Boolean,
    onQuickLaunch: (QuickLaunchSlot) -> Unit,
    onPeekStatusBar: () -> Unit,
    onExpandShade: () -> Unit,
    onManageFavorites: () -> Unit,
    onSetName: (AppInfo, String?) -> Unit,
    onChangeIcon: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
) {    fun displayName(app: AppInfo) = nameOverrides[app.key] ?: app.label

    var menuForKey by remember { mutableStateOf<String?>(null) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var renameDialogFor by remember { mutableStateOf<AppInfo?>(null) }
    var folderMenuFor by remember { mutableStateOf<String?>(null) }
    var folderRenameFor by remember { mutableStateOf<Folder?>(null) }
    var nowPlayingMenu by remember { mutableStateOf(false) }
    var nowPlayingMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }
    val touchPosition = remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // A stepped value shows immediately and is written at the same time; holding it locally
    // as well means repeated taps compound instead of each one reading back the stale stored
    // value while DataStore catches up.
    var backgroundMenu by remember { mutableStateOf(false) }
    var backgroundMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    var liveSlot by remember { mutableStateOf<PaddingSlot?>(null) }
    var liveValue by remember { mutableIntStateOf(0) }

    // On a fresh install the favorites are placed by measurement rather than by a stored
    // number: centered, which is where a thumb and the A-Z strip both want them. Landing at
    // the top of an empty screen is what new users kept reporting as the strip being too
    // high. Setting any padding by hand retires this for good.
    var centeredFavTopDp by remember { mutableIntStateOf(0) }
    var rootY by remember { mutableFloatStateOf(0f) }

    fun padOf(slot: PaddingSlot): Int = when {
        liveSlot == slot -> liveValue
        centerFavorites && slot == PaddingSlot.FAVORITES_TOP && centeredFavTopDp > 0 -> centeredFavTopDp
        else -> paddings[slot]
    }

    fun setPadding(slot: PaddingSlot, value: Int) {
        // The centered placement is computed, not stored. Touching any other padding retires
        // it, so write out what is on screen first or the favorites snap to the stock gap.
        if (centerFavorites && slot != PaddingSlot.FAVORITES_TOP && centeredFavTopDp > 0) {
            onCommitPadding(PaddingSlot.FAVORITES_TOP, centeredFavTopDp)
        }
        liveSlot = slot
        liveValue = value
        onCommitPadding(slot, value)
    }


    // The background drag is disabled while a padding is being adjusted; leaving edit mode
    // has to release that or the home screen stops scrolling entirely.
    LaunchedEffect(editMode) { if (!editMode) liveSlot = null }

    // Show the widget slot when a widget exists, and also in edit mode when there isn't one —
    // that placeholder is the only way back to the picker once a widget has been removed.
    val hasWidget = widgetIds.isNotEmpty()
    val showWidgetSlot = hasWidget || editMode

    // Reordering runs against a local copy and is committed once on release; going through
    // storage on every swap would lag behind the finger.
    var dragOrder by remember { mutableStateOf<List<HomeItem>?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }

    val homeItems = remember(favorites, widgetPosition, showWidgetSlot) {
        buildHomeItems(favorites, widgetPosition, showWidgetSlot)
    }
    val displayItems = dragOrder ?: homeItems

    // Folders sit alongside apps in the favorites block, so both bound its padding.
    val firstRowIndex = displayItems.indexOfFirst { it !is HomeItem.Widget }
    val lastRowIndex = displayItems.indexOfLast { it !is HomeItem.Widget }

    fun tokensOf(items: List<HomeItem>) = items.mapNotNull {
        when (it) {
            is HomeItem.Favorite -> it.app.key
            is HomeItem.FolderItem -> folderToken(it.folder.id)
            HomeItem.Widget -> null
        }
    }

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

        val below = itemHeights[from + 1]
        if (below != null && from + 1 <= order.lastIndex && dragOffset > below / 2f) {
            dragOrder = order.toMutableList().apply { add(from + 1, removeAt(from)) }
            draggingIndex = from + 1
            dragOffset -= below
            return
        }
        val above = itemHeights[from - 1]
        if (above != null && from - 1 >= 0 && dragOffset < -above / 2f) {
            dragOrder = order.toMutableList().apply { add(from - 1, removeAt(from)) }
            draggingIndex = from - 1
            dragOffset += above
        }
    }

    // Measured span of the favorites list, handed up so the A-Z strip can match it.
    var favBoundsTop by remember { mutableFloatStateOf(0f) }
    var favBoundsBottom by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(favBoundsTop, favBoundsBottom) {
        if (favBoundsBottom > favBoundsTop) onFavoritesBoundsChanged(favBoundsTop, favBoundsBottom)
    }

    val peekPullPx = with(density) { 30.dp.toPx() }
    val deepPullPx = with(density) { 200.dp.toPx() }
    val swipeUpPx = with(density) { 120.dp.toPx() }
    val quickLaunchPx = with(density) { 80.dp.toPx() }
    var rawPullDown by remember { mutableFloatStateOf(0f) }
    var rawPullUp by remember { mutableFloatStateOf(0f) }
    var pullActionFired by remember { mutableStateOf(false) }
    var peekFired by remember { mutableStateOf(false) }
    var swipeUpFired by remember { mutableStateOf(false) }

    // Free vertical drag with spring bounce at both ends, outside edit mode.
    val offsetY = remember { Animatable(0f) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var contentHeight by remember { mutableIntStateOf(0) }
    val minOffset = remember(viewportHeight, contentHeight) {
        minOf(0f, (viewportHeight - contentHeight).toFloat())
    }
    val editScrollState = rememberScrollState()

    LaunchedEffect(centerFavorites, favBoundsTop, favBoundsBottom, viewportHeight, rootY) {
        if (!centerFavorites || viewportHeight <= 0 || favBoundsBottom <= favBoundsTop) {
            return@LaunchedEffect
        }
        val blockHeight = favBoundsBottom - favBoundsTop
        val appliedTopPx = with(density) { padOf(PaddingSlot.FAVORITES_TOP).dp.toPx() }
        // Everything stacked above the favorites' own gap — a widget, Now Playing, their
        // paddings — measured rather than assumed, so this works whatever is up there.
        val above = (favBoundsTop - rootY - offsetY.value) - appliedTopPx
        val desiredPx = ((viewportHeight - blockHeight) / 2f - above).coerceAtLeast(0f)
        val desiredDp = with(density) { desiredPx.toDp().value.roundToInt() }
        // Subtracting the applied gap makes this idempotent, so it settles in one pass; the
        // guard only stops a rounding wobble from looping.
        if (abs(desiredDp - centeredFavTopDp) > 1) centeredFavTopDp = desiredDp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeight = it.height }
            .onGloballyPositioned { rootY = it.positionInWindow().y }
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !editMode && liveSlot == null,
                onDragStarted = {
                    rawPullDown = 0f
                    rawPullUp = 0f
                    pullActionFired = false
                    peekFired = false
                    swipeUpFired = false
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
                    // The mirror of the pull-down, measured from the bottom of the stack: a
                    // push up past the end of the content opens the app list.
                    if (swipeUpOpensAppList && delta < 0f && offsetY.value <= minOffset + 2f) {
                        rawPullUp -= delta
                        if (!swipeUpFired && rawPullUp > swipeUpPx) {
                            swipeUpFired = true
                            onSwipeUp()
                        }
                    }
                    scope.launch {
                        val next = offsetY.value + delta
                        val outOfBounds = next > 0f || next < minOffset
                        val applied = if (outOfBounds) offsetY.value + delta * 0.4f else next
                        offsetY.snapTo(applied)
                    }
                },
                onDragStopped = { velocity ->
                    if (!pullActionFired && rawPullDown > peekPullPx && velocity > 2200f) {
                        onExpandShade()
                    }
                    if (swipeUpOpensAppList && !swipeUpFired && rawPullUp > peekPullPx && velocity < -2200f) {
                        onSwipeUp()
                    }
                    rawPullDown = 0f
                    rawPullUp = 0f
                    pullActionFired = false
                    peekFired = false
                    swipeUpFired = false

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
                },
            )
            .then(
                // Only worth a pointer handler when something is actually bound to it.
                if (quickLaunchEnabled) {
                    Modifier.pointerInput(editMode, contentHeight) {
                        var eligible = false
                        var travelled = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { start ->
                                // The empty space under the whole stack. Anywhere higher is a
                                // row, the widget, or Now Playing, and belongs to them.
                                eligible = !editMode && start.y > offsetY.value + contentHeight
                                travelled = 0f
                            },
                            onDragEnd = {
                                if (eligible && abs(travelled) > quickLaunchPx) {
                                    onQuickLaunch(
                                        if (travelled < 0f) QuickLaunchSlot.LEFT else QuickLaunchSlot.RIGHT
                                    )
                                }
                                eligible = false
                            },
                            onDragCancel = { eligible = false },
                        ) { change, dx ->
                            if (eligible) {
                                change.consume()
                                travelled += dx
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
            .pointerInput(editMode) {
                if (editMode) return@pointerInput
                detectTapGestures(
                    onLongPress = { offset ->
                        backgroundMenuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                        backgroundMenu = true
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onEditModeChange(false) }) {
                        Icon(Icons.Filled.Done, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_done))
                    }
                }
                StepperRow(
                    label = stringResource(R.string.handle_side_padding),
                    value = sidePaddingDp,
                    range = 0..96,
                    step = PADDING_STEP_DP,
                    onChange = onSetSidePadding,
                )
                if (showWidgetSlot) {
                    Spacer(Modifier.height(6.dp))
                    StepperRow(
                        label = stringResource(R.string.handle_widget_side_padding),
                        value = widgetSidePaddingDp,
                        range = 0..96,
                        step = PADDING_STEP_DP,
                        onChange = onSetWidgetSidePadding,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            // With no widget on screen there is nothing above the favorites, so Now Playing
            // takes that slot instead.
            if (!hasWidget && nowPlayingHasContent) {
                NowPlayingBlock(
                    editMode = editMode,
                    heightDp = nowPlayingHeightDp,
                    contentColor = contentColor,
                    alignment = alignment,
                    sidePaddingDp = sidePaddingDp,
                    padTop = padOf(PaddingSlot.NOW_PLAYING_TOP),
                    padBottom = padOf(PaddingSlot.NOW_PLAYING_BOTTOM),
                    touchPosition = touchPosition,
                    menuExpanded = nowPlayingMenu,
                    menuOffset = nowPlayingMenuOffset,
                    onOpenMenu = { offset -> nowPlayingMenuOffset = offset; nowPlayingMenu = true },
                    onDismissMenu = { nowPlayingMenu = false },
                    onEditLayout = { nowPlayingMenu = false; onEditModeChange(true) },
                    onOpenSettings = { nowPlayingMenu = false; onOpenSettings() },
                    onResize = onResizeNowPlaying,
                    onSetPadding = { slot, v -> setPadding(slot, v) },
                )
            }

            if (displayItems.isEmpty()) {
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
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onManageFavorites) { Text(stringResource(R.string.home_choose_favorites)) }
                }
            }

            displayItems.forEachIndexed { index, item ->
                // Reordering hangs off a visible grab handle now. It used to be a long press
                // anywhere on the row, which nothing on screen advertised and which fought
                // every other thing a long press could mean.
                val dragHandle = if (editMode) {
                    Modifier.pointerInput(index, displayItems.size) {
                        detectDragGestures(
                            onDragStart = {
                                // The handle is small and the row does not move until the
                                // finger does, so a tick is the only confirmation that the
                                // grab took.
                                HapticUtil.tick(view, hapticsEnabled)
                                dragOrder = displayItems
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDragEnd = { commitDragOrder() },
                            onDragCancel = { commitDragOrder() },
                        ) { change, amount ->
                            change.consume()
                            onDragBy(amount.y)
                        }
                    }
                } else {
                    null
                }

                // Spacing handles live outside the draggable wrapper: inside it they would
                // travel with a dragged row and skew the height the swap threshold uses.
                if (item is HomeItem.Widget) {
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_widget_top,
                        value = padOf(PaddingSlot.WIDGET_TOP),
                        onChange = { setPadding(PaddingSlot.WIDGET_TOP, it) },
                    )
                } else if (index == firstRowIndex) {
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_favorites_top,
                        value = padOf(PaddingSlot.FAVORITES_TOP),
                        onChange = { setPadding(PaddingSlot.FAVORITES_TOP, it) },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            itemHeights[index] = coords.size.height
                            if (item !is HomeItem.Widget) {
                                val top = coords.positionInWindow().y
                                if (index == firstRowIndex) favBoundsTop = top
                                if (index == lastRowIndex) favBoundsBottom = top + coords.size.height
                            }
                        }
                        .zIndex(if (draggingIndex == index) 1f else 0f)
                        .graphicsLayer {
                            if (draggingIndex == index) {
                                translationY = dragOffset
                                scaleX = 1.03f
                                scaleY = 1.03f
                                alpha = 0.9f
                            }
                        }
                ) {
                    when (item) {
                        HomeItem.Widget -> Box {
                            WidgetSlot(
                                widgetIds = widgetIds,
                                heightDp = widgetHeightDp,
                                onEditLayout = { onEditModeChange(true) },
                                actions = widgetActions,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = widgetSidePaddingDp.dp),
                            )
                            // The widget is a row in the order like any other, so it needs a
                            // handle of its own to be moved among them.
                            if (dragHandle != null) {
                                DragHandle(
                                    contentColor = contentColor,
                                    modifier = Modifier.align(Alignment.TopEnd).then(dragHandle),
                                )
                            }
                        }

                        is HomeItem.FolderItem -> FolderRow(
                            folder = item.folder,
                            members = item.folder.apps.mapNotNull { appsByKey[it] },
                            expanded = item.folder.id in expandedFolders,
                            editMode = editMode,
                            dragHandle = dragHandle,
                            iconSizeDp = iconSizeDp,
                            labelSizeSp = labelSizeSp,
                            sidePaddingDp = sidePaddingDp,
                            contentColor = contentColor,
                            showLabels = showFavoriteLabels,
                            alignment = alignment,
                            menuExpanded = folderMenuFor == item.folder.id,
                            menuOffset = menuOffset,
                            touchPosition = touchPosition,
                            displayName = { displayName(it) },
                            onToggleExpanded = {
                                expandedFolders = if (item.folder.id in expandedFolders) {
                                    expandedFolders - item.folder.id
                                } else {
                                    expandedFolders + item.folder.id
                                }
                            },
                            onOpenMenu = { offset -> menuOffset = offset; folderMenuFor = item.folder.id },
                            onDismissMenu = { folderMenuFor = null },
                            onManage = { folderMenuFor = null; onManageFolder(item.folder) },
                            onEdit = { folderMenuFor = null; folderRenameFor = item.folder },
                            onEditLayout = { folderMenuFor = null; onEditModeChange(true) },
                            onDelete = { folderMenuFor = null; onDeleteFolder(item.folder) },
                            onOpenApp = onOpenFolderApp,
                            onRemoveApp = { member -> onRemoveFromFolder(item.folder, member) },
                        )

                        is HomeItem.Favorite -> FavoriteRow(
                            app = item.app,
                            label = displayName(item.app),
                            editMode = editMode,
                            dragHandle = dragHandle,
                            iconSizeDp = iconSizeDp,
                            labelSizeSp = labelSizeSp,
                            sidePaddingDp = sidePaddingDp,
                            contentColor = contentColor,
                            showLabels = showFavoriteLabels,
                            alignment = alignment,
                            menuExpanded = menuForKey == item.app.key,
                            menuOffset = menuOffset,
                            touchPosition = touchPosition,
                            onLaunch = { onLaunch(item.app) },
                            onOpenMenu = { offset -> menuOffset = offset; menuForKey = item.app.key },
                            onDismissMenu = { menuForKey = null },
                            onMoveToFolder = { menuForKey = null; onMoveToFolder(item.app) },
                            onEditLayout = { menuForKey = null; onEditModeChange(true) },
                            onAppInfo = { menuForKey = null; onAppInfo(item.app) },
                            onRemove = { menuForKey = null; onRemoveFavorite(item.app) },
                            onEditIconName = { menuForKey = null; renameDialogFor = item.app },
                            onOpenSettings = { menuForKey = null; onOpenSettings() },
                        )
                    }
                }

                if (item is HomeItem.Widget) {
                    if (editMode) {
                        Spacer(Modifier.height(6.dp))
                        StepperRow(
                            label = stringResource(R.string.handle_widget_height),
                            value = widgetHeightDp,
                            range = 80..900,
                            step = HEIGHT_STEP_DP,
                            onChange = { widgetActions.onResize(it) },
                        )
                    }
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_widget_bottom,
                        value = padOf(PaddingSlot.WIDGET_BOTTOM),
                        onChange = { setPadding(PaddingSlot.WIDGET_BOTTOM, it) },
                    )
                    // Now Playing sits between the widget and the favorites.
                    if (nowPlayingHasContent) {
                        NowPlayingBlock(
                            editMode = editMode,
                            heightDp = nowPlayingHeightDp,
                            contentColor = contentColor,
                            alignment = alignment,
                            sidePaddingDp = sidePaddingDp,
                            padTop = padOf(PaddingSlot.NOW_PLAYING_TOP),
                            padBottom = padOf(PaddingSlot.NOW_PLAYING_BOTTOM),
                            touchPosition = touchPosition,
                            menuExpanded = nowPlayingMenu,
                            menuOffset = nowPlayingMenuOffset,
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
                    )
                } else {
                    Spacer(Modifier.height(itemSpacingDp.dp))
                }
            }
        }

        TouchAnchoredMenu(
            expanded = backgroundMenu,
            offset = backgroundMenuOffset,
            onDismissRequest = { backgroundMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_layout)) },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                onClick = { backgroundMenu = false; onEditModeChange(true) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.widget_add)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { backgroundMenu = false; widgetActions.onAddWidget() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_choose_favorites)) },
                leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                onClick = { backgroundMenu = false; onManageFavorites() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = { backgroundMenu = false; onOpenSettings() },
            )
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

/** One favorite: icon, optional name, press highlight and its context menu. */
@Composable
private fun FavoriteRow(
    app: AppInfo,
    label: String,
    editMode: Boolean,
    /** Non-null in edit mode: the gesture that reorders, attached to this row's grab handle. */
    dragHandle: Modifier?,
    iconSizeDp: Int,
    labelSizeSp: Int,
    sidePaddingDp: Int,
    contentColor: Color,
    showLabels: Boolean,
    alignment: HomeAlignment,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    touchPosition: MutableState<Offset>,
    onLaunch: () -> Unit,
    onOpenMenu: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onMoveToFolder: () -> Unit,
    onEditLayout: () -> Unit,
    onAppInfo: () -> Unit,
    onRemove: () -> Unit,
    onEditIconName: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = alignment.arrangement(),
        ) {
            if (dragHandle != null && alignment == HomeAlignment.RIGHT) {
                DragHandle(contentColor, dragHandle)
            }
            AlignedIconLabel(
                alignment = alignment,
                showLabel = showLabels,
                iconWidth = iconSizeDp.dp,
                icon = { AppIcon(app = app, sizeDp = iconSizeDp) },
            ) { labelModifier ->
                Text(
                    label,
                    color = contentColor,
                    fontSize = labelSizeSp.sp,
                    modifier = labelModifier,
                    textAlign = alignment.textAlign(),
                )
            }
            if (dragHandle != null && alignment != HomeAlignment.RIGHT) {
                DragHandle(contentColor, dragHandle)
            }
        }

        TouchAnchoredMenu(expanded = menuExpanded, offset = menuOffset, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move_to_folder)) },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                onClick = onMoveToFolder,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_layout)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = onEditLayout,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_app_info)) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = onAppInfo,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = onRemove,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                onClick = onEditIconName,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = onOpenSettings,
            )
        }
    }
}

/** A folder row, which expands in place to show its apps. */
@Composable
private fun FolderRow(
    folder: Folder,
    members: List<AppInfo>,
    expanded: Boolean,
    editMode: Boolean,
    /** Non-null in edit mode: the gesture that reorders, attached to this row's grab handle. */
    dragHandle: Modifier?,
    iconSizeDp: Int,
    labelSizeSp: Int,
    sidePaddingDp: Int,
    contentColor: Color,
    showLabels: Boolean,
    alignment: HomeAlignment,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    touchPosition: MutableState<Offset>,
    displayName: (AppInfo) -> String,
    onToggleExpanded: () -> Unit,
    onOpenMenu: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onManage: () -> Unit,
    onEdit: () -> Unit,
    onEditLayout: () -> Unit,
    onDelete: () -> Unit,
    onOpenApp: (AppInfo) -> Unit,
    onRemoveApp: (AppInfo) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current

    Column {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                                    onClick = onToggleExpanded,
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
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = alignment.arrangement(),
            ) {
                if (dragHandle != null && alignment == HomeAlignment.RIGHT) {
                    DragHandle(contentColor, dragHandle)
                }
                AlignedIconLabel(
                    alignment = alignment,
                    showLabel = showLabels,
                    iconWidth = iconSizeDp.dp,
                    icon = { FolderIcon(members, iconSizeDp, contentColor, folder.icon) },
                ) { labelModifier ->
                    Text(
                        folder.name,
                        color = contentColor,
                        fontSize = labelSizeSp.sp,
                        modifier = labelModifier,
                        textAlign = alignment.textAlign(),
                    )
                    Text(
                        "${members.size}",
                        color = contentColor.copy(alpha = 0.5f),
                        fontSize = (labelSizeSp - 3).coerceAtLeast(9).sp,
                    )
                }
                if (dragHandle != null && alignment != HomeAlignment.RIGHT) {
                    DragHandle(contentColor, dragHandle)
                }
            }

            TouchAnchoredMenu(expanded = menuExpanded, offset = menuOffset, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_folder_choose_apps)) },
                    leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                    onClick = onManage,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = onEdit,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_layout)) },
                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    onClick = onEditLayout,
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_folder_delete)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = onDelete,
                )
            }
        }

        // Members expand in place rather than opening a separate screen.
        AnimatedVisibility(visible = expanded && !editMode) {
            Column {
                members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Members sit one indent inside their folder, on whichever side
                            // the folder's own name is on.
                            .padding(
                                start = if (alignment == HomeAlignment.RIGHT) sidePaddingDp.dp else (sidePaddingDp + 24).dp,
                                end = if (alignment == HomeAlignment.RIGHT) (sidePaddingDp + 24).dp else sidePaddingDp.dp,
                            )
                            .combinedClickable(
                                onClick = { onOpenApp(member) },
                                onLongClick = { onRemoveApp(member) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = alignment.arrangement(),
                    ) {
                        AlignedIconLabel(
                            alignment = alignment,
                            showLabel = showLabels,
                            iconWidth = (iconSizeDp * 0.8f).dp,
                            icon = { AppIcon(app = member, sizeDp = (iconSizeDp * 0.8f).toInt()) },
                        ) { labelModifier ->
                            Text(
                                displayName(member),
                                color = contentColor,
                                fontSize = labelSizeSp.sp,
                                modifier = labelModifier,
                                textAlign = alignment.textAlign(),
                            )
                        }
                    }
                }
                if (members.isEmpty()) {
                    Text(
                        stringResource(R.string.home_folder_empty),
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = (labelSizeSp - 3).coerceAtLeast(10).sp,
                        modifier = Modifier.padding(start = (sidePaddingDp + 24).dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

/** Now Playing with its own padding handles and height handle. */
@Composable
private fun NowPlayingBlock(
    editMode: Boolean,
    heightDp: Int,
    contentColor: Color,
    alignment: HomeAlignment,
    sidePaddingDp: Int,
    padTop: Int,
    padBottom: Int,
    touchPosition: MutableState<Offset>,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
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
    )
    Box {
        NowPlayingWidget(
            heightDp = heightDp,
            contentColor = contentColor,
            alignment = alignment,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sidePaddingDp.dp)
                .recordTouchPosition(touchPosition)
                .combinedClickable(
                    // The transport buttons consume their own taps, so this is only ever the
                    // card itself — which should get you to what is playing. Say so when it
                    // can't, rather than leaving a tap that looks ignored.
                    onClick = {
                        if (!openNowPlayingApp(context)) {
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
        )
    }
    PaddingHandle(
        editMode = editMode,
        label = R.string.handle_now_playing_bottom,
        value = padBottom,
        onChange = { onSetPadding(PaddingSlot.NOW_PLAYING_BOTTOM, it) },
    )
}
@Composable
private fun FolderIcon(
    members: List<AppInfo>,
    sizeDp: Int,
    contentColor: Color,
    iconOverride: String?,
) {
    if (!LocalIconConfig.current.showIcons) return
    var drewOverride = false
    if (iconOverride != null) {
        drewOverride = FolderIconImage(iconOverride, sizeDp)
    }
    if (drewOverride) return

    val preview = members.take(4)
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .background(contentColor.copy(alpha = 0.12f), RoundedCornerShape(sizeDp.dp / 4)),
        contentAlignment = Alignment.Center,
    ) {
        if (preview.isEmpty()) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size((sizeDp * 0.55f).dp),
            )
        } else {
            val cell = (sizeDp * 0.36f).toInt()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                preview.chunked(2).forEach { rowApps ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        rowApps.forEach { AppIcon(app = it, sizeDp = cell) }
                    }
                }
            }
        }
    }
}

/** The grab handle that reorders a row in edit mode. */
@Composable
private fun DragHandle(contentColor: Color, modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.Menu,
        contentDescription = stringResource(R.string.home_drag_handle),
        tint = contentColor.copy(alpha = 0.6f),
        modifier = modifier.size(40.dp).padding(8.dp),
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
    showLabel: Boolean,
    /** Width of [icon], so a centered label can be balanced against it. */
    iconWidth: Dp,
    icon: @Composable () -> Unit,
    label: @Composable RowScope.(Modifier) -> Unit,
) {
    // No icon drawn means no gap to leave for one.
    val showIcons = LocalIconConfig.current.showIcons
    val gap = if (showIcons) 16.dp else 0.dp
    when {
        alignment == HomeAlignment.RIGHT -> {
            if (showLabel) {
                label(Modifier.weight(1f))
                Spacer(Modifier.width(gap))
            } else {
                Spacer(Modifier.weight(1f))
            }
            icon()
        }

        alignment == HomeAlignment.CENTER && showLabel -> {
            // Centered means centered on the row, not on whatever the icon left over. The
            // label takes the weight and a spacer the width of the icon balances it on the
            // far side, so the text lands on the screen's middle either way.
            icon()
            Spacer(Modifier.width(gap))
            label(Modifier.weight(1f))
            if (showIcons) {
                Spacer(Modifier.width(iconWidth + gap))
            }
        }

        else -> {
            icon()
            if (showLabel) {
                Spacer(Modifier.width(gap))
                label(Modifier.weight(1f))
            } else if (alignment != HomeAlignment.CENTER) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun HomeAlignment.textAlign() = when (this) {
    HomeAlignment.LEFT -> TextAlign.Start
    HomeAlignment.CENTER -> TextAlign.Center
    HomeAlignment.RIGHT -> TextAlign.End
}

private fun HomeAlignment.arrangement() =
    if (this == HomeAlignment.CENTER) Arrangement.Center else Arrangement.Start

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
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}