// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshBatchTest {

    @Test
    fun `a burst of packages collapses into one pass`() {
        val batch = RefreshBatch()
        repeat(45) { batch.add("com.example.app$it", clearIcons = true) }

        assertEquals(45, batch.names.size)
        assertFalse(batch.full)
        assertTrue(batch.clearsIcons)
    }

    @Test
    fun `the same package twice is named once`() {
        val batch = RefreshBatch()
        batch.add("com.example.notes", clearIcons = false)
        batch.add("com.example.notes", clearIcons = false)

        assertEquals(setOf("com.example.notes"), batch.names)
    }

    @Test
    fun `a request without a package is a full pass`() {
        val batch = RefreshBatch()
        batch.add(null, clearIcons = true)

        assertTrue(batch.full)
        assertTrue(batch.names.isEmpty())
    }

    @Test
    fun `one request asking for icons makes the whole batch clear them`() {
        val batch = RefreshBatch()
        batch.add("com.example.notes", clearIcons = false)
        batch.add("com.example.clock", clearIcons = true)

        assertTrue(batch.clearsIcons)
    }

    @Test
    fun `a suspend callback leaves the icons alone`() {
        val batch = RefreshBatch()
        batch.add("com.example.notes", clearIcons = false)

        assertFalse(batch.clearsIcons)
    }
}
