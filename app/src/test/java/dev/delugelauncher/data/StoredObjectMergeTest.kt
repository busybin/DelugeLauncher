// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge helpers behind every small JSON table (widget providers, app tags).
 *
 * The rule under test: a table that cannot be parsed is left alone. Replacing it with just the
 * row being written would silently drop every other row it used to hold.
 */
class StoredObjectMergeTest {

    @Test
    fun `a missing table starts empty`() {
        val text = mergeStoredObject(null, "7", "com.example.widget")!!
        assertEquals("com.example.widget", JSONObject(text).getString("7"))
    }

    @Test
    fun `a blank table starts empty`() {
        val text = mergeStoredObject("   ", "1", "com.example")!!
        assertEquals("com.example", JSONObject(text).getString("1"))
    }

    @Test
    fun `an existing table keeps its other rows`() {
        val text = mergeStoredObject("""{"1":"com.a"}""", "2", "com.b")!!
        val obj = JSONObject(text)
        assertEquals("com.a", obj.getString("1"))
        assertEquals("com.b", obj.getString("2"))
    }

    @Test
    fun `a corrupt table refuses the write`() {
        assertNull(mergeStoredObject("{not json", "1", "com.a"))
        assertNull(mergeStoredObject("""["a","b"]""", "1", "com.a"))
        assertNull(mergeStoredObject("plain text", "1", "com.a"))
    }

    @Test
    fun `a change can remove a row and still keep the rest`() {
        val text = updateStoredObject("""{"a":["x"],"b":["y"]}""") { obj ->
            obj.remove("a")
        }!!
        val obj = JSONObject(text)
        assertTrue(!obj.has("a"))
        assertEquals("y", obj.getJSONArray("b").getString(0))
    }

    @Test
    fun `a change can put an array under a key`() {
        val text = updateStoredObject(null) { obj ->
            obj.put("k", JSONArray(listOf("one", "two")))
        }!!
        assertEquals(2, JSONObject(text).getJSONArray("k").length())
    }

    @Test
    fun `a corrupt table refuses a remove as well`() {
        assertNull(updateStoredObject("}}{{") { it.remove("k") })
    }
}
