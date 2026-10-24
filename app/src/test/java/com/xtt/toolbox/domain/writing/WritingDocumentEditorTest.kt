package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookDocument
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WritingDocumentEditorTest {
    @Test
    fun blankDocumentHasStableFirstChapterAndHeadingBlock() {
        val document = WritingDocumentEditor.blank(book())
        val chapter = document.document.chapters.single()
        assertEquals("第一章\n", document.text)
        assertEquals(0, chapter.startOffset)
        assertEquals(document.text.length, chapter.endOffset)
        assertEquals(BookBlockType.HEADING, document.document.blocks.single().type)
        assertEquals(chapter.chapterId + "-heading", document.document.blocks.single().blockId)
    }

    @Test
    fun addChapterAppendsStableIdAndRanges() {
        val first = WritingDocumentEditor.blank(book())
        val second = WritingDocumentEditor.addChapter(first, "第二章")

        assertEquals(2, second.document.chapters.size)
        assertEquals("第一章\n第二章\n", second.text)
        assertNotEquals(
            second.document.chapters[0].chapterId,
            second.document.chapters[1].chapterId,
        )
        assertEquals(0, second.document.chapters[0].order)
        assertEquals(1, second.document.chapters[1].order)
        assertEquals(second.text.length, second.document.chapters[1].endOffset)
        WritingDocumentEditor.validate(second)
    }

    @Test
    fun addChapterAtIndexPreservesExistingChapterIdAndContent() {
        val first = WritingDocumentEditor.blank(book())
        val originalId = first.document.chapters.single().chapterId
        val inserted = WritingDocumentEditor.addChapter(first, "序章", index = 0)

        assertEquals("序章\n第一章\n", inserted.text)
        assertEquals(2, inserted.document.chapters.size)
        assertEquals(originalId, inserted.document.chapters[1].chapterId)
        assertEquals("第一章", inserted.document.chapters[1].title)
        assertEquals("序章", inserted.document.chapters[0].title)
    }

    @Test
    fun renameChapterUpdatesHeadingTextAndKeepsId() {
        val document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        val renamed = WritingDocumentEditor.renameChapter(document, chapterId, "新的标题")

        assertEquals(chapterId, renamed.document.chapters.single().chapterId)
        assertEquals("新的标题", renamed.document.chapters.single().title)
        assertEquals("新的标题\n", renamed.text)
        assertEquals(BookBlockType.HEADING, renamed.document.blocks.single().type)
        WritingDocumentEditor.validate(renamed)
    }

    @Test
    fun reorderChaptersKeepsIdsButChangesTextOrder() {
        var document = WritingDocumentEditor.blank(book())
        document = WritingDocumentEditor.addChapter(document, "第二章")
        document = WritingDocumentEditor.addChapter(document, "第三章")
        val ids = document.document.chapters.map { it.chapterId }
        val reordered = WritingDocumentEditor.reorderChapters(
            document,
            listOf(ids[2], ids[0], ids[1]),
        )

        assertEquals(listOf("第三章", "第一章", "第二章"), reordered.text.lines().filter { it.isNotEmpty() })
        assertEquals(listOf(ids[2], ids[0], ids[1]), reordered.document.chapters.map { it.chapterId })
        assertEquals(listOf(0, 1, 2), reordered.document.chapters.map { it.order })
        assertEquals(reordered.text.length, reordered.document.chapters.last().endOffset)
        WritingDocumentEditor.validate(reordered)
    }

    @Test
    fun deleteMiddleChapterPreservesOtherContentAndOffsets() {
        var document = WritingDocumentEditor.blank(book())
        document = WritingDocumentEditor.addChapter(document, "第二章")
        document = WritingDocumentEditor.addChapter(document, "第三章")
        val middle = document.document.chapters[1]
        val deleted = WritingDocumentEditor.deleteChapter(document, middle.chapterId)

        assertEquals("第一章\n第三章\n", deleted.text)
        assertEquals(2, deleted.document.chapters.size)
        assertEquals(listOf("第一章", "第三章"), deleted.document.chapters.map { it.title })
        assertEquals(deleted.text.length, deleted.document.chapters.last().endOffset)
        WritingDocumentEditor.validate(deleted)
    }

    @Test
    fun reorderKeepsImageResourceReferencedByItsChapter() {
        var document = WritingDocumentEditor.blank(book())
        document = WritingDocumentEditor.addChapter(document, "第二章")
        val resource = BookResource(
            resourceId = "image-1",
            relativePath = "images/a.png",
            mediaType = "image/png",
            sizeBytes = 3,
            sha256 = "abc",
            kind = BookResourceKind.IMAGE,
        )
        val first = document.document.chapters[0]
        val image = BookBlock(
            blockId = "image-block",
            order = 0,
            type = BookBlockType.IMAGE,
            startOffset = first.startOffset,
            endOffset = first.startOffset,
            resourceId = resource.resourceId,
            alt = "a",
        )
        document = document.copy(
            document = document.document.copy(
                blocks = (document.document.blocks + image)
                    .sortedWith(compareBy({ it.startOffset }, { it.type == BookBlockType.IMAGE }))
                    .mapIndexed { index, block -> block.copy(order = index) },
                resources = listOf(resource),
            ),
        )
        WritingDocumentEditor.validate(document)

        val reordered = WritingDocumentEditor.reorderChapters(
            document,
            document.document.chapters.reversed().map { it.chapterId },
        )
        assertEquals(resource.resourceId, reordered.document.blocks.first { it.blockId == image.blockId }.resourceId)
        assertTrue(reordered.document.resources.any { it.resourceId == resource.resourceId })
        WritingDocumentEditor.validate(reordered)
    }

    @Test
    fun invalidChapterIdsAndLastChapterDeletionAreRejected() {
        val document = WritingDocumentEditor.blank(book())
        val chapterId = document.document.chapters.single().chapterId
        runCatching {
            WritingDocumentEditor.reorderChapters(document, listOf("not-this-book"))
        }.also { assertTrue(it.isFailure) }
        runCatching {
            WritingDocumentEditor.deleteChapter(document, chapterId)
        }.also { assertTrue(it.isFailure) }
        runCatching {
            WritingDocumentEditor.renameChapter(document, "missing", "标题")
        }.also { assertTrue(it.isFailure) }
    }

    @Test
    fun duplicateOrCrossBookChapterIsRejected() {
        val document = WritingDocumentEditor.blank(book())
        val chapter = document.document.chapters.single()
        runCatching {
            document.copy(
                document = document.document.copy(chapters = listOf(chapter, chapter)),
            )
        }.also { assertTrue(it.isFailure) }
        runCatching {
            WritingDocument(
                book = document.book,
                text = document.text,
                document = document.document.copy(
                    chapters = listOf(
                        chapter.copy(bookId = UUID.randomUUID().toString()),
                    ),
                ),
            )
        }.also { assertTrue(it.isFailure) }
    }

    private fun book(): Book = Book(
        bookId = UUID.randomUUID().toString(),
        title = "测试作品",
        shelfType = ShelfType.WRITING,
        sourceType = SourceType.CREATED,
        format = BookFormat.EPUB,
        createdAt = 1,
        updatedAt = 1,
    )

    @Suppress("unused")
    private fun document(blocks: List<BookBlock>): BookDocument = BookDocument(
        chapters = emptyList(),
        blocks = blocks,
        resources = emptyList(),
    )
}
