// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The firewall's pure half: command shapes, the marker protocol, the readback parser and the
 * rule that only a confirmed readback may move a row into "applied".
 */
class NetworkTest {

    @Test
    fun `block and restore flip the package flag`() {
        assertEquals(
            "cmd connectivity set-package-networking-enabled false com.example.app",
            NetworkCommands.setPackage("com.example.app", blocked = true),
        )
        assertEquals(
            "cmd connectivity set-package-networking-enabled true com.example.app",
            NetworkCommands.setPackage("com.example.app", blocked = false),
        )
    }

    @Test
    fun `chain commands`() {
        assertEquals("cmd connectivity set-chain3-enabled true", NetworkCommands.setChain(true))
        assertEquals("cmd connectivity set-chain3-enabled false", NetworkCommands.setChain(false))
        assertEquals("cmd connectivity get-chain3-enabled", NetworkCommands.readChain())
        assertEquals(
            "cmd connectivity get-package-networking-enabled com.example.app",
            NetworkCommands.readPackage("com.example.app"),
        )
    }

    @Test
    fun `apply script marks every command`() {
        val script = NetworkCommands.applyScript(listOf("com.a", "com.b"), blocked = true)

        assertTrue(script.contains("echo __N0__"))
        assertTrue(script.contains("set-package-networking-enabled false com.a"))
        assertTrue(script.contains("echo __R0__:\$?"))
        assertTrue(script.contains("echo __N1__"))
        assertTrue(script.contains("set-package-networking-enabled false com.b"))
        assertTrue(script.contains("echo __R1__:\$?"))
    }

    @Test
    fun `invalid package names never reach the shell`() {
        val script = NetworkCommands.applyScript(listOf("com.good", "bad name", "", "../x"), true)

        assertTrue(script.contains("com.good"))
        assertFalse(script.contains("bad name"))
        assertFalse(script.contains("../x"))
    }

    @Test
    fun `apply output maps each marker to its exit code`() {
        val codes = NetworkCommands.parseApply("noise\n__R0__:0\n__R1__:1\n__R2__:0\n", 3)

        assertEquals(mapOf(0 to true, 1 to false, 2 to true), codes)
    }

    @Test
    fun `read output maps allow deny and unreadable`() {
        val output = "__N0__\ncom.a:deny\n__N1__\ncom.b:allow\n__N2__\ncom.c:weird\n"

        val read = NetworkCommands.parseRead(output, listOf("com.a", "com.b", "com.c"))

        assertEquals(true, read["com.a"])
        assertEquals(false, read["com.b"])
        assertEquals(null, read["com.c"])
    }

    @Test
    fun `chain readback`() {
        assertEquals(true, NetworkCommands.parseChain("chain:enabled\n"))
        assertEquals(false, NetworkCommands.parseChain("chain:disabled\n"))
        assertEquals(null, NetworkCommands.parseChain("error: no access\n"))
    }

    @Test
    fun `policy decides who should be offline`() {
        val blocked = NetworkPolicy(
            mode = NetworkMode.BLACKLIST,
            blocked = setOf("com.a"),
        )
        assertTrue(blocked.wantsBlocked("com.a"))
        assertFalse(blocked.wantsBlocked("com.b"))
        assertFalse(NetworkPolicy(mode = NetworkMode.OFF, blocked = setOf("com.a")).wantsBlocked("com.a"))

        val whitelist = NetworkPolicy(mode = NetworkMode.WHITELIST, allowed = setOf("com.keep"))
        assertFalse(whitelist.wantsBlocked("com.keep"))
        assertTrue(whitelist.wantsBlocked("com.other"))
    }

    @Test
    fun `the desired set follows the freeze unless unrestricted`() {
        val policy = NetworkPolicy(
            mode = NetworkMode.BLACKLIST,
            blocked = setOf("com.blocked"),
            unrestricted = setOf("com.free"),
        )

        val desired = policy.desired(
            frozen = setOf("com.frozen", "com.free", "com.protected"),
            protected = setOf("com.protected"),
        )

        assertEquals(setOf("com.blocked", "com.frozen"), desired)
    }

    @Test
    fun `the block list wins over unrestricted in the desired set`() {
        val policy = NetworkPolicy(
            mode = NetworkMode.BLACKLIST,
            blocked = setOf("com.both"),
            unrestricted = setOf("com.both"),
        )

        assertEquals(setOf("com.both"), policy.desired(frozen = emptySet()))
    }

    @Test
    fun `an off policy desires nothing`() {
        val policy = NetworkPolicy(mode = NetworkMode.OFF, blocked = setOf("com.blocked"))

        assertTrue(policy.desired(frozen = setOf("com.frozen")).isEmpty())
    }

    @Test
    fun `mode parse falls back to off`() {
        assertEquals(NetworkMode.BLACKLIST, NetworkMode.parse("BLACKLIST"))
        assertEquals(NetworkMode.OFF, NetworkMode.parse("nonsense"))
        assertEquals(NetworkMode.OFF, NetworkMode.parse(null))
    }

    @Test
    fun `protected rows`() {
        assertTrue(NetworkProtection.isProtected("com.android.phone", "dev.delugelauncher"))
        assertTrue(NetworkProtection.isProtected("dev.delugelauncher", "dev.delugelauncher"))
        assertTrue(NetworkProtection.isProtected("com.home", "dev.delugelauncher", setOf("com.home")))
        assertFalse(NetworkProtection.isProtected("com.example.app", "dev.delugelauncher"))
    }

    @Test
    fun `a confirmed deny becomes applied and an unreadable answer stays unknown`() {
        val after = NetworkState.block(
            before = NetworkBuckets(),
            chunk = listOf("com.a", "com.b", "com.c", "com.d"),
            codes = mapOf(0 to true, 1 to true, 2 to true, 3 to false),
            read = mapOf("com.a" to true, "com.b" to false, "com.c" to null, "com.d" to null),
        )

        assertEquals(setOf("com.a"), after.applied)
        assertEquals(setOf("com.b", "com.d"), after.pending)
        assertEquals(setOf("com.c"), after.unknown)
    }

    @Test
    fun `a clean exit code without a readable answer is not applied`() {
        val after = NetworkState.block(
            before = NetworkBuckets(),
            chunk = listOf("com.a"),
            codes = mapOf(0 to true),
            read = mapOf("com.a" to null),
        )

        assertTrue(after.applied.isEmpty())
        assertEquals(setOf("com.a"), after.unknown)
    }

    @Test
    fun `restore only clears rows the platform reports as allowed`() {
        val before = NetworkBuckets(
            applied = setOf("com.a", "com.b"),
            pending = setOf("com.c"),
            unknown = setOf("com.d"),
        )

        val after = NetworkState.restore(
            before = before,
            chunk = listOf("com.a", "com.b", "com.c", "com.d"),
            read = mapOf("com.a" to false, "com.b" to true, "com.c" to null, "com.d" to null),
        )

        assertFalse("com.a" in after.applied)
        assertTrue("com.b" in after.pending)
        assertTrue("com.c" in after.pending)
        assertTrue("com.d" in after.pending)
        assertFalse("com.a" in after.pending)
        assertFalse("com.a" in after.unknown)
    }

    @Test
    fun `buckets drop a package from every state at once`() {
        val before = NetworkBuckets(
            applied = setOf("com.a"),
            pending = setOf("com.a"),
            unknown = setOf("com.a"),
        )

        val after = before.without(listOf("com.a"))

        assertTrue(after.applied.isEmpty())
        assertTrue(after.pending.isEmpty())
        assertTrue(after.unknown.isEmpty())
    }
}
