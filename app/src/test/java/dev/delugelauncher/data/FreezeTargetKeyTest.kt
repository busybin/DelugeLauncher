// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreezeTargetKeyTest {

    @Test
    fun `the main profile keeps the bare package name`() {
        assertEquals("com.example.notes", FreezeTargetKey.of("com.example.notes", 0L))
        assertEquals(0L, FreezeTargetKey.userSerial("com.example.notes"))
    }

    @Test
    fun `another profile carries its serial`() {
        val key = FreezeTargetKey.of("com.example.notes", 10L)
        assertEquals("com.example.notes|u10", key)
        assertEquals("com.example.notes", FreezeTargetKey.packageName(key))
        assertEquals(10L, FreezeTargetKey.userSerial(key))
    }

    @Test
    fun `a package name is never mistaken for the suffix`() {
        // Package names cannot contain a bar, which is what makes the split unambiguous.
        assertEquals("a.b.c", FreezeTargetKey.packageName("a.b.c"))
        assertEquals("a.b.c", FreezeTargetKey.packageName("a.b.c|u7"))
    }
}
