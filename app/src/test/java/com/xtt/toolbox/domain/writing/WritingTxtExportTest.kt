package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.domain.chapter.RegexChapterParser
import com.xtt.toolbox.domain.chapter.TextOffsets
import com.xtt.toolbox.domain.reading.ReaderDocument
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WritingTxtExportTest {
    @Test
    fun emptyTitleDoesNotWriteBookTitleOrPlaceholder() {
        val document = WritingDocumentFactory.plainText(book(), "正文\n　　", "")
        assertEquals("正文\n　　", WritingTxtExport.build(document))
    }

    @Test
    fun titleIsWrittenOnceAndBodyIsPreserved() {
        val document = WritingDocumentFactory.plainText(book(), "标题\n正文", "标题")
        assertEquals("标题\n正文", WritingTxtExport.build(document))
    }

    @Test
    fun imageBecomesReadablePlaceholderWithoutResourcePath() {
        val base = WritingDocumentFactory.plainText(book(), "前文\n后文", "")
        val image = BookBlock(
            blockId = "export-image",
            order = 1,
            type = BookBlockType.IMAGE,
            startOffset = 2,
            endOffset = 2,
            resourceId = "private-resource-id",
        )
        val document = base.copy(
            document = base.document.copy(
                blocks = base.document.blocks + image,
                resources = listOf(
                    BookResource(
                        resourceId = "private-resource-id",
                        relativePath = "editor/images/private.png",
                        mediaType = "image/png",
                        sizeBytes = 1,
                        sha256 = "test",
                        kind = BookResourceKind.IMAGE,
                    ),
                ),
            ),
        )
        val text = WritingTxtExport.build(document)
        assertTrue(text.contains("[图片]"))
        assertFalse(text.contains("private-resource-id"))
        assertFalse(text.contains("editor/images"))
    }

    @Test
    fun exportedIndentSurvivesImportAndReaderProjection() {
        var document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        var draft = WritingChapterEditor.load(document, chapterId)!!
        draft = WritingChapterEditor.updateText(
            draft,
            "第一章\n\n　　第一段\n\n　　第二段",
        )
        document = WritingChapterEditor.toDocument(document, draft)
        val exported = WritingTxtExport.build(document)
        assertTrue(exported.contains("　　第一段"))
        val normalized = TextOffsets.normalizeNewlines(exported.replace("\n", "\r\n"))
        val chapters = RegexChapterParser().parse(normalized)
        assertTrue(chapters.candidates.isNotEmpty())
        val reader = ReaderDocument.fromText(normalized)
        val paragraph = reader.blocks.first { it.text.contains("第一段") }
        assertTrue(paragraph.text.startsWith("　　"))
        assertEquals(2, normalized.windowed(2).count { it == "　　" })
    }

    @Test
    fun chaptersAreExportedInChapterOrder() {
        var document = WritingDocumentEditor.blank(book())
        val firstId = document.document.chapters.single().chapterId
        var first = WritingChapterEditor.load(document, firstId)!!
        first = WritingChapterEditor.updateText(first, "第一章\n甲")
        document = WritingChapterEditor.toDocument(document, first)
        document = WritingDocumentEditor.addChapter(document, "第二章")
        val secondId = document.document.chapters.maxByOrNull { it.order }!!.chapterId
        var second = WritingChapterEditor.load(document, secondId)!!
        second = WritingChapterEditor.updateText(second, "第二章\n乙")
        document = WritingChapterEditor.toDocument(document, second)
        assertEquals("第一章\n甲\n\n第二章\n乙", WritingTxtExport.build(document))
    }

    @Test
    fun suggestedFileNameIsSafeUtf8TextName() {
        assertEquals("作品.txt", WritingTxtExport.suggestedFileName("作品"))
        assertFalse(WritingTxtExport.suggestedFileName("a/b:c").contains("/"))
        assertEquals("未命名作品.txt", WritingTxtExport.suggestedFileName("   "))
    }

    private fun book(): Book = Book(
        bookId = UUID.randomUUID().toString(),
        title = "作品名不能作为章节名",
        shelfType = ShelfType.WRITING,
        sourceType = SourceType.CREATED,
        format = BookFormat.DRAFT,
        createdAt = 1,
        updatedAt = 1,
    )
}
