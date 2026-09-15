// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Test

class IndexLetterTest {

    @Test
    fun `plain names file under their own letter`() {
        assertEquals('F', indexLetter("Firefox"))
        assertEquals('F', indexLetter("firefox"))
        assertEquals('Z', indexLetter("Zoom"))
    }

    @Test
    fun `accents fold onto the base letter`() {
        assertEquals('A', indexLetter("Ärger"))
        assertEquals('E', indexLetter("Élan"))
        assertEquals('U', indexLetter("Über"))
    }

    @Test
    fun `latin letters that are not an accented A-Z one still find their letter`() {
        // Unicode holds these as characters in their own right, so normalizing never reaches
        // the letter underneath and they would otherwise land in '#'.
        assertEquals('O', indexLetter("Œuvre"))
        assertEquals('O', indexLetter("Ørsted"))
        assertEquals('A', indexLetter("Ægir"))
        assertEquals('L', indexLetter("Łódź"))
        assertEquals('T', indexLetter("Þór"))
    }

    @Test
    fun `scripts with no place on an A-Z strip go to hash`() {
        // The report that prompted this: one strip entry per kanji is not an index. These
        // cannot be romanized a letter at a time either — it takes the whole word, and the
        // same character reads differently in Japanese and Chinese.
        assertEquals('#', indexLetter("設定"))
        assertEquals('#', indexLetter("カメラ"))
        assertEquals('#', indexLetter("카카오톡"))
    }

    @Test
    fun `alphabets that romanize land on their own letter`() {
        // The examples from the report, checked against where Niagara files them.
        assertEquals('K', indexLetter("Калькулятор"))
        assertEquals('K', indexLetter("Календарь"))
        assertEquals('G', indexLetter("Галерея"))
        assertEquals('B', indexLetter("Браузер"))
        assertEquals('B', indexLetter("Билайн"))
        assertEquals('A', indexLetter("Аптечка"))
        assertEquals('T', indexLetter("Телефон"))
        assertEquals('T', indexLetter("Транзистор"))
        assertEquals('E', indexLetter("Ελλάδα"))
        assertEquals('D', indexLetter("Δελτίο"))
    }

    @Test
    fun `lower case romanizes the same as upper`() {
        assertEquals('K', indexLetter("калькулятор"))
        assertEquals('T', indexLetter("телефон"))
    }

    @Test
    fun `digits, symbols and nothing at all go to hash`() {
        assertEquals('#', indexLetter("1Password"))
        assertEquals('#', indexLetter("+Message"))
        assertEquals('#', indexLetter(""))
        assertEquals('#', indexLetter("🚀 Launcher"))
    }

    @Test
    fun `hash sorts above A`() {
        // The strip is built from a sorted map, so this ordering is what puts # at the top.
        assertEquals(listOf('#', 'A', 'Z'), listOf('Z', '#', 'A').sorted())
    }
}
