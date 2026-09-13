// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.victorialauncher.R
import dev.victorialauncher.service.HapticUtil
import kotlin.math.roundToInt

/** Every reorderable row is this tall, so a drag works out where it landed by division. */
val REORDER_ROW_HEIGHT = 60.dp

/**
 * A short list of keys that can be dragged into a different order.
 *
 * Deliberately not lazy, and deliberately fixed height. Both are what make the drag simple:
 * nothing recycles the row under the finger, and how many places it has travelled is the
 * offset over one row. The lists this serves — a folder's members, the favorites — are short
 * enough that laziness would buy nothing.
 *
 * [onReorder] fires once, when the finger lifts, so the store is not rewritten on every frame
 * of a drag.
 */
@Composable
fun ReorderableRows(
    keys: List<String>,
    onReorder: (List<String>) -> Unit,
    row: @Composable RowScope.(key: String) -> Unit,
) {
    val view = LocalView.current
    val rowPx = with(LocalDensity.current) { REORDER_ROW_HEIGHT.toPx() }

    // Keyed on the stored list, so a key added or removed elsewhere resets the working copy.
    // While a drag is in flight this is what gets drawn, since nothing is written until the
    // finger lifts.
    var order by remember(keys) { mutableStateOf(keys) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        order.forEachIndexed { index, key ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(REORDER_ROW_HEIGHT)
                    .zIndex(if (draggingIndex == index) 1f else 0f)
                    .graphicsLayer {
                        if (draggingIndex == index) {
                            translationY = dragOffset
                            alpha = 0.9f
                        }
                    }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row(key)
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.home_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(40.dp)
                        .pointerInput(index, order.size) {
                            detectDragGestures(
                                onDragStart = {
                                    // The row does not move until the finger does, so a tick
                                    // is the only confirmation that the grab took.
                                    HapticUtil.tick(view, true)
                                    draggingIndex = index
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    if (draggingIndex >= 0) onReorder(order)
                                    draggingIndex = -1
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffset = 0f
                                },
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val shift = (dragOffset / rowPx).roundToInt()
                                if (shift != 0) {
                                    val from = draggingIndex
                                    val to = (from + shift).coerceIn(0, order.lastIndex)
                                    if (to != from) {
                                        order = order.toMutableList().apply { add(to, removeAt(from)) }
                                        // The row has moved under the finger, so the offset it
                                        // is drawn at has to come back by as much.
                                        dragOffset -= (to - from) * rowPx
                                        draggingIndex = to
                                        HapticUtil.tick(view, true)
                                    }
                                }
                            }
                        },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        }
    }
}
