package com.xtt.toolbox.domain.reading

import com.xtt.toolbox.domain.chapter.RegexChapterParser
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookTable
import com.xtt.toolbox.domain.content.BookTableCell
import com.xtt.toolbox.domain.content.BookTextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
class ReaderDocumentTest {
    @Test
    fun blocksCoverFullTextWithoutGaps() {
        val text = "第一段。\n\n第二段，有标点。\n😀 代理对\n最后一行"
        val document = ReaderDocument.fromText(text)

        assertTrue(document.blocks.isNotEmpty())
        assertEquals(0, document.blocks.first().startOffset)
        assertEquals(text.length, document.blocks.last().endOffset)
        document.blocks.zipWithNext().forEach { (current, next) ->
            assertEquals(current.endOffset, next.startOffset)
        }
        document.blocks.forEach { block ->
            assertEquals(block.text, text.substring(block.startOffset, block.endOffset))
        }
        assertEquals(text, document.blocks.joinToString(separator = "") { it.text })
    }

    @Test
    fun normalizesCrLfBeforeBuildingOffsets() {
        val raw = "第一行\r\n第二行\r第三行"
        val document = ReaderDocument.fromText(raw)

        assertEquals("第一行\n第二行\n第三行", document.text)
        document.blocks.forEach { block ->
            assertEquals(
                block.text,
                document.text.substring(block.startOffset, block.endOffset),
            )
        }
    }

    @Test
    fun longTextIsChunkedAndDoesNotSplitSurrogatePair() {
        val text = "😀".repeat(2_000)
        val document = ReaderDocument.fromText(text)

        assertTrue(document.blocks.size > 1)
        assertTrue(document.blocks.all { it.text.length <= ReaderDocument.MAX_BLOCK_CHARS })
        assertEquals(text, document.blocks.joinToString(separator = "") { it.text })
        document.blocks.forEach { block ->
            val start = block.startOffset
            val end = block.endOffset
            if (start < end) {
                assertEquals(block.text, text.substring(start, end))
            }
        }
    }

    @Test
    fun blockIndexAtBoundariesAndEnd() {
        val text = buildString {
            repeat(5) { append("第${it + 1}段。\n") }
        }
        val document = ReaderDocument.fromText(text)

        assertEquals(0, document.blockIndexForOffset(0))
        assertEquals(
            document.blocks.lastIndex,
            document.blockIndexForOffset(text.length),
        )
        document.blocks.forEach { block ->
            assertEquals(block.index, document.blockIndexForOffset(block.startOffset))
        }
    }

    @Test
    fun emptyTextHasNoBlocksAndSafePosition() {
        val document = ReaderDocument.fromText("")
        assertTrue(document.blocks.isEmpty())
        assertEquals(0, document.blockIndexForOffset(999))
        assertEquals(0, ReaderPositionPolicy.clampOffset(0, 999))
        assertEquals(0f, ReaderPositionPolicy.percent(0, 999))
        assertEquals(0, ReaderPositionPolicy.position(0, 999).offset)
    }

    @Test
    fun clampsOutOfRangeOffsetsAndComputesProgress() {
        assertEquals(0, ReaderPositionPolicy.clampOffset(10, -3))
        assertEquals(10, ReaderPositionPolicy.clampOffset(10, 99))
        assertEquals(10, ReaderPositionPolicy.clampOffset(10, 10))
        assertEquals(0f, ReaderPositionPolicy.percent(10, 0))
        assertEquals(0.5f, ReaderPositionPolicy.percent(10, 5))
        assertEquals(1f, ReaderPositionPolicy.percent(10, 10))
        assertEquals(1f, ReaderPositionPolicy.percent(10, 99))
    }

    @Test
    fun tableBlockMapsToReaderTableWithoutConsumingText() {
        val table = BookTable(
            header = listOf(BookTableCell("A"), BookTableCell("B")),
            rows = listOf(listOf(BookTableCell("1"), BookTableCell("2"))),
            alignments = listOf(BookTextAlign.START, BookTextAlign.END),
        )
        val document = ReaderDocument.fromContentBlocks(
            text = "前文\n",
            bookBlocks = listOf(
                BookBlock(
                    blockId = "table",
                    order = 0,
                    type = BookBlockType.TABLE,
                    startOffset = 3,
                    endOffset = 3,
                    table = table,
                ),
            ),
            imagePaths = emptyMap(),
        )
        val block = document.blocks.single()
        assertEquals(ReaderBlockKind.TABLE, block.kind)
        assertEquals(3, block.startOffset)
        assertEquals(3, block.endOffset)
        assertEquals(table, block.table)
    }

    @Test
    fun chapterParserOffsetsStayInsideReaderText() {
        val text = "前言\n第一章 开始\n正文\n第二章 继续\n结尾"
        val chapters = RegexChapterParser().parse(text).candidates
        val document = ReaderDocument.fromText(text)

        assertTrue(chapters.isNotEmpty())
        chapters.forEach { chapter ->
            assertTrue(chapter.startOffset in 0..document.length)
            assertTrue(chapter.endOffset in chapter.startOffset..document.length)
            val block = document.blockForOffset(chapter.startOffset)
            assertTrue(block != null)
            assertTrue(chapter.startOffset >= block!!.startOffset)
            assertTrue(chapter.startOffset < block.endOffset)
            assertEquals(
                text.substring(chapter.startOffset, chapter.endOffset),
                document.text.substring(chapter.startOffset, chapter.endOffset),
            )
        }
    }

    @Test
    fun positionBufferMergesUpdatesAndKeepsLatest() {
        val buffer = ReaderPositionBuffer()
        buffer.initialize(0)

        assertTrue(buffer.update(10))
        assertEquals(10, buffer.peek())
        assertTrue(buffer.update(20))
        assertEquals(20, buffer.peek())
        org.junit.Assert.assertFalse(buffer.update(20))
        assertEquals(20, buffer.peek())

        buffer.markSaved(10)
        assertEquals(20, buffer.peek())
        buffer.markSaved(20)
        assertNull(buffer.peek())
        assertEquals(20, buffer.lastSavedOffset())
    }

    @Test
    fun settingsClampValuesAndKeepReadableLineHeight() {
        val normalized = ReaderSettings(
            fontSizeSp = 99f,
            lineHeightSp = 1f,
            paragraphSpacingDp = -1f,
            horizontalPaddingDp = 99f,
        ).normalized()

        assertEquals(ReaderSettings.MAX_FONT_SIZE_SP, normalized.fontSizeSp)
        assertTrue(normalized.lineHeightSp >= normalized.fontSizeSp + ReaderSettings.MIN_EXTRA_LINE_HEIGHT_SP)
        assertEquals(ReaderSettings.MIN_PARAGRAPH_SPACING_DP, normalized.paragraphSpacingDp)
        assertEquals(ReaderSettings.MAX_HORIZONTAL_PADDING_DP, normalized.horizontalPaddingDp)
        assertTrue(normalized.showCommentBubbles)
        assertTrue(normalized.showChapterCommentEntry)
        assertTrue(normalized.enableLongPressMenu)
    }

    @Test
    fun chapterTitleUsesNearestParsedChapter() {
        val text = "前言\n第一章 开始\n正文\n第二章 继续\n结尾"
        val chapters = RegexChapterParser().parse(text).candidates

        assertNull(ReaderChapterTitle.titleAt(chapters, 0))
        assertEquals("第一章 开始", ReaderChapterTitle.titleAt(chapters, chapters[0].startOffset))
        assertEquals("第一章 开始", ReaderChapterTitle.titleAt(chapters, chapters[1].startOffset - 1))
        assertEquals("第二章 继续", ReaderChapterTitle.titleAt(chapters, chapters[1].startOffset))
        assertEquals("第二章 继续", ReaderChapterTitle.titleAt(chapters, text.length))
    }

    @Test
    fun persistedChapterKeyPrefersStableIdAndLegacyOffsetFallsBack() {
        val base = RegexChapterParser().parse("第一章 开始\n正文").candidates.single()
        val stable = base.copy(chapterId = "chapter-uuid-1")
        val legacy = base.copy(chapterId = null)

        assertEquals("chapter-uuid-1", ReaderChapterTitle.keyOf(stable))
        assertEquals("offset:${base.startOffset}", ReaderChapterTitle.keyOf(legacy))
    }

    @Test
    fun chapterEndCanBeResolvedToALineBlock() {
        val text = "第一章 开始\n正文\n\n第二章 继续\n正文"
        val document = ReaderDocument.fromText(text)
        val chapters = RegexChapterParser().parse(document.text).candidates

        chapters.forEach { chapter ->
            assertTrue(
                "章节 ${chapter.title} 在行块中找不到结束边界",
                document.blocks.any { ReaderChapterTitle.endsAtBlock(chapter, it) },
            )
        }
    }
}
