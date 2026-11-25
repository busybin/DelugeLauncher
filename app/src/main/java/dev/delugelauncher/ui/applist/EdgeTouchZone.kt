// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import dev.delugelauncher.data.EdgeSide
import dev.delugelauncher.data.HapticFeedback
import dev.delugelauncher.service.HapticUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far the strip may be dragged inward before the pull stops growing. */
/**
 * How far past the letters counts as leaving rather than reaching for the first or last of
 * them, counted in letters.
 *
 * Letters and not a share of the band: a fifth of the band sounds small and is about five
 * letters, which puts the threshold nowhere near the end of the alphabet you just dragged off.
 * A letter and a half is far enough that reaching for A from above still lands on A, and near
 * enough that leaving feels like leaving.
 */
/** How long a finger has to sit still on a letter before the list starts moving on its own. */
private const val HOLD_START_MS = 450L

/** Further than this and it was a scrub, not a rest. */
private const val HOLD_SLOP_DP = 6f

private const val OFF_STRIP_DISMISS_LETTERS = 1.5f

/** Bounds, so a short alphabet does not make this a flick and a long one does not bury it. */
private const val OFF_STRIP_DISMISS_MIN_DP = 24f
private const val OFF_STRIP_DISMISS_MAX_DP = 72f

private const val MAX_PULL_DP = 400f

/**
 * How far in toward the list a finger has to travel before the letters stop being picked, and
 * how far back out it has to come before they are picked again.
 *
 * Two numbers rather than one: at a single threshold a fingertip resting exactly on it makes
 * the strip flicker between scrubbing and opening, and the list jumps a letter every time.
 */
internal const val ON_APPS_ENTER_DP = 28f
internal const val ON_APPS_EXIT_DP = 16f

/** How close two taps on the strip have to be to count as one gesture. */
private const val DOUBLE_TAP_WINDOW_MS = 300L

/** A strip thinner than this is a line nobody can hit, so the reach grows instead. */
private const val MIN_ZONE_WIDTH_DP = 12f

/**
 * How wide the zone itself is, given how far it reaches from the screen edge.
 *
 * The setting names the reach, not the zone: the outermost pixels belong to the system's own
 * back gesture, so the zone starts inside them and that much of the reach is spent before it
 * begins. Counting it as extra width is what put the strip over the leftmost icons on a
 * gesture-navigation phone, where the edge alone is a thumb's width.
 */
fun edgeZoneWidth(reach: Dp, inset: Dp): Dp = (reach - inset).coerceAtLeast(MIN_ZONE_WIDTH_DP.dp)

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
    /** How far from its screen edge the zone reaches, system gesture edge included. */
    widthDp: Dp,
    letters: List<String>,
    band: ScrubBand,
    /** Whether the live part of the edge is only as tall as the band. */
    bandOnly: Boolean,
    /** Whether resting on a letter walks the list on through it. */
    holdScroll: Boolean,
    /**
     * Whether dragging off an end keeps the end letter rather than arming the close, and
     * whether dragging past Z asks for the very end of the list.
     */
    followPastEnds: Boolean = false,
    /** How much of the very edge is left to the system's own gestures. */
    insetDp: Dp,
    hapticFeedback: HapticFeedback,
    state: ScrubState,
    /** Whether the app list is already showing, so a tap on the strip knows which it is. */
    listOpen: Boolean,
    onOpen: () -> Unit,
    /** Closes an already-open list, the way tapping the empty space above it does. */
    onDismiss: () -> Unit,
    /** Null when double-tap-to-lock is off, so a second tap is simply another tap. */
    onDoubleTap: (() -> Unit)?,
    /**
     * Whether dragging off the letters onto the list and letting go there opens the app under
     * the finger (#175). Off, letting go places the list at the last letter it was on.
     */
    releaseToLaunch: Boolean = false,
    /** Called with the app's key when one is let go on. */
    onReleaseToLaunch: (String) -> Unit = {},
    /** A rect in root pixels the zone leaves alone — the corner button lives there. */
    exclusion: Rect? = null,
    modifier: Modifier = Modifier,
) {
    // "Off" means no strip at all: no box, no pointer handler, nothing to take a touch.
    // Callers already stop building one per side, so this is a second line of defence for a
    // value that arrives here some other way — a stray NONE would otherwise draw a strip on
    // the right and open the list from it.
    if (side == EdgeSide.NONE) return
    val view = LocalView.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val fromLeft = side == EdgeSide.LEFT
    // The gesture handler outlives the composition that built it, so it must not close over
    // this frame's callbacks.
    val currentDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentListOpen by rememberUpdatedState(listOpen)
    val currentExclusion by rememberUpdatedState(exclusion)
    // The gesture handler outlives this composition, and the list publishes a fresh hit test
    // whenever it lays out.
    val currentReleaseToLaunch by rememberUpdatedState(onReleaseToLaunch)
    // Where this zone sits in the root, so a press can be tested against the exclusion in
    // the same space the button reported itself in.
    var zoneOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            // Held off the edge rather than made narrower: the point is to leave the outermost
            // pixels to the system, so the strip has to start inside them.
            .padding(start = if (fromLeft) insetDp else 0.dp, end = if (fromLeft) 0.dp else insetDp)
            .width(edgeZoneWidth(widthDp, insetDp))
            // The whole side, or only as much of it as the letters occupy. Full height is the
            // old behaviour and still the default; confined, the edge stops taking touches
            // meant for whatever shares that side of the screen.
            .then(
                if (!bandOnly) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier
                        .offset { IntOffset(0, band.topPx.roundToInt()) }
                        .height((band.heightPx / density).dp)
                }
            )
            .pointerInput(letters, band, hapticFeedback, fromLeft, bandOnly, followPastEnds, releaseToLaunch) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Starting on the corner button: not ours, leave it alone so the tap
                    // reaches the button underneath instead of opening the list.
                    if (currentExclusion?.contains(zoneOrigin + down.position) == true) {
                        return@awaitEachGesture
                    }
                    down.consume()
                    // Read before onOpen changes it: a tap that opened the list must not
                    // also be read as a tap on an open one.
                    val openAtDown = currentListOpen
                    state.begin(side)
                    onOpen()

                    var lastIndex = -1
                    // The edge strip runs the height of the screen while the letters occupy
                    // only a band of it. Clamping a touch above the band onto A yanked the
                    // list to A from somewhere A visibly is not — so nothing is picked until
                    // the finger has actually been on the letters. Once it has, clamping
                    // resumes, and A can be grabbed and dragged well above its own position.
                    var enteredBand = false
                    // Dragged far enough off an end of the letters to mean "done with this".
                    // Armed rather than acted on, so the list goes when the finger lifts and
                    // not the instant it crosses — which would take the list away mid-drag.
                    var armedToClose = false
                    // How far the strip has been pushed by running off an end; see below.
                    var shiftPx = 0f
                    // Off the letters and over the list: no letter is picked, and letting go
                    // opens whatever is under the finger (#175).
                    var onApps = false
                    // Where the last letter was picked, so the strip can stay parked there
                    // while the finger heads for an app.
                    var lastStripY = down.position.y
                    var lastLetter: String? = null
                    val zoneTopPx = if (bandOnly) band.topPx else 0f
                    val letterHeight = if (letters.isEmpty()) 0f else band.heightPx / letters.size
                    val overshoot = (letterHeight * OFF_STRIP_DISMISS_LETTERS)
                        .coerceIn(OFF_STRIP_DISMISS_MIN_DP * density, OFF_STRIP_DISMISS_MAX_DP * density)
                    fun report(x: Float, localY: Float) {
                        // A touch arrives in the zone's own coordinates while the band is
                        // measured against the whole screen. Those were the same thing while
                        // the zone ran the full height; confined to the band it starts at the
                        // band's top, so local y has to be put back into the space the geometry
                        // is written in — otherwise every y reads as above the band, no letter
                        // is ever picked, and the strip looks dead.
                        val y = localY + zoneTopPx
                        // How far the finger has pulled in toward the middle of the screen.
                        val inward = if (fromLeft) x - size.width else -x
                        if (!enteredBand && ScrubberGeometry.isWithin(y, band.topPx, band.heightPx)) {
                            enteredBand = true
                        }
                        // Heading for an app, the letter stays put: a finger moving inward
                        // drifts up or down, and every letter it crossed on the way would
                        // otherwise move the list on before it got there (#179).
                        if (releaseToLaunch && enteredBand && lastLetter != null) {
                            val over = overApps(
                                inward,
                                onApps,
                                ON_APPS_ENTER_DP * density,
                                ON_APPS_EXIT_DP * density,
                            )
                            if (over != onApps) {
                                onApps = over
                                state.updateOnApps(over)
                                if (over) state.holdScroll(false)
                                HapticUtil.tick(view, hapticFeedback)
                            }
                            if (onApps) {
                                // Letting go over nothing closes the list rather than
                                // leaving it parked at a letter nobody asked for.
                                armedToClose = false
                                state.update(
                                    lastStripY,
                                    inward.coerceIn(0f, MAX_PULL_DP * density),
                                    lastLetter,
                                    shiftPx = shiftPx,
                                )
                                // The list itself owns the hit test: it is the only thing
                                // that knows where its rows ended up.
                                state.noteAppsFinger(zoneOrigin.x + x, y)
                                val before = state.hoverKey
                                state.refreshHover()
                                if (state.hoverKey != before && state.hoverKey != null) {
                                    HapticUtil.tick(view, hapticFeedback)
                                }
                                return
                            }
                        }
                        // With the strip set to follow, running off an end pushes the whole
                        // strip along rather than leaving it behind, and it stays pushed: coming
                        // back across it picks letters where they now are, and only running off
                        // the other end pushes it back.
                        if (followPastEnds && enteredBand) {
                            if (y - shiftPx < band.topPx) shiftPx = y - band.topPx
                            else if (y - shiftPx > band.bottomPx) shiftPx = y - band.bottomPx
                        }
                        val onStrip = y - shiftPx
                        // Being pushed past the last letter is asking for the end of the list.
                        val atEnd = followPastEnds && shiftPx > 0f && onStrip >= band.bottomPx - 0.5f
                        // Same geometry the visible strip uses, so the letter under the
                        // fingertip is the one that swells.
                        val index = ScrubberGeometry.indexForY(onStrip, band.topPx, band.heightPx, letters.size)
                        if (enteredBand && index != lastIndex) {
                            lastIndex = index
                            HapticUtil.tick(view, hapticFeedback)
                        }
                        if (enteredBand && !armedToClose) {
                            lastStripY = y
                            lastLetter = letters.getOrNull(index)
                        }
                        // Past the top of the letters by more than a thumb's width. Only
                        // the top dismisses: scrubbing down to Z and lifting off the bottom
                        // must park the list at Z, not close it. The overshoot still matters:
                        // a short one clamps to A, which is what lets the first letter be
                        // grabbed from outside its own position, and only past that does
                        // leaving mean leaving.
                        // Following takes the overshoot out of the equation entirely: the
                        // finger running off an end is a request to keep going, not to leave.
                        val off = !followPastEnds && enteredBand && y < band.topPx - overshoot
                        if (off != armedToClose) {
                            armedToClose = off
                            // Said out loud, or nothing tells you that letting go now does
                            // something other than what it did a moment ago.
                            HapticUtil.tick(view, hapticFeedback)
                        }
                        state.update(
                            y,
                            inward.coerceIn(0f, MAX_PULL_DP * density),
                            // No letter while the list is on its way out: placing it at A as
                            // you leave is work nobody asked for and nobody sees.
                            if (enteredBand && !armedToClose) letters.getOrNull(index) else null,
                            shiftPx = shiftPx,
                            atEnd = atEnd,
                        )
                    }

                    report(down.position.x, down.position.y)

                    // Two things start the same way, told apart by whether the finger moves:
                    // travelling is a scrub, letting go without it is a tap that a second tap
                    // turns into a lock. Opening the list on the down is untouched by either.
                    var moved = false
                    // Where the finger last actually went somewhere, and when. Resting on a
                    // letter still delivers events — a fingertip is never quite still — so the
                    // hold is judged by distance travelled rather than by events arriving.
                    var restAt = down.position
                    // The dwell is timed beside the gesture rather than inside it. Waiting for
                    // the wait with a timeout around awaitPointerEvent meant abandoning and
                    // restarting the await every few frames, and an event arriving in one of
                    // those gaps was simply lost — which showed up as the strip lagging and its
                    // letters jumping, for everyone, whether this option was on or not.
                    var holdJob: Job? = null
                    fun armHold() {
                        holdJob?.cancel()
                        if (!holdScroll) return
                        holdJob = scope.launch {
                            delay(HOLD_START_MS)
                            // Read when it fires, not when it was armed: by now the finger may
                            // have left the letters or crossed the line that means leaving.
                            if (enteredBand && !armedToClose) state.holdScroll(true)
                        }
                    }
                    fun cancelHold() {
                        holdJob?.cancel()
                        holdJob = null
                        state.holdScroll(false)
                    }
                    armHold()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if ((change.position - restAt).getDistance() > HOLD_SLOP_DP * density) {
                            // Moved on: this is a scrub again, not a rest. The clock restarts
                            // from here rather than from where the finger first landed.
                            restAt = change.position
                            cancelHold()
                            armHold()
                        }
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

                    cancelHold()
                    // Asked again before the spring clears anything: the row under a still
                    // finger can have changed while the list settled, and opening the one it
                    // was over a moment ago is opening the wrong app.
                    state.refreshHover()
                    val releasedOn = state.hoverKey
                    val effect = if (releaseToLaunch) {
                        releaseEffect(state.onApps, releasedOn)
                    } else {
                        ReleaseEffect.PARK
                    }
                    scope.launch { state.release() }
                    when (effect) {
                        ReleaseEffect.LAUNCH -> currentReleaseToLaunch(releasedOn!!)
                        ReleaseEffect.DISMISS -> currentDismiss()
                        ReleaseEffect.PARK -> Unit
                    }
                    if (effect != ReleaseEffect.PARK) return@awaitEachGesture

                    // Dragged off the end and let go: the whole gesture was open, look, leave,
                    // without the finger coming up in between.
                    if (armedToClose) {
                        currentDismiss()
                        return@awaitEachGesture
                    }

                    if (!moved) {
                        val doubleTap = currentDoubleTap
                        val withinWindow = doubleTap != null &&
                            down.uptimeMillis - state.lastTapUptimeMs <= DOUBLE_TAP_WINDOW_MS
                        when (stripTapAction(withinWindow, state.lastTapEffect)) {
                            StripTap.CLOSE -> {
                                // The first tap opened the list; this one is the "oops, close
                                // it again" tap, not a request for the lock.
                                state.lastTapUptimeMs = 0L
                                state.lastTapEffect = TapEffect.NONE
                                currentDismiss()
                            }
                            StripTap.LOCK -> {
                                state.lastTapUptimeMs = 0L
                                state.lastTapEffect = TapEffect.NONE
                                doubleTap?.invoke()
                            }
                            StripTap.RESTART -> {
                                state.lastTapUptimeMs = down.uptimeMillis
                                // Nothing was picked, so this was a tap on bare strip. Above
                                // and below the letters the strip is empty space like the
                                // space above the list, and it dismisses for the same reason.
                                val dismissed = !enteredBand && openAtDown
                                if (dismissed) currentDismiss()
                                state.lastTapEffect = when {
                                    !openAtDown -> TapEffect.OPENED
                                    dismissed -> TapEffect.DISMISSED
                                    else -> TapEffect.NONE
                                }
                            }
                        }
                    }
                }
            }
            // On the same node as the gesture above, so a press tests against the exclusion
            // in the same coordinates it arrives in — not the padded outer box.
            .onGloballyPositioned { zoneOrigin = it.positionInRoot() },
    )
}