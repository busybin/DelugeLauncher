// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.network

import android.content.ComponentName
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.NetworkAppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The page's pure half: one row per package, the readback deciding the label, protected rows
 * marked rather than hidden, and the search box reaching the package name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkUiTest {

    private fun app(
        id: String,
        label: String = id,
        system: Boolean = false,
        kind: EntryKind = EntryKind.APP,
        userSerial: Long = 0L,
    ) = AppInfo(
        componentName = ComponentName("com.example.$id", "com.example.$id.Main"),
        label = label,
        system = system,
        kind = kind,
        userSerial = userSerial,
    )

    @Test
    fun `readback decides the row state`() {
        val rows = NetworkUi.rows(
            apps = listOf(app("a"), app("b"), app("c"), app("d")),
            blocked = setOf("com.example.a", "com.example.b", "com.example.c"),
            applied = setOf("com.example.a"),
            pending = setOf("com.example.b"),
            unknown = setOf("com.example.c"),
            protectedPackages = emptySet(),
        ).associateBy { it.packageName }

        assertEquals(NetworkRowState.BLOCKED, rows.getValue("com.example.a").state)
        assertEquals(NetworkRowState.PENDING, rows.getValue("com.example.b").state)
        assertEquals(NetworkRowState.UNKNOWN, rows.getValue("com.example.c").state)
        assertEquals(NetworkRowState.ALLOWED, rows.getValue("com.example.d").state)
    }

    @Test
    fun `a protected row is never blocked even when the list says so`() {
        val rows = NetworkUi.rows(
            apps = listOf(app("phone")),
            blocked = setOf("com.example.phone"),
            applied = setOf("com.example.phone"),
            pending = emptySet(),
            unknown = emptySet(),
            protectedPackages = setOf("com.example.phone"),
        )

        assertEquals(NetworkRowState.PROTECTED, rows.single().state)
        assertFalse(rows.single().blocked)
    }

    @Test
    fun `a frozen row follows the freeze and is owned by the link`() {
        val rows = NetworkUi.rows(
            apps = listOf(app("a")),
            blocked = emptySet(),
            applied = setOf("com.example.a"),
            pending = emptySet(),
            unknown = emptySet(),
            protectedPackages = emptySet(),
            frozenPackages = setOf("com.example.a"),
        )

        assertTrue(rows.single().blocked)
        assertTrue(rows.single().freezeLinked)
        assertEquals(NetworkAppState.FOLLOW_FREEZE, rows.single().policy)
        assertEquals(NetworkRowState.BLOCKED, rows.single().state)
    }

    @Test
    fun `a frozen row on the block list stays blocked after a thaw`() {
        val rows = NetworkUi.rows(
            apps = listOf(app("a")),
            blocked = setOf("com.example.a"),
            applied = setOf("com.example.a"),
            pending = emptySet(),
            unknown = emptySet(),
            protectedPackages = emptySet(),
            frozenPackages = setOf("com.example.a"),
        )

        assertTrue(rows.single().blocked)
        assertFalse(rows.single().freezeLinked)
        assertEquals(NetworkAppState.BLOCKED, rows.single().policy)
    }

    @Test
    fun `an unrestricted frozen row stays online`() {
        val rows = NetworkUi.rows(
            apps = listOf(app("a")),
            blocked = emptySet(),
            applied = emptySet(),
            pending = emptySet(),
            unknown = emptySet(),
            protectedPackages = emptySet(),
            frozenPackages = setOf("com.example.a"),
            unrestricted = setOf("com.example.a"),
        )

        assertFalse(rows.single().blocked)
        assertFalse(rows.single().freezeLinked)
        assertEquals(NetworkAppState.UNRESTRICTED, rows.single().policy)
    }

    @Test
    fun `the block list wins over unrestricted`() {
        val rows = NetworkUi.rows(
            apps = listOf(app("a")),
            blocked = setOf("com.example.a"),
            applied = setOf("com.example.a"),
            pending = emptySet(),
            unknown = emptySet(),
            protectedPackages = emptySet(),
            unrestricted = setOf("com.example.a"),
        )

        assertTrue(rows.single().blocked)
        assertEquals(NetworkAppState.BLOCKED, rows.single().policy)
    }

    @Test
    fun `filters keep only their own policy`() {
        val apps = listOf(app("a"), app("b"), app("c"))
        val blocked = setOf("com.example.a")
        val unrestricted = setOf("com.example.b")

        fun filtered(filter: NetworkFilter) = NetworkUi.rows(
            apps = apps,
            blocked = blocked,
            applied = emptySet(),
            pending = emptySet(),
            unknown = emptySet(),
            protectedPackages = emptySet(),
            unrestricted = unrestricted,
            filter = filter,
        ).map { it.packageName }

        assertEquals(listOf("com.example.a"), filtered(NetworkFilter.BLOCKED))
        assertEquals(listOf("com.example.b"), filtered(NetworkFilter.UNRESTRICTED))
        assertEquals(listOf("com.example.c"), filtered(NetworkFilter.FOLLOW_FREEZE))
        assertEquals(3, filtered(NetworkFilter.ALL).size)
    }

    @Test
    fun `one row per package across profiles`() {
        val rows = NetworkUi.rows(
            apps = listOf(app("a"), app("a", userSerial = 10L)),
            blocked = emptySet(),
            applied = emptySet(),
            pending = emptySet(),
            unknown = emptySet(),
            protectedPackages = emptySet(),
        )

        assertEquals(1, rows.size)
    }

    @Test
    fun `shortcuts are not app rows`() {
        val rows = NetworkUi.rows(
            apps = listOf(app("a"), app("s", kind = EntryKind.SHORTCUT)),
            blocked = emptySet(),
            applied = emptySet(),
            pending = emptySet(),
            unknown = emptySet(),
            protectedPackages = emptySet(),
        )

        assertEquals(listOf("com.example.a"), rows.map { it.packageName })
    }

    @Test
    fun `search reaches the label and the package name`() {
        val apps = listOf(app("alpha", label = "Alpha"), app("beta", label = "Beta"))

        val byLabel = NetworkUi.rows(
            apps, emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), query = "alph",
        )
        val byPackage = NetworkUi.rows(
            apps, emptySet(), emptySet(), emptySet(), emptySet(), emptySet(),
            query = "example.beta",
        )

        assertEquals(listOf("com.example.alpha"), byLabel.map { it.packageName })
        assertEquals(listOf("com.example.beta"), byPackage.map { it.packageName })
    }

    @Test
    fun `system apps can be hidden`() {
        val apps = listOf(app("user"), app("system", system = true))

        val rows = NetworkUi.rows(
            apps, emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), showSystem = false,
        )

        assertEquals(listOf("com.example.user"), rows.map { it.packageName })
        assertTrue(rows.none { it.app.system })
    }

    @Test
    fun `rows sort by label`() {
        val apps = listOf(app("c", label = "Charlie"), app("a", label = "alpha"), app("b", label = "Bravo"))

        val rows = NetworkUi.rows(apps, emptySet(), emptySet(), emptySet(), emptySet(), emptySet())

        assertEquals(listOf("alpha", "Bravo", "Charlie"), rows.map { it.app.label })
    }
}
