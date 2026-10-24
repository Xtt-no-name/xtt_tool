package com.xtt.toolbox.data.repository

import com.xtt.toolbox.data.content.BookContentStore
import com.xtt.toolbox.data.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 删除结果；[fileCleanupSucceeded] 为 false 时，书籍已从书架移除，资源等待启动孤儿清理重试。 */
data class BookDeletionResult(
    val book: Book?,
    val fileCleanupSucceeded: Boolean,
)

/**
 * 书籍删除编排。
 *
 * 顺序固定为：数据库事务删除书籍及级联注释/文档索引 -> 清理该书专属正文与资源目录。
 * 先删文件再删数据库会在数据库失败时留下无法阅读的可见书籍，因此不采用。
 * 如果文件清理失败，数据库已经不再引用该书，启动时的孤儿清理可以安全重试。
 */
class BookDeletionService(
    private val repository: BookRepository,
    private val contentStore: BookContentStore,
) {
    suspend fun delete(bookId: String): Result<BookDeletionResult> = withContext(Dispatchers.IO) {
        val book = try {
            repository.getBook(bookId)
        } catch (error: Throwable) {
            return@withContext Result.failure(error)
        }

        try {
            repository.removeFromShelf(bookId)
        } catch (error: Throwable) {
            return@withContext Result.failure(error)
        }

        val fileCleanupSucceeded = runCatching {
            contentStore.deleteBook(bookId)
        }.getOrDefault(false)

        Result.success(
            BookDeletionResult(
                book = book,
                fileCleanupSucceeded = fileCleanupSucceeded,
            ),
        )
    }
}
