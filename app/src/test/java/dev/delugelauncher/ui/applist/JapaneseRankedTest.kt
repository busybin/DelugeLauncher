// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import android.content.ComponentName
import dev.delugelauncher.data.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ranked and searched lists keep the kana rows the full list gave the same names. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JapaneseRankedTest {

    private fun app(pkg: String, label: String) = AppInfo(
        componentName = ComponentName("com.example.$pkg", "com.example.$pkg.Main"),
        label = label,
    )

    private fun model(vararg labels: String) = AppListModel(
        rows = labels.mapIndexed { i, label -> AppListRow.Entry(app("a$i", label)) },
        letterIndex = emptyList(),
    )

    @Test
    fun `a ranked list files kana under its row on a japanese phone`() {
        val ranked = model("あんず", "ヴァイオリン").rankedBy(
            rank = { -it.label.length },
            japanese = true,
        )
        assertEquals(listOf("あ" to 0), ranked.letterIndex)
    }

    @Test
    fun `without the flag the same names fall back to hash`() {
        val ranked = model("あんず", "ヴァイオリン").rankedBy(rank = { -it.label.length })
        assertEquals(listOf("#" to 0), ranked.letterIndex)
        assertFalse(ranked.letterIndex.any { it.first == "あ" })
    }
}
