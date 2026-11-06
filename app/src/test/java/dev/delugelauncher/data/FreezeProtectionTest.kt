// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FreezeProtectionTest {

    @Test
    fun `merge keeps the base and drops blanks and junk`() {
        val merged = FreezeProtection.merge(
            base = setOf("com.example.keyboard"),
            extra = listOf(
                "com.example.dialer",
                "",
                "not a package",
                null,
                "com.example.clock",
            ),
        )
        assertEquals(
            setOf("com.example.keyboard", "com.example.dialer", "com.example.clock"),
            merged,
        )
    }
}
