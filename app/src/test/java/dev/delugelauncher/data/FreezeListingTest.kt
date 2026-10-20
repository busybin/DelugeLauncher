// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The freeze screen's order. Chinese goes through the real ICU, so this is a Robolectric test
 * rather than a plain one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FreezeListingTest {

    private fun app(id: String, installed: Long = 0, updated: Long = 0, label: String = id) =
        AppInfo(
            componentName = ComponentName("com.example.$id", "com.example.$id.Main"),
            label = label,
            installedAt = installed,
            updatedAt = updated,
        )

    private fun names(list: List<AppInfo>) = list.map { it.label }

    @Test
    fun `by name ascending and descending`() {
        val apps = listOf(app("b"), app("a"), app("c"))
        assertEquals(
            listOf("a", "b", "c"),
            names(FreezeListing.sorted(apps, FreezeSort.NAME_ASC, { it.label }, pinyin = false)),
        )
        assertEquals(
            listOf("c", "b", "a"),
            names(FreezeListing.sorted(apps, FreezeSort.NAME_DESC, { it.label }, pinyin = false)),
        )
    }

    @Test
    fun `a Chinese name sorts by its pinyin`() {
        val apps = listOf(app("z", label = "支付宝"), app("w", label = "微信"))
        assertEquals(
            listOf("微信", "支付宝"),
            names(FreezeListing.sorted(apps, FreezeSort.NAME_ASC, { it.label }, pinyin = true)),
        )
    }

    @Test
    fun `newest install first and newest update first`() {
        val old = app("old", installed = 100, updated = 100)
        val middle = app("middle", installed = 200, updated = 400)
        val recent = app("recent", installed = 300, updated = 200)
        assertEquals(
            listOf("recent", "middle", "old"),
            names(FreezeListing.sorted(listOf(old, middle, recent), FreezeSort.INSTALLED, { it.label }, false)),
        )
        assertEquals(
            listOf("middle", "recent", "old"),
            names(FreezeListing.sorted(listOf(old, middle, recent), FreezeSort.UPDATED, { it.label }, false)),
        )
    }

    @Test
    fun `equal times fall back to the name`() {
        val first = app("b", installed = 5)
        val second = app("a", installed = 5)
        assertEquals(
            listOf("a", "b"),
            names(FreezeListing.sorted(listOf(first, second), FreezeSort.INSTALLED, { it.label }, false)),
        )
    }
}
