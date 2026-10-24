package com.xtt.toolbox.data.repository

import com.xtt.toolbox.data.content.BookContentStore
import com.xtt.toolbox.data.content.FileBookContentStore
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import com.xtt.toolbox.domain.content.ExtractedResource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class BookDeletionServiceTest {
    private lateinit var root: File
    private lateinit var contentStore: FileBookContentStore
    private lateinit var repository: InMemoryBookRepository
    private lateinit var service: BookDeletionService

    @Before
    fun setUp() {
        root = Files.createTempDirectory("xtt-delete-test").toFile()
        contentStore = FileBookContentStore(root)
        repository = InMemoryBookRepository()
        service = BookDeletionService(repository, contentStore)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun deletesTxtBookWithoutTouchingOtherBook() = runBlocking {
        val first = sampleBook("book-1")
        val second = sampleBook("book-2")
        val firstSaved = contentStore.save(first.bookId, "第一本")
        val secondSaved = contentStore.save(second.bookId, "第二本")
        repository.insert(first.copy(contentLocation = firstSaved.location)).getOrThrow()
        repository.insert(second.copy(contentLocation = secondSaved.location)).getOrThrow()

        val result = service.delete(first.bookId).getOrThrow()

        assertTrue(result.fileCleanupSucceeded)
        assertNull(repository.getBook(first.bookId))
        assertFalse(contentStore.exists(firstSaved.location))
        assertTrue(contentStore.exists(secondSaved.location))
        assertTrue(repository.getBook(second.bookId) != null)
    }

    @Test
    fun deletesEpubDirectoryIncludingCoverAndImages() = runBlocking {
        val book = sampleBook("epub-book").copy(format = BookFormat.EPUB)
        val cover = BookResource(
            resourceId = "cover-1",
            relativePath = "epub/resources/cover.png",
            mediaType = "image/png",
            sizeBytes = 3,
            sha256 = "cover",
            kind = BookResourceKind.COVER,
        )
        val image = BookResource(
            resourceId = "image-1",
            relativePath = "epub/resources/image.png",
            mediaType = "image/png",
            sizeBytes = 3,
            sha256 = "image",
            kind = BookResourceKind.IMAGE,
        )
        val saved = contentStore.saveDocument(
            book.bookId,
            "EPUB 正文",
            listOf(
                ExtractedResource(cover, byteArrayOf(1, 2, 3)),
                ExtractedResource(image, byteArrayOf(4, 5, 6)),
            ),
        )
        repository.insert(book.copy(contentLocation = saved.location)).getOrThrow()
        assertTrue(contentStore.resolveBookResource(book.bookId, cover.relativePath)?.isFile == true)

        assertTrue(service.delete(book.bookId).getOrThrow().fileCleanupSucceeded)

        assertFalse(File(root, "books/${book.bookId}").exists())
        assertNull(repository.getBook(book.bookId))
    }

    @Test
    fun alreadyMissingFilesStillDeleteMetadata() = runBlocking {
        val book = sampleBook("book-1")
        repository.insert(book).getOrThrow()

        val result = service.delete(book.bookId).getOrThrow()

        assertTrue(result.fileCleanupSucceeded)
        assertNull(repository.getBook(book.bookId))
    }

    @Test
    fun databaseFailureDoesNotDeleteFiles() = runBlocking {
        val book = sampleBook("book-1")
        val saved = contentStore.save(book.bookId, "保留正文")
        repository.insert(book.copy(contentLocation = saved.location)).getOrThrow()
        val failingRepository = object : BookRepository by repository {
            override suspend fun removeFromShelf(bookId: String) {
                throw IOException("模拟数据库删除失败")
            }
        }
        val failingService = BookDeletionService(failingRepository, contentStore)

        val result = failingService.delete(book.bookId)

        assertTrue(result.isFailure)
        assertTrue(contentStore.exists(saved.location))
        assertTrue(repository.getBook(book.bookId) != null)
    }

    @Test
    fun fileCleanupFailureLeavesDatabaseDeletionPendingForOrphanCleanup() = runBlocking {
        val book = sampleBook("book-1")
        val saved = contentStore.save(book.bookId, "待清理正文")
        repository.insert(book.copy(contentLocation = saved.location)).getOrThrow()
        val failingStore = object : BookContentStore by contentStore {
            override fun deleteBook(bookId: String): Boolean = false
        }
        val failingService = BookDeletionService(repository, failingStore)

        val result = failingService.delete(book.bookId).getOrThrow()

        assertFalse(result.fileCleanupSucceeded)
        assertNull(repository.getBook(book.bookId))
        assertTrue(contentStore.exists(saved.location))
        contentStore.cleanupOrphans(emptySet())
        assertFalse(contentStore.exists(saved.location))
    }

    private fun sampleBook(bookId: String): Book = Book(
        bookId = bookId,
        title = "测试书",
        shelfType = ShelfType.READING,
        sourceType = SourceType.IMPORTED,
        contentLocation = "books/$bookId/content.txt",
        sourceName = "测试书.txt",
        contentHash = "hash-$bookId",
        contentSize = 8L,
        sourceEncoding = "UTF-8",
        format = BookFormat.TXT,
        readingPosition = ReadingPosition(offset = 0, percent = 0f),
        createdAt = 1000L,
        updatedAt = 1000L,
    )
}
