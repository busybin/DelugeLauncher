// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeSelectionTest {

    private val plan = mapOf(
        "com.example.disabled" to FreezeMethod.DISABLE,
        "com.example.suspended" to FreezeMethod.SUSPEND,
        "com.example.loose" to FreezeMethod.SUSPEND,
        "com.example.stopped" to FreezeMethod.STOP,
    )

    private val frozenNow = setOf("com.example.disabled", "com.example.suspended", "com.example.stopped")

    private fun isFrozen(packageName: String) = packageName in frozenNow

    @Test
    fun `freeze skips what is already frozen and keeps the one that is not`() {
        val targets = FreezeSelection.toFreeze(plan, emptySet(), ::isFrozen)
        assertEquals(listOf("com.example.loose", "com.example.stopped"), targets)
    }

    @Test
    fun `stop is repeated because it persists nothing`() {
        val targets = FreezeSelection.toFreeze(
            mapOf("com.example.stopped" to FreezeMethod.STOP),
            emptySet(),
            { false },
        )
        assertEquals(listOf("com.example.stopped"), targets)
    }

    @Test
    fun `the app on screen is left out of the automatic run`() {
        val targets = FreezeSelection.toFreeze(plan, setOf("com.example.loose"), ::isFrozen)
        assertEquals(listOf("com.example.stopped"), targets)
    }

    @Test
    fun `lift takes the record and the live state, and leaves stopped apps alone`() {
        val applied = setOf("com.example.disabled", "com.example.stopped")
        val targets = FreezeSelection.toLift(
            plan.keys + applied,
            applied,
            plan,
            { it == "com.example.suspended" },
        )
        // disabled is recorded, suspended is reported held, loose is neither, stopped has
        // nothing to undo.
        assertEquals(
            listOf("com.example.disabled", "com.example.suspended"),
            targets,
        )
    }

    @Test
    fun `lift still touches a recorded app the platform claims is free`() {
        // HIDE cannot be read back, so the record alone has to carry it.
        val hidePlan = mapOf("com.example.hidden" to FreezeMethod.HIDE)
        val targets = FreezeSelection.toLift(
            hidePlan.keys,
            hidePlan.keys,
            hidePlan,
            { false },
        )
        assertEquals(listOf("com.example.hidden"), targets)
    }

    @Test
    fun `hiding is the one lift that has no state to read back`() {
        // Without this the lift counted as done before its command ran, and a hidden app
        // stayed hidden with no record left to bring it back.
        assertEquals(
            listOf(FreezeMethod.STOP, FreezeMethod.SUSPEND, FreezeMethod.DISABLE),
            FreezeMethod.entries.filter { FreezeSelection.verifiable(it) },
        )
        assertTrue(FreezeSelection.verifiable(null))
    }
}
