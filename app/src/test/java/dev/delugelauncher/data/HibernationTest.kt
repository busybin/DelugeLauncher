// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HibernationTest {

    private val now = 1_000L * Hibernation.DAY_MS

    @Test
    fun `an app opened long ago is stale`() {
        val stale = Hibernation.staleKeys(
            lastLaunch = mapOf("com.old/com.old.Main" to now - 120 * Hibernation.DAY_MS),
            tracked = setOf("com.old/com.old.Main"),
            nowMillis = now,
            days = 90,
        )
        assertEquals(setOf("com.old/com.old.Main"), stale)
    }

    @Test
    fun `a recent open and an untracked key are not stale`() {
        val stale = Hibernation.staleKeys(
            lastLaunch = mapOf(
                "com.recent/com.recent.Main" to now - 2 * Hibernation.DAY_MS,
                "com.gone/com.gone.Main" to now - 200 * Hibernation.DAY_MS,
            ),
            tracked = setOf("com.recent/com.recent.Main"),
            nowMillis = now,
            days = 90,
        )
        assertTrue(stale.isEmpty())
    }

    @Test
    fun `an evicted launch key falls back to when it was installed`() {
        // The launch log keeps only the most recent keys; the app it dropped is exactly the
        // long-unused one this screen exists to find.
        val stale = Hibernation.staleKeys(
            lastLaunch = emptyMap(),
            tracked = setOf("com.old/com.old.Main"),
            nowMillis = now,
            days = 90,
            installedAt = mapOf("com.old/com.old.Main" to now - 200 * Hibernation.DAY_MS),
        )
        assertEquals(setOf("com.old/com.old.Main"), stale)
    }

    @Test
    fun `a zero-day filter reports nothing`() {
        assertTrue(
            Hibernation.staleKeys(
                lastLaunch = mapOf("com.old/com.old.Main" to 1L),
                tracked = setOf("com.old/com.old.Main"),
                nowMillis = now,
                days = 0,
            ).isEmpty(),
        )
    }
}
