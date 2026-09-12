// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.runtime.Immutable
import dev.victorialauncher.data.AppInfo

@Immutable
sealed interface AppListRow {
    data class Header(val text: String) : AppListRow
    data class Entry(val app: AppInfo) : AppListRow
}

/**
 * Marked immutable so Compose treats it as a stable parameter. It is only ever replaced
 * wholesale, never mutated, but the `List` fields on their own have it inferred as unstable,
 * which costs the whole app list a recomposition every time anything above it changes.
 */
@Immutable
data class AppListModel(
    val rows: List<AppListRow>,
    /** First row index for each A-Z letter, in scrubber order. */
    val letterIndex: List<Pair<Char, Int>>,
) {
    /** The scrubber's alphabet, derived once here rather than at each place that draws it. */
    val letters: List<Char> = letterIndex.map { it.first }
}

/**
 * [launchCounts] empty keeps every section alphabetical; otherwise the apps inside each letter
 * are ordered by how often they were opened from here. The letters themselves never move —
 * an app is still filed under its own name, or the scrubber would be pointing at nothing.
 */
fun buildAppListModel(
    apps: List<AppInfo>,
    hidden: Set<String>,
    displayName: (AppInfo) -> String,
    launchCounts: Map<String, Int> = emptyMap(),
): AppListModel {
    val visible = apps.filter { it.key !in hidden }
    val rows = mutableListOf<AppListRow>()

    val byLetter = visible.groupBy { app ->
        val c = displayName(app).firstOrNull()?.uppercaseChar()
        if (c != null && c.isLetter()) c else '#'
    }

    val letterIndex = mutableListOf<Pair<Char, Int>>()
    byLetter.toSortedMap().forEach { (letter, list) ->
        letterIndex += letter to rows.size
        rows += AppListRow.Header(letter.toString())
        list.sortedWith(
            compareByDescending<AppInfo> { launchCounts[it.key] ?: 0 }
                .thenBy { displayName(it).lowercase() }
        ).forEach { rows += AppListRow.Entry(it) }
    }

    return AppListModel(rows, letterIndex)
}

/**
 * The same list with only the entries [match] accepts, and only the headers still holding
 * something. Rebuilt rather than filtered in place: letterIndex stores row indices, so
 * removing any row invalidates every index after it.
 */
fun AppListModel.filtered(match: (AppInfo) -> Boolean): AppListModel {
    val kept = mutableListOf<AppListRow>()
    val letterIndex = mutableListOf<Pair<Char, Int>>()
    var pendingHeader: AppListRow.Header? = null

    rows.forEach { row ->
        when (row) {
            is AppListRow.Header -> pendingHeader = row
            is AppListRow.Entry -> if (match(row.app)) {
                pendingHeader?.let { header ->
                    header.text.firstOrNull()?.let { letterIndex += it to kept.size }
                    kept += header
                    pendingHeader = null
                }
                kept += row
            }
        }
    }
    return AppListModel(kept, letterIndex)
}
