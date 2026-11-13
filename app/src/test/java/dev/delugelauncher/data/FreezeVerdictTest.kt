// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule both freeze directions share: an unreadable state is not an answer, and an app that
 * is gone is never a failure to freeze or a reason to lift.
 */
class FreezeVerdictTest {

    private fun state(
        enabled: Boolean = true,
        hidden: Boolean = false,
        suspended: Boolean = false,
        installed: Boolean = true,
    ) = FreezeAppState(enabled = enabled, hidden = hidden, suspended = suspended, installed = installed)

    @Test
    fun `an unknown state is not held`() {
        assertFalse(FreezeVerdict.held(null, parkedByPlatform = false))
    }

    @Test
    fun `an unknown state is not free`() {
        assertFalse(FreezeVerdict.free(null, suspended = false, parkedByPlatform = false))
    }

    @Test
    fun `an uninstalled app is neither held nor a failed freeze`() {
        val gone = state(installed = false)
        assertFalse(FreezeVerdict.held(gone, parkedByPlatform = false))
        assertTrue(FreezeVerdict.free(gone, suspended = false, parkedByPlatform = false))
    }

    @Test
    fun `a plain enabled app is free and not held`() {
        val plain = state()
        assertFalse(FreezeVerdict.held(plain, parkedByPlatform = false))
        assertTrue(FreezeVerdict.free(plain, suspended = false, parkedByPlatform = false))
    }

    @Test
    fun `a disabled app is held and not free`() {
        val disabled = state(enabled = false)
        assertTrue(FreezeVerdict.held(disabled, parkedByPlatform = false))
        assertFalse(FreezeVerdict.free(disabled, suspended = false, parkedByPlatform = false))
    }

    @Test
    fun `a platform-parked disabled app is neither held nor free`() {
        val parked = state(enabled = false)
        // The platform's own park is not a freeze of ours, so nothing reports it as held.
        assertFalse(FreezeVerdict.held(parked, parkedByPlatform = true))
        // But a shell cannot lift a park either, and calling it free would leave the app
        // disabled behind a record that said it had been released.
        assertFalse(FreezeVerdict.free(parked, suspended = false, parkedByPlatform = true))
    }

    @Test
    fun `a hidden app is held and never read as free`() {
        val hidden = state(hidden = true)
        assertTrue(FreezeVerdict.held(hidden, parkedByPlatform = false))
        // An enabled app can still be hidden, so the shared predicate must not answer "free"
        // for it: only the hide probe can say that.
        assertFalse(FreezeVerdict.free(hidden, suspended = false, parkedByPlatform = false))
    }

    @Test
    fun `a suspended app is held and not free`() {
        val suspended = state(suspended = true)
        assertTrue(FreezeVerdict.held(suspended, parkedByPlatform = false))
        assertFalse(FreezeVerdict.free(suspended, suspended = true, parkedByPlatform = false))
    }

    @Test
    fun `an app the shell reports free is free even if the snapshot said suspended`() {
        // The live probe is the answer the lift path has always trusted.
        assertTrue(FreezeVerdict.free(state(suspended = true), suspended = false, parkedByPlatform = false))
    }
}
