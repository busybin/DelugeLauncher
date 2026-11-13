// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRecoveryTest {

    @Test
    fun `nothing is rebuilt when the lock set survived`() {
        assertTrue(
            LockRecovery.orphanedPackages(
                listed = setOf("com.example.notes"),
                locked = setOf("com.example.notes/com.example.notes.Main"),
            ).isEmpty(),
        )
    }

    @Test
    fun `a dropped lock set comes back from the guard list`() {
        assertEquals(
            setOf("com.example.notes", "com.example.clock"),
            LockRecovery.orphanedPackages(
                listed = setOf("com.example.notes", "com.example.clock"),
                locked = emptySet(),
            ),
        )
    }

    @Test
    fun `a lock that survived is not added twice`() {
        assertEquals(
            setOf("com.example.clock"),
            LockRecovery.orphanedPackages(
                listed = setOf("com.example.notes", "com.example.clock"),
                locked = setOf("com.example.notes/com.example.notes.Main"),
            ),
        )
    }
}
