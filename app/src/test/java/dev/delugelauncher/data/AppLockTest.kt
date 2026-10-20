// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A locked app has to stay locked when a freeze and unfreeze rebuilds its row under another
 * launcher activity. The stored key names one component; the lock has to follow the package.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockTest {

    private fun app(packageName: String, activity: String) = AppInfo(
        componentName = ComponentName(packageName, "$packageName.$activity"),
        label = activity,
    )

    @Test
    fun `a lock follows the package when the launcher activity changes`() {
        val locked = app("com.example.app", "Main")
        val rebuilt = app("com.example.app", "Launcher")
        assertTrue(rebuilt.lockedBy(setOf(locked.key)))
    }

    @Test
    fun `another package is not covered`() {
        val locked = app("com.example.app", "Main")
        val other = app("com.example.other", "Main")
        assertFalse(other.lockedBy(setOf(locked.key)))
    }

    @Test
    fun `another profile keeps its own lock`() {
        val locked = app("com.example.app", "Main")
        val private = app("com.example.app", "Main").copy(userSerial = 10)
        assertFalse(private.lockedBy(setOf(locked.key)))
        assertTrue(private.copy().lockedBy(setOf(private.key)))
    }

    @Test
    fun `a locked shortcut does not lock the app`() {
        val target = app("com.example.app", "Main")
        val shortcut = AppInfo(
            componentName = ComponentName("com.example.app", "com.example.app.Main"),
            label = "shortcut",
            kind = EntryKind.SHORTCUT,
            shortcutId = "pinned-1",
        )
        assertFalse(target.lockedBy(setOf(shortcut.key)))
    }

    @Test
    fun `a locked app covers the shortcuts into it`() {
        val locked = app("com.example.app", "Main")
        val shortcut = AppInfo(
            componentName = ComponentName("com.example.app", "com.example.app.Main"),
            label = "shortcut",
            kind = EntryKind.SHORTCUT,
            shortcutId = "pinned-1",
        )
        assertTrue(shortcut.lockedBy(setOf(locked.key)))
    }

    @Test
    fun `a shortcut stays free when only another profile is locked`() {
        val locked = app("com.example.app", "Main")
        val work = AppInfo(
            componentName = ComponentName("com.example.app", "com.example.app.Main"),
            label = "shortcut",
            kind = EntryKind.SHORTCUT,
            shortcutId = "pinned-1",
            userSerial = 10,
        )
        assertFalse(work.lockedBy(setOf(locked.key)))
        assertTrue(work.lockedBy(setOf(work.key)))
    }

    @Test
    fun `locking a second app keeps the first`() {
        val first = app("com.example.one", "Main").key
        val second = app("com.example.two", "Main").key
        val locked = EntryKeys.withAppLocked(EntryKeys.withAppLocked(emptySet(), first, true), second, true)
        assertEquals(setOf(first, second), locked)
    }

    @Test
    fun `unlocking one app leaves the others locked`() {
        val first = app("com.example.one", "Main").key
        val second = app("com.example.two", "Main").key
        val both = setOf(first, second)
        assertEquals(setOf(second), EntryKeys.withAppLocked(both, first, false))
        assertEquals(emptySet<String>(), EntryKeys.withAppLocked(setOf(second), second, false))
    }

    @Test
    fun `a rebuilt launcher activity replaces the old key and can be unlocked`() {
        val original = app("com.example.app", "Main").key
        val rebuilt = app("com.example.app", "Launcher").key
        val locked = EntryKeys.withAppLocked(setOf(original), rebuilt, true)
        assertEquals(setOf(rebuilt), locked)
        assertEquals(emptySet<String>(), EntryKeys.withAppLocked(locked, rebuilt, false))
    }

    @Test
    fun `another profile and a shortcut keep their locks`() {
        val main = app("com.example.app", "Main").key
        val work = AppInfo(
            componentName = ComponentName("com.example.app", "com.example.app.Main"),
            label = "work",
            userSerial = 10,
        ).key
        val shortcut = EntryKeys.shortcut("com.example.app", "pinned-1")
        val locked = setOf(main, work, shortcut)
        assertEquals(setOf(work, shortcut, main), EntryKeys.withAppLocked(locked, main, true))
        assertEquals(setOf(work, shortcut), EntryKeys.withAppLocked(locked, main, false))
    }
}
