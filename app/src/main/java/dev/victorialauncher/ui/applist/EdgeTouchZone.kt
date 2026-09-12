// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.service.HapticUtil
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How far the strip may be dragged inward before the pull stops growing. */
private const val MAX_PULL_DP = 400f

/** How close two taps on the strip have to be to count as one gesture. */
private const val DOUBLE_TAP_WINDOW_MS = 300L

/**
 * The invisible strip along a screen edge that opens the app list and then scrubs it, so one
 * unbroken touch does both.
 *
 * Writes straight into [state] rather than reporting upward through callbacks: the letter
 * changes ~26 times per gesture, and routing that through the caller is what used to
 * recompose the home screen on every one of them.
 */
@Composable
fun EdgeTouchZone(
    side: EdgeSide,
    widthDp: Dp,
    letters: List<Char>,
    band: ScrubBand,
    hapticsEnabled: Boolean,
    state: ScrubState,
    onOpen: () -> Unit,
    /** Null when double-tap-to-lock is off, so a second tap is simply another tap. */
    onDoubleTap: (() -> Unit)?,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val fromLeft = side == EdgeSide.LEFT
    // The gesture handler outlives the composition that built it, so it must not close over
    // this frame's callbacks.
    val currentDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentLongPress by rememberUpdatedState(onLongPress)

    Box(
        modifier = modifier
            .width(widthDp)
            .fillMaxHeight()
            .pointerInput(letters, band, hapticsEnabled, fromLeft) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    state.begin(side)
                    onOpen()

                    var lastIndex = -1
                    fun report(x: Float, y: Float) {
                        // Same geometry the visible strip uses, so the letter under the
                        // fingertip is the one that swells.
                        val index = ScrubberGeometry.indexForY(y, band.topPx, band.heightPx, letters.size)
                        if (index != lastIndex) {
                            lastIndex = index
                            HapticUtil.tick(view, hapticsEnabled)
                        }
                        // How far the finger has pulled in toward the middle of the screen.
                        val inward = if (fromLeft) x - size.width else -x
                        state.update(y, inward.coerceIn(0f, MAX_PULL_DP * density), letters.getOrNull(index))
                    }

                    report(down.position.x, down.position.y)

                    // Three things start the same way here, so they are told apart by what
                    // happens next: moving is a scrub, holding still is the band editor, and
                    // letting go without either is a tap — which a second tap turns into a
                    // lock. Opening the list on the down stays untouched through all of it.
                    var moved = false
                    var heldStill = false
                    while (true) {
                        val event = if (moved) {
                            awaitPointerEvent()
                        } else {
                            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() }
                        }
                        if (event == null) {
                            heldStill = true
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        // A tap places the list without ever counting as a scrub, so the
                        // overlay does not spend the tap fading itself out and back in.
                        if (!moved &&
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        ) {
                            moved = true
                            state.markScrubbing()
                        }
                        report(change.position.x, change.position.y)
                        change.consume()
                    }

                    if (heldStill) {
                        state.cancel()
                        HapticUtil.tick(view, hapticsEnabled)
                        currentLongPress()
                        // Swallow what is left, or lifting would also register as a tap.
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                        return@awaitEachGesture
                    }

                    scope.launch { state.release() }

                    if (!moved) {
                        val doubleTap = currentDoubleTap
                        val previous = state.lastTapUptimeMs
                        if (doubleTap != null && down.uptimeMillis - previous <= DOUBLE_TAP_WINDOW_MS) {
                            state.lastTapUptimeMs = 0L
                            doubleTap()
                        } else {
                            state.lastTapUptimeMs = down.uptimeMillis
                        }
                    }
                }
            },
    )
}