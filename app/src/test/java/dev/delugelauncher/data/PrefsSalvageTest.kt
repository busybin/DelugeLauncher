// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrefsSalvageTest {

    private val first = stringPreferencesKey("favorites")
    private val second = intPreferencesKey("icon_size_dp")
    private val third = stringPreferencesKey("icon_shape")

    private fun encoded(): ByteArray = runBlocking {
        val buffer = Buffer()
        PreferencesSerializer.writeTo(preferencesOf(first to "a,b,c", second to 42, third to "CIRCLE"), buffer)
        buffer.readByteArray()
    }

    private fun write(bytes: ByteArray): File =
        File.createTempFile("deluge_prefs", ".preferences_pb").apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    @Test
    fun `an intact file comes back whole`() {
        val salvaged = salvageFile(write(encoded()))
        assertEquals("a,b,c", salvaged.preferences[first])
        assertEquals(42, salvaged.preferences[second])
        assertEquals("CIRCLE", salvaged.preferences[third])
        assertFalse(salvaged.lost)
    }

    @Test
    fun `a torn write keeps the entries before the tear`() {
        val bytes = encoded()
        // Zeros from partway into the last entry to the end, as the reported file had.
        val torn = bytes.copyOf()
        for (i in bytes.size - 4 until bytes.size) torn[i] = 0
        val salvaged = salvageFile(write(torn + ByteArray(64)))
        assertEquals("a,b,c", salvaged.preferences[first])
        assertEquals(42, salvaged.preferences[second])
        assertNull(salvaged.preferences[third])
        assertTrue(salvaged.lost)
    }

    @Test
    fun `a trailing zero from an empty value is not damage`() {
        // An empty string encodes as tag 0x0A, length 0 — the file legitimately ends in zero.
        val last = stringPreferencesKey("last_value")
        val bytes = runBlocking {
            val buffer = Buffer()
            PreferencesSerializer.writeTo(preferencesOf(first to "a", last to ""), buffer)
            buffer.readByteArray()
        }
        val salvaged = salvageFile(write(bytes))
        assertEquals("a", salvaged.preferences[first])
        assertEquals("", salvaged.preferences[last])
        assertFalse(salvaged.lost)
    }

    @Test
    fun `damage in the middle still recovers the entries after it`() {
        val firstSize = runBlocking {
            val buffer = Buffer()
            PreferencesSerializer.writeTo(preferencesOf(first to "a,b,c"), buffer)
            buffer.readByteArray().size
        }
        val bytes = encoded()
        // Break the second entry's body; the scan used to stop here and drop the third.
        for (i in firstSize + 2 until firstSize + 6) bytes[i] = 0
        val salvaged = salvageFile(write(bytes))
        assertEquals("a,b,c", salvaged.preferences[first])
        assertEquals("CIRCLE", salvaged.preferences[third])
        assertTrue(salvaged.lost)
    }

    @Test
    fun `nothing readable is an empty set rather than a crash`() {
        assertTrue(salvagePreferences(write(ByteArray(128))).asMap().isEmpty())
        assertTrue(salvagePreferences(File("/nonexistent/deluge_prefs")).asMap().isEmpty())
    }
}
