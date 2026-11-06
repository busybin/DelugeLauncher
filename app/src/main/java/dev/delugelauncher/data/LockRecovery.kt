// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

/**
 * Lock records a damaged settings file lost while the guard still holds the apps.
 *
 * The lock set is one entry among many in the same store. A torn write can drop it whole,
 * which would leave the apps the system still has hidden and suspended with no row and no way
 * back from inside the launcher. The guard's plain file is written from the same set and says
 * which packages are being held, so the records can be rebuilt from it.
 *
 * Only ask this after a read that had to be salvaged: on a healthy store the guard list can
 * briefly lag a lock the owner just lifted, and resurrecting that one would undo their tap.
 */
object LockRecovery {
    fun orphanedPackages(listed: Set<String>, locked: Set<String>): Set<String> {
        val held = locked.mapTo(mutableSetOf()) { it.substringBefore('/') }
        return listed - held
    }
}
