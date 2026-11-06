// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTest {

    @Test
    fun `a running session survives a round trip`() {
        val session = FocusSession(
            active = true,
            targetKey = "com.example.notes/com.example.notes.Main",
            packageName = "com.example.notes",
            label = "Notes",
        )
        assertEquals(session, focusSessionFromJson(focusSessionToJson(session)))
    }

    @Test
    fun `an empty or malformed record is inactive`() {
        assertFalse(focusSessionFromJson(null).active)
        assertFalse(focusSessionFromJson("").active)
        assertFalse(focusSessionFromJson("not json").active)
    }

    @Test
    fun `the reader keeps an empty package so the overlay can report it`() {
        // The store drops a session with no package; JSON can still carry one, so the reader
        // must not invent a target it cannot open.
        val raw = focusSessionToJson(FocusSession(active = true, targetKey = "x", packageName = "", label = "x"))
        val parsed = focusSessionFromJson(raw)
        assertTrue(parsed.active)
        assertTrue(parsed.packageName.isBlank())
    }
}
