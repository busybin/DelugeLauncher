// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

/**
 * One source of truth for where the A-Z strip sits, so the letter your finger is on is the
 * letter that lights up. The strip occupies a band — by default the vertical span of the
 * favorites list — rather than the whole screen, so scrubbing never asks for a stretch.
 */
object ScrubberGeometry {

    /**
     * How much of the screen the strip may take.
     *
     * Half. That is where a thumb rests and where 26 letters are still worth aiming at; a
     * strip the height of the viewport asks the hand to move rather than the finger.
     *
     * Every band goes through here — the favorites' span, a range set by hand, the fallback
     * — so no arrangement can quietly make it taller again.
     */
    const val MAX_HEIGHT_FRACTION = 0.5f

    /**
     * Where the fallback band starts.
     *
     * Low rather than high: the strip is a thumb control, and a thumb lives at the bottom of
     * a tall screen. At the top it sat in the part of the screen the hand travels to reach.
     */
    const val FALLBACK_TOP_FRACTION = 0.42f
    const val FALLBACK_HEIGHT_FRACTION = MAX_HEIGHT_FRACTION

    /** [band] with its height no more than [MAX_HEIGHT_FRACTION] of [viewportPx]. */
    fun capHeight(band: ScrubBand, viewportPx: Float): ScrubBand {
        if (viewportPx <= 0f || band.heightPx <= 0f) return band
        val max = viewportPx * MAX_HEIGHT_FRACTION
        if (band.heightPx <= max) return band
        // Cropped around the band's own middle: keeping the bottom glued the strip to the
        // floor whenever the favorites ran long, and keeping the top parked it up high.
        // The middle of what it belongs to is the only end that is never wrong.
        val center = (band.topPx + band.bottomPx) / 2f
        val top = (center - max / 2f).coerceIn(0f, (viewportPx - max).coerceAtLeast(0f))
        return ScrubBand(top, max)
    }

    /**
     * [band] with its height no less than [MAX_HEIGHT_FRACTION] of [viewportPx].
     *
     * A band shorter than this is a strip of a few letters squeezed into a corner — with
     * only a handful of apps installed, or an alphabet that stops well short of Z, the
     * favorites' span is a few rows high and sits wherever the favorites are. Growing
     * downward from its top keeps it attached to what it belongs to instead of dropping it
     * somewhere unrelated.
     */
    fun floorHeight(band: ScrubBand, viewportPx: Float): ScrubBand {
        if (viewportPx <= 0f || band.heightPx <= 0f) return band
        val min = viewportPx * MAX_HEIGHT_FRACTION
        if (band.heightPx >= min) return band
        val height = min.coerceAtMost(viewportPx)
        return ScrubBand(band.topPx.coerceIn(0f, (viewportPx - height).coerceAtLeast(0f)), height)
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