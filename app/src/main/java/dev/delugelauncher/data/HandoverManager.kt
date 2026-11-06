// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hands the phone over with the freeze list asleep, and puts it back afterwards.
 *
 * Entering remembers what was already frozen and freezes the rest of the list; leaving thaws
 * only what the handover added, so apps the owner had asleep before stay asleep. Locking the
 * owner's private apps is deliberately not part of it yet: lifting a lock needs the device
 * credential, and a handover that cannot be undone without one is a worse trap than an app
 * left open.
 */
class HandoverManager(
    private val prefs: Prefs,
    private val freeze: FreezeManager,
) {
    suspend fun isActive(): Boolean = prefs.handoverNow().active

    /** Freezes the list; returns how many apps were put to sleep. Zero when already active. */
    suspend fun enter(): Int = gate.withLock {
        if (prefs.handoverNow().active) return@withLock 0
        prefs.setHandover(HandoverState(active = true, frozenBefore = prefs.freezeAppliedNow()))
        // The guest holds the phone: an app left awake because it was in front or had a
        // notification is exactly what the handover promised to put away.
        freeze.freezeAll(auto = true, skipInUse = false).done
    }

    /** Thaws what the handover froze and clears the state; zero when it was not active. */
    suspend fun exit(): Int = gate.withLock {
        val state = prefs.handoverNow()
        if (!state.active) return@withLock 0
        val exit = handoverExit(
            state = state,
            frozenNow = prefs.freezeAppliedNow(),
            lockedNow = prefs.lockedAppsNow(),
        )
        var lifted = 0
        for (key in exit.thaw) {
            if (freeze.unfreezeKey(key) == FreezeOutcome.DONE) lifted++
        }
        prefs.setHandover(HandoverState())
        lifted
    }

    private companion object {
        /** A fast double tap used to run two enters; the second recorded the first's work. */
        val gate = Mutex()
    }
}
