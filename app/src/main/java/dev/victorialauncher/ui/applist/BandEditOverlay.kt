// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.R
import dev.victorialauncher.data.EdgeSide
import kotlin.math.roundToInt

/** Small enough to be useless below this, so the two handles can never cross. */
private val MIN_BAND_HEIGHT = 120.dp

/**
 * Sets how much of the screen the A-Z strip spans.
 *
 * The strip normally matches the favorites list, which reads well until somebody keeps three
 * favorites and the alphabet is squeezed into a couple of centimetres. Dragging either handle
 * here pins the range by hand; Reset hands it back to following the favorites.
 */
@Composable
fun BandEditOverlay(
    band: ScrubBand,
    side: EdgeSide,
    viewportHeightPx: Int,
    contentColor: Color,
    onBandChange: (ScrubBand) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val minHeightPx = with(density) { MIN_BAND_HEIGHT.toPx() }
    val alignment = if (side == EdgeSide.LEFT) Alignment.TopStart else Alignment.TopEnd

    fun moveTop(delta: Float) {
        val top = (band.topPx + delta).coerceIn(0f, band.bottomPx - minHeightPx)
        onBandChange(ScrubBand(topPx = top, heightPx = band.bottomPx - top))
    }

    fun moveBottom(delta: Float) {
        val bottom = (band.bottomPx + delta)
            .coerceIn(band.topPx + minHeightPx, viewportHeightPx.toFloat())
        onBandChange(ScrubBand(topPx = band.topPx, heightPx = bottom - band.topPx))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Swallow everything. Without this the scrim is only paint: taps went straight
            // through to the app list behind and launched whatever was under the finger,
            // leaving the editor sitting on top of the app that just opened.
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .align(alignment)
                .offset { IntOffset(0, band.topPx.roundToInt()) }
                .width(96.dp)
                .height(with(density) { band.heightPx.toDp() })
                .background(contentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        )

        BandHandle(alignment, band.topPx, contentColor, R.string.applist_band_top) { moveTop(it) }
        BandHandle(alignment, band.bottomPx, contentColor, R.string.applist_band_bottom) { moveBottom(it) }

        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.applist_band_hint),
                color = contentColor.copy(alpha = 0.8f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.action_reset)) }
                TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

@Composable
private fun BoxScope.BandHandle(
    alignment: Alignment,
    y: Float,
    contentColor: Color,
    descriptionRes: Int,
    onDrag: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val half = with(density) { 22.dp.toPx() }
    Box(
        modifier = Modifier
            .align(alignment)
            .offset { IntOffset(0, (y - half).roundToInt()) }
            .size(88.dp, 44.dp)
            .background(contentColor.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { onDrag(it) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = stringResource(descriptionRes),
            tint = contentColor,
        )
    }
}
