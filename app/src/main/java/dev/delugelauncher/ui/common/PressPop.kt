// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Pops the icon down while the row is pressed and springs it back on release.
 *
 * The rows already dim their background on press; the icon answers with a squash of its
 * own, so the tap lands visibly on the thing that was tapped rather than around it.
 * Reads the row's own press interaction, so no new gesture detector fights the row's.
 */
@Composable
fun Modifier.pressPop(
    interaction: InteractionSource,
    pressedScale: Float = 0.85f,
): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressPop",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
