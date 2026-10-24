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
    fun recognizesHuiWithSpacesArabicNumbersAndPunctuation() {
        val text = """
            第 1 回 开始
            正文一
            第12回 继续
            正文二
            第一百回：结束
            正文三
            第一千回
            正文四
        """.trimIndent()

        val chapters = parser.parse(text).candidates

        assertEquals(
            listOf("第 1 回 开始", "第12回 继续", "第一百回：结束", "第一千回"),
            chapters.map { it.title },
        )
        assertEquals(4, chapters.size)
    }

    @Test
    fun recognizesLongChapterTitleWithoutLengthCutoff() {
        val longTitle = "第一章 " + "很长的标题".repeat(40)
        val chapters = parser.parse("$longTitle\n正文").candidates

        assertEquals(1, chapters.size)
        assertEquals(longTitle, chapters.single().title)
    }

    @Test
    fun parsesNineHundredNinetyNineChapterHeadings() {
        val expected = (1..999).map { index ->
            when (index % 3) {
                0 -> "第 $index 回 标题$index"
                1 -> "第${index}章 标题$index"
                else -> "第${index}节：标题$index"
            }
        }
        val text = buildString {
            expected.forEachIndexed { index, heading ->
                append(heading).append('\n')
                append("正文").append(index + 1).append('\n')
                if (index != expected.lastIndex) append('\n')
            }
        }

        val chapters = parser.parse(text).candidates

        assertEquals(999, chapters.size)
        assertEquals(expected, chapters.map { it.title })
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
