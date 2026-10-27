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

    private fun build(showFolders: Boolean): AppListModel = buildAppListModel(
        apps = listOf(alpha, beta, zulu),
        hidden = emptySet(),
        displayName = { it.label },
        folders = listOf(photos, archive),
        showFolders = showFolders,
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
}
