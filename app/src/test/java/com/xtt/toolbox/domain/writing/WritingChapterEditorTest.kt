package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WritingChapterEditorTest {
    @Test
    fun emptyChapterTitleDoesNotFallBackToBookTitle() {
        val document = WritingDocumentFactory.plainText(book(), "第一段正文", "")
        assertEquals("", document.document.chapters.single().title)
        val draft = WritingChapterEditor.load(
            document,
            document.document.chapters.single().chapterId,
        )!!
        assertEquals("第一段正文", draft.text)
        assertEquals(BookBlockType.PARAGRAPH, draft.lineTypes.single())
        assertEquals(0, WritingChapterEditor.initialBodyOffset(draft))
    }

    @Test
    fun loadsAndSavesBasicTextWithHeadingAndBody() {
        var document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        var draft = WritingChapterEditor.load(document, chapterId)!!
        assertEquals("第一章", draft.text)
        assertEquals(BookBlockType.HEADING, draft.lineTypes.single())

        draft = WritingChapterEditor.updateText(draft, "第一章\n正文内容")
        document = WritingChapterEditor.toDocument(document, draft)
        val reloaded = WritingChapterEditor.load(document, chapterId)!!
        assertEquals("第一章\n正文内容", reloaded.text)
        assertEquals(draft.lineTypes, reloaded.lineTypes)
        assertEquals(1, document.document.chapters.size)
        assertTrue(document.document.blocks.any { it.type == BookBlockType.PARAGRAPH })
        val paragraph = document.document.blocks.first { it.type == BookBlockType.PARAGRAPH }
        assertEquals(null, paragraph.style)
        val heading = document.document.blocks.first { it.type == BookBlockType.HEADING }
        assertEquals(null, heading.style)
        WritingDocumentEditor.validate(document)
    }

    @Test
    fun headingAndQuoteAndSpansSurviveRoundTrip() {
        var document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        var draft = WritingChapterEditor.updateText(
            WritingChapterEditor.load(document, chapterId)!!,
            "第一章\n引用段落",
        )
        draft = WritingChapterEditor.setLineType(draft, 4, 4, BookBlockType.QUOTE)
        draft = WritingChapterEditor.addInlineStyle(draft, 4, 8, bold = true, italic = true)
        document = WritingChapterEditor.toDocument(document, draft)
        val reloaded = WritingChapterEditor.load(document, chapterId)!!
        assertEquals(BookBlockType.QUOTE, reloaded.lineTypes[1])
        assertTrue(reloaded.spans.any { it.bold && it.italic })
        assertEquals(1, document.document.chapters.size)
    }

    @Test
    fun replaceAndWordCountAreImmediate() {
        var document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        var draft = WritingChapterEditor.updateText(
            WritingChapterEditor.load(document, chapterId)!!,
            "标题\nHello world",
        )
        draft = WritingChapterEditor.replaceText(draft, 3, 8, "Universe")
        assertEquals("标题\nUniverse world", draft.text)
        assertEquals(13, WritingChapterEditor.wordCount(draft))
        document = WritingChapterEditor.toDocument(document, draft)
        assertNotNull(WritingChapterEditor.load(document, chapterId))
    }

    @Test
    fun editingOneChapterPreservesOtherChapterTextAndOffsets() {
        var document = WritingDocumentEditor.blank(book())
        document = WritingDocumentEditor.addChapter(document, "第二章")
        val first = document.document.chapters[0].chapterId
        val second = document.document.chapters[1].chapterId
        val draft = WritingChapterEditor.updateText(
            WritingChapterEditor.load(document, first)!!,
            "第一章\n新增一段",
        )
        document = WritingChapterEditor.toDocument(document, draft)
        assertEquals("第二章", WritingChapterEditor.load(document, second)!!.text)
        assertEquals(2, document.document.chapters.size)
        assertTrue(document.document.chapters[0].endOffset <= document.document.chapters[1].startOffset)
        WritingDocumentEditor.validate(document)
    }

    @Test
    fun initialBodyOffsetSkipsCenteredChapterHeading() {
        val document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        var draft = WritingChapterEditor.load(document, chapterId)!!
        draft = WritingChapterEditor.updateText(draft, "第一章\n正文")
        assertEquals("第一章\n".length, WritingChapterEditor.initialBodyOffset(draft))
        val indentedBody = WritingChapterEditor.updateText(draft, "第一章\n\n　　正文")
        assertEquals("第一章\n\n　　".length, WritingChapterEditor.initialBodyOffset(indentedBody))
        val emptyBody = WritingChapterEditor.updateText(draft, "第一章")
        assertEquals("第一章".length, WritingChapterEditor.initialBodyOffset(emptyBody))
    }

    @Test
    fun clearingBodyKeepsChapterTitle() {
        val document = WritingDocumentEditor.blank(book())
        var draft = WritingChapterEditor.load(
            document,
            document.document.chapters.single().chapterId,
        )!!
        draft = WritingChapterEditor.updateText(draft, "标题\n正文")
        val cleared = WritingChapterEditor.replaceText(draft, "标题\n".length, draft.text.length, "")
        assertTrue(cleared.text.startsWith("标题\n"))
        assertEquals("标题\n", cleared.text)
    }

    @Test
    fun replacingChapterTitlePreservesBodyAndImageAnchor() {
        val document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        var draft = WritingChapterEditor.load(document, chapterId)!!
        draft = WritingChapterEditor.updateText(draft, "第一章\n正文")
        draft = draft.copy(
            images = listOf(
                WritingChapterImage(
                    anchor = "第一章\n".length,
                    blockId = "title-body-image",
                    resourceId = "resource",
                ),
            ),
        )
        val updated = WritingChapterEditor.replaceText(draft, 0, 3, "新章节名")
        assertTrue(updated.text.startsWith("新章节名\n正文"))
        assertEquals("第一章\n".length + 1, updated.images.single().anchor)
    }

    @Test
    fun imageAnchorMovesWithTextBeforeIt() {
        var document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        val resource = BookResource(
            resourceId = "image-anchor",
            relativePath = "editor/images/image-anchor.jpg",
            mediaType = "image/jpeg",
            sizeBytes = 3,
            sha256 = "abc",
            kind = BookResourceKind.IMAGE,
        )
        document = document.copy(
            document = document.document.copy(resources = listOf(resource)),
        )
        var draft = WritingChapterEditor.load(document, chapterId)!!
        draft = WritingChapterEditor.updateText(draft, "第一段。\n\n　　第二段")
        draft = draft.copy(
            images = listOf(
                WritingChapterImage(
                    anchor = 8,
                    blockId = "image-anchor-block",
                    resourceId = resource.resourceId,
                ),
            ),
        )
        draft = WritingChapterEditor.updateText(draft, "新增开头。第一段。\n\n　　第二段")
        assertEquals(13, draft.images.single().anchor)
        val updated = WritingChapterEditor.toDocument(document, draft)
        WritingDocumentEditor.validate(updated)
    }

    @Test
    fun imageBlockSurvivesDraftRoundTrip() {
        var document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        val resource = BookResource(
            resourceId = "image-1",
            relativePath = "editor/images/image-1.jpg",
            mediaType = "image/jpeg",
            sizeBytes = 3,
            sha256 = "abc",
            kind = BookResourceKind.IMAGE,
        )
        document = document.copy(
            document = document.document.copy(resources = listOf(resource)),
        )
        val draft = WritingChapterEditor.load(document, chapterId)!!.copy(
            images = listOf(
                WritingChapterImage(
                    anchor = 0,
                    blockId = "image-block-1",
                    resourceId = resource.resourceId,
                ),
            ),
        )
        val updated = WritingChapterEditor.toDocument(document, draft)
        val imageBlock = updated.document.blocks.single {
            it.type == BookBlockType.IMAGE
        }
        assertEquals("image-1", imageBlock.resourceId)
        val reloaded = WritingChapterEditor.load(updated, chapterId)!!
        assertEquals(1, reloaded.images.size)
        assertEquals("image-block-1", reloaded.images.single().blockId)
        WritingDocumentEditor.validate(updated)
    }

    private fun book(): Book = Book(
        bookId = UUID.randomUUID().toString(),
        title = "编辑测试",
        shelfType = ShelfType.WRITING,
        sourceType = SourceType.CREATED,
        format = BookFormat.DRAFT,
        createdAt = 1,
        updatedAt = 1,
    )
}
