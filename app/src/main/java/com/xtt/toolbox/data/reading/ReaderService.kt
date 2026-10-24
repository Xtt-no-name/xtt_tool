package com.xtt.toolbox.data.reading

import com.xtt.toolbox.data.content.BookContentStore
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.domain.chapter.TextOffsets
import com.xtt.toolbox.domain.content.BookDocument
import com.xtt.toolbox.domain.reading.ReaderPositionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReaderBook(
    val book: Book,
    val text: String,
    val initialOffset: Int,
    val document: BookDocument? = null,
    val imagePaths: Map<String, String?> = emptyMap(),
)

class ReaderLoadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 阅读数据加载边界：按 bookId 读取元数据，再从应用内部正文存储读取规范化文本。
 * 不访问 sourceUri，也不直接拼接文件路径。
 */
class ReaderService(
    private val repository: BookRepository,
    private val contentStore: BookContentStore,
) {
    suspend fun load(bookId: String): Result<ReaderBook> = withContext(Dispatchers.IO) {
        val book = try {
            repository.getBook(bookId)
        } catch (error: Throwable) {
            return@withContext Result.failure(
                ReaderLoadException("读取书籍信息失败", error),
            )
        } ?: return@withContext Result.failure(
            ReaderLoadException("书籍记录不存在，可能已被移除"),
        )

        val location = book.contentLocation?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(
                ReaderLoadException("这本书没有可读取的内部正文"),
            )

        val rawText = try {
            contentStore.readText(location)
        } catch (error: Throwable) {
            return@withContext Result.failure(
                ReaderLoadException("正文读取失败，文件可能缺失或损坏", error),
            )
        }

        val text = TextOffsets.normalizeNewlines(rawText)
        val initialOffset = ReaderPositionPolicy.clampOffset(
            textLength = text.length,
            offset = book.readingPosition?.offset ?: 0,
        )
        val document = if (
            book.format == com.xtt.toolbox.data.model.BookFormat.EPUB ||
            book.format == com.xtt.toolbox.data.model.BookFormat.MARKDOWN ||
            book.format == com.xtt.toolbox.data.model.BookFormat.DRAFT
        ) {
            repository.loadDocument(bookId) ?: return@withContext Result.failure(
                ReaderLoadException("书籍内容索引缺失，无法打开这本书"),
            )
        } else {
            null
        }
        val imagePaths = document?.resources
            ?.associate { resource ->
                val bookDir = location.substringBeforeLast('/', "")
                val resourceLocation = if (bookDir.isBlank()) {
                    resource.relativePath
                } else {
                    "$bookDir/${resource.relativePath}"
                }
                resource.resourceId to contentStore.resolveResource(resourceLocation)?.absolutePath
            }
            .orEmpty()
        Result.success(
            ReaderBook(
                book = book,
                text = text,
                initialOffset = initialOffset,
                document = document,
                imagePaths = imagePaths,
            ),
        )
    }
}
