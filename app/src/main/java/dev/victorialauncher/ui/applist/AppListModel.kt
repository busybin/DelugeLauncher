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

fun buildAppListModel(
    apps: List<AppInfo>,
    hidden: Set<String>,
    displayName: (AppInfo) -> String,
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
        list.sortedBy { displayName(it).lowercase() }.forEach { rows += AppListRow.Entry(it) }
    }

    return AppListModel(rows, letterIndex)
}