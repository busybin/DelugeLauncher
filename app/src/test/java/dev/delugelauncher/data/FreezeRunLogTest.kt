// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreezeRunLogTest {

    @Test
    fun `a run survives a round trip`() {
        val log = FreezeRunLog(
            atMillis = 1_700_000_000_000,
            source = "network",
            frozen = setOf("com.a/com.a.Main"),
            thawed = setOf("com.b/com.b.Main"),
        )
        assertEquals(log, FreezeRunLog.fromJson(FreezeRunLog.toJson(log)))
    }

    @Test
    fun `an empty run is not a log`() {
        assertNull(FreezeRunLog.fromJson(FreezeRunLog.toJson(FreezeRunLog(atMillis = 1L))))
        assertNull(FreezeRunLog.fromJson(null))
    }
}
