// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezePlanFileTest {

    @Test
    fun `a list survives its own encoding`() {
        val plan = mapOf(
            "com.example.notes" to FreezeMethod.DISABLE,
            "com.example.chat|u10" to FreezeMethod.SUSPEND,
        )
        val rows = mapOf(
            "com.example.notes" to FreezeRow(label = "Notes", component = "com.example.notes/.Main"),
        )

        val decoded = FreezePlanFile.decode(FreezePlanFile.encode(plan, rows))!!

        assertEquals(plan, decoded.mapValues { it.value.method })
        assertEquals("Notes", decoded["com.example.notes"]?.label)
        assertEquals(10L, FreezeTargetKey.userSerial("com.example.chat|u10"))
    }

    @Test
    fun `another version or junk is refused`() {
        assertNull(FreezePlanFile.decode(""))
        assertNull(FreezePlanFile.decode("not json"))
        assertNull(FreezePlanFile.decode("{\"version\":2,\"apps\":[]}"))
        assertNull(FreezePlanFile.decode("{\"version\":1}"))
    }

    @Test
    fun `entries that could not be run are skipped`() {
        val text = """
            {"version":1,"apps":[
              {"key":"com.example.ok","method":"DISABLE"},
              {"key":"com.example bad","method":"DISABLE"},
              {"key":"com.example.unknown","method":"PAUSE"},
              {"key":"com.example.ok|u0","method":"DISABLE"},
              {"key":"com.example.ok|u99999999999999999999","method":"DISABLE"},
              {"key":"","method":"STOP"}
            ]}
        """.trimIndent()

        val decoded = FreezePlanFile.decode(text)!!

        assertEquals(setOf("com.example.ok"), decoded.keys)
    }

    @Test
    fun `labels lose control characters and cannot be unbounded`() {
        val longLabel = "Name\nwith\tcontrol " + "x".repeat(500)
        val text = org.json.JSONObject()
            .put("version", 1)
            .put(
                "apps",
                org.json.JSONArray().put(
                    org.json.JSONObject()
                        .put("key", "com.example.one")
                        .put("method", "STOP")
                        .put("label", longLabel)
                ),
            )
            .toString()

        val label = FreezePlanFile.decode(text)!!.getValue("com.example.one").label!!

        assertTrue(label.length <= 120)
        assertTrue(label.none { Character.isISOControl(it) })
    }

    @Test
    fun `an oversized file is refused before it is parsed`() {
        val text = """{"version":1,"apps":[{"key":"com.example.one","method":"STOP","label":"""" +
            "x".repeat(1_000_001) + """"}]}"""

        assertNull(FreezePlanFile.decode(text))
    }
}
