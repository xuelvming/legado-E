package io.legado.app.ui.book.read.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.BreakIterator
import java.util.Locale

class WordSelectionTest {

    @Test
    fun everyLetterSelectsWholePhrasesInBothDirections() {
        for ((first, second) in listOf("put" to "down", "get" to "up", "a" to "word")) {
            val text = "$first $second"
            for (start in first.indices) {
                val forward = WordSelection(text, start, Locale.ENGLISH)
                assertEquals(first, text.substring(forward.initialSelection))
                for (end in first.length + 1 until text.length) {
                    val backward = WordSelection(text, end, Locale.ENGLISH)
                    assertEquals(second, text.substring(backward.initialSelection))
                    assertEquals(text, text.substring(forward.selectionAt(end)))
                    assertEquals(text, text.substring(backward.selectionAt(start)))
                    assertEquals(first, text.substring(forward.selectionAt(start)))
                    assertEquals(second, text.substring(backward.selectionAt(end)))
                }
            }
        }
    }

    @Test
    fun selectionCanGrowShrinkAndCrossTheAnchor() {
        val text = "put down now"
        val selection = WordSelection(text, 5, Locale.ENGLISH)
        for ((offset, expected) in listOf(
            1 to "put down", 10 to "down now", 5 to "down", 0 to "put down",
            11 to "down now", 7 to "down"
        )) {
            assertEquals(expected, text.substring(selection.selectionAt(offset)))
        }
    }

    @Test
    fun separatorsResolveInwardWithoutChangingInternalText() {
        val text = "\"put  down, now!\""
        val selection = WordSelection(text, 7, Locale.ENGLISH)
        for ((offset, expected) in listOf(
            0 to "put  down", 1 to "put  down", 4 to "down", 5 to "down",
            10 to "down", 11 to "down", 12 to "down, now", 15 to "down, now"
        )) {
            assertEquals("offset=$offset", expected, text.substring(selection.selectionAt(offset)))
        }
    }

    @Test
    fun unicodeAndParagraphSeparatorsPreserveExactOffsets() {
        val text = "\uD83D\uDE00 cafe\u0301\n  get up"
        val cafe = WordSelection(text, text.indexOf('f'), Locale.ENGLISH)
        assertEquals("cafe\u0301", text.substring(cafe.initialSelection))
        assertEquals(
            "cafe\u0301\n  get up",
            text.substring(cafe.selectionAt(text.lastIndex))
        )
        val get = WordSelection(text, text.indexOf('g'), Locale.ENGLISH)
        assertEquals("get up", text.substring(get.selectionAt(text.lastIndex)))
        assertEquals(
            "cafe\u0301\n  get",
            text.substring(get.selectionAt(text.indexOf('f')))
        )
    }

    @Test
    fun nonWordAnchorsDoNotJumpToUnrelatedWords() {
        for (text in listOf("! word", "\uD83D\uDE00 word", "  word")) {
            val selection = WordSelection(text, 0, Locale.ENGLISH)
            assertEquals(0, selection.initialSelection.first)
            assertTrue(selection.initialSelection.last < text.indexOf('w'))
            assertEquals(
                selection.initialSelection, selection.selectionAt(0)
            )
        }
    }

    @Test
    fun contractionsHyphensAndMixedScriptsRetainPlatformSegmentation() {
        for ((text, locale) in listOf(
            "don't re-enter" to Locale.ENGLISH,
            "can\u2019t get up" to Locale.ENGLISH,
            "\u4f60\u597d get up \u4e16\u754c" to Locale.CHINESE,
            "\uD801\uDC00abc word" to Locale.ENGLISH
        )) {
            val boundary = BreakIterator.getWordInstance(locale).apply { setText(text) }
            for (offset in text.indices) {
                val expected = boundary.preceding(offset + 1) until boundary.following(offset)
                val selection = WordSelection(text, offset, locale)
                assertEquals(expected, selection.initialSelection)
                assertEquals(expected, selection.selectionAt(offset))
            }
        }
    }

    @Test
    fun inlineObjectsSeparateWords() {
        val text = "put\uFFFCdown"
        val selection = WordSelection(text, 1, Locale.ENGLISH)
        assertEquals("put", text.substring(selection.initialSelection))
        assertEquals("put", text.substring(selection.selectionAt(3)))
        assertEquals(text, text.substring(selection.selectionAt(5)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidOffsetsAreRejectedExplicitly() {
        WordSelection("word", 4, Locale.ENGLISH)
    }
}
