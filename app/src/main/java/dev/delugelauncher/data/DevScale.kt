// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

/**
 * Developer mode's fake screen size, kept pure so the arithmetic is testable.
 *
 * The launcher lays everything out in dp, so the only lever needed is the density: a lower
 * density fits more dp on the same panel and reads as a bigger phone, and a higher one does
 * the opposite. 100 percent is the device as it is.
 */
object DevScale {

    fun clamped(percent: Int): Int = percent.coerceIn(MIN_DEV_SCALE, MAX_DEV_SCALE)

    /** The density to lay out at: [baseDensity] scaled by [percent], clamped to the range. */
    fun density(baseDensity: Float, percent: Int): Float =
        baseDensity * clamped(percent) / 100f
}
