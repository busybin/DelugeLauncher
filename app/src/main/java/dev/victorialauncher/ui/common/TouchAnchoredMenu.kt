// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * A dropdown that opens at the finger rather than beneath whatever it happens to sit in.
 *
 * A DropdownMenu is a Popup, and a Popup anchors to the bounds of its *parent layout node*,
 * not to a point — Material then places it at that parent's bottom edge plus the offset. On a
 * row one line tall that error is invisible, but on something tall, like a 300dp widget, the
 * menu lands most of a screen below the press or flips above the whole thing. Giving it a
 * zero-size parent positioned at the touch point makes those bounds the fingertip, so the menu
 * opens against the finger and flips around it when there is no room below.
 */
@Composable
fun TouchAnchoredMenu(
    expanded: Boolean,
    offset: DpOffset,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.offset(x = offset.x, y = offset.y).size(0.dp)) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, content = content)
    }
}
