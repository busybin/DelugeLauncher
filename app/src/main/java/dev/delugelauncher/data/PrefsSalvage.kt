// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.runBlocking
import okio.Buffer
import java.io.File

/** The tag of field 1, length-delimited: one entry of the preferences map. */
private const val ENTRY_TAG = 0x0A

/** What a damaged file still gave up, and whether salvaging it had to drop anything. */
class SalvagedPreferences(val preferences: Preferences, val lost: Boolean)

/**
 * What can still be read out of a preferences file that no longer parses.
 *
 * The file is a run of entries, one per setting, and the damage seen in practice is a torn
 * write: whole entries up front, then zeros where the rest should be. Every entry that still
 * reads on its own is kept, so a launcher that would otherwise crash at every start comes back
 * with its favorites and folders rather than as a fresh install. Nothing readable at all is
 * an empty set of preferences, which is still better than never starting.
 */
fun salvagePreferences(file: File): Preferences = salvageFile(file).preferences

/**
 * [salvagePreferences], also saying whether anything had to be dropped.
 *
 * A dropped entry is how the lock list can vanish: it is one entry among many, and the guard
 * that still holds those apps writes its own file, so the caller can put the records back.
 */
fun salvageFile(file: File): SalvagedPreferences {
    val bytes = runCatching { file.readBytes() }.getOrNull()
        ?: return SalvagedPreferences(emptyPreferences(), lost = false)
    val kept = salvageEntries(bytes)
    val preferences = runCatching { parsePreferences(kept) }.getOrDefault(emptyPreferences())
    return SalvagedPreferences(preferences, lost = kept.size != bytes.size)
}

/**
 * The entries of [bytes] that each parse on their own, joined back together.
 *
 * One that runs into the zeros at the end is dropped even when it parses: zeros are valid in
 * the middle of a string, so the setting the tear cut through reads back as a value nobody
 * chose — half a list of favorites, or a name ending in nothing.
 */
fun salvageEntries(bytes: ByteArray): ByteArray {
    // A torn write leaves a long run of zeros; a single trailing zero is a legitimate empty
    // string value (`0A 00`), and cutting it dropped the last entry and marked the store
    // damaged — which in turn triggered the lock-recovery path.
    var lastNonZero = bytes.size - 1
    while (lastNonZero >= 0 && bytes[lastNonZero].toInt() == 0) lastNonZero--
    val intactEnd = if (bytes.size - 1 - lastNonZero > 1) lastNonZero + 1 else bytes.size
    val kept = Buffer()
    var pos = 0
    while (pos < bytes.size) {
        // Damage in the middle used to stop the scan; resync to the next entry tag instead so
        // settings after the tear still come back.
        if (bytes[pos].toInt() != ENTRY_TAG) {
            pos++
            continue
        }
        val next = readVarint(bytes, pos + 1)
        if (next == null) {
            pos++
            continue
        }
        val length = next.first
        val start = next.second
        val end = start + length
        if (length < 0 || end > intactEnd || end < start) {
            pos++
            continue
        }
        val entry = bytes.copyOfRange(pos, end)
        if (runCatching { parsePreferences(entry) }.isSuccess) {
            kept.write(entry)
            pos = end
        } else {
            pos++
        }
    }
    return kept.readByteArray()
}

/**
 * Whether this process had to salvage the store.
 *
 * Read once by the lock recovery, before the guard's list is overwritten. A normal file never
 * sets it, so nothing is repaired on a healthy start.
 */
object PrefsSalvage {
    @Volatile
    private var damaged = false

    fun wasDamaged(): Boolean = damaged

    internal fun markDamaged() {
        damaged = true
    }
}

private fun parsePreferences(bytes: ByteArray): Preferences =
    runBlocking { PreferencesSerializer.readFrom(Buffer().write(bytes)) }

/** A varint starting at [from], and where the bytes after it begin; null if it runs off the end. */
private fun readVarint(bytes: ByteArray, from: Int): Pair<Int, Int>? {
    var result = 0L
    var shift = 0
    var pos = from
    while (pos < bytes.size && shift < 35) {
        val b = bytes[pos++].toInt()
        result = result or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) return if (result > Int.MAX_VALUE) null else result.toInt() to pos
        shift += 7
    }
    return null
}
