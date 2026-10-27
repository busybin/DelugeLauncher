// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeBackupTest {

    @Test
    fun `a backup survives its own encoding`() {
        val snapshot = FreezeBackup.Snapshot(
            firstInstallTime = 1_700_000_000_000L,
            enabled = false,
            systemApps = true,
            protect = false,
            methods = mapOf(
                "com.example.notes" to FreezeMethod.DISABLE,
                "com.example.chat|u10" to FreezeMethod.SUSPEND,
            ),
            applied = setOf("com.example.notes"),
            rows = mapOf(
                "com.example.notes" to FreezeRow(
                    label = "Notes 笔记",
                    component = "com.example.notes/.Main",
                ),
            ),
            locked = setOf(
                "com.example.notes/.Main",
                "shortcut:com.example.notes/pin|u10",
                "private:space",
            ),
        )

        val decoded = FreezeBackup.decode(FreezeBackup.encode(snapshot))!!

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `another version or junk is refused`() {
        assertNull(FreezeBackup.decode(""))
        assertNull(FreezeBackup.decode("not json"))
        assertNull(FreezeBackup.decode("""{"version":2}"""))
    }

    @Test
    fun `keys and methods that could not be run are skipped`() {
        val text = """
            {"version":1,"firstInstall":7,"methods":{
              "com.example.ok":"DISABLE",
              "com.example.bad name":"STOP",
              "com.example.unknown":"PAUSE",
              "com.example.ok|u0":"HIDE",
              "com.example.ok|u99999999999999999999":"STOP"
            },"applied":[
              "com.example.ok","com.example.bad name","com.example.missing"
            ]}
        """.trimIndent()

        val decoded = FreezeBackup.decode(text)!!

        assertEquals(mapOf("com.example.ok" to FreezeMethod.DISABLE), decoded.methods)
        assertEquals(setOf("com.example.ok"), decoded.applied)
        assertEquals(7L, decoded.firstInstallTime)
        assertEquals(emptySet<String>(), decoded.locked)
    }

    @Test
    fun `lock keys keep shortcuts but drop control characters and oversized keys`() {
        val text = org.json.JSONObject()
            .put("version", 1)
            .put("firstInstall", 9)
            .put("locked", org.json.JSONArray(listOf(
                "com.example.notes/.Main",
                "shortcut:com.example.notes/pin|u10",
                "private:space",
                "com.example.notes/.Bad\nName",
                "x".repeat(1_201),
            )))
            .toString()

        assertEquals(
            setOf(
                "com.example.notes/.Main",
                "shortcut:com.example.notes/pin|u10",
                "private:space",
            ),
            FreezeBackup.decode(text)!!.locked,
        )
    }

    @Test
    fun `labels lose control characters and cannot be unbounded`() {
        val longLabel = "Name\nwith\tcontrol " + "x".repeat(500)
        val text = org.json.JSONObject()
            .put("version", 1)
            .put("firstInstall", 9)
            .put("methods", org.json.JSONObject().put("com.example.one", "STOP"))
            .put(
                "rows",
                org.json.JSONObject().put(
                    "com.example.one",
                    org.json.JSONObject().put("label", longLabel),
                ),
            )
            .toString()

        val label = FreezeBackup.decode(text)!!.rows.getValue("com.example.one").label!!

        assertTrue(label.length <= 120)
        assertTrue(label.none { Character.isISOControl(it) })
    }
}
