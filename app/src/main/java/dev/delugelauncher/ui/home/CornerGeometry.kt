// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.home

import androidx.compose.ui.geometry.Offset

/** The corner button's diameter, in dp. Drawing, hit testing and placement all read this. */
internal const val CORNER_BUTTON_DP = 56

/** How far the button keeps off the system bars and the screen edges, in dp. */
internal const val CORNER_EDGE_MARGIN_DP = 12

/** Where the button parks before anyone drags it, in dp from the screen's right and bottom. */
internal const val CORNER_DEFAULT_MARGIN_DP = 28

/** How long the button waits, held, before a finger owns it for a slide. */
internal const val CORNER_HOLD_MS = 350L

/**
 * Everything placement needs about the room the button can move in, in pixels.
 *
 * [insetLeft] and friends are the system bars; the button keeps [marginPx] clear of them
 * and of the screen edges on every side.
 */
internal data class CornerFrame(
    val viewportW: Float,
    val viewportH: Float,
    val buttonPx: Float,
    val marginPx: Float,
    val insetLeft: Float,
    val insetTop: Float,
    val insetRight: Float,
    val insetBottom: Float,
)

/**
 * The corner button's positions, as pure functions so they can be tested off the device.
 *
 * A position is stored as the disc's center over the viewport: two fractions in 0..1.
 * Center-based on purpose, because a top-left would shift every time the disc's size or
 * the margin changed, and the stored value would drift with them.
 */
internal object CornerGeometry {

    /** Top-left of the disc for a stored center, kept inside the bars and the margin. */
    fun topLeft(fx: Float, fy: Float, f: CornerFrame): Offset {
        val wantX = fx * f.viewportW - f.buttonPx / 2f
        val wantY = fy * f.viewportH - f.buttonPx / 2f
        return Offset(
            axis(wantX, f.insetLeft + f.marginPx, f.viewportW - f.insetRight - f.marginPx - f.buttonPx),
            axis(wantY, f.insetTop + f.marginPx, f.viewportH - f.insetBottom - f.marginPx - f.buttonPx),
        )
    }

    /**
     * Clamps one axis into [lo, hi]. When the room is smaller than the disc, centres it in
     * the room rather than letting it fall off one side.
     */
    private fun axis(wanted: Float, lo: Float, hi: Float): Float =
        if (hi < lo) (lo + hi) / 2f else wanted.coerceIn(lo, hi)

    /**
     * Where the button sits before anyone has moved it: bottom right, the same distance from
     * the right edge as from the bottom. The letter strip yields to the button's measured
     * rect on its own, so no avoidance is baked in here.
     */
    fun defaultCenter(f: CornerFrame, marginPx: Float): Offset {
        val half = f.buttonPx / 2f
        return Offset(f.viewportW - marginPx - half, f.viewportH - marginPx - half)
    }

    /** [defaultCenter] as stored fractions, clamped to the viewport. */
    fun defaultFractions(f: CornerFrame, marginPx: Float): Pair<Float, Float> {
        val c = defaultCenter(f, marginPx)
        return (c.x / f.viewportW).coerceIn(0f, 1f) to (c.y / f.viewportH).coerceIn(0f, 1f)
    }
}
