// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeZoneWidthTest {

    @Test
    fun `the setting is the whole reach, so the system edge comes out of it`() {
        // 40dp of reach on a phone whose back gesture claims 24dp leaves a 16dp strip.
        assertEquals(16f, edgeZoneWidth(40.dp, 24.dp).value, 0.01f)
    }

    @Test
    fun `no system edge leaves the reach as the strip's width`() {
        assertEquals(56f, edgeZoneWidth(56.dp, 0.dp).value, 0.01f)
    }

    @Test
    fun `a reach inside the system edge still leaves a strip worth hitting`() {
        assertEquals(12f, edgeZoneWidth(20.dp, 24.dp).value, 0.01f)
    }
}
