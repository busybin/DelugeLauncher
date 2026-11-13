// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FreezePermanentTest {

    private val plan = mapOf(
        "com.example.notes" to FreezeMethod.DISABLE,
        "com.example.chat|u10" to FreezeMethod.SUSPEND,
        "com.example.keyboard" to FreezeMethod.STOP,
    )

    @Test
    fun `a pin on a listed persistent row becomes permanent`() {
        val seed = FreezePermanent.seed(
            pinned = setOf("com.example.notes", "com.example.chat|u10"),
            methods = plan,
        )

        assertEquals(setOf("com.example.notes", "com.example.chat|u10"), seed)
    }

    @Test
    fun `a pin on a stopped row stays a shortcut only`() {
        val seed = FreezePermanent.seed(pinned = setOf("com.example.keyboard"), methods = plan)

        assertEquals(emptySet<String>(), seed)
    }

    @Test
    fun `a pin on a row that is no longer listed is ignored`() {
        val seed = FreezePermanent.seed(
            pinned = setOf("com.example.gone"),
            methods = plan,
        )

        assertEquals(emptySet<String>(), seed)
    }

    @Test
    fun `flags set before the migration are kept`() {
        val seed = FreezePermanent.seed(
            pinned = setOf("com.example.notes"),
            methods = plan,
            existing = setOf("com.example.other"),
        )

        assertEquals(setOf("com.example.notes", "com.example.other"), seed)
    }

    @Test
    fun `seeding twice changes nothing`() {
        val once = FreezePermanent.seed(
            pinned = setOf("com.example.notes"),
            methods = plan,
        )

        assertEquals(once, FreezePermanent.seed(setOf("com.example.notes"), plan, once))
    }
}
