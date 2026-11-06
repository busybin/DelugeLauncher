// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HandoverTest {

    @Test
    fun `a state survives a round trip`() {
        val state = HandoverState(
            active = true,
            frozenBefore = setOf("com.a/com.a.Main"),
            lockedBefore = setOf("com.b/com.b.Main"),
        )
        assertEquals(state, HandoverState.fromJson(HandoverState.toJson(state)))
    }

    @Test
    fun `leaving restores only what handover added`() {
        val exit = handoverExit(
            state = HandoverState(
                active = true,
                frozenBefore = setOf("com.a/com.a.Main"),
                lockedBefore = setOf("com.b/com.b.Main"),
            ),
            frozenNow = setOf("com.a/com.a.Main", "com.c/com.c.Main"),
            lockedNow = setOf("com.b/com.b.Main", "com.d/com.d.Main"),
        )
        assertEquals(setOf("com.c/com.c.Main"), exit.thaw)
        assertEquals(setOf("com.a/com.a.Main"), exit.keepFrozen)
        assertEquals(setOf("com.d/com.d.Main"), exit.unlock)
        assertEquals(setOf("com.b/com.b.Main"), exit.keepLocked)
    }

    @Test
    fun `junk is read as no handover`() {
        assertEquals(HandoverState(), HandoverState.fromJson("not json"))
    }
}
