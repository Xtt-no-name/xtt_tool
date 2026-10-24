package com.xtt.toolbox.domain.writing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingPunctuationTest {
    @Test
    fun pairsAllSupportedOpeningCharacters() {
        WritingPunctuation.PAIR_MAP.forEach { (open, close) ->
            val edit = WritingPunctuation.applyAutoPair(
                oldText = "",
                oldSelectionStart = 0,
                oldSelectionEnd = 0,
                newText = open,
                newSelectionStart = open.length,
                newSelectionEnd = open.length,
                autoCloseOffsets = emptySet(),
            )
            assertEquals("$open$close", edit?.text)
            assertEquals(open.length, edit?.cursor)
            assertTrue(edit?.autoCloseOffsets?.contains(open.length) == true)
        }
    }

    @Test
    fun wrapsSelectedTextWithoutDeletingIt() {
        val edit = WritingPunctuation.applyAutoPair(
            oldText = "你好",
            oldSelectionStart = 0,
            oldSelectionEnd = 2,
            newText = "“",
            newSelectionStart = 1,
            newSelectionEnd = 1,
            autoCloseOffsets = emptySet(),
        )!!
        assertEquals("“你好”", edit.text)
        assertEquals(4, edit.cursor)
        assertTrue(3 in edit.autoCloseOffsets)
    }

    @Test
    fun typingClosingPunctuationBeforeAutoCloseMovesCursorWithoutDuplicate() {
        val edit = WritingPunctuation.applyAutoPair(
            oldText = "“”",
            oldSelectionStart = 1,
            oldSelectionEnd = 1,
            newText = "“””",
            newSelectionStart = 2,
            newSelectionEnd = 2,
            autoCloseOffsets = setOf(1),
        )!!
        assertEquals("“”", edit.text)
        assertEquals(2, edit.cursor)
        assertEquals(setOf(1), edit.autoCloseOffsets)
    }

    @Test
    fun deletingOpenPunctuationBetweenPairRemovesBothCharacters() {
        val edit = WritingPunctuation.applyAutoPair(
            oldText = "“”",
            oldSelectionStart = 1,
            oldSelectionEnd = 1,
            newText = "”",
            newSelectionStart = 0,
            newSelectionEnd = 0,
            autoCloseOffsets = setOf(1),
        )!!
        assertEquals("", edit.text)
        assertEquals(0, edit.cursor)
        assertTrue(edit.autoCloseOffsets.isEmpty())
    }

    @Test
    fun deletingClosingPunctuationDoesNotDeleteOpeningOrBody() {
        val edit = WritingPunctuation.applyAutoPair(
            oldText = "“你好”",
            oldSelectionStart = 4,
            oldSelectionEnd = 4,
            newText = "“你好",
            newSelectionStart = 3,
            newSelectionEnd = 3,
            autoCloseOffsets = setOf(4),
        )
        assertNull(edit)
    }

    @Test
    fun multiCharacterPunctuationNeverTriggersPairing() {
        val edit = WritingPunctuation.applyAutoPair(
            oldText = "",
            oldSelectionStart = 0,
            oldSelectionEnd = 0,
            newText = "——",
            newSelectionStart = 2,
            newSelectionEnd = 2,
            autoCloseOffsets = emptySet(),
        )
        assertNull(edit)
    }

    @Test
    fun defaultListContainsRequiredPairs() {
        val defaults = WritingPunctuation.DEFAULT_PUNCTUATION
        assertTrue(defaults.contains("“"))
        assertTrue(defaults.contains("”"))
        assertTrue(defaults.contains("（"))
        assertTrue(defaults.contains("）"))
        assertTrue(defaults.contains("——"))
        assertTrue(defaults.contains("……"))
        assertEquals(defaults.size, defaults.distinct().size)
    }
}
