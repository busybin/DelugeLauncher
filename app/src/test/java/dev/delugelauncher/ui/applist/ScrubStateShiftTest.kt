// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import dev.delugelauncher.data.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubStateShiftTest {
    @Test
    fun `pushed past the last letter asks for the end of the list`() {
        val state = ScrubState()
        state.begin(EdgeSide.RIGHT)
        state.update(y = 900f, inwardPx = 0f, letter = "Z", shiftPx = 60f, atEnd = true)
        assertNull(state.letter)
        assertEquals("Z", state.placement?.letter)
        assertTrue(state.placement?.atEnd == true)
    }

    @Test
    fun `coming back onto the strip drops the end request`() {
        val state = ScrubState()
        state.begin(EdgeSide.RIGHT)
        state.update(y = 900f, inwardPx = 0f, letter = "Z", shiftPx = 60f, atEnd = true)
        state.update(y = 800f, inwardPx = 0f, letter = "M")
        assertEquals("M", state.letter)
        assertFalse(state.placement?.atEnd ?: true)
    }

    @Test
    fun `the pushed strip springs home when the gesture is cancelled`() {
        val state = ScrubState()
        state.begin(EdgeSide.RIGHT)
        state.update(y = 900f, inwardPx = 0f, letter = "Z", shiftPx = 40f)
        assertEquals(40f, state.currentShift(), 0.01f)
        state.cancel()
        assertEquals(0f, state.currentShift(), 0.01f)
    }
}
