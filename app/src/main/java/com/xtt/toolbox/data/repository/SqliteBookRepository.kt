package com.xtt.toolbox.data.repository

import com.xtt.toolbox.data.local.BookDatabaseHelper
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookVolume
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.domain.content.BookDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * SQLite 元数据仓库。
 *
 * [insert] 只有在数据库写入成功后才更新 [books] 缓存；失败时缓存不变，
 * 由调用方清理已经写入的正文文件，避免出现可见的错误书架条目。
 */
class SqliteBookRepository(
    private val database: BookDatabaseHelper,
) : BookRepository {
    private val _books = MutableStateFlow<List<Book>>(emptyList())
    override val books: StateFlow<List<Book>> = _books.asStateFlow()

    override suspend fun reload() = withContext(Dispatchers.IO) {
        _books.value = database.readAll()
    }

    override suspend fun getBook(bookId: String): Book? = withContext(Dispatchers.IO) {
        database.findByBookId(bookId)
    }

    override suspend fun findByContentHash(contentHash: String): Book? =
        withContext(Dispatchers.IO) {
            database.findByContentHash(contentHash)
        }

    override suspend fun insert(book: Book): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.insert(book)
        } catch (error: Throwable) {
            return@withContext Result.failure(error)
        }
        _books.update { current -> current + book }
        Result.success(Unit)
    }

    override suspend fun insertDocument(
        book: Book,
        document: BookDocument,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.insertDocument(
                book = book,
                chapters = document.chapters,
                blocks = document.blocks,
                resources = document.resources,
            )
        } catch (error: Throwable) {
            return@withContext Result.failure(error)
        }
        _books.update { current -> current + book }
        Result.success(Unit)
    }

    override suspend fun loadDocument(bookId: String): BookDocument? =
        withContext(Dispatchers.IO) {
            val chapters = database.readChapters(bookId)
            val blocks = database.readBlocks(bookId)
            if (blocks.isEmpty()) return@withContext null
            BookDocument(
                chapters = chapters,
                blocks = blocks,
                resources = database.readResources(bookId),
            )
        }

    override suspend fun loadVolumes(bookId: String): List<BookVolume> =
        withContext(Dispatchers.IO) { database.readVolumes(bookId) }

    override suspend fun insertVolume(volume: BookVolume): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { database.insertVolume(volume) }
        }

    override suspend fun renameVolume(volumeId: String, title: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!database.renameVolume(volumeId, title)) error("分卷不存在")
            }
        }

    override suspend fun deleteVolume(volumeId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!database.deleteVolume(volumeId)) error("分卷不存在")
            }
        }

    override suspend fun replaceDocument(
        bookId: String,
        document: BookDocument,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.replaceDocument(
                bookId = bookId,
                chapters = document.chapters,
                blocks = document.blocks,
                resources = document.resources,
            )
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    override suspend fun commitDocument(
        book: Book,
        document: BookDocument,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.commitDocument(
                book = book,
                chapters = document.chapters,
                blocks = document.blocks,
                resources = document.resources,
            )
            _books.update { current ->
                val index = current.indexOfFirst { it.bookId == book.bookId }
                if (index < 0) current else current.toMutableList().also { it[index] = book }
            }
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    override suspend fun commitDocumentIfUnchanged(
        book: Book,
        document: BookDocument,
        expectedContentHash: String?,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val committed = database.commitDocumentIfUnchanged(
                book = book,
                chapters = document.chapters,
                blocks = document.blocks,
                resources = document.resources,
                expectedContentHash = expectedContentHash,
            )
            if (committed) {
                _books.update { current ->
                    val index = current.indexOfFirst { it.bookId == book.bookId }
                    if (index < 0) current else current.toMutableList().also { it[index] = book }
                }
            }
            Result.success(committed)
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    override suspend fun upsert(book: Book) = withContext(Dispatchers.IO) {
        database.upsert(book)
        _books.update { current ->
            val index = current.indexOfFirst { it.bookId == book.bookId }
            if (index == -1) {
                current + book
            } else {
                current.toMutableList().also { it[index] = book }
            }
        }
    }

    override suspend fun removeFromShelf(bookId: String) = withContext(Dispatchers.IO) {
        database.delete(bookId)
        _books.update { current -> current.filterNot { it.bookId == bookId } }
    }

    override suspend fun updateReadingPosition(
        bookId: String,
        position: ReadingPosition,
        lastOpenedAt: Long?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val rows = try {
            database.updateReadingPosition(
                bookId = bookId,
                readingPosition = position,
                updatedAt = now,
                lastOpenedAt = lastOpenedAt,
            )
        } catch (error: Throwable) {
            return@withContext Result.failure(error)
        }
        if (rows <= 0) {
            return@withContext Result.failure(NoSuchElementException("书籍不存在：$bookId"))
        }
        _books.update { current ->
            current.map { book ->
                if (book.bookId == bookId) {
                    book.copy(
                        readingPosition = position,
                        updatedAt = maxOf(book.updatedAt, now),
                        lastOpenedAt = lastOpenedAt ?: book.lastOpenedAt,
                    )
                } else {
                    book
                }
            }
        }
        Result.success(Unit)
    }
}
