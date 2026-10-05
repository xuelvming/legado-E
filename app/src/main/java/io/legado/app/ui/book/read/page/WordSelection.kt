package io.legado.app.ui.book.read.page

import java.text.BreakIterator
import java.util.Locale

internal class WordSelection(
    private val text: String,
    anchorOffset: Int,
    locale: Locale = Locale.getDefault()
) {
    private val boundary = BreakIterator.getWordInstance(locale).also { it.setText(text) }
    val initialSelection = segmentAt(anchorOffset)
    private val wordAnchor = isWord(initialSelection)

    fun selectionAt(offset: Int): IntRange {
        var target = segmentAt(offset)
        if (wordAnchor) {
            while (!isWord(target)) {
                target = when {
                    target.first > initialSelection.last -> segmentAt(target.first - 1)
                    target.last < initialSelection.first -> segmentAt(target.last + 1)
                    else -> break
                }
            }
        }
        return minOf(initialSelection.first, target.first)..maxOf(initialSelection.last, target.last)
    }

    private fun segmentAt(offset: Int): IntRange {
        require(offset in text.indices) { "Selection offset $offset is outside the text" }
        return boundary.preceding(offset + 1) until boundary.following(offset)
    }

    private fun isWord(range: IntRange): Boolean {
        var offset = range.first
        while (offset <= range.last) {
            val codePoint = text.codePointAt(offset)
            if (Character.isLetterOrDigit(codePoint)) return true
            offset += Character.charCount(codePoint)
        }
        return false
    }
}
