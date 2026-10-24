package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.data.model.ChapterDetectionSource
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookDocument
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class WritingTextStatsTest {
    @Test
    fun countsCodePointsWithoutWhitespaceAndExcludesHeading() {
        val text = "标题\nHello, 世界! 🚀"
        val headingEnd = "标题\n".length
        val chapter = Chapter(
            chapterId = "chapter-1",
            bookId = "book-1",
            title = "标题",
            order = 0,
            startOffset = 0,
            endOffset = text.length,
            headingOffset = 0,
            detectionSource = ChapterDetectionSource.MANUAL,
            confidence = 1f,
        )
        val document = BookDocument(
            chapters = listOf(chapter),
            blocks = listOf(
                BookBlock(
                    blockId = "heading",
                    order = 0,
                    type = BookBlockType.HEADING,
                    startOffset = 0,
                    endOffset = headingEnd,
                ),
                BookBlock(
                    blockId = "body",
                    order = 1,
                    type = BookBlockType.PARAGRAPH,
                    startOffset = headingEnd,
                    endOffset = text.length,
                ),
            ),
            resources = emptyList(),
        )
        val writing = WritingDocument(
            book = Book(
                bookId = "book-1",
                title = "测试",
                shelfType = ShelfType.WRITING,
                sourceType = SourceType.CREATED,
                format = BookFormat.DRAFT,
                createdAt = 1,
                updatedAt = 1,
            ),
            text = text,
            document = document,
        )

        // Hello, 世界! 🚀 的非空白 code point：5 + 1 + 2 + 1 + 1 = 10。
        assertEquals(10, WritingTextStats.chapterCount(writing, chapter.chapterId))
        assertEquals(10, WritingTextStats.totalCount(writing))
        assertEquals(0, WritingTextStats.chapterCount(writing, "missing"))
    }

    @Test
    fun countsVisibleTextButNotSpacesOrNewlines() {
        assertEquals(5, WritingTextStats.count("中a1，。 \n　　"))
    }

    @Test
    fun canExcludeChineseAndEnglishPunctuationWithoutChangingOtherCharacters() {
        val text = "中文，English! 123. 🚀\n（测试）“好”"
        assertEquals(23, WritingTextStats.count(text))
        assertEquals(16, WritingTextStats.count(text, excludePunctuation = true))
        assertEquals(0, WritingTextStats.count("，。！？；：、（）【】“”‘’", true))
        assertEquals(5, WritingTextStats.count("Hello", true))
    }

    @Test
    fun chapterWordCountSupportsPunctuationSetting() {
        val draft = WritingChapterDraft(
            chapterId = "chapter",
            text = "第一章\n正文，English！",
            lineTypes = listOf(BookBlockType.HEADING, BookBlockType.PARAGRAPH),
            spans = emptyList(),
        )
        assertEquals(11, WritingChapterEditor.wordCount(draft))
        assertEquals(9, WritingChapterEditor.wordCount(draft, excludePunctuation = true))
    }

    @Test
    fun chapterStatsExcludeHeadingAndImages() {
        var document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        var draft = WritingChapterEditor.load(document, chapterId)!!
        draft = WritingChapterEditor.updateText(draft, "第一章\n中文abc")
        draft = draft.copy(
            images = listOf(
                WritingChapterImage(
                    anchor = "第一章\n".length,
                    blockId = "stats-image",
                    resourceId = "resource-not-counted",
                ),
            ),
        )
        document = document.copy(
            document = document.document.copy(
                resources = listOf(resource("resource-not-counted")),
            ),
        )
        document = WritingChapterEditor.toDocument(document, draft)
        assertEquals(5, WritingTextStats.chapterCount(document, chapterId))
        assertEquals(5, WritingTextStats.totalCount(document))
        assertEquals(5, WritingTextStats.chapterCounts(document).values.single())
    }

    @Test
    fun emptyChapterAndEmptyTitleAreZero() {
        val document = WritingDocumentFactory.plainText(book(), "", "")
        val chapter = document.document.chapters.single()
        assertEquals("", chapter.title)
        assertEquals(0, WritingTextStats.chapterCount(document, chapter.chapterId))
        assertEquals(0, WritingTextStats.totalCount(document))
    }

    @Test
    fun imageBlockTextIsNotCounted() {
        var document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        var draft = WritingChapterEditor.load(document, chapterId)!!
        draft = WritingChapterEditor.updateText(draft, "第一章\n正文")
        draft = draft.copy(
            images = listOf(WritingChapterImage(2, "image", "resource")),
        )
        document = document.copy(
            document = document.document.copy(resources = listOf(resource("resource"))),
        )
        document = WritingChapterEditor.toDocument(document, draft)
        val imageBlock = document.document.blocks.first { it.type == BookBlockType.IMAGE }
        assertEquals(2, imageBlock.startOffset)
        assertEquals(2, WritingTextStats.chapterCount(document, chapterId))
    }

    private fun resource(id: String): BookResource = BookResource(
        resourceId = id,
        relativePath = "editor/images/$id.png",
        mediaType = "image/png",
        sizeBytes = 1,
        sha256 = "test",
        kind = BookResourceKind.IMAGE,
    )

    private fun book(): Book = Book(
        bookId = UUID.randomUUID().toString(),
        title = "统计测试",
        shelfType = ShelfType.WRITING,
        sourceType = SourceType.CREATED,
        format = BookFormat.DRAFT,
        createdAt = 1,
        updatedAt = 1,
    )
}
