package com.xtt.toolbox.data.importing

import com.xtt.toolbox.data.content.BookContentStore
import com.xtt.toolbox.data.content.ContentHash
import com.xtt.toolbox.data.content.FileBookContentStore
import com.xtt.toolbox.data.content.SavedContent
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.domain.chapter.RegexChapterParser
import com.xtt.toolbox.domain.importing.TxtDocument
import com.xtt.toolbox.domain.importing.TxtDocumentSource
import com.xtt.toolbox.domain.importing.TxtImportException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class TxtImportServiceTest {
    private lateinit var root: File
    private lateinit var store: FileBookContentStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("xtt-import-test").toFile()
        store = FileBookContentStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun previewsTitleSizeEncodingAndChapters() = runBlocking {
        val text = "第一章 开始\n正文\n第二章 继续"
        val repo = FakeBookRepository()
        val service = service(text, repo)

        val preview = service.prepare(URI)

        assertEquals("我的小说", preview.defaultTitle)
        assertEquals("我的小说.txt", preview.sourceName)
        assertEquals(2, preview.chapterCount)
        assertEquals(text.toByteArray(Charsets.UTF_8).size.toLong(), preview.contentSizeBytes)
        assertTrue(preview.contentHash.isNotBlank())
        assertNull(preview.duplicateBook)
    }

    @Test
    fun importsBookAndContent() = runBlocking {
        val text = "第一章 开始\n正文"
        val repo = FakeBookRepository()
        val service = service(text, repo)
        val preview = service.prepare(URI)

        val book = service.commit(preview, "自定义书名").getOrThrow()

        assertEquals("自定义书名", book.title)
        assertEquals(ShelfType.READING, book.shelfType)
        assertEquals(SourceType.IMPORTED, book.sourceType)
        assertEquals(BookFormat.TXT, book.format)
        assertEquals("我的小说.txt", book.sourceName)
        assertNotNull(book.contentLocation)
        assertTrue(store.exists(book.contentLocation!!))
        assertEquals(0f, book.readingPosition?.percent)
        assertEquals(1, repo.books.value.size)
    }

    @Test
    fun rejectsUnsupportedExtensionBeforeCreatingAnything() = runBlocking {
        val repo = FakeBookRepository()
        val service = TxtImportService(
            source = FakeSource(TxtDocument("book.pdf", 5, "hello".toByteArray())),
            contentStore = store,
            repository = repo,
        )
        val error = runCatching { service.prepare(URI) }.exceptionOrNull()
        assertTrue(error is TxtImportException)
        assertTrue(repo.books.value.isEmpty())
        assertTrue(File(root, "books").exists().not() ||
            File(root, "books").listFiles().isNullOrEmpty())
    }

    @Test
    fun rejectsObviousBinaryContentForTxtExtension() = runBlocking {
        val repo = FakeBookRepository()
        val service = TxtImportService(
            source = FakeSource(TxtDocument("伪装.txt", 8, "%PDF-1.7".toByteArray())),
            contentStore = store,
            repository = repo,
        )
        assertThrows(TxtImportException::class.java) {
            runBlocking { service.prepare(URI) }
        }
        assertTrue(repo.books.value.isEmpty())
    }

    @Test
    fun previewsAndReReadsNineHundredNinetyNineChapters() = runBlocking {
        val text = buildString {
            for (index in 1..999) {
                val heading = when (index % 3) {
                    0 -> "第 $index 回 标题$index"
                    1 -> "第${index}章 标题$index"
                    else -> "第${index}节：标题$index"
                }
                append(heading).append('\n')
                append("正文").append(index).append('\n')
                if (index != 999) append('\n')
            }
        }
        val repo = FakeBookRepository()
        val service = service(text, repo)
        val preview = service.prepare(URI)

        assertEquals(999, preview.chapterCount)
        val book = service.commit(preview, "999章测试").getOrThrow()
        val stored = store.readText(book.contentLocation!!)
        val reparsed = RegexChapterParser().parse(stored).candidates

        assertEquals(999, reparsed.size)
        assertEquals("第 999 回 标题999", reparsed.last().title)
        assertEquals(stored.length, reparsed.last().endOffset)
    }

    @Test
    fun importsTextWithoutChapters() = runBlocking {
        val text = "没有章节标题的普通正文。"
        val repo = FakeBookRepository()
        val service = service(text, repo)
        val preview = service.prepare(URI)

        assertEquals(0, preview.chapterCount)
        val book = service.commit(preview, "无章节").getOrThrow()
        assertTrue(store.exists(book.contentLocation!!))
    }

    @Test
    fun rejectsEmptyFile() = runBlocking {
        val repo = FakeBookRepository()
        val service = TxtImportService(
            source = FakeSource(TxtDocument("空.txt", 0, ByteArray(0))),
            contentStore = store,
            repository = repo,
        )

        assertThrows(TxtImportException::class.java) {
            runBlocking { service.prepare(URI) }
        }
        Unit
    }

    @Test
    fun reportsDecodeFailureWithEncodingChoices() = runBlocking {
        val repo = FakeBookRepository()
        val service = TxtImportService(
            source = FakeSource(
                TxtDocument("坏文件.txt", 2, byteArrayOf(0xC3.toByte(), 0x28)),
            ),
            contentStore = store,
            repository = repo,
        )

        val error = assertThrows(TxtImportException::class.java) {
            runBlocking { service.prepare(URI, forcedEncoding = "UTF-8") }
        }
        assertTrue(error.availableEncodings.isNotEmpty())
    }

    @Test
    fun detectsDuplicateByContentHash() = runBlocking {
        val text = "第一章\n正文"
        val hash = ContentHash.sha256(text.toByteArray(Charsets.UTF_8))
        val existing = Book.create(
            title = "已有书",
            shelfType = ShelfType.READING,
            sourceType = SourceType.IMPORTED,
            contentHash = hash,
            contentSize = text.toByteArray(Charsets.UTF_8).size.toLong(),
            format = BookFormat.TXT,
        )
        val repo = FakeBookRepository(listOf(existing))
        val service = service(text, repo)

        val preview = service.prepare(URI)

        assertEquals(existing.bookId, preview.duplicateBook?.bookId)
    }

    @Test
    fun databaseFailureDeletesContentAndLeavesNoBook() = runBlocking {
        val text = "第一章\n正文"
        val repo = FakeBookRepository(failInsert = true)
        val service = service(text, repo)
        val preview = service.prepare(URI)

        val result = service.commit(preview, "失败的书")

        assertTrue(result.isFailure)
        assertTrue(repo.books.value.isEmpty())
        assertFalse(root.walkTopDown().any { it.isFile })
    }

    @Test
    fun contentStoreFailureLeavesNoBook() = runBlocking {
        val text = "正文"
        val repo = FakeBookRepository()
        val service = TxtImportService(
            source = FakeSource(TxtDocument("书.txt", text.length.toLong(), text.toByteArray())),
            contentStore = FailingContentStore,
            repository = repo,
        )
        val preview = service.prepare(URI)

        assertTrue(service.commit(preview, "失败的书").isFailure)
        assertTrue(repo.books.value.isEmpty())
    }

    @Test
    fun blankTitleDoesNotCreateContent() = runBlocking {
        val repo = FakeBookRepository()
        val service = service("正文", repo)
        val preview = service.prepare(URI)

        assertTrue(service.commit(preview, "   ").isFailure)
        assertTrue(repo.books.value.isEmpty())
        assertFalse(root.walkTopDown().any { it.isFile })
    }

    @Test
    fun duplicateImportCreatesNewBookIdentity() = runBlocking {
        val text = "第一章\n正文"
        val hash = ContentHash.sha256(text.toByteArray(Charsets.UTF_8))
        val existing = Book.create(
            title = "已有书",
            shelfType = ShelfType.READING,
            sourceType = SourceType.IMPORTED,
            contentHash = hash,
        )
        val repo = FakeBookRepository(listOf(existing))
        val service = service(text, repo)
        val preview = service.prepare(URI)

        val imported = service.commit(preview, "重复导入").getOrThrow()

        assertNotEquals(existing.bookId, imported.bookId)
        assertEquals(2, repo.books.value.size)
    }

    private fun service(text: String, repo: FakeBookRepository): TxtImportService =
        TxtImportService(
            source = FakeSource(
                TxtDocument(
                    displayName = "我的小说.txt",
                    sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong(),
                    bytes = text.toByteArray(Charsets.UTF_8),
                ),
            ),
            contentStore = store,
            repository = repo,
        )

    private companion object {
        const val URI = "content://test/book.txt"
    }
}

private class FakeSource(private val document: TxtDocument) : TxtDocumentSource {
    override suspend fun read(uri: String): TxtDocument = document
}

private class FakeBookRepository(
    initial: List<Book> = emptyList(),
    private val failInsert: Boolean = false,
) : BookRepository {
    private val state = MutableStateFlow(initial)
    override val books: StateFlow<List<Book>> = state.asStateFlow()

    override suspend fun reload() = Unit

    override suspend fun getBook(bookId: String): Book? =
        state.value.firstOrNull { it.bookId == bookId }

    override suspend fun findByContentHash(contentHash: String): Book? =
        state.value.firstOrNull { it.contentHash == contentHash }

    override suspend fun insert(book: Book): Result<Unit> {
        if (failInsert) return Result.failure(IOException("database failure"))
        if (state.value.any { it.bookId == book.bookId }) {
            return Result.failure(IllegalStateException("bookId exists"))
        }
        state.update { it + book }
        return Result.success(Unit)
    }

    override suspend fun upsert(book: Book) {
        state.update { current ->
            current.map { if (it.bookId == book.bookId) book else it }
        }
    }

    override suspend fun removeFromShelf(bookId: String) {
        state.update { current -> current.filterNot { it.bookId == bookId } }
    }

    override suspend fun updateReadingPosition(
        bookId: String,
        position: ReadingPosition,
        lastOpenedAt: Long?,
    ): Result<Unit> {
        if (state.value.none { it.bookId == bookId }) {
            return Result.failure(IllegalStateException("book not found"))
        }
        val now = System.currentTimeMillis()
        state.update { current ->
            current.map {
                if (it.bookId == bookId) {
                    it.copy(
                        readingPosition = position,
                        updatedAt = maxOf(it.updatedAt, now),
                        lastOpenedAt = lastOpenedAt ?: it.lastOpenedAt,
                    )
                } else {
                    it
                }
            }
        }
        return Result.success(Unit)
    }
}

private object FailingContentStore : BookContentStore {
    override fun save(bookId: String, text: String): SavedContent =
        throw IOException("disk full")

    override fun readText(location: String): String =
        throw IOException("disk full")

    override fun delete(location: String) = Unit

    override fun exists(location: String): Boolean = false
}
