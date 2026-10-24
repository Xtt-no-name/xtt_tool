package com.xtt.toolbox.data.repository

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookVolume
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.domain.content.BookDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 单元测试和早期界面使用的内存实现。
 *
 * 它不写入数据库、不持久化；真实应用路径使用 [SqliteBookRepository]。
 */
class InMemoryBookRepository(
    initialBooks: List<Book> = emptyList(),
) : BookRepository {
    private val _books = MutableStateFlow(initialBooks)
    override val books: StateFlow<List<Book>> = _books.asStateFlow()
    private val documents = mutableMapOf<String, BookDocument>()
    private val volumes = mutableMapOf<String, MutableList<BookVolume>>()

    override suspend fun reload() = Unit

    override suspend fun getBook(bookId: String): Book? =
        _books.value.firstOrNull { it.bookId == bookId }

    override suspend fun findByContentHash(contentHash: String): Book? =
        _books.value.firstOrNull { it.contentHash == contentHash }

    override suspend fun insert(book: Book): Result<Unit> =
        if (_books.value.any { it.bookId == book.bookId }) {
            Result.failure(IllegalStateException("bookId 已存在"))
        } else {
            _books.update { it + book }
            Result.success(Unit)
        }

    override suspend fun insertDocument(book: Book, document: BookDocument): Result<Unit> {
        if (_books.value.any { it.bookId == book.bookId }) {
            return Result.failure(IllegalStateException("bookId 已存在"))
        }
        _books.update { it + book }
        documents[book.bookId] = document
        return Result.success(Unit)
    }

    override suspend fun loadDocument(bookId: String): BookDocument? = documents[bookId]

    override suspend fun loadVolumes(bookId: String): List<BookVolume> =
        volumes[bookId].orEmpty().sortedBy { it.order }

    override suspend fun insertVolume(volume: BookVolume): Result<Unit> {
        if (_books.value.none { it.bookId == volume.bookId }) {
            return Result.failure(IllegalStateException("书籍不存在"))
        }
        volumes.getOrPut(volume.bookId) { mutableListOf() }.add(volume)
        return Result.success(Unit)
    }

    override suspend fun renameVolume(volumeId: String, title: String): Result<Unit> {
        val list = volumes.values.firstOrNull { list -> list.any { it.volumeId == volumeId } }
            ?: return Result.failure(IllegalStateException("分卷不存在"))
        val index = list.indexOfFirst { it.volumeId == volumeId }
        list[index] = list[index].copy(title = title)
        return Result.success(Unit)
    }

    override suspend fun deleteVolume(volumeId: String): Result<Unit> {
        val list = volumes.values.firstOrNull { list -> list.any { it.volumeId == volumeId } }
            ?: return Result.failure(IllegalStateException("分卷不存在"))
        list.removeAll { it.volumeId == volumeId }
        return Result.success(Unit)
    }

    override suspend fun replaceDocument(
        bookId: String,
        document: BookDocument,
    ): Result<Unit> {
        if (_books.value.none { it.bookId == bookId }) {
            return Result.failure(IllegalStateException("书籍不存在"))
        }
        documents[bookId] = document
        return Result.success(Unit)
    }

    override suspend fun commitDocument(
        book: Book,
        document: BookDocument,
    ): Result<Unit> {
        if (_books.value.none { it.bookId == book.bookId }) {
            return Result.failure(IllegalStateException("书籍不存在"))
        }
        _books.update { current ->
            current.map { if (it.bookId == book.bookId) book else it }
        }
        documents[book.bookId] = document
        return Result.success(Unit)
    }

    override suspend fun commitDocumentIfUnchanged(
        book: Book,
        document: BookDocument,
        expectedContentHash: String?,
    ): Result<Boolean> {
        val current = _books.value.firstOrNull { it.bookId == book.bookId }
            ?: return Result.success(false)
        if (current.contentHash != expectedContentHash) return Result.success(false)
        _books.update { books ->
            books.map { if (it.bookId == book.bookId) book else it }
        }
        documents[book.bookId] = document
        return Result.success(true)
    }

    override suspend fun upsert(book: Book) {
        _books.update { current ->
            val index = current.indexOfFirst { it.bookId == book.bookId }
            if (index == -1) {
                current + book
            } else {
                current.toMutableList().also { it[index] = book }
            }
        }
    }

    override suspend fun removeFromShelf(bookId: String) {
        _books.update { current -> current.filterNot { it.bookId == bookId } }
    }

    override suspend fun updateReadingPosition(
        bookId: String,
        position: ReadingPosition,
        lastOpenedAt: Long?,
    ): Result<Unit> {
        if (_books.value.none { it.bookId == bookId }) {
            return Result.failure(NoSuchElementException("书籍不存在：$bookId"))
        }
        val now = System.currentTimeMillis()
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
        return Result.success(Unit)
    }
}
