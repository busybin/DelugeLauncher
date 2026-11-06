// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLimitTest {

    @Test
    fun `zero is no limit, not never open`() {
        assertFalse(openLimitReached(limit = 0, opensToday = 0))
        assertFalse(openLimitReached(limit = 0, opensToday = 99))
    }

    @Test
    fun `the limit allows exactly that many opens`() {
        assertFalse(openLimitReached(limit = 3, opensToday = 2))
        assertTrue(openLimitReached(limit = 3, opensToday = 3))
        assertTrue(openLimitReached(limit = 3, opensToday = 4))
    }

    @Test
    fun `a stored limit stays inside what the editor can draw`() {
        assertEquals(0, sanitizeOpenLimit(-5))
        assertEquals(999, sanitizeOpenLimit(10_000))
        assertEquals(42, sanitizeOpenLimit(42))
    }
}
