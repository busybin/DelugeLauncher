// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.ComponentName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which row a stored lock covers. The store is keyed by component, but every row of the same
 * package in the same profile has to be behind the lock, or a shortcut or an updated launcher
 * activity would become a way around it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppInfoLockTest {

    private val component = ComponentName("com.example.notes", "com.example.notes.MainActivity")
    private val app = AppInfo(componentName = component, label = "Notes")

    @Test
    fun `the exact row is covered`() {
        assertTrue(app.lockedBy(setOf(app.key)))
    }

    @Test
    fun `another launcher activity of the same package is covered`() {
        val other = EntryKeys.app("com.example.notes/com.example.notes.OtherActivity", 0L)
        assertTrue(app.lockedBy(setOf(other)))
    }

    @Test
    fun `a shortcut into the same package is covered`() {
        val shortcut = AppInfo(
            componentName = component,
            label = "New note",
            kind = EntryKind.SHORTCUT,
            shortcutId = "new-note",
        )
        assertTrue(shortcut.lockedBy(setOf(app.key)))
    }

    @Test
    fun `another package is not covered`() {
        assertFalse(app.lockedBy(setOf("com.example.clock/com.example.clock.MainActivity")))
    }

    @Test
    fun `the same package in another profile keeps its own lock`() {
        val work = AppInfo(componentName = component, label = "Notes", userSerial = 10L)
        assertFalse(work.lockedBy(setOf(app.key)))
    }

    @Test
    fun `the private space row is never an app lock`() {
        val row = AppInfo(
            componentName = ComponentName("android", "PrivateSpace"),
            label = "Private space",
            kind = EntryKind.PRIVATE_SPACE,
        )
        assertFalse(row.lockedBy(setOf(app.key)))
    }

    @Test
    fun `a package is locked even when its row is not in the list`() {
        // A hidden app has no row to ask, but a notification still names its package.
        assertTrue(isPackageLocked(setOf(app.key), "com.example.notes", 0L))
        assertFalse(isPackageLocked(setOf(app.key), "com.example.notes", 10L))
    }

    @Test
    fun `shortcut-only and private-space keys do not lock a package`() {
        val shortcut = EntryKeys.shortcut("com.example.notes", "new-note")
        val privateSpace = EntryKeys.PRIVATE_SPACE
        assertFalse(isPackageLocked(setOf(shortcut, privateSpace), "com.example.notes", 0L))
    }
}
