// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a settings file without touching the store: what lands, what is skipped, and what
 * must be reported rather than swallowed.
 */
class ImportParseTest {

    private fun file(vararg entries: Pair<String, String>, format: Int = 1): String {
        val values = entries.joinToString(",") { (name, entry) -> "\"$name\":$entry" }
        return """{"format":$format,"values":{$values}}"""
    }

    /** Same as Prefs' own key; it is private there. */
    private val LANGUAGE_KEY = "language"

    private fun str(value: String) = """{"type":"string","value":"$value"}"""

    @Test
    fun `a plain file parses every entry`() {
        val parsed = parseImport(
            file("some_flag" to """{"type":"boolean","value":true}""", "other" to str("x"))
        )!!
        assertEquals(2, parsed.values.size)
        assertEquals(0, parsed.dropped)
    }

    @Test
    fun `the language is lifted out of the value list`() {
        val parsed = parseImport(file(LANGUAGE_KEY to str("zh-CN")))!!
        assertEquals("zh-CN", parsed.language)
        assertTrue(parsed.languagePresent)
        assertTrue(parsed.values.isEmpty())
    }

    @Test
    fun `a blank language is present but empty`() {
        val parsed = parseImport(file(LANGUAGE_KEY to str("")))!!
        assertTrue(parsed.languagePresent)
        assertEquals("", parsed.language)
    }

    @Test
    fun `an absent language is not a request to change it`() {
        val parsed = parseImport(file("some_flag" to """{"type":"boolean","value":true}"""))!!
        assertFalse(parsed.languagePresent)
        assertNull(parsed.language)
    }

    @Test
    fun `an entry with an unknown type is dropped and counted`() {
        val parsed = parseImport(file("weird" to """{"type":"future","value":1}"""))!!
        assertTrue(parsed.values.isEmpty())
        assertEquals(1, parsed.dropped)
    }

    @Test
    fun `an entry that is not an object is dropped and counted`() {
        val parsed = parseImport("""{"format":1,"values":{"broken":42}}""")!!
        assertEquals(1, parsed.dropped)
    }

    @Test
    fun `an entry whose value has the wrong type is dropped and counted`() {
        val parsed = parseImport(file("some_flag" to """{"type":"boolean","value":"yes"}"""))!!
        assertTrue(parsed.values.isEmpty())
        assertEquals(1, parsed.dropped)
    }

    @Test
    fun `a numeric setting outside its range is clamped, not dropped`() {
        val parsed = parseImport(
            file("icon_size_dp" to """{"type":"int","value":5000}""")
        )!!
        assertEquals(0, parsed.dropped)
        assertEquals(96, parsed.values.single().second)
    }

    @Test
    fun `a set above the item limit is dropped rather than stored`() {
        val items = (0 until ImportLimits.MAX_SET_ITEMS + 1).joinToString(",") { "\"v$it\"" }
        val parsed = parseImport(file("tags" to """{"type":"stringSet","value":[$items]}"""))!!
        assertTrue(parsed.values.isEmpty())
        assertEquals(1, parsed.dropped)
    }

    @Test
    fun `a newer format still restores the entries this build understands`() {
        val parsed = parseImport(
            file("some_flag" to """{"type":"boolean","value":true}""", format = 99)
        )!!
        assertEquals(1, parsed.values.size)
    }

    @Test
    fun `an older format is refused whole`() {
        assertNull(
            parseImport(file("some_flag" to """{"type":"boolean","value":true}""", format = 0))
        )
    }

    @Test
    fun `junk and oversized text are refused`() {
        assertNull(parseImport("{not json"))
        assertNull(parseImport(""))
        assertNull(parseImport("x".repeat(ImportLimits.MAX_BYTES + 1)))
    }

    @Test
    fun `a file with no values object is refused`() {
        assertNull(parseImport("""{"format":1}"""))
    }

    @Test
    fun `a favorite line with an unusable shortcut id is stripped, not stored`() {
        val parsed = parseImport(
            file(PREF_FAVORITES to str("app/a/Main\\nshortcut:com.example/ba\\td\\napp/c/Main"))
        )!!
        val value = parsed.values.single().second as String
        assertEquals("app/a/Main\napp/c/Main", value)
    }
}
