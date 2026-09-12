// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.R

/** A dp at a time: coarser steps were quicker to travel but never landed where you wanted. */
const val PADDING_STEP_DP = 1
const val HEIGHT_STEP_DP = 1

/** Widest a padding can be pushed; roughly a phone screen. */
private val PADDING_RANGE = 0..400

/**
 * One adjustable value in edit mode: a label, the value, and a pair of steppers.
 *
 * These replaced drag handles. A handle set its value from finger travel that had nothing to
 * do with the thing being moved — a few pixels of drag jumped the padding further than the row
 * it was spacing — so landing on a particular value meant overshooting it back and forth.
 * Stepping is slower and lands exactly where it says.
 */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canDecrease = value > range.first
    val canIncrease = value < range.last
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        IconButton(
            onClick = { onChange((value - step).coerceIn(range.first, range.last)) },
            enabled = canDecrease,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.settings_less),
                tint = Color.White.copy(alpha = if (canDecrease) 0.9f else 0.3f),
            )
        }
        Text("${value}dp", color = Color.White, fontSize = 12.sp)
        IconButton(
            onClick = { onChange((value + step).coerceIn(range.first, range.last)) },
            enabled = canIncrease,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.settings_more),
                tint = Color.White.copy(alpha = if (canIncrease) 0.9f else 0.3f),
            )
        }
        Spacer(Modifier.size(4.dp))
    }
}

/**
 * The gap above or below a home block. Out of edit mode it is only that gap; in edit mode the
 * gap doubles as the control that sets it, so the stepper sits exactly where its effect shows.
 */
@Composable
fun PaddingHandle(
    editMode: Boolean,
    @StringRes label: Int,
    value: Int,
    onChange: (Int) -> Unit,
) {
    if (!editMode) {
        Spacer(Modifier.height(value.dp))
        return
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(maxOf(value, 36).dp),
        contentAlignment = Alignment.Center,
    ) {
        StepperRow(
            label = stringResource(label),
            value = value,
            range = PADDING_RANGE,
            step = PADDING_STEP_DP,
            onChange = onChange,
        )
    }
}
