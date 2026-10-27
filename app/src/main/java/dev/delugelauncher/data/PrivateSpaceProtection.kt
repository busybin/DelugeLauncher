// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

/**
 * The step the private-space flow is on. It is its own action on an app — "Add to private
 * space" — not part of locking: the lock already hides an app on this phone, while the space
 * moves it behind a boundary the rest of Android cannot reach.
 *
 * Creating the space stays the owner's own step: only the system may do it, behind its own
 * authentication. Everything after that can be done without a store when a shell is available:
 * `pm install-existing` puts the same package into the private profile, and `pm uninstall
 * --user 0` removes the copy outside. Both keep the app usable and make it disappear from
 * Settings while the space is locked.
 */
enum class ProtectStep {
    /** No flow is running. */
    NONE,

    /** There is no private space yet; offer to open the system's creation page. */
    CREATE,

    /** There is a space, but it is locked and cannot be read; ask to unlock it. */
    UNLOCK,

    /** The space is open and the app is not in it yet; open the store inside the space. */
    INSTALL_STORE,

    /** A shell can put the existing package into the space without a store or an APK. */
    INSTALL_AUTO,

    /** The app is in the space and also outside; offer to remove the outside copy. */
    REMOVE_MAIN,

    /** Only the private copy is left, or the outside copy has just gone. Nothing to ask. */
    DONE,
}

/**
 * What to ask next, given what the platform will say about the space and the package.
 *
 * [installedInSpace] is null whenever the space is not open enough to be asked through the
 * launcher. [shellAvailable] is what makes the locked state irrelevant: a shell can read and
 * write the private profile while it is locked, so the space does not have to be opened just to
 * install the package. [installedInMain] is what the launcher's own profile still has.
 * [afterRemoval] is true when the previous step was [ProtectStep.REMOVE_MAIN], which is the one
 * case where the space no longer has to be readable: its copy was seen a moment ago, and all
 * that is left to check is whether the outside copy is gone.
 */
fun protectStepFor(
    space: PrivateSpaceKind,
    installedInSpace: Boolean?,
    installedInMain: Boolean,
    shellAvailable: Boolean,
    afterRemoval: Boolean = false,
): ProtectStep = when {
    afterRemoval && !installedInMain -> ProtectStep.DONE
    space == PrivateSpaceKind.ABSENT -> ProtectStep.CREATE
    installedInSpace == true && !installedInMain -> ProtectStep.DONE
    installedInSpace == true -> ProtectStep.REMOVE_MAIN
    // A shell answers for a locked space and installs without opening it. Without one the
    // store is the only way in, and that needs the space open.
    installedInSpace == false && (space == PrivateSpaceKind.UNLOCKED || shellAvailable) ->
        if (shellAvailable) ProtectStep.INSTALL_AUTO else ProtectStep.INSTALL_STORE
    space == PrivateSpaceKind.LOCKED -> ProtectStep.UNLOCK
    shellAvailable -> ProtectStep.INSTALL_AUTO
    else -> ProtectStep.INSTALL_STORE
}

/** [PrivateSpace] as the three answers [protectStepFor] decides from. */
val PrivateSpace.protectionKind: PrivateSpaceKind
    get() = when (this) {
        is PrivateSpace.Unlocked -> PrivateSpaceKind.UNLOCKED
        is PrivateSpace.Absent -> PrivateSpaceKind.ABSENT
        // Locked, and Uncertain for the same reason it is treated as locked everywhere else.
        is PrivateSpace.Locked, is PrivateSpace.Uncertain -> PrivateSpaceKind.LOCKED
    }
