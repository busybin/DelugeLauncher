// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.network

import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.NetworkAppState
import dev.delugelauncher.data.Pinyin

/** What one row is doing right now, as far as the platform has confirmed. */
enum class NetworkRowState {
    /** The user wants it offline and the readback agrees. */
    BLOCKED,
    /** The user wants it offline but the readback does not confirm it yet. */
    PENDING,
    /** The write ran but the platform's answer could not be read. */
    UNKNOWN,
    /** Reachable. */
    ALLOWED,
    /** Never offered: the launcher, telephony, the home screen, the keyboard. */
    PROTECTED,
}

/** Which rows the page is showing. */
enum class NetworkFilter {
    ALL,
    FOLLOW_FREEZE,
    BLOCKED,
    UNRESTRICTED,
}

data class NetworkRow(
    val app: AppInfo,
    /** The answer the user stored for this row. */
    val policy: NetworkAppState,
    val state: NetworkRowState,
    /** The freeze link is holding this row offline right now. */
    val freezeLinked: Boolean = false,
) {
    val packageName: String get() = app.packageName

    /** The circle is filled when the row is meant to be offline right now. */
    val blocked: Boolean
        get() = policy == NetworkAppState.BLOCKED || freezeLinked
}

/**
 * The page's pure half: one row per package (chain3 works per appId, so two profiles of the
 * same app cannot be split), protected rows marked instead of hidden, and the search box
 * answering to the label, the package name and pinyin.
 */
object NetworkUi {
    fun rows(
        apps: List<AppInfo>,
        blocked: Set<String>,
        applied: Set<String>,
        pending: Set<String>,
        unknown: Set<String>,
        protectedPackages: Set<String>,
        frozenPackages: Set<String> = emptySet(),
        unrestricted: Set<String> = emptySet(),
        filter: NetworkFilter = NetworkFilter.ALL,
        query: String = "",
        showSystem: Boolean = true,
    ): List<NetworkRow> {
        val term = query.trim()
        val seen = HashSet<String>()
        val rows = ArrayList<NetworkRow>()
        apps.forEach { app ->
            if (app.kind != EntryKind.APP) return@forEach
            if (!showSystem && app.system) return@forEach
            if (!seen.add(app.packageName)) return@forEach
            val isProtected = app.packageName in protectedPackages
            val isBlocked = app.packageName in blocked && !isProtected
            val isUnrestricted = app.packageName in unrestricted && !isProtected
            val policy = when {
                isProtected -> NetworkAppState.PROTECTED
                isBlocked -> NetworkAppState.BLOCKED
                isUnrestricted -> NetworkAppState.UNRESTRICTED
                else -> NetworkAppState.FOLLOW_FREEZE
            }
            // The explicit answer wins over the link: a blocked row is offline even if the
            // freeze record is gone, and an unrestricted row stays online while frozen.
            val freezeLinked = policy == NetworkAppState.FOLLOW_FREEZE &&
                app.packageName in frozenPackages
            val state = when {
                isProtected -> NetworkRowState.PROTECTED
                app.packageName in applied -> NetworkRowState.BLOCKED
                app.packageName in pending -> NetworkRowState.PENDING
                app.packageName in unknown -> NetworkRowState.UNKNOWN
                else -> NetworkRowState.ALLOWED
            }
            if (!filter.matches(policy)) return@forEach
            if (term.isNotEmpty() && !matches(app, term)) return@forEach
            rows += NetworkRow(
                app = app,
                policy = policy,
                state = state,
                freezeLinked = freezeLinked,
            )
        }
        return rows.sortedWith(
            compareBy<NetworkRow> { it.app.label.lowercase() }
                .thenBy { it.packageName.lowercase() },
        )
    }

    private fun NetworkFilter.matches(policy: NetworkAppState): Boolean = when (this) {
        NetworkFilter.ALL -> true
        NetworkFilter.FOLLOW_FREEZE -> policy == NetworkAppState.FOLLOW_FREEZE
        NetworkFilter.BLOCKED -> policy == NetworkAppState.BLOCKED
        NetworkFilter.UNRESTRICTED -> policy == NetworkAppState.UNRESTRICTED
    }

    private fun matches(app: AppInfo, term: String): Boolean =
        app.label.contains(term, ignoreCase = true) ||
            app.packageName.contains(term, ignoreCase = true) ||
            Pinyin.rank(app.label, app.packageName, term) != Pinyin.NO_MATCH
}
