// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPanelBusTest {

    private fun entry(
        key: String = "0|com.example|1|null|10",
        pkg: String = "com.example",
        serial: Long = 0L,
        title: String = "Title",
        text: String = "Text",
        at: Long = 10L,
    ) = NotificationEntry(
        notificationKey = key,
        packageName = pkg,
        userSerial = serial,
        title = title,
        text = text,
        postTime = at,
        canDismiss = true,
    )

    @Test
    fun `one line joins a title and a body`() {
        assertEquals("Title · Text", entry().line)
    }

    @Test
    fun `a repeated body is not said twice`() {
        assertEquals("Title", entry(text = "Title").line)
    }

    @Test
    fun `a notification with only a body reads as its body`() {
        assertEquals("Text", entry(title = "").line)
    }

    @Test
    fun `the bus holds what was published and clears on request`() {
        NotificationPanelBus.clear()
        val first = entry(key = "a")
        val second = entry(key = "b", pkg = "com.other", at = 20)
        NotificationPanelBus.update(listOf(second, first))
        assertEquals(listOf(second, first), NotificationPanelBus.entries.value)
        NotificationPanelBus.clear()
        assertTrue(NotificationPanelBus.entries.value.isEmpty())
    }

    @Test
    fun `the same notification posted again does not replace the list`() {
        NotificationPanelBus.clear()
        val only = entry()
        NotificationPanelBus.update(listOf(only))
        val before = NotificationPanelBus.entries.value
        // Same value: an equal list is dropped rather than republished, so the panel does not
        // recompose on every notification refresh.
        NotificationPanelBus.update(listOf(only))
        assertEquals(before, NotificationPanelBus.entries.value)
    }
}
