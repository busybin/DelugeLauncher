// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The exported list as a file, now that it also carries which keys a bulk lift must skip. */
class FreezePlanFilePinnedTest {

    private val plan = mapOf(
        "com.example.notes" to FreezeMethod.DISABLE,
        "com.example.chat|u10" to FreezeMethod.SUSPEND,
        "com.example.keyboard" to FreezeMethod.STOP,
    )

    private val rows = mapOf("com.example.notes" to FreezeRow(label = "Notes"))

    @Test
    fun `version 2 keeps the pins through a round trip`() {
        val pinned = setOf("com.example.notes", "com.example.chat|u10")

        val decoded = FreezePlanFile.decodePlan(FreezePlanFile.encode(plan, rows, pinned))!!

        assertEquals(plan, decoded.entries.mapValues { it.value.method })
        assertEquals("Notes", decoded.entries["com.example.notes"]?.label)
        assertEquals(pinned, decoded.pinned)
    }

    @Test
    fun `a file with no pins decodes to an empty pinned set`() {
        val decoded = FreezePlanFile.decodePlan(FreezePlanFile.encode(plan, rows))!!

        assertEquals(plan.keys, decoded.keys)
        assertEquals(emptySet<String>(), decoded.pinned)
    }

    @Test
    fun `a version 1 file is still read, with nothing pinned`() {
        // Exactly what the previous build wrote: there is no "pinned" array at all.
        val v1 = """
            {"version":1,"apps":[
              {"key":"com.example.notes","method":"DISABLE","label":"Notes"},
              {"key":"com.example.chat|u10","method":"SUSPEND"}
            ]}
        """.trimIndent()

        val decoded = FreezePlanFile.decodePlan(v1) ?: error("a version 1 file must still decode")

        assertEquals(
            mapOf(
                "com.example.notes" to FreezeMethod.DISABLE,
                "com.example.chat|u10" to FreezeMethod.SUSPEND,
            ),
            decoded.entries.mapValues { it.value.method },
        )
        assertEquals(emptySet<String>(), decoded.pinned)
        // The older accessor still answers with the list alone.
        assertEquals(decoded.entries, FreezePlanFile.decode(v1))
    }

    @Test
    fun `a pin for an app the file does not list is dropped`() {
        val text = """
            {"version":2,"apps":[{"key":"com.example.notes","method":"DISABLE"}],
             "pinned":["com.example.notes","com.example.ghost"]}
        """.trimIndent()

        val decoded = FreezePlanFile.decodePlan(text)!!

        // A ghost pin would be an exemption nobody can see or take off again.
        assertEquals(setOf("com.example.notes"), decoded.pinned)
    }

    @Test
    fun `junk in the pinned array cannot widen the set`() {
        val text = """
            {"version":2,"apps":[{"key":"com.example.notes","method":"DISABLE"}],
             "pinned":[42,null,{"key":"com.example.notes"},"com.example.notes"]}
        """.trimIndent()

        assertEquals(setOf("com.example.notes"), FreezePlanFile.decodePlan(text)!!.pinned)
    }

    @Test
    fun `a newer version than this build writes is refused`() {
        assertNull(FreezePlanFile.decodePlan("""{"version":3,"apps":[]}"""))
        assertNull(FreezePlanFile.decode("""{"version":99,"apps":[]}"""))
    }

    @Test
    fun `a version 1 file without apps is still refused`() {
        assertNull(FreezePlanFile.decodePlan("""{"version":1}"""))
        assertNull(FreezePlanFile.decodePlan("""{"version":2}"""))
        assertNull(FreezePlanFile.decodePlan(""))
    }

    @Test
    fun `a pin is only written for a key the file carries`() {
        val encoded = FreezePlanFile.encode(plan, rows, setOf("com.example.notes", "com.example.ghost"))

        val decoded = FreezePlanFile.decodePlan(encoded)!!

        assertEquals(setOf("com.example.notes"), decoded.pinned)
        assertTrue(encoded.contains("\"pinned\""))
    }

    @Test
    fun `the pinned set that comes back is the one the caller may store`() {
        // importPlan adds these to whatever is pinned here, so it has to be a plain Set.
        val decoded = FreezePlanFile.decodePlan(FreezePlanFile.encode(plan, rows, plan.keys))!!

        assertEquals(plan.keys, decoded.pinned)
    }

    @Test
    fun `a backup carries the pins, so a reinstall keeps them`() {
        val snapshot = FreezeBackup.Snapshot(
            firstInstallTime = 1_700_000_000_000L,
            methods = plan,
            pinned = setOf("com.example.notes", "com.example.chat|u10"),
        )

        val decoded = FreezeBackup.decode(FreezeBackup.encode(snapshot))!!

        assertEquals(setOf("com.example.notes", "com.example.chat|u10"), decoded.pinned)
    }

    @Test
    fun `a backup written before pins existed decodes with none`() {
        val old = """
            {"version":1,"firstInstall":7,"methods":{"com.example.notes":"DISABLE"},
             "applied":["com.example.notes"],"rows":{},"locked":[]}
        """.trimIndent()

        val decoded = FreezeBackup.decode(old)!!

        assertEquals(emptySet<String>(), decoded.pinned)
        assertEquals(setOf("com.example.notes"), decoded.methods.keys)
    }

    @Test
    fun `a backup pin for a key it does not list is dropped`() {
        val text = """
            {"version":1,"methods":{"com.example.notes":"DISABLE"},
             "pinned":["com.example.notes","com.example.ghost"]}
        """.trimIndent()

        assertEquals(setOf("com.example.notes"), FreezeBackup.decode(text)!!.pinned)
    }
}
