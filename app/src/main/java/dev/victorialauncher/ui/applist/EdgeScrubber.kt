// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.data.EdgeSide
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
 * Where the letter bubble starts, kept a fixed gap beyond the peak of the bell.
 *
 * Derived rather than written down twice: the bubble sat at a number of its own, so growing
 * the bulge to clear a fingertip walked the strip straight into it.
 */
const val SCRUB_BUBBLE_INSET_DP = EDGE_INSET_DP + BELL_AMPLITUDE_DP + LETTER_HALF_DP + 18f

/**
 * The A-Z strip. The letters never change size — the *column* bows outward around the
 * fingertip on a gaussian, so the alphabet traces a bell curve and settles flat again when
 * the finger lifts. Scaling the glyphs (what this used to do) just stretched them until
 * they looked pixelated.
 *
 * [scrubY] and [pullPx] are read as lambdas inside a graphicsLayer so the whole strip
 * animates in the draw phase; recomposing 26 Text nodes per pointer move made this jitter.
 */
@Composable
fun EdgeScrubber(
    letters: List<Char>,
    scrubY: () -> Float?,
    pullPx: () -> Float,
    band: ScrubBand,
    side: EdgeSide,
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

            Box(
                modifier = Modifier
                    .align(if (side == EdgeSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .offset { IntOffset(0, (centerY - spacingPx / 2f).roundToInt()) }
                    .width(20.dp)
                    .graphicsLayer {
                        val y = scrubY()
                        val gain = if (y == null) {
                            0f
                        } else {
                            val d = y - centerY
                            exp(-(d * d) / (2f * sigmaPx * sigmaPx))
                        }

                        // Position only — the glyph is never scaled.
                        val outward = bellPx * gain + pullPx() * gain
                        translationX = if (side == EdgeSide.LEFT) outward else -outward
                        alpha = 0.55f + 0.45f * gain
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = c.toString(), fontSize = 13.sp, color = Color.White)
            }
        }
    }
}