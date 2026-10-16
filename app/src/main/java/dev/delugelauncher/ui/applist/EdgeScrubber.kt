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
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    val density = LocalDensity.current.density
    val spacingPx = band.heightPx / letters.size
    // Wide enough that a good stretch of the alphabet takes part in the curve.
    val sigmaPx = 2.6f * spacingPx
    val bellPx = BELL_AMPLITUDE_DP * density

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
            val centerY = ScrubberGeometry.letterCenterY(index, band.topPx, band.heightPx, letters.size)
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
                        val y = scrubY()
                        val gain = if (y == null) {
                            0f
                        } else {
                            val d = y - centerY
                            exp(-(d * d) / (2f * sigmaPx * sigmaPx))
                        }

                        // Position only for the scrub curve; the spotlight scales
                        // instead, and stays under the size that used to go pixelated.
                        val outward = bellPx * gain + pullPx() * gain
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