// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubberGeometryTest {

    @Test
    fun `favorites tall enough keep their own span`() {
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 400f, bottomPx = 1000f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(400f, band.topPx, 0.01f)
        assertEquals(600f, band.heightPx, 0.01f)
    }

    @Test
    fun `a single favorite still gets a strip worth aiming at`() {
        // One row tall: the whole alphabet had a row's height to share between its letters.
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 900f, bottomPx = 1000f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(400f, band.heightPx, 0.01f)
        // Grown around its own middle, so it stays where the favorites are.
        assertEquals(950f, band.topPx + band.heightPx / 2f, 0.01f)
    }

    @Test
    fun `growing never pushes the band off either end`() {
        val atTop = ScrubberGeometry.bandForFavorites(
            topPx = 0f, bottomPx = 50f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(0f, atTop.topPx, 0.01f)

        val atBottom = ScrubberGeometry.bandForFavorites(
            topPx = 1950f, bottomPx = 2000f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(2000f, atBottom.topPx + atBottom.heightPx, 0.01f)
    }

    @Test
    fun `a minimum taller than the screen is capped by the screen`() {
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 100f, bottomPx = 200f, viewportPx = 600f, minHeightPx = 900f,
        )
        assertEquals(600f, band.heightPx, 0.01f)
        assertEquals(0f, band.topPx, 0.01f)
    }

    @Test
    fun `no favorites measured yet is not grown into nonsense`() {
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 500f, bottomPx = 400f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertTrue(band.heightPx >= 0f)
    }

    @Test
    fun `the letter under a fingertip is the one that lights`() {
        // The invariant the whole strip hangs on: the strip draws letter i at
        // letterCenterY(i), and a finger at that height maps back to i. The touch zone and
        // the strip must be handed the same band for this to hold — a second, shorter band
        // (the idle strip used to squeeze one around the corner button) lights a letter
        // that is not the one under the finger.
        val band = ScrubBand(topPx = 1000f, heightPx = 1200f)
        val count = 26
        for (i in 0 until count) {
            val center = ScrubberGeometry.letterCenterY(i, band.topPx, band.heightPx, count)
            assertEquals(i, ScrubberGeometry.indexForY(center, band.topPx, band.heightPx, count))
        }
        // Half a cell either way still lands in the same letter's cell.
        val spacing = band.heightPx / count
        val center = ScrubberGeometry.letterCenterY(12, band.topPx, band.heightPx, count)
        assertEquals(12, ScrubberGeometry.indexForY(center - spacing / 2f + 1f, band.topPx, band.heightPx, count))
        assertEquals(12, ScrubberGeometry.indexForY(center + spacing / 2f - 1f, band.topPx, band.heightPx, count))
    }

    @Test
    fun `a bottom-corner button covers the end of the band it sits in`() {
        val band = ScrubBand(topPx = 1000f, heightPx = 1200f)
        val covered = ScrubberGeometry.coveredSpan(
            band = band,
            buttonTopPx = 2100f,
            buttonBottomPx = 2250f,
            buttonCenterX = 900f,
            buttonOnScreen = true,
            viewportWidthPx = 1000,
            viewportHeightPx = 2400,
            stripOnLeft = false,
            marginPx = 33f,
        )
        // From a thumb's width above the button to the end of the band: the letters under
        // it and below it go missing, the ones above keep their positions.
        assertEquals(2067f, covered!!.start, 0.01f)
        assertEquals(2200f, covered.endInclusive, 0.01f)
    }

    @Test
    fun `a top-corner button covers the start of the band instead`() {
        val band = ScrubBand(topPx = 200f, heightPx = 1200f)
        val covered = ScrubberGeometry.coveredSpan(
            band = band,
            buttonTopPx = 60f,
            buttonBottomPx = 250f,
            buttonCenterX = 900f,
            buttonOnScreen = true,
            viewportWidthPx = 1000,
            viewportHeightPx = 2400,
            stripOnLeft = false,
            marginPx = 33f,
        )
        assertEquals(200f, covered!!.start, 0.01f)
        assertEquals(283f, covered.endInclusive, 0.01f)
    }

    @Test
    fun `a button clear of the band covers nothing`() {
        // A button above the band used to be cut in anyway, which stretched the band past
        // its own top and left the letters spread wider than the scrub mapped the finger.
        val above = ScrubberGeometry.coveredSpan(
            band = ScrubBand(topPx = 1000f, heightPx = 1200f),
            buttonTopPx = 60f,
            buttonBottomPx = 250f,
            buttonCenterX = 900f,
            buttonOnScreen = true,
            viewportWidthPx = 1000,
            viewportHeightPx = 2400,
            stripOnLeft = false,
            marginPx = 33f,
        )
        assertNull(above)

        val below = ScrubberGeometry.coveredSpan(
            band = ScrubBand(topPx = 1000f, heightPx = 1200f),
            buttonTopPx = 2300f,
            buttonBottomPx = 2450f,
            buttonCenterX = 900f,
            buttonOnScreen = true,
            viewportWidthPx = 1000,
            viewportHeightPx = 2400,
            stripOnLeft = false,
            marginPx = 33f,
        )
        assertNull(below)
    }

    @Test
    fun `a button on the far side from the strip covers nothing`() {
        val covered = ScrubberGeometry.coveredSpan(
            band = ScrubBand(topPx = 1000f, heightPx = 1200f),
            buttonTopPx = 2100f,
            buttonBottomPx = 2250f,
            buttonCenterX = 100f,
            buttonOnScreen = true,
            viewportWidthPx = 1000,
            viewportHeightPx = 2400,
            stripOnLeft = false,
            marginPx = 33f,
        )
        assertNull(covered)
    }

    @Test
    fun `no button on screen covers nothing`() {
        val covered = ScrubberGeometry.coveredSpan(
            band = ScrubBand(topPx = 1000f, heightPx = 1200f),
            buttonTopPx = 2100f,
            buttonBottomPx = 2250f,
            buttonCenterX = 900f,
            buttonOnScreen = false,
            viewportWidthPx = 1000,
            viewportHeightPx = 2400,
            stripOnLeft = false,
            marginPx = 33f,
        )
        assertNull(covered)
    }
}
