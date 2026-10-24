package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.content.FileBookContentStore
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.data.model.ChapterDetectionSource
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.InMemoryBookRepository
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookDocument
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import com.xtt.toolbox.domain.content.ExtractedResource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WritingLibraryServiceTest {
    private lateinit var root: File
    private lateinit var store: FileBookContentStore
    private lateinit var repository: InMemoryBookRepository
    private lateinit var documentService: WritingDocumentService
    private lateinit var library: WritingLibraryService

    @Before
    fun setUp() {
        root = Files.createTempDirectory("xtt-writing-library").toFile()
        store = FileBookContentStore(root)
        repository = InMemoryBookRepository()
        documentService = WritingDocumentService(repository, store)
        library = WritingLibraryService(repository, store, documentService)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun movingReadingBookKeepsIdentityAndFormatDraft() = runBlocking {
        val source = sourceBook("source-book")
        repository.insertDocument(source, sourceDocument(source.bookId))
        val moved = library.moveToWriting(source.bookId).getOrThrow()

        assertEquals(source.bookId, moved.book.bookId)
        assertEquals(ShelfType.WRITING, moved.book.shelfType)
        assertEquals(BookFormat.DRAFT, moved.book.format)
        assertEquals(source.sourceUri, moved.book.sourceUri)
        assertEquals(1, repository.books.value.count { it.bookId == source.bookId })
    }

    @Test
    fun copyingCreatesNewIdentityRemapsIdsAndKeepsSource() = runBlocking {
        val source = sourceBook("source-book")
        repository.insertDocument(source, sourceDocument(source.bookId))
        val copy = library.copyToWriting(source.bookId).getOrThrow()

        assertNotEquals(source.bookId, copy.book.bookId)
        assertEquals(source.bookId, copy.book.originBookId)
        assertEquals(ShelfType.WRITING, copy.book.shelfType)
        assertEquals(BookFormat.DRAFT, copy.book.format)
        val sourceDocument = repository.loadDocument(source.bookId)!!
        val copyDocument = repository.loadDocument(copy.book.bookId)!!
        assertNotEquals(
            sourceDocument.chapters.single().chapterId,
            copyDocument.chapters.single().chapterId,
        )
        assertNotEquals(
            sourceDocument.blocks.first().blockId,
            copyDocument.blocks.first().blockId,
        )
        assertNotEquals(
            sourceDocument.resources.single().resourceId,
            copyDocument.resources.single().resourceId,
        )
        assertEquals(
            copyDocument.blocks.last().resourceId,
            copyDocument.resources.single().resourceId,
        )
        assertEquals(source.sourceUri, repository.getBook(source.bookId)?.sourceUri)
    }

    @Test
    fun copyFailureDoesNotCreateNewBookOrModifySource() = runBlocking {
        val source = sourceBook("broken-source")
        val document = sourceDocument(source.bookId)
        repository.insertDocument(source, document)
        store.deleteResource(source.bookId, document.resources.single().relativePath)

        val result = library.copyToWriting(source.bookId)
        assertTrue(result.isFailure)
        assertEquals(1, repository.books.value.size)
        assertEquals(source, repository.getBook(source.bookId))
    }

    private fun sourceBook(id: String): Book {
        val now = 1L
        return Book(
            bookId = id,
            title = "来源作品",
            shelfType = ShelfType.READING,
            sourceType = SourceType.IMPORTED,
            sourceUri = "content://source/book.epub",
            contentLocation = "books/$id/content.txt",
            sourceName = "book.epub",
            contentHash = "source-hash",
            contentSize = 8,
            format = BookFormat.EPUB,
            createdAt = now,
            updatedAt = now,
        )
    }

    private suspend fun sourceDocument(bookId: String): BookDocument {
        val resource = BookResource(
            resourceId = "source-resource",
            relativePath = "images/cover.png",
            mediaType = "image/png",
            sizeBytes = 4,
            sha256 = "image-hash",
            kind = BookResourceKind.IMAGE,
        )
        val chapter = Chapter(
            chapterId = "source-chapter",
            bookId = bookId,
            title = "第一章",
            order = 0,
            startOffset = 0,
            endOffset = 2,
            headingOffset = 0,
            detectionSource = ChapterDetectionSource.EPUB_NAV,
            confidence = 1f,
        )
        val block = BookBlock(
            blockId = "source-block",
            order = 0,
            type = BookBlockType.HEADING,
            startOffset = 0,
            endOffset = 2,
        )
        val image = BookBlock(
            blockId = "source-image",
            order = 1,
            type = BookBlockType.IMAGE,
            startOffset = 2,
            endOffset = 2,
            resourceId = resource.resourceId,
            alt = "图",
        )
        val document = BookDocument(
            chapters = listOf(chapter),
            blocks = listOf(block, image),
            resources = listOf(resource),
        )
        store.saveDocument(
            bookId = bookId,
            text = "标题",
            resources = listOf(ExtractedResource(resource, byteArrayOf(1, 2, 3, 4))),
        )
        return document
    }
}
