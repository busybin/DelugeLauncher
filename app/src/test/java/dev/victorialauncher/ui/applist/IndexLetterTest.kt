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
        // The report that prompted this: one strip entry per kanji is not an index.
        assertEquals('#', indexLetter("設定"))
        assertEquals('#', indexLetter("카카오톡"))
        assertEquals('#', indexLetter("Телеграм"))
        assertEquals('#', indexLetter("Ελλάδα"))
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
