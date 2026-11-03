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

    @Test
    fun filtersLeadingDirectoryWhenTitlesRepeat() {
        val text = """
            第一章 开端
            第二章 发展
            第三章 高潮
            第一章 开端
            正文一
            第二章 发展
            正文二
            第三章 高潮
            正文三
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(listOf("第一章 开端", "第二章 发展", "第三章 高潮"), result.candidates.map { it.title })
        assertEquals(3, result.candidates.size)
        assertTrue(result.warnings.any { it.contains("目录") })
    }

    @Test
    fun parsesChineseNumbersByPlaceValue() {
        assertEquals(123, parser.parseNumber("一百二十三"))
        assertEquals(1001, parser.parseNumber("一千零一"))
        assertEquals(10_000, parser.parseNumber("一万"))
        assertEquals(2, parser.parseNumber("两"))
    }

    @Test
    fun supportsFullWidthWhitespaceAndTabs() {
        val text = "\t第　一　回　标题\n正文"
        val chapters = parser.parse(text).candidates

        assertEquals(1, chapters.size)
        assertEquals("第　一　回　标题", chapters.single().title)
    }

    @Test
    fun warnsAboutNumberJumpsAndRollbacksButKeepsChapterText() {
        val text = "第一章\n正文一\n第三章\n正文三\n第二章\n正文二"
        val result = parser.parse(text)

        assertEquals(3, result.candidates.size)
        assertTrue(result.warnings.any { it.contains("跳跃") })
        assertTrue(result.warnings.any { it.contains("回退") })
        result.candidates.zipWithNext().forEach { (left, right) ->
            assertEquals(left.endOffset, right.startOffset)
        }
    }

    @Test
    fun ignoresAdvertisementAndUrlLines() {
        val text = "第一章 开始\n正文\n最新网址 http://example.com\n请收藏本站\n第二章 继续\n正文"
        val chapters = parser.parse(text).candidates

        assertEquals(listOf("第一章 开始", "第二章 继续"), chapters.map { it.title })
    }

    @Test
    fun doesNotMatchInlineSentenceWithChapterWords() {
        val text = "他说第一章的内容还没有写完，也没有独立的章节标题。"
        assertTrue(parser.parse(text).candidates.isEmpty())
    }

    @Test
    fun recognizesBodyMarkerAndBookTitlePrefixesFromRealNovels() {
        val text = """
            正文 第一章 冰雪祭祀的追杀(上)
            正文内容一
            正文 第一章 冰雪祭祀的追杀(中)
            正文内容二
            【某书】第二回：回目
            回目正文
            【某书】第二回：回目
            回目正文二
        """.trimIndent()

        val chapters = parser.parse(text).candidates
        assertEquals(3, chapters.size)
        assertEquals("第一章 冰雪祭祀的追杀(上)", chapters[0].title)
        assertEquals("第一章 冰雪祭祀的追杀(中)", chapters[1].title)
        assertEquals("第二回：回目", chapters[2].title)
    }

    @Test
    fun supportsNoSpaceChapterTitlesAndRealTitleWords() {
        val text = """
            第一章是寒是暖
            正文一
            第二章求不得放不下
            正文二
            第三章来了来了
            正文三
        """.trimIndent()

        val chapters = parser.parse(text).candidates
        assertEquals(
            listOf("第一章是寒是暖", "第二章求不得放不下", "第三章来了来了"),
            chapters.map { it.title },
        )
    }

    @Test
    fun keepsRealSubsectionsWithSameNumber() {
        val text = """
            第一章 标题（上）
            正文上
            第一章 标题（中）
            正文中
            第一章 标题（下）
            正文下
        """.trimIndent()

        val chapters = parser.parse(text).candidates
        assertEquals(3, chapters.size)
        assertEquals(3, chapters.map { it.title }.distinct().size)
    }

    @Test
    fun realNovelFormatsPreserveAllBodyOffsets() {
        val text = "前言目录\n正文 第一章 开始\n正文一\n【书名】第二回：继续却说正文二"
        val chapters = parser.parse(text).candidates
        assertEquals(2, chapters.size)
        assertEquals(text.length, chapters.last().endOffset)
        chapters.zipWithNext().forEach { (left, right) ->
            assertEquals(left.endOffset, right.startOffset)
        }
        assertTrue(text.substring(chapters[0].startOffset, chapters[0].endOffset).contains("正文一"))
    }

    @Test
    fun supportsExplicitUnnumberedBodyHeading() {
        val text = "正文 特别短篇\n正文内容"
        val chapters = parser.parse(text).candidates
        assertEquals(1, chapters.size)
        assertEquals("正文 特别短篇", chapters.single().title)
    }
}
