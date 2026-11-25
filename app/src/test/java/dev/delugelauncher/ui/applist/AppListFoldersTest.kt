// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import android.content.ComponentName
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.EntryKind
import dev.delugelauncher.data.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Folders standing in the alphabet by their own names.
 *
 * The list is an index of everything installed, so a folder never hides its members: it only
 * gains a row where its name says it is. The switch is off by default, and with it off the
 * list must be byte-for-byte the one that was there before folders existed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppListFoldersTest {

    private fun app(pkg: String, label: String) = AppInfo(
        componentName = ComponentName("com.example.$pkg", "com.example.$pkg.Main"),
        label = label,
    )

    private val alpha = app("alpha", "Alpha")
    private val beta = app("beta", "Beta")
    private val zulu = app("zulu", "Zulu")
    private val photos = Folder(id = "f1", name = "Photos", apps = listOf(beta.key))
    private val archive = Folder(id = "f2", name = "Archive", apps = emptyList())

    private fun build(
        showFolders: Boolean,
        foldersSection: String? = null,
        hideFolderApps: Boolean = false,
    ): AppListModel = buildAppListModel(
        apps = listOf(alpha, beta, zulu),
        hidden = emptySet(),
        displayName = { it.label },
        folders = listOf(photos, archive),
        showFolders = showFolders,
        foldersSection = foldersSection,
        hideFolderApps = hideFolderApps,
    )

    @Test
    fun `folders are off unless asked for`() {
        assertTrue(build(showFolders = false).rows.none { it is AppListRow.FolderRow })
    }

    @Test
    fun `a folder is filed under the first letter of its name`() {
        val rows = build(showFolders = true).rows
        val at = rows.indexOfFirst { it is AppListRow.FolderRow && it.folder.id == "f1" }
        assertTrue(at > 0)
        assertEquals("P", (rows[at - 1] as AppListRow.Header).text)
        // Archive comes first in its own letter.
        assertEquals("Archive", (rows.filterIsInstance<AppListRow.FolderRow>().first()).folder.name)
    }

    @Test
    fun `apps inside a folder are still in the alphabet`() {
        val labels = build(showFolders = true).rows.filterIsInstance<AppListRow.Entry>().map { it.app.label }
        assertEquals(listOf("Alpha", "Beta", "Zulu"), labels)
    }

    @Test
    fun `a folder takes a cell of its own`() {
        val cells = build(showFolders = true).toCells(twoColumn = false)
        val folders = cells.rows.filterIsInstance<CellRow.One>()
            .map { it.item }
            .filterIsInstance<ListItem.FolderItem>()
            .map { it.key }
        assertEquals(listOf("folder:f2", "folder:f1"), folders)
    }

    @Test
    fun `a search drops folders rather than matching them`() {
        val filtered = build(showFolders = true).filtered { it.label.startsWith("A") }
        assertTrue(filtered.rows.none { it is AppListRow.FolderRow })
        assertEquals(listOf("Alpha"), filtered.rows.filterIsInstance<AppListRow.Entry>().map { it.app.label })
    }

    @Test
    fun `a folder shares a two column row with its own letter`() {
        val cells = build(showFolders = true).toCells(twoColumn = true)
        val paired = cells.rows.filterIsInstance<CellRow.Two>().filter {
            it.left is ListItem.FolderItem || it.right is ListItem.FolderItem
        }
        assertTrue(paired.isNotEmpty())
        // Pairing never crosses a group: both cells belong to the same letter.
        paired.forEach { pair ->
            val names = listOfNotNull(pair.left, pair.right).map {
                when (it) {
                    is ListItem.FolderItem -> it.row.folder.name
                    is ListItem.AppItem -> it.entry.app.label
                }
            }
            assertEquals(1, names.map { it.first() }.toSet().size)
        }
        // A folder with no neighbour in its letter still stands alone.
        assertTrue(cells.rows.any {
            it is CellRow.One && it.item is ListItem.FolderItem && it.item.key == "folder:f1"
        })
    }

    @Test
    fun `an unnamed folder files under its stand in name`() {
        val unnamed = Folder(id = "f3", name = "  ", apps = emptyList())
        val rows = buildAppListModel(
            apps = listOf(alpha),
            hidden = emptySet(),
            displayName = { it.label },
            folders = listOf(unnamed),
            showFolders = true,
            unnamedFolder = "Folder",
        ).rows
        val at = rows.indexOfFirst { it is AppListRow.FolderRow }
        assertTrue(at > 0)
        assertEquals("F", (rows[at - 1] as AppListRow.Header).text)
    }

    @Test
    fun `folders vanish when the setting is turned back off`() {
        assertFalse(build(showFolders = false).toCells(twoColumn = false).rows.any {
            it is CellRow.One && it.item is ListItem.FolderItem
        })
    }

    @Test
    fun `the padlock ends the alphabet and opens the private apps`() {
        val padlock = AppInfo(
            componentName = ComponentName("dev.delugelauncher", "dev.delugelauncher.PrivateUnlocked"),
            label = "Private space",
            kind = EntryKind.PRIVATE_SPACE,
        )
        val privateApp = AppInfo(
            componentName = ComponentName("com.example.secret", "com.example.secret.Main"),
            label = "Notes",
            userSerial = 12,
            privateProfile = true,
        )
        val model = buildAppListModel(
            apps = listOf(alpha, padlock, privateApp, zulu),
            hidden = emptySet(),
            displayName = { it.label },
            privateSectionLabel = "Private space",
            recentSection = "Recently installed" to 3,
        )
        val rows = model.rows
        // Nothing but the private section is left after the padlock: it is not under P.
        val padlockAt = rows.indexOfLast { it is AppListRow.Entry && it.app.kind == EntryKind.PRIVATE_SPACE }
        assertTrue(padlockAt > 0)
        assertEquals(rows.lastIndex - 1, padlockAt)
        assertEquals("Private space", (rows[padlockAt - 1] as AppListRow.Header).text)
        assertEquals("Notes", (rows.last() as AppListRow.Entry).app.label)
        assertTrue(rows.none { it is AppListRow.Entry && it.app.kind == EntryKind.PRIVATE_SPACE && it.section != null })
    }

    @Test
    fun `folders at the top get a heading and a dot on the strip instead of a letter`() {
        val model = build(showFolders = true, foldersSection = "Folders")
        assertEquals(AppListRow.Header("Folders"), model.rows[0])
        assertEquals(
            listOf("Archive", "Photos"),
            model.rows.drop(1).take(2).map { (it as AppListRow.FolderRow).folder.name },
        )
        // Above the alphabet, which starts after them, and marked on the strip by something
        // that is not a letter.
        assertEquals(FOLDERS_LETTER, model.letterIndex.first().first)
        assertEquals(0, model.letterIndex.first().second)
        assertTrue(model.alphabetStart > 2)
        assertEquals("A", model.letterIndex[1].first)
        // No folder is filed under its own name any more.
        assertEquals(2, model.rows.filterIsInstance<AppListRow.FolderRow>().size)
    }

    @Test
    fun `without the top section folders keep their own letters`() {
        val model = build(showFolders = true)
        assertTrue(model.letterIndex.none { it.first == FOLDERS_LETTER })
        assertEquals(0, model.alphabetStart)
    }

    @Test
    fun `a folder app can be listed only in its folder`() {
        val model = build(showFolders = true, hideFolderApps = true)
        val letters = model.rows.filterIsInstance<AppListRow.Entry>().map { it.app.label }
        assertEquals(listOf("Alpha", "Zulu"), letters)
        // The folder is still there and still carries it, so nothing was actually lost.
        val folder = model.rows.filterIsInstance<AppListRow.FolderRow>().single { it.folder.id == "f1" }
        assertTrue(beta.key in folder.folder.apps)
    }

    @Test
    fun `a search still finds an app listed only in its folder`() {
        // The search box searches a model built without the folder switches, which is why
        // leaving an app out of the alphabet does not also make it unfindable.
        val search = build(showFolders = false)
        val found = search.filtered { it.label == "Beta" }
        assertEquals(listOf("Beta"), found.rows.filterIsInstance<AppListRow.Entry>().map { it.app.label })
        assertTrue(
            build(showFolders = true, hideFolderApps = true).rows.none {
                it is AppListRow.Entry && it.app.label == "Beta"
            }
        )
    }

    @Test
    fun `an app left out of the alphabet leaves its letter empty`() {
        val model = build(showFolders = true, hideFolderApps = true)
        val betaRow = model.rows.indexOfFirst {
            it is AppListRow.Entry && it.app.label == "Beta"
        }
        assertEquals(-1, betaRow)
    }

    @Test
    fun `with the switches off the list is the one it always was`() {
        val model = build(showFolders = false)
        assertEquals(
            listOf("A", "B", "Z"),
            model.rows.filterIsInstance<AppListRow.Header>().map { it.text },
        )
        assertEquals(
            listOf("A" to 0, "B" to 2, "Z" to 4),
            model.letterIndex,
        )
        assertEquals(0, model.alphabetStart)
        assertEquals(listOf("Alpha", "Beta", "Zulu"), model.rows.filterIsInstance<AppListRow.Entry>().map { it.app.label })
    }

    @Test
    fun `a section above the alphabet keeps the dot first in one column`() {
        val cells = build(showFolders = true, foldersSection = "Folders").toCells(twoColumn = false)
        // The dot marks the section, which starts at the top of the list.
        assertEquals(FOLDERS_LETTER, cells.letterIndex.first().first)
        assertEquals(0, cells.letterIndex.first().second)
        // The alphabet still starts after the section, so the spy starts there too.
        assertTrue(cells.alphabetStart > 0)
        assertEquals("A", cells.letterIndex[1].first)
    }

    @Test
    fun `a section above the alphabet keeps the dot first in two columns`() {
        val cells = build(showFolders = true, foldersSection = "Folders").toCells(twoColumn = true)
        assertEquals(FOLDERS_LETTER, cells.letterIndex.first().first)
        assertEquals(0, cells.letterIndex.first().second)
        // Pairing halves the index, never the meaning: A still points at its own heading,
        // and the section is still above it.
        val aAt = cells.letterIndex.first { it.first == "A" }.second
        assertEquals("A", (cells.rows[aAt] as CellRow.Header).text)
        assertTrue(aAt > 0)
    }

    @Test
    fun `a search over a list with the dot still finds the alphabet`() {
        val model = build(showFolders = true, foldersSection = "Folders")
        val filtered = model.filtered { it.label.startsWith("Z") }
        assertEquals(listOf("Zulu"), filtered.rows.filterIsInstance<AppListRow.Entry>().map { it.app.label })
        // Folders are dropped from a search rather than matched, dot section and all.
        assertTrue(filtered.rows.none { it is AppListRow.FolderRow })
        assertTrue(filtered.letterIndex.none { it.first == FOLDERS_LETTER })
    }

    @Test
    fun `the section without headings points the dot at its first folder`() {
        val model = buildAppListModel(
            apps = listOf(alpha, beta, zulu),
            hidden = emptySet(),
            displayName = { it.label },
            folders = listOf(photos, archive),
            showFolders = true,
            foldersSection = "Folders",
            showHeaders = false,
        )
        assertEquals(FOLDERS_LETTER to 0, model.letterIndex.first())
        assertEquals("Archive", (model.rows[0] as AppListRow.FolderRow).folder.name)
        assertEquals("A", model.letterIndex[1].first)
        assertEquals(2, model.letterIndex[1].second)
    }

    @Test
    fun `a section and hidden members combine`() {
        val model = build(showFolders = true, foldersSection = "Folders", hideFolderApps = true)
        val labels = model.rows.filterIsInstance<AppListRow.Entry>().map { it.app.label }
        assertEquals(listOf("Alpha", "Zulu"), labels)
        assertEquals(FOLDERS_LETTER, model.letterIndex.first().first)
        // Beta is only in the folder, which is the first row after the heading.
        val folders = model.rows.filterIsInstance<AppListRow.FolderRow>().map { it.folder.name }
        assertEquals(listOf("Archive", "Photos"), folders)
    }
}
