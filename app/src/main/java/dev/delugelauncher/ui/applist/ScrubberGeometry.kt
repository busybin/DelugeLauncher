// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

/**
 * One source of truth for where the A-Z strip sits, so the letter your finger is on is the
 * letter that lights up. The strip occupies a band — by default the vertical span of the
 * favorites list — rather than the whole screen, so scrubbing never asks for a stretch.
 */
object ScrubberGeometry {

    /**
     * How much of the screen the strip may take, as a fraction.
     *
     * Half the screen, and half it stays: that is where a thumb rests and where 26
     * letters are still worth aiming at. A strip the height of the whole viewport asks
     * the hand to move instead of the finger, which is the slower way round.
     *
     * Everything that computes a band — the span of the favorites, a range stored by
     * hand, the fallback — is clamped through here, so no arrangement can quietly make
     * it taller than this again.
     */
    const val MAX_HEIGHT_FRACTION = 0.5f

    /**
     * Where the fallback band starts, as a fraction of the screen.
     *
     * Low rather than high: the strip is a thumb control, and a thumb lives at the
     * bottom of a phone-held tall screen. Starting at the top left it in the part of
     * the screen the hand has to travel to reach.
     */
    const val FALLBACK_TOP_FRACTION = 0.42f
    const val FALLBACK_HEIGHT_FRACTION = MAX_HEIGHT_FRACTION

    /** [band] with its height no more than [MAX_HEIGHT_FRACTION] of [viewportPx]. */
    fun capHeight(band: ScrubBand, viewportPx: Float): ScrubBand {
        if (viewportPx <= 0f || band.heightPx <= 0f) return band
        val max = viewportPx * MAX_HEIGHT_FRACTION
        if (band.heightPx <= max) return band
        // The bottom holds still and the top gives way: a stripped-down strip stays
        // anchored where it ended, which is the half of the screen a thumb is in.
        // Keeping the top instead parked the whole thing in the upper half of the
        // screen on any arrangement taller than the cap.
        val top = (band.bottomPx - max).coerceIn(0f, (viewportPx - max).coerceAtLeast(0f))
        return ScrubBand(top, max)
    }

    fun indexForY(y: Float, topPx: Float, heightPx: Float, count: Int): Int {
        if (count <= 0 || heightPx <= 0f) return 0
        val idx = ((y - topPx) / (heightPx / count)).toInt()
        return idx.coerceIn(0, count - 1)
    }

    /** True while [y] is within the band, rather than clamped to one of its ends. */
    fun isWithin(y: Float, topPx: Float, heightPx: Float): Boolean =
        heightPx > 0f && y >= topPx && y <= topPx + heightPx

    /**
     * The band the strip should take, given where the favorites are.
     *
     * Normally exactly their span — the strip sits beside what it belongs to. But the favorites
     * are as tall as there are favorites, and one of them is one row: the whole alphabet then
     * has a row's height to divide between its letters, which is a strip with nothing in it
     * anyone could hit or read. Below a minimum it grows around its own middle instead, so it
     * stays where the favorites are without being their size.
     */
    fun bandForFavorites(
        topPx: Float,
        bottomPx: Float,
        viewportPx: Float,
        minHeightPx: Float,
    ): ScrubBand {
        val height = (bottomPx - topPx).coerceAtLeast(0f)
        if (viewportPx <= 0f || height >= minHeightPx) return ScrubBand(topPx, height)
        val wanted = minHeightPx.coerceAtMost(viewportPx)
        val center = (topPx + bottomPx) / 2f
        val top = (center - wanted / 2f).coerceIn(0f, viewportPx - wanted)
        return ScrubBand(top, wanted)
    }

    /** Screen-space center of the letter at [index]. */
    fun letterCenterY(index: Int, topPx: Float, heightPx: Float, count: Int): Float {
        if (count <= 0) return topPx
        val spacing = heightPx / count
        return topPx + index * spacing + spacing / 2f
    }
}

/** The vertical band the strip is laid out in, in screen pixels. */
data class ScrubBand(val topPx: Float, val heightPx: Float) {
    val bottomPx: Float get() = topPx + heightPx

    companion object {
        fun fallbackFor(viewportHeightPx: Int) = ScrubBand(
            topPx = viewportHeightPx * ScrubberGeometry.FALLBACK_TOP_FRACTION,
            heightPx = viewportHeightPx * ScrubberGeometry.FALLBACK_HEIGHT_FRACTION,
        )
    }
}