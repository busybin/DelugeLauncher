// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.freeze

import dev.delugelauncher.data.FreezeMethod
import dev.delugelauncher.data.FreezeSort
import dev.delugelauncher.data.FreezeSourceFilter
import dev.delugelauncher.data.FreezeStateFilter

/** Everything the filter bar holds. One object so a rotation or a test needs no screen. */
data class FreezeFilters(
    val query: String = "",
    val source: FreezeSourceFilter = FreezeSourceFilter.USER,
    val state: FreezeStateFilter = FreezeStateFilter.ALL,
    val sort: FreezeSort = FreezeSort.NAME_ASC,
    /** Order by a latin reading of the name, so a pinyin query sorts the way it found it. */
    val latin: Boolean = true,
)

/**
 * One row as the table needs it, built from an app and the freeze state.
 *
 * Kept free of Android types so the whole filter/sort/count layer runs as a JVM test.
 */
data class FreezeAppRow(
    val key: String,
    val label: String,
    val latinName: String,
    val method: FreezeMethod? = null,
    val frozen: Boolean = false,
    val permanent: Boolean = false,
    val system: Boolean = false,
    val userSerial: Long = 0L,
    val privateSpace: Boolean = false,
    /** The app lock owns this row's hide/suspend state. */
    val locked: Boolean = false,
    /** Keyboard, home app, accessibility service or an access provider: never frozen. */
    val protectedRow: Boolean = false,
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val listed: Boolean get() = method != null

    val source: FreezeSourceFilter
        get() = when {
            privateSpace || userSerial != 0L -> FreezeSourceFilter.OTHER_PROFILE
            system -> FreezeSourceFilter.SYSTEM
            else -> FreezeSourceFilter.USER
        }
}

/** The three numbers above the list; each is a shortcut into the matching filter. */
data class FreezeCounts(val listed: Int, val frozen: Int, val permanent: Int)

object FreezeUi {

    fun visible(rows: List<FreezeAppRow>, filters: FreezeFilters): List<FreezeAppRow> {
        val term = filters.query.trim()
        val matching = rows.filter { row ->
            row.source == filters.source &&
                matchesState(row, filters.state) &&
                (term.isEmpty() ||
                    row.label.contains(term, ignoreCase = true) ||
                    row.latinName.contains(term, ignoreCase = true))
        }
        return sort(matching, filters)
    }

    /** Totals for one bucket, so a count and the list it opens always agree. */
    fun counts(rows: List<FreezeAppRow>, source: FreezeSourceFilter): FreezeCounts {
        val bucket = rows.filter { it.source == source }
        return FreezeCounts(
            listed = bucket.count { it.listed },
            frozen = bucket.count { it.frozen },
            permanent = bucket.count { it.permanent },
        )
    }

    /** Totals across every bucket: a bulk action ignores the filter, so its confirm must too. */
    fun totalCounts(rows: List<FreezeAppRow>): FreezeCounts = FreezeCounts(
        listed = rows.count { it.listed },
        frozen = rows.count { it.frozen },
        permanent = rows.count { it.permanent },
    )

    /** Everything the current view shows, for a Select all that cannot reach outside it. */
    fun allKeys(rows: List<FreezeAppRow>): Set<String> = rows.mapTo(mutableSetOf()) { it.key }

    private fun matchesState(row: FreezeAppRow, state: FreezeStateFilter): Boolean = when (state) {
        FreezeStateFilter.ALL -> true
        FreezeStateFilter.FROZEN -> row.frozen
        FreezeStateFilter.UNFROZEN -> !row.frozen
        FreezeStateFilter.PERMANENT -> row.permanent
    }

    private fun sort(rows: List<FreezeAppRow>, filters: FreezeFilters): List<FreezeAppRow> {
        val key: (FreezeAppRow) -> String = {
            if (filters.latin) it.latinName else it.label.lowercase(java.util.Locale.ROOT)
        }
        val comparator = when (filters.sort) {
            FreezeSort.NAME_ASC -> compareBy(key)
            FreezeSort.NAME_DESC -> compareByDescending(key)
            FreezeSort.INSTALLED -> compareByDescending<FreezeAppRow> { it.installedAt }.thenBy(key)
            FreezeSort.UPDATED -> compareByDescending<FreezeAppRow> { it.updatedAt }.thenBy(key)
        }
        return rows.sortedWith(comparator)
    }
}
