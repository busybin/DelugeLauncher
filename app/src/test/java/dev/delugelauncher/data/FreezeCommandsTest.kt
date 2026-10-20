// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeCommandsTest {

    private val pkg = "com.example.app"

    @Test
    fun `tabs lead with the method that needs the least`() {
        assertEquals(
            listOf(
                FreezeMethod.STOP,
                FreezeMethod.SUSPEND,
                FreezeMethod.DISABLE,
                FreezeMethod.HIDE,
            ),
            FreezeMethod.entries,
        )
    }

    @Test
    fun `every freeze starts with a force stop`() {
        FreezeMethod.entries.forEach { method ->
            assertEquals("am force-stop $pkg", FreezeCommands.freeze(pkg, method).first())
        }
    }

    @Test
    fun `each method uses its own system command`() {
        assertTrue(FreezeCommands.freeze(pkg, FreezeMethod.DISABLE).contains("pm disable-user --user 0 $pkg"))
        assertTrue(FreezeCommands.freeze(pkg, FreezeMethod.HIDE).contains("pm hide $pkg"))
        assertTrue(FreezeCommands.freeze(pkg, FreezeMethod.SUSPEND).contains("pm suspend --user 0 $pkg"))
        assertEquals(listOf("am force-stop $pkg"), FreezeCommands.freeze(pkg, FreezeMethod.STOP))
    }

    @Test
    fun `suspending also restricts background runs and unsuspending restores them`() {
        val freeze = FreezeCommands.freeze(pkg, FreezeMethod.SUSPEND)
        assertTrue(freeze.contains("appops set $pkg RUN_ANY_IN_BACKGROUND ignore"))
        assertTrue(freeze.contains("appops set $pkg RUN_IN_BACKGROUND ignore"))
        val unfreeze = FreezeCommands.unfreeze(pkg, FreezeMethod.SUSPEND)
        assertTrue(unfreeze.contains("appops set $pkg RUN_IN_BACKGROUND allow"))
        assertTrue(unfreeze.indexOf("appops set $pkg RUN_IN_BACKGROUND allow") <
            unfreeze.indexOf("pm unsuspend --user 0 $pkg"))
    }

    @Test
    fun `unfreeze reverses only the method that was used`() {
        assertEquals(listOf("pm enable --user 0 $pkg"), FreezeCommands.unfreeze(pkg, FreezeMethod.DISABLE))
        assertEquals(listOf("pm unhide $pkg"), FreezeCommands.unfreeze(pkg, FreezeMethod.HIDE))
        assertEquals(emptyList<String>(), FreezeCommands.unfreeze(pkg, FreezeMethod.STOP))
    }

    @Test
    fun `the unfreeze everything fallback lifts every method`() {
        val commands = FreezeCommands.unfreezeEverything(pkg)
        assertEquals(5, commands.size)
        assertTrue(commands.any { it.startsWith("pm enable") })
        assertTrue(commands.any { it.startsWith("pm unhide") })
        assertTrue(commands.any { it == "appops set $pkg RUN_IN_BACKGROUND allow" })
        assertTrue(commands.any { it == "appops set $pkg RUN_ANY_IN_BACKGROUND allow" })
        assertTrue(commands.any { it.startsWith("pm unsuspend") })
    }

    @Test
    fun `a package name that could reach the shell is refused`() {
        listOf(
            "com.example; reboot",
            "com.example && rm -rf /",
            "com.example\nexit",
            "\$(id)",
            "",
            "com..example",
        ).forEach { bad ->
            assertEquals(emptyList<String>(), FreezeCommands.freeze(bad, FreezeMethod.STOP))
            assertEquals(emptyList<String>(), FreezeCommands.unfreezeEverything(bad))
        }
    }
}
