// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTest {

    @Test
    fun `a folder token round-trips`() {
        val token = folderToken("abc123")
        assertEquals("abc123", folderIdFromToken(token))
    }

    @Test
    fun `an app component key is not mistaken for a folder`() {
        assertNull(folderIdFromToken("com.example.app/com.example.app.MainActivity"))
    }

    @Test
    fun `folders survive a JSON round-trip`() {
        val folders = listOf(
            Folder(id = "1", name = "Media", apps = listOf("a/A", "b/B")),
            Folder(id = "2", name = "Work", apps = emptyList(), icon = "pack:com.pack:ic_work"),
        )
        val restored = foldersFromJson(foldersToJson(folders))
        assertEquals(folders, restored)
    }

    @Test
    fun `malformed JSON yields no folders rather than throwing`() {
        assertTrue(foldersFromJson("{not json").isEmpty())
        assertTrue(foldersFromJson("").isEmpty())
        assertTrue(foldersFromJson(null).isEmpty())
    }

    @Test
    fun `an entry with no id is dropped instead of producing a folder nothing can address`() {
        val restored = foldersFromJson("""[{"name":"Nameless","apps":[]},{"id":"2","name":"Real","apps":[]}]""")
        assertEquals(1, restored.size)
        assertEquals("2", restored.first().id)
    }

    @Test
    fun `a folder with no icon round-trips as null rather than an empty string`() {
        val restored = foldersFromJson(foldersToJson(listOf(Folder("1", "Plain", emptyList()))))
        assertNull(restored.single().icon)
    }

    @Test
    fun `widgets in a folder survive a JSON round-trip`() {
        val folders = listOf(
            Folder(
                id = "1",
                name = "Music",
                apps = listOf("a/A"),
                widgets = listOf(FolderWidget(42, 200), FolderWidget(7, 96)),
            ),
            Folder(id = "2", name = "Plain", apps = emptyList()),
        )
        val restored = foldersFromJson(foldersToJson(folders))
        assertEquals(folders, restored)
    }

    @Test
    fun `a widget entry without a usable id is dropped, the rest of the folder stays`() {
        val restored = foldersFromJson(
            """[{"id":"1","name":"Mix","apps":[],"widgets":[{"id":-3,"h":90},{"id":8,"h":120}]}]"""
        ).single()
        assertEquals(listOf(FolderWidget(8, 120)), restored.widgets)
    }

    @Test
    fun `a widget with no stored height takes the default`() {
        val restored = foldersFromJson(
            """[{"id":"1","name":"Mix","apps":[],"widgets":[{"id":5}]}]"""
        ).single()
        assertEquals(FOLDER_WIDGET_DEFAULT_HEIGHT_DP, restored.widgets.single().heightDp)
    }

    @Test
    fun `a folder keeps its Now Playing card`() {
        val folders = listOf(Folder(id = "1", name = "Audio", apps = listOf("a/A"), nowPlaying = true))
        assertEquals(folders, foldersFromJson(foldersToJson(folders)))
        assertEquals(false, foldersFromJson("""[{"id":"1","name":"Old","apps":["a/A"]}]""").single().nowPlaying)
    }
}
