// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The automatic freeze has to read the foreground app and the notified apps out of dumps the
 * platform prints in shapes that vary by release, so the parsing is pinned here.
 */
class FreezeGuardsTest {

    @Test
    fun `the resumed activity names its package`() {
        val dump = """
            ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
              topResumedActivity=ActivityRecord{8a1 u0 com.example.notes/.MainActivity t42}
              mResumedActivity: ActivityRecord{7b2 u0 com.example.notes/.MainActivity t42}
        """.trimIndent()
        assertEquals("com.example.notes", FreezeGuards.foregroundPackage(dump))
    }

    @Test
    fun `a focused app is the fallback`() {
        val dump = "  mFocusedApp=ActivityRecord{1a2 u0 com.example.chat/.Chat t7}"
        assertEquals("com.example.chat", FreezeGuards.foregroundPackage(dump))
    }

    @Test
    fun `a dump with nothing resumed has no foreground app`() {
        assertNull(FreezeGuards.foregroundPackage("ACTIVITY MANAGER ACTIVITIES (dumpsys)"))
    }

    @Test
    fun `notified packages are collected without duplicates`() {
        val dump = """
            NotificationRecord(0x1: pkg=com.example.mail user=0)
            NotificationRecord(0x2: pkg=com.example.mail user=0)
            NotificationRecord(0x3: pkg=com.example.music user=0)
        """.trimIndent()
        assertEquals(
            setOf("com.example.mail", "com.example.music"),
            FreezeGuards.notifiedPackages(dump),
        )
    }

    @Test
    fun `a notification dump with no packages yields nothing`() {
        assertEquals(emptySet<String>(), FreezeGuards.notifiedPackages("no records"))
    }
}
