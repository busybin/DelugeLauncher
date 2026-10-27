// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which letter a name files under when it starts with none.
 *
 * The English fallback is there so a phone whose apps name themselves in another script still
 * sorts somewhere useful. These are the cases where it must not apply.
 */
class FilingLetterTest {

    @Test
    fun `a chosen name starting with a digit files under hash, not the name it replaced`() {
        assertEquals('#', filingLetter("4PDA", renamed = true) { "ForPDA" })
    }

    @Test
    fun `an untouched name with no letter falls back to its english one`() {
        assertEquals('B', filingLetter("ブルー", renamed = false) { "Blue" })
    }

    @Test
    fun `a name with no letter and no english name files under hash`() {
        assertEquals('#', filingLetter("ブルー", renamed = false) { null })
    }

    @Test
    fun `a chosen name with a letter files under that letter`() {
        assertEquals('Q', filingLetter("Quad PDA", renamed = true) { "ForPDA" })
    }

    @Test
    fun `a japanese phone files kana under its own row, not under a romanization`() {
        assertEquals('あ', filingLetter("あんず", renamed = false, japanese = true) { "Apricot" })
        assertEquals('か', filingLetter("がっこう", renamed = false, japanese = true) { "School" })
        assertEquals('か', filingLetter("カメラ", renamed = false, japanese = true) { "Camera" })
        assertEquals('あ', filingLetter("ぁい", renamed = false, japanese = true) { null })
        assertEquals('や', filingLetter("ゆき", renamed = false, japanese = true) { null })
        assertEquals('は', filingLetter("ブルー", renamed = false, japanese = true) { "Blue" })
        assertEquals('あ', filingLetter("ヴァイオリン", renamed = false, japanese = true) { null })
        assertEquals('あ', filingLetter("ゔ", renamed = false, japanese = true) { null })
    }

    @Test
    fun `a japanese phone files a kanji name under the kanji row`() {
        assertEquals(KANJI_HEADING, filingLetter("天気", renamed = false, japanese = true) { "Weather" })
        assertEquals('漢', filingLetter("漢字", renamed = true, japanese = true) { null })
    }

    @Test
    fun `a name an alphabet can carry still files there on a japanese phone`() {
        assertEquals('F', filingLetter("Firefox", renamed = false, japanese = true) { null })
        assertEquals('D', filingLetter("Дом", renamed = false, japanese = true) { null })
    }

    @Test
    fun `the english name is never asked for when the shown name has a letter`() {
        var asked = false
        val letter = filingLetter("Firefox", renamed = false) { asked = true; "Firefox" }
        assertEquals('F', letter)
        assertEquals(false, asked)
    }
}
