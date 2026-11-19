// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.home

import dev.delugelauncher.data.TwoColumnLabels

/**
 * Where the two names go in a half cell that is too narrow to keep them beside an icon.
 *
 * Automatic is the look this launcher shipped before the setting existed: the folder stacks
 * and the app beside it does not. Beside takes both back to one line, Under stacks both.
 * [halfCellWidthDp] is null before the screen has been measured, when nothing may stack yet.
 */
internal fun stackHalfCellNames(
    labels: TwoColumnLabels,
    halfCellWidthDp: Float?,
): Pair<Boolean, Boolean> {
    val narrow = halfCellWidthDp != null && halfCellWidthDp < NARROW_HALF_CELL_DP
    return when {
        !narrow -> false to false
        labels == TwoColumnLabels.BESIDE -> false to false
        labels == TwoColumnLabels.UNDER -> true to true
        else -> true to false
    }
}

/** A half cell under this has no room for a name beside its icon. */
internal const val NARROW_HALF_CELL_DP = 170f
