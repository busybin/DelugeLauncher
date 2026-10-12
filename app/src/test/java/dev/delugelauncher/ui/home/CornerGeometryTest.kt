// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The corner button's placement math, off the device. This is where the coordinate bugs
 * lived — a top-left convention read as a center, a rotation feeding back into the reading
 * that moved the disc — so the convention is pinned here rather than trusted by eye.
 */
class CornerGeometryTest {

    private fun frame(
        w: Float = 1000f,
        h: Float = 2000f,
        button: Float = 112f,
        margin: Float = 24f,
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 0f,
        bottom: Float = 0f,
    ) = CornerFrame(w, h, button, margin, left, top, right, bottom)

    @Test
    fun `a centered fraction lands the disc centered`() {
        val tl = CornerGeometry.topLeft(0.5f, 0.5f, frame())
        assertEquals(444f, tl.x, 0.01f)
        assertEquals(944f, tl.y, 0.01f)
    }

    @Test
    fun `the edges clamp the disc inside the bars and the margin`() {
        val topLeft = CornerGeometry.topLeft(0f, 0f, frame())
        assertEquals(24f, topLeft.x, 0.01f)
        assertEquals(24f, topLeft.y, 0.01f)

        val bottomRight = CornerGeometry.topLeft(1f, 1f, frame())
        assertEquals(864f, bottomRight.x, 0.01f)
        assertEquals(1864f, bottomRight.y, 0.01f)
    }

    @Test
    fun `a system bar is kept clear of, not run under`() {
        // A tall gesture bar: the disc may not sit under it.
        val tl = CornerGeometry.topLeft(1f, 1f, frame(bottom = 200f))
        assertEquals(1664f, tl.y, 0.01f) // 2000 - 200 - 24 - 112
    }

    @Test
    fun `the default is bottom right, equally far from both edges`() {
        val f = frame()
        val c = CornerGeometry.defaultCenter(f, marginPx = 56f)
        assertEquals(1000f - 56f - 56f, c.x, 0.01f)
        assertEquals(2000f - 56f - 56f, c.y, 0.01f)
    }

    @Test
    fun `the default as fractions is the center over the viewport`() {
        val (fx, fy) = CornerGeometry.defaultFractions(frame(), marginPx = 56f)
        assertEquals(888f / 1000f, fx, 0.001f)
        assertEquals(1888f / 2000f, fy, 0.001f)
    }

    @Test
    fun `a viewport smaller than the disc does not throw, it centres`() {
        // Degenerate on purpose: a huge disc in a tiny viewport. The room is negative and
        // the clamp has to fall back to the middle rather than the wrong end.
        val tl = CornerGeometry.topLeft(0f, 0f, frame(w = 50f, h = 50f, button = 112f, margin = 24f))
        assertEquals(-31f, tl.x, 0.01f)
        assertEquals(-31f, tl.y, 0.01f)
    }
}
