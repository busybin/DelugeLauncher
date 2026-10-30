// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.delugelauncher.data.EdgeSide
import dev.delugelauncher.data.RippleParams
import dev.delugelauncher.data.ScrubStyle
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** Letters sit this far in from the screen edge so they aren't crowding it. */
private const val EDGE_INSET_DP = 28f

/**
 * How far the bulge pushes the column away from the edge at its peak.
 *
 * Has to clear the fingertip without reaching the bubble. The thumb is already an edge-zone in
 * from the screen edge, so at 75dp the peak landed about 7dp from it — underneath, hidden by
 * the hand pointing at it — while the bubble beginning at 122dp left only about 19dp of room
 * to move into. The two only go further out together, which is what [SCRUB_BUBBLE_INSET_DP]
 * is for.
 */
private const val BELL_AMPLITUDE_DP = 112f

/** Half the letter cell, so the peak is measured by its edge rather than its middle. */
private const val LETTER_HALF_DP = 10f

/**
 * How much bigger the spotlighted section letter is drawn. Kept well under the scale
 * that used to go pixelated: this one letter is the exception to never scaling glyphs.
 */
private const val SPOTLIGHT_SCALE = 1.6f

/**
 * Where the letter bubble starts, kept a fixed gap beyond the peak of the bell.
 *
 * Derived rather than written down twice: the bubble sat at a number of its own, so growing
 * the bulge to clear a fingertip walked the strip straight into it.
 */
const val SCRUB_BUBBLE_INSET_DP = EDGE_INSET_DP + BELL_AMPLITUDE_DP + LETTER_HALF_DP + 18f

// ---- Ripple simulation -----------------------------------------------------------------------
private const val SIM_DT = 1f / 240f

/**
 * A 1D damped wave equation sampled at the letters, written for the deviation
 * w = u − bell from the bell curve under the finger:
 *
 *   u_tt = c²·∂²w/∂x² − γ·u_t − k·w
 *
 * With the finger held still the bell is constant, so w decays to zero and the strip comes to
 * rest on exactly the bell curve. Moving the finger drags the bell away from the strip, which
 * then springs after it and sheds ripples along the way; lifting it drops the bell to zero
 * and the strip rings back down to flat.
 */
private class RippleString(private val n: Int) {
    val u = FloatArray(n)          // outward displacement, px
    private val v = FloatArray(n)
    private val w = FloatArray(n)
    private val a = FloatArray(n)

    fun step(dt: Float, bell: FloatArray, p: RippleParams) {
        val c2 = p.speed * p.speed
        for (i in 0 until n) w[i] = u[i] - bell[i]
        for (i in 0 until n) {
            val left = if (i > 0) w[i - 1] else 0f           // ends pinned to the bell's edge
            val right = if (i < n - 1) w[i + 1] else 0f
            val laplacian = left - 2f * w[i] + right
            a[i] = c2 * laplacian - p.damping * v[i] - p.stiffness * w[i]
        }
        for (i in 0 until n) {                                // semi-implicit Euler
            v[i] += a[i] * dt
            u[i] += v[i] * dt
        }
    }

    fun isAtRest(): Boolean {
        for (i in 0 until n) if (abs(u[i]) > 0.05f || abs(v[i]) > 0.5f) return false
        return true
    }

    fun reset() {
        u.fill(0f)
        v.fill(0f)
    }
}

/**
 * The A-Z strip. The letters never change size for the scrub curve — the *column* bows
 * outward around the fingertip on a gaussian, so the alphabet traces a bell curve and
 * settles flat again when the finger lifts. Scaling the glyphs for that (what this used
 * to do) just stretched them until they looked pixelated.
 *
 * The exception is [spotlights]: the sections with a row on screen while nobody is
 * scrubbing. They are drawn enlarged, but they change per section rather than per pixel,
 * so the strip only recomposes when the set changes.
 *
 * [scrubY] and [pullPx] are read as lambdas inside a graphicsLayer so the whole strip
 * animates in the draw phase; recomposing 26 Text nodes per pointer move made this jitter.
 */
@Composable
fun EdgeScrubber(
    letters: List<String>,
    scrubY: () -> Float?,
    pullPx: () -> Float,
    band: ScrubBand,
    side: EdgeSide,
    /** Sections with a row on screen while nobody is scrubbing, drawn enlarged. */
    spotlights: Set<String> = emptySet(),
    /**
     * A stretch of the band where letters are not drawn, because the corner button sits
     * over it. Only a gap: the letters either side keep the positions a scrub maps a
     * finger to, so the letter under the fingertip is the one that lights.
     */
    coveredPx: ClosedFloatingPointRange<Float>? = null,
    style: ScrubStyle = ScrubStyle.BELL,
    ripple: RippleParams = RippleParams(),
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    val density = LocalDensity.current.density
    val spacingPx = band.heightPx / letters.size
    // Wide enough that a good stretch of the alphabet takes part in the curve.
    val sigmaPx = 2.6f * spacingPx
    val bellPx = BELL_AMPLITUDE_DP * density
    val centerYs = remember(letters.size, band.topPx, band.heightPx) {
        FloatArray(letters.size) {
            ScrubberGeometry.letterCenterY(it, band.topPx, band.heightPx, letters.size)
        }
    }
    val sim = remember(letters.size) { RippleString(letters.size) }
    // Read only inside graphicsLayer, so each simulation tick invalidates the draw phase alone.
    var frame by remember { mutableIntStateOf(0) }
    val scrubYState by rememberUpdatedState(scrubY)
    val pullState by rememberUpdatedState(pullPx)
    val rippleState by rememberUpdatedState(ripple)

    LaunchedEffect(style, sim, centerYs, sigmaPx, bellPx) {
        if (style != ScrubStyle.RIPPLE) {
            sim.reset()
            return@LaunchedEffect
        }
        val bell = FloatArray(centerYs.size)
        var last = 0L
        var acc = 0f
        while (true) {
            // Idle until the finger lands (or until something is still rippling).
            if (sim.isAtRest() && scrubYState() == null) {
                sim.reset()
                frame++
                snapshotFlow { scrubYState() != null }.first { it }
                last = 0L
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) SIM_DT else ((now - last) / 1e9f).coerceAtMost(1f / 30f)
                last = now
                acc += dt

                val y = scrubYState()
                val amplitude = bellPx + pullState()
                for (i in bell.indices) {
                    bell[i] = if (y == null) 0f else {
                        val d = y - centerYs[i]
                        amplitude * exp(-(d * d) / (2f * sigmaPx * sigmaPx))
                    }
                }
                val params = rippleState
                while (acc >= SIM_DT) {
                    sim.step(SIM_DT, bell, params)
                    acc -= SIM_DT
                }
                frame++
            }
        }
    }

    Box(
        modifier = modifier
            // Wide enough for the inset, the letter cell and the whole outward bulge — at
            // 56dp the horizontal padding ate the entire width and the curve had nowhere to
            // go, and the bulge has since grown enough to clear a fingertip.
            .width(210.dp)
            .fillMaxHeight()
            .padding(horizontal = EDGE_INSET_DP.dp),
    ) {
        letters.forEachIndexed { index, c ->
            val centerY = centerYs[index]
            // Read once per composition, not per frame: the lit set changes per
            // section while the finger moves per pixel.
            val lit = c in spotlights
            // Only the letters whose cell the button actually reaches go missing — the
            // rest stay exactly where the scrub expects to find them.
            val covered = coveredPx != null &&
                centerY + spacingPx / 2f > coveredPx.start &&
                centerY - spacingPx / 2f < coveredPx.endInclusive
            Box(
                modifier = Modifier
                    .align(if (side == EdgeSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .offset { IntOffset(0, (centerY - spacingPx / 2f).roundToInt()) }
                    .width(20.dp)
                    // Fixed cell so an enlarged letter stays centred on its row
                    // instead of pushing its own top edge down.
                    .height((spacingPx / density).dp)
                    .graphicsLayer {
                        val outward: Float
                        val gain: Float
                        if (style == ScrubStyle.RIPPLE) {
                            @Suppress("UNUSED_VARIABLE") val tick = frame // subscribe to sim updates
                            // Ripples overshoot negative; keep letters off the screen edge.
                            outward = sim.u[index].coerceAtLeast(-EDGE_INSET_DP * density)
                            gain = (abs(outward) / bellPx).coerceIn(0f, 1f)
                        } else {
                            val y = scrubY()
                            gain = if (y == null) {
                                0f
                            } else {
                                val d = y - centerY
                                exp(-(d * d) / (2f * sigmaPx * sigmaPx))
                            }
                            outward = bellPx * gain + pullPx() * gain
                        }

                        // Position only for the scrub curve; the spotlight scales
                        // instead, and stays under the size that used to go pixelated.
                        translationX = if (side == EdgeSide.LEFT) outward else -outward
                        val s = if (lit) SPOTLIGHT_SCALE else 1f
                        scaleX = s
                        scaleY = s
                        alpha = if (covered) 0f else maxOf(0.55f + 0.45f * gain, if (lit) 1f else 0f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = c,
                    // A tag shows three characters where a letter shows one. Smaller, so it
                    // fits the cell; centred on the same line, so it sits level with them.
                    fontSize = if (c.length == 1) 13.sp else 9.sp,
                    maxLines = 1,
                    // Always the same weight: the spotlight already scales the letter 1.6x
                    // in the draw phase, and toggling Bold here re-laid-out every letter of
                    // the strip on each section change mid-scrub.
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                )
            }
        }
    }
}