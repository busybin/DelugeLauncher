// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreviewBusTest {

    private fun item(
        pkg: String = "com.example",
        serial: Long = 0L,
        text: String = "Text",
        at: Long = 1L,
        clearable: Boolean = true,
    ) = NotificationEntry(
        packageName = pkg,
        userSerial = serial,
        title = "Title",
        text = text,
        postTime = at,
        notificationKey = "$pkg:$at",
        contentIntent = null,
        actions = emptyList(),
        canDismiss = clearable,
    )

    @Test
    fun `an app keeps its newest three, newest first`() {
        NotificationPreviewBus.clear()
        NotificationPreviewBus.update(
            listOf(
                item(text = "oldest", at = 10),
                item(text = "newest", at = 40),
                item(text = "middle", at = 30),
                item(text = "older", at = 20),
            ),
        )
        assertEquals(
            listOf("newest", "middle", "older"),
            NotificationPreviewBus.previews.value.values.single().map { it.text },
        )
    }

    @Test
    fun `the same package in two profiles stays apart`() {
        NotificationPreviewBus.clear()
        NotificationPreviewBus.update(
            listOf(item(serial = 0, text = "main"), item(serial = 12, text = "private")),
        )
        val previews = NotificationPreviewBus.previews.value
        assertEquals(2, previews.size)
        assertEquals("main", previews.getValue(NotificationPreviewBus.key("com.example", 0)).single().text)
        assertEquals(
            "private",
            previews.getValue(NotificationPreviewBus.key("com.example", 12)).single().text,
        )
    }

    @Test
    fun `an ongoing notification is not a line to read`() {
        NotificationPreviewBus.clear()
        NotificationPreviewBus.update(listOf(item(text = "downloading", clearable = false)))
        assertTrue(NotificationPreviewBus.previews.value.isEmpty())
    }

    @Test
    fun `clearing leaves nothing behind`() {
        NotificationPreviewBus.update(listOf(item()))
        NotificationPreviewBus.clear()
        assertTrue(NotificationPreviewBus.previews.value.isEmpty())
    }
}
