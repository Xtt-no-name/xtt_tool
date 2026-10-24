package com.xtt.toolbox.domain.writing

import org.junit.Assert.assertEquals
import org.junit.Test

class WritingParagraphTest {
    @Test
    fun insertsRealBlankLineAndIndentAtCursor() {
        val result = WritingParagraph.replaceSelection("第一段。", 4, 4)
        assertEquals("第一段。\n\n　　第二段。", result.text + "第二段。")
        assertEquals("第一段。\n\n　　".length, result.cursor)
    }

    @Test
    fun replacesSelectedTextWithOneParagraphBreak() {
        val result = WritingParagraph.replaceSelection("第一段。保留", 4, 6)
        assertEquals("第一段。\n\n　　", result.text)
        assertEquals(4 + WritingParagraph.BREAK.length, result.cursor)
    }

    @Test
    fun consecutiveBreakContainsOnlyOneIndentPerBreak() {
        val first = WritingParagraph.replaceSelection("段落", 2, 2)
        val second = WritingParagraph.replaceSelection(first.text, first.cursor, first.cursor)
        assertEquals("段落\n\n　　\n\n　　", second.text)
        assertEquals(2, second.text.windowed(2).count { it == WritingParagraph.INDENT })
    }

    @Test
    fun imageAddsOnlyOneParagraphBreakAndIndent() {
        val result = WritingParagraph.afterImage("正文", 2)
        assertEquals("正文\n　　", result.text)
        assertEquals(2 + WritingParagraph.IMAGE_BREAK.length, result.cursor)
        assertEquals(1, result.text.windowed(2).count { it == "\n　" })
    }

    @Test
    fun imageReusesExistingParagraphBoundary() {
        val original = "正文\n　　"
        val result = WritingParagraph.afterImage(original, 2)
        assertEquals(original, result.text)
        assertEquals(original.length, result.cursor)
    }

    @Test
    fun imageAddsIndentWhenExistingLineBreakHasNoIndent() {
        val result = WritingParagraph.afterImage("正文\n", 2)
        assertEquals("正文\n　　", result.text)
        assertEquals("正文\n　　".length, result.cursor)
    }

    @Test
    fun imageAddsIndentBeforeExistingFollowingText() {
        val result = WritingParagraph.afterImage("正文\n后续", 2)
        assertEquals("正文\n　　\n后续", result.text)
        assertEquals("正文\n　　".length, result.cursor)
    }

    @Test
    fun imageDoesNotAccumulateExistingBlankLine() {
        val original = "正文\n\n　　后续"
        val result = WritingParagraph.afterImage(original, 2)
        assertEquals("正文\n　　\n　　后续", result.text)
        assertEquals("正文\n　　".length, result.cursor)
        assertEquals(0, result.text.windowed(3).count { it == "\n\n　" })
    }
}
