// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.Immutable

/**
 * What a row is. Everything that lists, sorts, searches, renames or files rows away treats them
 * alike; only starting one, finding its icon and the menu it offers depend on this.
 */
enum class EntryKind {
    /** An installed app's launchable activity. */
    APP,

    /** A shortcut another app asked to have pinned, such as a browser's bookmark. */
    SHORTCUT,

    /** The entry that asks for a query and hands it to a search engine. */
    SEARCH,

    /** The row that locks and unlocks Android's private space. */
    PRIVATE_SPACE,
}

/**
 * A ComponentName is immutable, but it comes from the platform with no stability information,
 * so Compose infers this whole class as unstable and stops every row that takes one from ever
 * skipping recomposition. The annotation states what is already true.
 */
@Immutable
data class AppInfo(
    val componentName: ComponentName,
    val label: String,
    /** Which profile owns this activity: work, private space, or the main one. */
    val user: UserHandle? = null,
    /** The profile's serial. Zero is the main profile, which is what every old key assumed. */
    val userSerial: Long = 0L,
    val kind: EntryKind = EntryKind.APP,
    /** The publisher's id for a pinned shortcut. Null for every other kind. */
    val shortcutId: String? = null,
    /** A shortcut its publisher has switched off: still listed, shown dimmed, never started. */
    val disabled: Boolean = false,
    /**
     * Paused by the user (Android's "pause app" / suspended): still installed and launchable,
     * but the launcher can show it as set aside. Kept separate from [disabled], which is a
     * shortcut the publisher retired.
     */
    val suspended: Boolean = false,
    /**
     * The method currently holding this app frozen, or null when it is free. Rows that only
     * exist because they were frozen carry it too, so they can offer a one-tap unfreeze.
     */
    val frozen: FreezeMethod? = null,
    /**
     * In the freeze list, whether or not it is frozen right now. A row that is in the list
     * but currently lifted offers to be put back rather than to be added; a row that is in
     * neither offers to be frozen.
     */
    val planned: Boolean = false,
    /**
     * From a work profile, so it can say so.
     *
     * The system badges the icon already, but this is a list of names where an icon is small
     * and can be switched off entirely — and a work profile's whole problem is two rows both
     * called Teams.
     */
    val work: Boolean = false,
    /** Preinstalled (or an in-place update of one), so freezing it needs the extra warning. */
    val system: Boolean = false,
    /**
     * From the private space, as opposed to a work profile or the main one.
     *
     * Distinct from [EntryKind.PRIVATE_SPACE], which is the single row that locks and unlocks
     * the space rather than an app living inside it.
     */
    val privateProfile: Boolean = false,
    /** When the package was first installed, for listing what arrived recently. Zero if unknown. */
    val installedAt: Long = 0L,
    /** When the package last changed, for sorting by update order. Zero if unknown. */
    val updatedAt: Long = 0L,
) {
    // Held rather than derived: this is the map key for overrides, favorites and list item
    // keys, so it is asked for several times per visible row per frame while scrubbing, and
    // flattenToString() builds a new string every time.
    //
    // The main profile's key is byte-for-byte what it was before profiles existed, so every
    // stored favorite, rename, icon and hidden entry still matches. Only apps from a second
    // profile carry the suffix, and those could not have been stored before anyway.
    //
    // A shortcut or the search entry has no activity of its own to name it, so its key carries
    // a prefix no component can start with. See EntryKeys.
    val key: String = when (kind) {
        EntryKind.APP -> EntryKeys.app(componentName.flattenToString(), userSerial)
        EntryKind.SHORTCUT ->
            EntryKeys.shortcut(componentName.packageName, shortcutId.orEmpty(), userSerial)
        EntryKind.SEARCH -> EntryKeys.SEARCH
        EntryKind.PRIVATE_SPACE -> EntryKeys.PRIVATE_SPACE
    }

    val packageName: String get() = componentName.packageName

    /**
     * Whether [locked] covers this row. Locks are stored per component, but a frozen app is
     * rebuilt under whichever activity the package manager names as its launcher, so the key
     * can change while the app is the same one. Any row of the same package in the same
     * profile therefore counts, shortcuts into that app included; another profile keeps its
     * own lock.
     */
    fun lockedBy(locked: Set<String>): Boolean =
        key in locked || ((kind == EntryKind.APP || kind == EntryKind.SHORTCUT) &&
            locked.any { other ->
                !EntryKeys.isShortcut(other) &&
                    other.substringBefore('/') == packageName &&
                    EntryKeys.userSerial(other) == userSerial
            })
}