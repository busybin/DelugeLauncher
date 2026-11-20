// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The three-way frozen look and the old on/off switch it replaced. */
class FrozenLookTest {

    @Test
    fun `a stored look wins over whatever the legacy switch says`() {
        assertEquals(FrozenLook.GREYSCALE, frozenLookOf("GREYSCALE", true))
        assertEquals(FrozenLook.NORMAL, frozenLookOf("NORMAL", true))
        assertEquals(FrozenLook.DIMMED, frozenLookOf("DIMMED", false))
    }

    @Test
    fun `an install with only the old switch keeps its answer`() {
        // The switch meant "fade paused apps"; on is DIMMED, off is what it looks like today.
        assertEquals(FrozenLook.DIMMED, frozenLookOf(null, true))
        assertEquals(FrozenLook.NORMAL, frozenLookOf(null, false))
    }

    @Test
    fun `a first run without either key fades, as it always did`() {
        assertEquals(FrozenLook.DIMMED, frozenLookOf(null, null))
    }

    @Test
    fun `an unknown or corrupt value falls back instead of failing`() {
        assertEquals(FrozenLook.DIMMED, frozenLookOf("SEPIA", null))
        assertEquals(FrozenLook.DIMMED, frozenLookOf("", null))
    }
}
