// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hardening layer's pure half: which app-ops a platform gets, the order they run in, and
 * the undo record that has to survive a second freeze.
 */
class DeepFreezeTest {

    private val target = FreezeTarget(packageName = "com.example.app", userId = 0, userSerial = 0)
    private val key = target.key

    @Test
    fun `notification lever waits for android 13`() {
        assertFalse(DeepFreezeCommands.levers(32).contains(DeepFreezeLever.NOTIFICATIONS))
        assertTrue(DeepFreezeCommands.levers(33).contains(DeepFreezeLever.NOTIFICATIONS))
    }

    @Test
    fun `foreground lever waits for android 9`() {
        assertFalse(DeepFreezeCommands.levers(27).contains(DeepFreezeLever.FOREGROUND))
        assertTrue(DeepFreezeCommands.levers(28).contains(DeepFreezeLever.FOREGROUND))
    }

    @Test
    fun `background work is never a lever`() {
        val ops = DeepFreezeCommands.levers(34).mapNotNull { it.op }
        assertFalse(ops.contains("RUN_IN_BACKGROUND"))
        assertEquals(
            listOf("POST_NOTIFICATION", "WAKE_LOCK", "START_FOREGROUND"),
            ops,
        )
    }

    @Test
    fun `hardening writes app-ops only`() {
        val script = DeepFreezeCommands.hardenScript(listOf(target), DeepFreezeCommands.levers(34))

        assertFalse(script.contains("set-chain3-enabled"))
        assertFalse(script.contains("set-package-networking-enabled"))
        assertTrue(script.contains("appops set --user 0 com.example.app POST_NOTIFICATION ignore"))
        assertTrue(script.contains("appops set --user 0 com.example.app WAKE_LOCK ignore"))
        assertTrue(script.contains("appops set --user 0 com.example.app START_FOREGROUND ignore"))
    }

    @Test
    fun `reading the prior modes tells an explicit mode from the default`() {
        val probes = listOf(
            DeepFreezeCommands.Probe(key, target.packageName, 0, "WAKE_LOCK"),
            DeepFreezeCommands.Probe(key, target.packageName, 0, "POST_NOTIFICATION"),
        )
        val output = """
            __P0__
            WAKE_LOCK: ignore; time=+1s
            __P1__
            No operations.
            Default mode: allow
        """.trimIndent()

        val modes = DeepFreezeCommands.parseCaptures(output, probes)

        assertEquals("ignore", modes[key to "WAKE_LOCK"])
        assertEquals("default", modes[key to "POST_NOTIFICATION"])
    }

    @Test
    fun `a second freeze keeps the mode recorded before the first one`() {
        val once = DeepFreezeJournal.merge(
            emptyMap(),
            mapOf(key to target),
            DeepFreezeCommands.levers(34),
            mapOf((key to "WAKE_LOCK") to "allow"),
        )
        val twice = DeepFreezeJournal.merge(
            once,
            mapOf(key to target),
            DeepFreezeCommands.levers(34),
            mapOf((key to "WAKE_LOCK") to "default"),
        )

        assertEquals("allow", twice.getValue(key).ops["WAKE_LOCK"])
        assertEquals(twice, DeepFreezeJournal.decode(DeepFreezeJournal.encode(twice)))
    }

    @Test
    fun `release puts the recorded mode back and marks a failed command`() {
        val journal = mapOf(
            key to DeepFreezeRecord(
                packageName = target.packageName,
                userId = 0,
                ops = mapOf("WAKE_LOCK" to "allow"),
            )
        )

        val script = DeepFreezeCommands.releaseScript(journal)

        assertFalse(script.contains("set-package-networking-enabled"))
        assertTrue(script.contains("appops set --user 0 com.example.app WAKE_LOCK allow"))
        assertTrue(script.contains(DeepFreezeCommands.FAIL_MARK))
        // Every row starts in its own block, so a failure can be charged to that row alone.
        val marker = "echo ${DeepFreezeCommands.RECORD_MARK}"
        assertTrue(script.lines().any { it.startsWith(marker) })
        assertTrue(
            script.lines()
                .filterNot { it.isBlank() || it.startsWith(marker) }
                .all { it.endsWith("|| echo ${DeepFreezeCommands.FAIL_MARK}") }
        )
    }

    @Test
    fun `a failed undo is attributed to the row that owns it`() {
        val output = """
            ${DeepFreezeCommands.RECORD_MARK}0
            cmd appops set --user 0 com.example.app WAKE_LOCK allow
            ${DeepFreezeCommands.RECORD_MARK}1
            cmd appops set --user 0 com.example.app WAKE_LOCK allow || echo ${DeepFreezeCommands.FAIL_MARK}
            ${DeepFreezeCommands.RECORD_MARK}2
            cmd appops set --user 0 com.example.app WAKE_LOCK allow || echo ${DeepFreezeCommands.FAIL_MARK}
        """.trimIndent()

        assertEquals(setOf(1, 2), DeepFreezeCommands.releaseFailures(output, 3))
    }

    @Test
    fun `a work profile record stays inside its own profile`() {
        val work = mapOf(
            key to DeepFreezeRecord(
                packageName = target.packageName,
                userId = 1,
                ops = mapOf("WAKE_LOCK" to "allow"),
            )
        )

        val script = DeepFreezeCommands.releaseScript(work)

        assertTrue(script.contains("appops set --user 1 com.example.app WAKE_LOCK allow"))
    }

    @Test
    fun `hardening skips a lever the caller already has on record`() {
        val script = DeepFreezeCommands.hardenScript(
            listOf(target),
            DeepFreezeCommands.levers(34),
        ) { _, lever -> lever != DeepFreezeLever.WAKE_LOCK }

        assertFalse(script.contains("WAKE_LOCK"))
        assertTrue(script.contains("POST_NOTIFICATION ignore"))
    }

    @Test
    fun `hardening a work profile still writes its own app-ops`() {
        val work = FreezeTarget(
            packageName = target.packageName,
            userId = 1,
            userSerial = 10,
        )

        val script = DeepFreezeCommands.hardenScript(
            listOf(work),
            DeepFreezeCommands.levers(34),
        )

        assertTrue(script.contains("appops set --user 1 com.example.app WAKE_LOCK ignore"))
    }

    @Test
    fun `the journal records only the levers that were sent`() {
        val merged = DeepFreezeJournal.merge(
            emptyMap(),
            mapOf(key to target),
            DeepFreezeCommands.levers(34),
            emptyMap(),
        ) { _, lever -> lever == DeepFreezeLever.NOTIFICATIONS }

        val record = merged.getValue(key)
        assertEquals(setOf("POST_NOTIFICATION"), record.ops.keys)
    }

    @Test
    fun `a package name with a shell metacharacter is refused`() {
        val bad = FreezeTarget(packageName = "com.example; rm -rf /", userId = 0, userSerial = 0)

        assertTrue(DeepFreezeCommands.hardenScript(listOf(bad), DeepFreezeCommands.levers(34)).isBlank())
    }
}
