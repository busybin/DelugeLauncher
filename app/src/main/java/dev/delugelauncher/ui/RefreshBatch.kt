// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui

/**
 * Folds a burst of package callbacks into one enumeration.
 *
 * LauncherApps reports changes one package at a time: freezing a list of forty apps sends forty
 * of them inside a second, and a full pass each would keep the list rebuilding for as long as
 * the run lasts. Names are pooled here and read once the burst has gone quiet.
 */
internal class RefreshBatch {

    private val packages = LinkedHashSet<String>()

    /** Something changed that is not a single package, so every icon is suspect. */
    var full: Boolean = false
        private set

    /** At least one request needs the pictures redrawn, not just the rows. */
    var clearsIcons: Boolean = false
        private set

    val names: Set<String> get() = packages

    fun add(packageName: String?, clearIcons: Boolean) {
        if (packageName == null) full = true else packages += packageName
        this.clearsIcons = this.clearsIcons || clearIcons
    }
}
