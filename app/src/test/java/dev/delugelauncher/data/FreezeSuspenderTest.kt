// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FreezeSuspenderTest {

    @Test
    fun `the reported owner is cleared first`() {
        assertEquals(
            listOf("dev.delugelauncher", "root", "com.android.shell"),
            FreezeBinder.suspendersToClear("dev.delugelauncher"),
        )
    }

    @Test
    fun `both names pm writes are always tried`() {
        assertEquals(
            listOf("root", "com.android.shell"),
            FreezeBinder.suspendersToClear(null),
        )
    }

    @Test
    fun `the channel name is tried before the other identity`() {
        assertEquals(
            listOf("com.android.shell", "root"),
            FreezeBinder.suspendersToClear(null, own = "com.android.shell"),
        )
    }

    @Test
    fun `a blank owner is ignored and nothing repeats`() {
        assertEquals(
            listOf("root", "com.android.shell"),
            FreezeBinder.suspendersToClear("  ", own = "root"),
        )
    }
}
