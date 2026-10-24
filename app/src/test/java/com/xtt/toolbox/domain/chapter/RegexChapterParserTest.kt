package com.xtt.toolbox.domain.chapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexChapterParserTest {
    private val parser = RegexChapterParser()

    @Test
    fun recognizesChineseChapterForms() {
        val text = """
            第一章 初见
            正文一
            第001章 再会
            正文二
            第十二回 终章
        """.trimIndent()

        val chapters = parser.parse(text).candidates

        assertEquals(listOf("第一章 初见", "第001章 再会", "第十二回 终章"), chapters.map { it.title })
        assertEquals(listOf(0, 1, 2), chapters.map { it.order })
        assertEquals(0, chapters.first().startOffset)
        assertEquals(text.length, chapters.last().endOffset)
    }

    @Test
    fun recognizesSpecialAndVolumeHeadings() {
        val text = "序章\n引子内容\n第一卷 风起\n卷一 雨落\n正文"
        val titles = parser.parse(text).candidates.map { it.title }

        assertEquals(listOf("序章", "第一卷 风起", "卷一 雨落"), titles)
    }

    @Test
    fun recognizesEnglishChapterHeadings() {
        val text = "Chapter 1\nHello\nCHAPTER 2: The End\nBody"
        val titles = parser.parse(text).candidates.map { it.title }

        assertEquals(listOf("Chapter 1", "CHAPTER 2: The End"), titles)
    }

    @Test
    fun doesNotTreatInlineChapterWordsAsHeadings() {
        val text = "这是第一章的内容，不是独立标题。\n正文中的 Chapter 1 也只是举例。"
        assertTrue(parser.parse(text).candidates.isEmpty())
    }

    @Test
    fun acceptsTextWithoutChapters() {
        val text = "这是一段普通正文。\n没有任何章节标题。"
        assertTrue(parser.parse(text).candidates.isEmpty())
    }

    @Test
    fun offsetsUseUtf16StringIndices() {
        val text = "😀\n第一章\n正文\n第二章\n尾"
        val chapters = parser.parse(text).candidates

        assertEquals(2, chapters.size)
        assertEquals(3, chapters[0].startOffset)
        assertEquals(3, chapters[0].headingOffset)
        assertEquals(10, chapters[0].endOffset)
        assertEquals(10, chapters[1].startOffset)
        assertEquals(10, chapters[1].headingOffset)
        assertEquals(text.length, chapters[1].endOffset)
    }
}
