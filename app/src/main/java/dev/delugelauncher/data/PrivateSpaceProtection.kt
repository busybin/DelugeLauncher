// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

/**
 * The step the lock-time protection flow is on. A main-space app that is locked through Deluge
 * keeps one way in that the launcher cannot close: Android Settings, recents and other
 * launchers still know the package. Moving it into the private space is the only boundary the
 * platform gives an unprivileged launcher, so locking such an app asks about that boundary.
 *
 * Nothing here can create the space or install the app on the owner's behalf — the system
 * keeps both behind its own authentication and its own store — so the flow names the single
 * next thing the owner has to do and then resumes where it left off.
 */
enum class ProtectStep {
    /** No flow is running. */
    NONE,

    /** There is no private space yet; offer to open the system's creation page. */
    CREATE,

    /** There is a space, but it is locked; ask to unlock it so the next step can read it. */
    UNLOCK,

    /** The space is open and the app is not in it yet; open the store inside the space. */
    INSTALL,

    /** The app is in the space and also outside; offer to remove the outside copy. */
    REMOVE_MAIN,

    /** Only the private copy is left, or the outside copy has just gone. Nothing to ask. */
    DONE,
}

/**
 * What to ask next, given what the platform will say about the space and the package.
 *
 * [installedInSpace] is null whenever the space is not open enough to be asked — including
 * when there is no space at all — and [installedInMain] is what the launcher's own profile
 * still has. [afterRemoval] is true when the previous step was [ProtectStep.REMOVE_MAIN], which
 * is the one case where the space no longer has to be readable: its copy was seen a moment
 * ago, and all that is left to check is whether the outside copy is gone.
 */
fun protectStepFor(
    space: PrivateSpaceKind,
    installedInSpace: Boolean?,
    installedInMain: Boolean,
    afterRemoval: Boolean = false,
): ProtectStep = when {
    afterRemoval && !installedInMain -> ProtectStep.DONE
    space == PrivateSpaceKind.ABSENT -> ProtectStep.CREATE
    space == PrivateSpaceKind.LOCKED -> ProtectStep.UNLOCK
    installedInSpace == true && !installedInMain -> ProtectStep.DONE
    installedInSpace == true -> ProtectStep.REMOVE_MAIN
    else -> ProtectStep.INSTALL
}

/** [PrivateSpace] as the three answers [protectStep] decides from. */
val PrivateSpace.protectionKind: PrivateSpaceKind
    get() = when (this) {
        is PrivateSpace.Unlocked -> PrivateSpaceKind.UNLOCKED
        is PrivateSpace.Absent -> PrivateSpaceKind.ABSENT
        // Locked, and Uncertain for the same reason it is treated as locked everywhere else.
        is PrivateSpace.Locked, is PrivateSpace.Uncertain -> PrivateSpaceKind.LOCKED
    }
