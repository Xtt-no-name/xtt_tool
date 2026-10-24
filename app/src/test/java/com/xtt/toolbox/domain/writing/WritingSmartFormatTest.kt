package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.InlineSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WritingSmartFormatTest {
    private fun draft(
        text: String,
        types: List<BookBlockType> = emptyList(),
        spans: List<InlineSpan> = emptyList(),
        images: List<WritingChapterImage> = emptyList(),
    ): WritingChapterDraft {
        val lineCount = text.count { it == '\n' } + 1
        return WritingChapterDraft(
            chapterId = "chapter",
            text = text,
            lineTypes = List(lineCount) { index -> types.getOrNull(index) ?: BookBlockType.PARAGRAPH },
            spans = spans,
            images = images,
        ).normalized()
    }

    private fun format(text: String, types: List<BookBlockType> = emptyList()): String? =
        WritingSmartFormat.format(draft(text, types), 0, 0)?.draft?.text

    @Test
    fun longBodyFormatsEveryParagraphThroughTheEnd() {
        val paragraphs = (1..120).map { "第${it}段" }
        val expected = paragraphs.joinToString("\n\n") { "　　$it" }
        val source = paragraphs.joinToString("\n")
        assertEquals(expected, format(source))
        assertNull(format(expected))
    }

    @Test
    fun longBodyWithMixedBlankCountsIsFullyNormalized() {
        val input = buildString {
            append("第一段")
            append("\n\n\n")
            append("中间段")
            append("\n")
            append("靠后段")
            append("\n\n")
            append("末段")
        }
        assertEquals(
            "　　第一段\n\n　　中间段\n\n　　靠后段\n\n　　末段",
            format(input),
        )
    }

    @Test
    fun doesNotApplyVisualOnlyIndentationWhenTextHasNoIndent() {
        val result = WritingSmartFormat.format(draft("第一段\n第二段"), 0, 0)
        assertNotNull(result)
        val text = result!!.draft.text
        assertEquals(2, text.indexOf("第一段"))
        assertEquals('\u3000', text[0])
        assertEquals('\u3000', text[1])
    }

    @Test
    fun addsIndentAndExactlyOneBlankBetweenPlainParagraphs() {
        assertEquals(
            "　　第一段\n\n　　第二段",
            format("第一段\n第二段"),
        )
    }

    @Test
    fun normalizesOneAndThreeFullwidthSpacesToExactlyTwo() {
        assertEquals("　　第一段", format("　第一段"))
        assertEquals("　　第一段", format("　　　　第一段"))
        assertEquals("　　第一段", format("　　第一段") ?: "　　第一段")
    }

    @Test
    fun normalizesAsciiSpaceAndTabAtParagraphStart() {
        assertEquals("　　第一段", format(" 第一段"))
        assertEquals("　　第一段", format("\t第一段"))
    }

    @Test
    fun bodySpacesAndPunctuationAreNotChanged() {
        assertEquals("　　第一 段，标点！", format("第一 段，标点！"))
    }

    @Test
    fun existingIndentAndSingleBlankAreNotDuplicated() {
        val source = "　　第一段\n\n第二段"
        assertEquals("　　第一段\n\n　　第二段", format(source))
    }

    @Test
    fun multipleBlankLinesAreCompressedToOne() {
        val source = "　　第一段\n\n\n\n　　第二段"
        assertEquals("　　第一段\n\n　　第二段", format(source))
        assertNull(format("　　第一段\n\n　　第二段"))
    }

    @Test
    fun leadingAndTrailingBlankLinesAreNotIncreased() {
        assertEquals("\n\n　　正文", format("\n\n正文"))
        assertEquals("　　正文\n\n", format("正文\n\n"))
    }

    @Test
    fun headingListQuoteTableRuleAndImageLinesAreProtected() {
        val source = "# 标题\n第一段\n- 列表项\n> 引用\n| a | b |\n---\n![图](img.png)"
        assertEquals(
            "# 标题\n　　第一段\n- 列表项\n> 引用\n| a | b |\n---\n![图](img.png)",
            format(source),
        )
    }

    @Test
    fun fencedCodeBlockKeepsIndentAndInternalBlankLines() {
        val source = "```kotlin\n  val a = 1\n\n  val b = 2\n```\n正文"
        assertEquals(
            "```kotlin\n  val a = 1\n\n  val b = 2\n```\n　　正文",
            format(source),
        )
    }

    @Test
    fun emptySingleAndHeadingOnlyTextIsSafe() {
        assertEquals("　　单段", format("单段"))
        assertNull(format(""))
        assertNull(format("第一章", listOf(BookBlockType.HEADING)))
    }

    @Test
    fun trailingNewlineIsPreservedWithoutAddingExtraBlank() {
        assertEquals("　　第一段\n", format("第一段\n"))
    }

    @Test
    fun formattingIsIdempotent() {
        val first = WritingSmartFormat.format(draft("第一段\n第二段"), 0, 0)
        assertNotNull(first)
        assertNull(WritingSmartFormat.format(first!!.draft, 0, 0))
    }

    @Test
    fun selectionIsMappedAfterInsertedIndent() {
        val result = WritingSmartFormat.format(draft("第一段"), 0, 3)
        assertNotNull(result)
        assertEquals(2, result!!.selectionStart)
        assertEquals(5, result.selectionEnd)
    }

    @Test
    fun inlineSpansAndImagesAreMappedWithoutChangingTheirContent() {
        val source = draft(
            text = "第一段",
            spans = listOf(InlineSpan(start = 0, end = 3, bold = true)),
            images = listOf(
                WritingChapterImage(
                    anchor = 0,
                    blockId = "image-1",
                    resourceId = "resource-1",
                ),
            ),
        )
        val result = WritingSmartFormat.format(source, 0, 0)
        assertNotNull(result)
        val formatted = result!!.draft
        assertEquals("　　第一段", formatted.text)
        assertEquals(2, formatted.spans.single().start)
        assertEquals(5, formatted.spans.single().end)
        assertEquals(0, formatted.images.single().anchor)
    }
}
