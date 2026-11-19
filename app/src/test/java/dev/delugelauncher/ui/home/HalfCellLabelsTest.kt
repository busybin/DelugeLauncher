// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.home

import dev.delugelauncher.data.TwoColumnLabels
import org.junit.Assert.assertEquals
import org.junit.Test

/** The half-cell name placement, including the look a phone had before the setting existed. */
class HalfCellLabelsTest {

    @Test
    fun `automatic keeps the pre-setting look on a narrow cell`() {
        assertEquals(true to false, stackHalfCellNames(TwoColumnLabels.AUTO, 120f))
    }

    @Test
    fun `beside never stacks and under stacks both`() {
        assertEquals(false to false, stackHalfCellNames(TwoColumnLabels.BESIDE, 120f))
        assertEquals(true to true, stackHalfCellNames(TwoColumnLabels.UNDER, 120f))
    }

    @Test
    fun `a wide cell and an unmeasured one stack nothing`() {
        assertEquals(false to false, stackHalfCellNames(TwoColumnLabels.AUTO, 200f))
        assertEquals(false to false, stackHalfCellNames(TwoColumnLabels.UNDER, 200f))
        assertEquals(false to false, stackHalfCellNames(TwoColumnLabels.UNDER, null))
    }
}
