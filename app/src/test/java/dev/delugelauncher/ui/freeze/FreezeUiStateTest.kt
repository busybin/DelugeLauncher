// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.freeze

import dev.delugelauncher.data.FreezeMethod
import dev.delugelauncher.data.FreezeSort
import dev.delugelauncher.data.FreezeSourceFilter
import dev.delugelauncher.data.FreezeStateFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeUiStateTest {

    private fun row(
        key: String,
        label: String = key,
        latin: String = label,
        method: FreezeMethod? = null,
        frozen: Boolean = false,
        permanent: Boolean = false,
        system: Boolean = false,
        userSerial: Long = 0L,
        privateSpace: Boolean = false,
        installedAt: Long = 0L,
        updatedAt: Long = 0L,
    ) = FreezeAppRow(
        key = key,
        label = label,
        latinName = latin,
        method = method,
        frozen = frozen,
        permanent = permanent,
        system = system,
        userSerial = userSerial,
        privateSpace = privateSpace,
        installedAt = installedAt,
        updatedAt = updatedAt,
    )

    private val rows = listOf(
        row("a", label = "Alpha", latin = "alpha", method = FreezeMethod.SUSPEND),
        row("b", label = "Beta", latin = "beta", method = FreezeMethod.DISABLE, frozen = true),
        row(
            "c",
            label = "Calendar",
            latin = "calendar",
            method = FreezeMethod.SUSPEND,
            frozen = true,
            permanent = true,
        ),
        row("d", label = "Settings", latin = "settings", system = true),
        row("e", label = "Work chat", latin = "work chat", userSerial = 10L),
        row("f", label = "Private notes", latin = "private notes", userSerial = 11L, privateSpace = true),
    )

    @Test
    fun `the default view is the main profile's own apps`() {
        val shown = FreezeUi.visible(rows, FreezeFilters())

        assertEquals(listOf("a", "b", "c"), shown.map { it.key })
    }

    @Test
    fun `system and other profiles are separate buckets`() {
        val system = FreezeUi.visible(rows, FreezeFilters(source = FreezeSourceFilter.SYSTEM))
        val other = FreezeUi.visible(rows, FreezeFilters(source = FreezeSourceFilter.OTHER_PROFILE))

        assertEquals(listOf("d"), system.map { it.key })
        assertEquals(listOf("f", "e"), other.map { it.key })
    }

    @Test
    fun `the state filter understands permanent on its own`() {
        val frozen = FreezeUi.visible(rows, FreezeFilters(state = FreezeStateFilter.FROZEN))
        val permanent = FreezeUi.visible(rows, FreezeFilters(state = FreezeStateFilter.PERMANENT))
        val unfrozen = FreezeUi.visible(rows, FreezeFilters(state = FreezeStateFilter.UNFROZEN))

        assertEquals(listOf("b", "c"), frozen.map { it.key })
        assertEquals(listOf("c"), permanent.map { it.key })
        assertEquals(listOf("a"), unfrozen.map { it.key })
    }

    @Test
    fun `search reads the latin name too`() {
        val shown = FreezeUi.visible(rows, FreezeFilters(query = "alph"))

        assertEquals(listOf("a"), shown.map { it.key })
    }

    @Test
    fun `a search never reaches outside the chosen source`() {
        val shown = FreezeUi.visible(rows, FreezeFilters(query = "work"))

        assertTrue(shown.isEmpty())
    }

    @Test
    fun `newest installs come first and ties fall back to the name`() {
        val shown = FreezeUi.visible(
            listOf(
                row("a", label = "A", installedAt = 5),
                row("b", label = "B", installedAt = 9),
                row("c", label = "C", installedAt = 5),
            ),
            FreezeFilters(sort = FreezeSort.INSTALLED),
        )

        assertEquals(listOf("b", "a", "c"), shown.map { it.key })
    }

    @Test
    fun `counts describe the whole phone, not the current view`() {
        assertEquals(
            FreezeCounts(listed = 3, frozen = 2, permanent = 1),
            FreezeUi.counts(rows, FreezeSourceFilter.USER),
        )
    }

    @Test
    fun `total counts include the buckets a bulk action also reaches`() {
        val withWork = rows + row(
            "g",
            label = "Work mail",
            userSerial = 10L,
            method = FreezeMethod.SUSPEND,
            frozen = true,
            permanent = true,
        )

        assertEquals(
            FreezeCounts(listed = 4, frozen = 3, permanent = 2),
            FreezeUi.totalCounts(withWork),
        )
        // The per-bucket count stays what the list below it shows.
        assertEquals(
            FreezeCounts(listed = 3, frozen = 2, permanent = 1),
            FreezeUi.counts(withWork, FreezeSourceFilter.USER),
        )
    }

    @Test
    fun `select all is only what the view shows`() {
        val shown = FreezeUi.visible(rows, FreezeFilters(state = FreezeStateFilter.FROZEN))

        assertEquals(setOf("b", "c"), FreezeUi.allKeys(shown))
    }
}
