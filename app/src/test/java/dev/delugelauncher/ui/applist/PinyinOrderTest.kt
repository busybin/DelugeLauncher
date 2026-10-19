// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import android.content.ComponentName
import dev.delugelauncher.data.AppInfo
import dev.delugelauncher.data.Pinyin
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Chinese launcher orders the apps by pinyin.
 *
 * Filing used to reach for the name the app carries in English, which put Alipay's Chinese name
 * under A — a name nobody in the list is looking for. The English name is still there for
 * what pinyin cannot read (a Japanese or Korean name has no pinyin to give), and the list in
 * any other language is unchanged.
 *
 * Robolectric rather than a plain JVM test: the romanization is the platform's ICU
 * transliterator, which the android.jar stubs do not answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PinyinOrderTest {

    @Test
    fun `the pinyin order is asked for by the launcher's own language`() {
        // LanguagePrefs puts the chosen language on the process default, which is what this
        // reads — so it answers for the launcher and not for the phone.
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            assertTrue(Pinyin.ordering())
            Locale.setDefault(Locale.US)
            assertFalse(Pinyin.ordering())
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `a chinese list files han names under their pinyin letter`() {
        assertEquals('W', indexLetter("微信", pinyin = true))
        assertEquals('Z', indexLetter("支付宝", pinyin = true))
        assertEquals('T', indexLetter("淘宝", pinyin = true))
        assertEquals('G', indexLetter("高德地图", pinyin = true))
    }

    @Test
    fun `the pinyin wins over the english name the app carries`() {
        // weixin is W either way; Alipay's Chinese name is what tells the two apart: its English
        // name would file it under A, which is not where a reader of the list would look.
        assertEquals('W', filingLetter("微信", renamed = false, pinyin = true) { "WeChat" })
        assertEquals('Z', filingLetter("支付宝", renamed = false, pinyin = true) { "Alipay" })
        assertEquals('A', filingLetter("支付宝", renamed = false, pinyin = false) { "Alipay" })
    }

    @Test
    fun `without pinyin a han name keeps the english fallback`() {
        assertEquals('#', indexLetter("微信"))
        assertEquals('W', filingLetter("微信", renamed = false) { "WeChat" })
    }

    @Test
    fun `what pinyin cannot read is left to the english name`() {
        // Kana and hangul read differently from the same characters in Chinese, so neither gets
        // a pinyin letter even in a Chinese list.
        assertEquals('#', indexLetter("カメラ", pinyin = true))
        assertEquals('#', indexLetter("카카오톡", pinyin = true))
        assertEquals('C', filingLetter("カメラ", renamed = false, pinyin = true) { "Camera" })
    }

    @Test
    fun `a name the user typed files by its pinyin too`() {
        // Renaming is the user's own word for the app, so it is exactly what pinyin should read.
        assertEquals('T', filingLetter("淘宝", renamed = true, pinyin = true) { "Alibaba" })
    }

    @Test
    fun `the latin reading keeps separators and drops accents`() {
        assertEquals("weixin", Pinyin.latin("微信"))
        assertEquals("call of duty", Pinyin.latin("Call of Duty"))
        assertEquals("cafe", Pinyin.latin("Café"))
    }

    @Test
    fun `the list files and orders han names by pinyin`() {
        val weixin = app("微信")
        val weibo = app("微博")
        val alipay = app("支付宝")
        val english = mapOf(weixin.key to "WeChat", weibo.key to "Weibo", alipay.key to "Alipay")

        fun build(pinyin: Boolean) = buildAppListModel(
            apps = listOf(weixin, weibo, alipay),
            hidden = emptySet(),
            displayName = { it.label },
            englishName = { english[it.key] },
            pinyin = pinyin,
        )

        val chinese = build(pinyin = true)
        assertEquals(listOf("W", "Z"), chinese.letters)
        // weibo before weixin: by codepoint it would be the other way round.
        assertEquals(
            listOf("微博", "微信", "支付宝"),
            chinese.rows.filterIsInstance<AppListRow.Entry>().map { it.app.label },
        )

        // And without pinyin the same three file by their English names, which is the report.
        // weibo and weixin share the one letter, and there they are ordered by codepoint — U+4FE1
        // before U+535A — which is exactly what pinyin gets right and this cannot.
        val byEnglish = build(pinyin = false)
        assertEquals(listOf("A", "W"), byEnglish.letters)
        assertEquals(
            listOf("支付宝", "微信", "微博"),
            byEnglish.rows.filterIsInstance<AppListRow.Entry>().map { it.app.label },
        )
    }

    private fun app(label: String) = AppInfo(
        componentName = ComponentName("dev.example.${label.hashCode()}", "Main"),
        label = label,
    )
}
