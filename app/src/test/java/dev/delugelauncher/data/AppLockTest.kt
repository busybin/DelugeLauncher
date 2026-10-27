// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    @After
    fun clear() = AppLock.forgetAll()

    @Test
    fun `nothing is open before it is opened`() {
        assertFalse(AppLock.isOpen("com.example.notes", timeoutSeconds = 300, now = 1_000L))
    }

    @Test
    fun `zero means every tap asks again`() {
        AppLock.markOpen("com.example.notes", now = 1_000L)
        assertFalse(AppLock.isOpen("com.example.notes", timeoutSeconds = 0, now = 1_000L))
    }

    @Test
    fun `an app stays open inside the timeout`() {
        AppLock.markOpen("com.example.notes", now = 1_000L)
        assertTrue(AppLock.isOpen("com.example.notes", 300, now = 1_000L + 299_000L))
    }

    @Test
    fun `the timeout runs out`() {
        AppLock.markOpen("com.example.notes", now = 1_000L)
        assertFalse(AppLock.isOpen("com.example.notes", 300, now = 1_000L + 300_000L))
    }

    @Test
    fun `a screen off forgets everything`() {
        AppLock.markOpen("com.example.notes", now = 1_000L)
        AppLock.forgetAll()
        assertFalse(AppLock.isOpen("com.example.notes", 300, now = 2_000L))
    }

    @Test
    fun `opening one app does not open another`() {
        AppLock.markOpen("com.example.notes", now = 1_000L)
        assertFalse(AppLock.isOpen("com.example.clock", 300, now = 1_000L))
    }
}
