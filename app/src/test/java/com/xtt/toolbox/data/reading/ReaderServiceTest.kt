package com.xtt.toolbox.data.reading

import com.xtt.toolbox.data.content.BookContentStore
import com.xtt.toolbox.data.content.SavedContent
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ReaderServiceTest {
    @Test
    fun loadsNormalizedInternalTextAndClampsOffset() = runBlocking {
        val book = sampleBook(
            readingPosition = ReadingPosition(offset = 999, percent = 1f),
        )
        val store = FakeContentStore(mapOf("books/book-1/content.txt" to "第一行\r\n第二行"))
        val service = ReaderService(FakeBookRepository(listOf(book)), store)

        val loaded = service.load("book-1").getOrThrow()

        assertEquals("第一行\n第二行", loaded.text)
        assertEquals(7, loaded.initialOffset)
        assertEquals(book.bookId, loaded.book.bookId)
    }

    @Test
    fun missingBookReturnsFailure() = runBlocking {
        val service = ReaderService(FakeBookRepository(), FakeContentStore())

        val result = service.load("missing")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("书籍记录") == true)
    }

    @Test
    fun missingContentLocationReturnsFailure() = runBlocking {
        val book = sampleBook(contentLocation = null)
        val service = ReaderService(FakeBookRepository(listOf(book)), FakeContentStore())

        val result = service.load(book.bookId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("内部正文") == true)
    }

    @Test
    fun contentReadFailureReturnsFailure() = runBlocking {
        val book = sampleBook()
        val service = ReaderService(
            FakeBookRepository(listOf(book)),
            FakeContentStore(failRead = true),
        )

        val result = service.load(book.bookId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("正文读取失败") == true)
    }

    @Test
    fun emptyContentIsValidForEmptyReaderState() = runBlocking {
        val book = sampleBook()
        val service = ReaderService(
            FakeBookRepository(listOf(book)),
            FakeContentStore(mapOf("books/book-1/content.txt" to "")),
        )

        val loaded = service.load(book.bookId).getOrThrow()

        assertEquals("", loaded.text)
        assertEquals(0, loaded.initialOffset)
    }

    @Test
    fun loadsMarkdownPersistedDocument() = runBlocking {
        val book = sampleBook().copy(format = BookFormat.MARKDOWN)
        val document = BookDocument(
            chapters = emptyList(),
            blocks = listOf(
                BookBlock(
                    blockId = "md:block-0",
                    order = 0,
                    type = BookBlockType.HEADING,
                    startOffset = 0,
                    endOffset = 4,
                ),
            ),
            resources = emptyList(),
        )
        val service = ReaderService(
            FakeBookRepository(listOf(book), document),
            FakeContentStore(mapOf("books/book-1/content.txt" to "# 标题\n正文")),
        )

        val loaded = service.load(book.bookId).getOrThrow()

        assertEquals(BookFormat.MARKDOWN, loaded.book.format)
        assertEquals(1, loaded.document?.blocks?.size)
    }

    @Test
    fun loadsDraftPersistedDocument() = runBlocking {
        val book = sampleBook().copy(format = BookFormat.DRAFT)
        val document = BookDocument(
            chapters = emptyList(),
            blocks = listOf(
                BookBlock(
                    blockId = "draft:block-0",
                    order = 0,
                    type = BookBlockType.HEADING,
                    startOffset = 0,
                    endOffset = 4,
                ),
            ),
            resources = emptyList(),
        )
        val service = ReaderService(
            FakeBookRepository(listOf(book), document),
            FakeContentStore(mapOf("books/book-1/content.txt" to "# 草稿\n正文")),
        )

        val loaded = service.load(book.bookId).getOrThrow()

        assertEquals(BookFormat.DRAFT, loaded.book.format)
        assertEquals(1, loaded.document?.blocks?.size)
    }

    private fun sampleBook(
        contentLocation: String? = "books/book-1/content.txt",
        readingPosition: ReadingPosition? = ReadingPosition(offset = 0, percent = 0f),
    ): Book = Book(
        bookId = "book-1",
        title = "测试书",
        shelfType = ShelfType.READING,
        sourceType = SourceType.IMPORTED,
        contentLocation = contentLocation,
        contentHash = "hash-1",
        contentSize = 20,
        sourceEncoding = "UTF-8",
        format = BookFormat.TXT,
        readingPosition = readingPosition,
        createdAt = 1,
        updatedAt = 1,
        lastOpenedAt = null,
    )
}

private class FakeContentStore(
    private val content: Map<String, String> = emptyMap(),
    private val failRead: Boolean = false,
) : BookContentStore {
    override fun save(bookId: String, text: String): SavedContent =
        throw UnsupportedOperationException()

    override fun readText(location: String): String {
        if (failRead) throw IOException("read failed")
        return content[location] ?: throw IOException("missing")
    }

    override fun delete(location: String) = Unit

    override fun exists(location: String): Boolean = content.containsKey(location)
}

private class FakeBookRepository(
    initial: List<Book> = emptyList(),
    private val document: BookDocument? = null,
) : BookRepository {
    private val state = MutableStateFlow(initial)
    override val books: StateFlow<List<Book>> = state.asStateFlow()

    override suspend fun reload() = Unit

    override suspend fun getBook(bookId: String): Book? =
        state.value.firstOrNull { it.bookId == bookId }

    override suspend fun findByContentHash(contentHash: String): Book? =
        state.value.firstOrNull { it.contentHash == contentHash }

    override suspend fun insert(book: Book): Result<Unit> =
        Result.success(Unit)

    override suspend fun loadDocument(bookId: String): BookDocument? = document

    override suspend fun upsert(book: Book) = Unit

    override suspend fun updateReadingPosition(
        bookId: String,
        position: ReadingPosition,
        lastOpenedAt: Long?,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun removeFromShelf(bookId: String) = Unit
}
