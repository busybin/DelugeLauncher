// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class ImportLimitsTest {

    @Test
    fun `geometry values are clamped to what the UI offers`() {
        assertEquals(12, ImportLimits.int("icon_size_dp", -4))
        assertEquals(96, ImportLimits.int("icon_size_dp", 4000))
        assertEquals(28, ImportLimits.int("label_size_sp", 900))
        assertEquals(900, ImportLimits.int("widget_height_dp", 100_000))
        assertEquals(-200, ImportLimits.int("widget_offset_x_dp", -9000))
    }

    @Test
    fun `a name with no stated range is left alone`() {
        assertEquals(12345, ImportLimits.int("something_else", 12345))
    }

    @Test
    fun `alpha is kept inside zero and one`() {
        assertEquals(1f, ImportLimits.float("dim_home_alpha", 9f))
        assertEquals(0f, ImportLimits.float("dim_wallpaper_alpha", -1f))
        assertEquals(0.4f, ImportLimits.float("dim_home_alpha", 0.4f))
    }

    @Test
    fun `a float that is not a number is refused`() {
        assertNull(ImportLimits.float("dim_home_alpha", Float.NaN))
        assertNull(ImportLimits.float("dim_home_alpha", Float.POSITIVE_INFINITY))
    }

    @Test
    fun `an oversized string is refused`() {
        assertEquals("ok", ImportLimits.string("ok"))
        assertNull(ImportLimits.string("x".repeat(ImportLimits.MAX_STRING_CHARS + 1)))
    }

    @Test
    fun `a string set is bounded in count and in length`() {
        val set = (1..10).map { "key$it" }.toSet()
        assertEquals(set, ImportLimits.stringSet(set))
        assertNull(ImportLimits.stringSet((1..ImportLimits.MAX_SET_ITEMS + 1).map { "k$it" }.toSet()))
        assertNull(ImportLimits.stringSet(setOf("x".repeat(ImportLimits.MAX_SET_ITEM_CHARS + 1))))
    }

    @Test
    fun `reading stops at the byte cap instead of growing without bound`() {
        val tooBig = ByteArray(ImportLimits.MAX_BYTES + 1) { 'a'.code.toByte() }
        assertNull(ImportLimits.read(ByteArrayInputStream(tooBig)))
    }

    @Test
    fun `a normal file is read as UTF-8`() {
        val text = """{"format":1,"values":{}}"""
        assertEquals(text, ImportLimits.read(ByteArrayInputStream(text.toByteArray())))
    }
}
