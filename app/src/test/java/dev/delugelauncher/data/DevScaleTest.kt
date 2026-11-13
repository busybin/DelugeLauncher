// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Developer mode's fake screen size: the range it may reach and what it does to the density. */
class DevScaleTest {

    @Test
    fun `a hundred percent leaves the density alone`() {
        assertEquals(2.75f, DevScale.density(2.75f, 100), 0.0001f)
    }

    @Test
    fun `a lower percentage fits more dp on the same panel`() {
        // 70 percent of the density is a screen that holds ~1.4x the dp: a bigger phone.
        assertEquals(1.925f, DevScale.density(2.75f, 70), 0.0001f)
        assertEquals(4.125f, DevScale.density(2.75f, 150), 0.0001f)
    }

    @Test
    fun `out of range values are clamped instead of accepted`() {
        assertEquals(MIN_DEV_SCALE, DevScale.clamped(-40))
        assertEquals(MAX_DEV_SCALE, DevScale.clamped(400))
        assertEquals(100, DevScale.clamped(100))
    }
}
