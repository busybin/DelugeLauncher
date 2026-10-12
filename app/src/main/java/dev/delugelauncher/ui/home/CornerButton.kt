// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.delugelauncher.data.CornerAction
import dev.delugelauncher.data.HapticFeedback
import dev.delugelauncher.service.HapticUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.atan2
import kotlin.math.roundToInt

/** The tug that stretches the button fully: a step this long in dp per touch event. */
private const val PULL_FULL_DP = 16f

/** How much of the pull is left after each frame with the finger still. */
private const val PULL_DECAY = 0.82f

/**
 * The launcher's corner button: a disc with a D, that runs the tap and the hold-then-slide
 * actions, and that the user can plant anywhere on the screen in edit layout.
 *
 * Two gestures, two pointer handlers, both on the outer box, which never moves in its own
 * coordinates: the disc's stretch, squash, turn and swipe travel all live on the inner
 * box, so pointer deltas stay true while the disc is being pushed about. Everything the
 * placement needs is a fraction of the viewport (see [CornerGeometry]), so a plant is one
 * store write on release and survives rotation and backup untouched.
 */
@Composable
internal fun CornerButton(
    visible: Boolean,
    storedPos: Pair<Float, Float>?,
    frame: CornerFrame?,
    editMode: Boolean,
    tapAction: CornerAction,
    swipeAction: CornerAction,
    hapticFeedback: HapticFeedback,
    onTap: () -> Unit,
    onSwipe: (CornerAction) -> Unit,
    onOwnershipChanged: (Boolean) -> Unit,
    onCommitPos: (Float, Float) -> Unit,
    onBoundsChanged: (Rect?) -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // The disc's own position while a finger is planting it. Adopted from the store only
    // when no finger is down: a release writes the store asynchronously, and re-seeding
    // mid-drag is what snapped the disc back under the finger on every retest.
    var dragging by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(storedPos) }
    LaunchedEffect(storedPos) {
        if (!dragging) live = storedPos
    }

    // The stretch: the direction the finger is pulling and how hard, decaying on its own
    // when the finger rests, so a still finger leaves the disc round.
    var pull by remember { mutableStateOf(Offset.Zero) }
    val pullFullPx = with(density) { PULL_FULL_DP.dp.toPx() }
    LaunchedEffect(dragging) {
        while (dragging) {
            delay(16)
            pull *= PULL_DECAY
        }
    }
    val pullMag = (pull.getDistance() / pullFullPx).coerceIn(0f, 1f)
    val pullAngleDeg = Math.toDegrees(atan2(pull.y, pull.x).toDouble()).toFloat()

    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "cornerPress",
    )
    val sinkPx = with(density) { 2.dp.toPx() }
    val pressSink = (1f - pressScale).coerceAtLeast(0f) / 0.16f * sinkPx

    // The slide: how far the disc has been dragged up, springing home on release.
    val slide = remember { Animatable(0f) }
    val diameterPx = with(density) { CORNER_BUTTON_DP.dp.toPx() }
    val maxTravelPx = diameterPx * 3f
    val thresholdPx = with(density) { 96.dp.toPx() }
    val stretchUp = (-slide.value / maxTravelPx).coerceIn(0f, 1f)

    val frameState = rememberUpdatedState(frame)
    val swipeState = rememberUpdatedState(swipeAction)
    val onSwipeState = rememberUpdatedState(onSwipe)
    val onOwnershipState = rememberUpdatedState(onOwnershipChanged)
    val onTapState = rememberUpdatedState(onTap)

    // Drawn straight from the wanted spot: the disc sits under the finger with no lag.
    val topLeft = remember(live, frame) {
        val pos = live
        val f = frame
        if (pos != null && f != null) CornerGeometry.topLeft(pos.first, pos.second, f) else null
    }

    // Edit layout: the only thing this disc does is get dragged. Relative steps in the
    // disc's own space, no absolute coordinates, so nothing it does feeds back into the
    // reading that moves it.
    val planting = Modifier.pointerInput(editMode) {
        if (!editMode) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            dragging = true
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val fr = frameState.value
                    if (fr != null) {
                        val step = change.positionChange()
                        val base = live ?: (0.5f to 0.5f)
                        live = (base.first + step.x / fr.viewportW).coerceIn(0f, 1f) to
                            (base.second + step.y / fr.viewportH).coerceIn(0f, 1f)
                        val mixed = pull * 0.6f + step * 0.4f
                        val cap = pullFullPx * 2f
                        val over = mixed.getDistance()
                        pull = if (over > cap) mixed / over * cap else mixed
                    }
                    change.consume()
                }
            } finally {
                dragging = false
                pull = Offset.Zero
            }
            live?.let { onCommitPos(it.first, it.second) }
        }
    }

    // Normal use: a tap runs the tap action, and a held-then-slid finger runs the slide.
    val sliding = Modifier.pointerInput(editMode) {
        if (editMode) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val held = withTimeoutOrNull(CORNER_HOLD_MS) { waitForUpOrCancellation() } == null
            if (!held) return@awaitEachGesture
            // Said out loud to the home screen: the same upward travel is also a
            // swipe-up-to-open-list, and without this both fire.
            HapticUtil.tick(view, hapticFeedback)
            onOwnershipState.value(true)
            var travel = 0f
            var armed = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dy = change.position.y - change.previousPosition.y
                if (dy != 0f) {
                    change.consume()
                    travel = (travel + dy).coerceAtMost(0f)
                    // Rubber toward the limit: the last stretch gives less and less.
                    val soft = maxTravelPx * 0.7f
                    val over = -travel - soft
                    val pulled = if (over <= 0f) travel else -(soft + over * 0.25f).coerceAtMost(maxTravelPx)
                    scope.launch { slide.snapTo(pulled) }
                }
                if (!armed && -travel >= thresholdPx) {
                    armed = true
                    HapticUtil.tick(view, hapticFeedback)
                }
            }
            // The gesture is over the instant the finger lifts; the spring home runs behind.
            onOwnershipState.value(false)
            if (armed) onSwipeState.value(swipeState.value)
            scope.launch {
                slide.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
        }
    }

    AnimatedVisibility(
        visible = visible && topLeft != null,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
            scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
            scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = 0.6f),
    ) {
        // Inside the visibility block on purpose: when the button goes away the strip must
        // stop yielding to a rect that is no longer there, and this dispose is what says so.
        DisposableEffect(Unit) { onDispose { onBoundsChanged(null) } }
        val tl = topLeft ?: Offset.Zero
        Box(
            modifier = Modifier
                .offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
                .size(CORNER_BUTTON_DP.dp)
                .onGloballyPositioned { coords ->
                    onBoundsChanged(Rect(coords.positionInRoot(), Size(coords.size.width.toFloat(), coords.size.height.toFloat())))
                }
                .then(if (editMode) planting else sliding)
                .clickable(
                    enabled = !editMode,
                    interactionSource = press,
                    indication = null,
                    role = Role.Button,
                    onClick = { onTapState.value() },
                ),
        ) {
            // Everything that changes shape lives on this layer, not on the touch target.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Turned toward the tug, then stretched along the turned axis: the
                        // long side points where the finger is going.
                        if (pullMag > 0.05f) rotationZ = pullAngleDeg
                        val up = 1f + 0.32f * stretchUp
                        val narrow = 1f - 0.14f * stretchUp
                        scaleX = pressScale * narrow * (1f + 0.28f * pullMag)
                        scaleY = pressScale * up * (1f - 0.18f * pullMag)
                        translationY = slide.value + pressSink
                    }
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                // Counter-turned, so the letter stays upright while the disc stretches.
                Text(
                    "D",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 35.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer {
                        if (pullMag > 0.05f) rotationZ = -pullAngleDeg
                    },
                )
            }
        }
    }
}
