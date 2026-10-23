// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreviewBusTest {

    private fun item(
        pkg: String = "com.example",
        serial: Long = 0L,
        title: String = "Title",
        text: String = "Text",
        at: Long = 1L,
    ) = NotificationPreview(pkg, serial, title, text, at)

    @Test
    fun `the newest notification of an app is the one kept`() {
        NotificationPreviewBus.clear()
        NotificationPreviewBus.update(
            listOf(
                item(text = "old", at = 10),
                item(text = "new", at = 20),
                item(text = "middle", at = 15),
            ),
        )
        assertEquals("new", NotificationPreviewBus.previews.value.values.single().text)
    }

    @Test
    fun `the same package in two profiles stays apart`() {
        NotificationPreviewBus.clear()
        NotificationPreviewBus.update(
            listOf(item(serial = 0, text = "main"), item(serial = 12, text = "private")),
        )
        val previews = NotificationPreviewBus.previews.value
        assertEquals(2, previews.size)
        assertEquals("main", previews[NotificationPreviewBus.key("com.example", 0)]?.text)
        assertEquals("private", previews[NotificationPreviewBus.key("com.example", 12)]?.text)
    }

    @Test
    fun `the line joins a title and a body once`() {
        assertEquals("Title · Text", item().line)
        assertEquals("Title", item(text = "Title").line)
        assertEquals("Text", item(title = "").line)
        assertEquals("", item(title = "", text = "").line)
    }

    @Test
    fun `clearing empties the published map`() {
        NotificationPreviewBus.update(listOf(item()))
        assertTrue(NotificationPreviewBus.previews.value.isNotEmpty())
        NotificationPreviewBus.clear()
        assertTrue(NotificationPreviewBus.previews.value.isEmpty())
    }
}
