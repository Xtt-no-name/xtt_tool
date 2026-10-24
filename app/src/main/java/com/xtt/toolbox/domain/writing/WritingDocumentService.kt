package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.content.BookContentStore
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.domain.chapter.TextOffsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 写作作品文档服务。
 *
 * 保存协议：
 * 1. 在 `books/<bookId>/revisions/<revisionId>` 写完整正文和资源；
 * 2. 数据库事务同时更新 books 与章节/块/资源；
 * 3. 成功后才清理旧 revision。
 * 数据库失败时删除新 revision，旧正文仍由旧 contentLocation 指向；因此单次保存失败
 * 不会破坏上一个有效版本。
 */
class WritingDocumentService(
    private val repository: BookRepository,
    private val contentStore: BookContentStore,
) {
    private val saveMutex = Mutex()

    suspend fun createBlank(title: String): Result<WritingDocument> = withContext(Dispatchers.IO) {
        var revisionId: String? = null
        var createdBookId: String? = null
        try {
            val now = System.currentTimeMillis()
            val provisionalBook = Book.create(
                title = title,
                shelfType = ShelfType.WRITING,
                sourceType = SourceType.CREATED,
                format = BookFormat.DRAFT,
                now = now,
            )
            createdBookId = provisionalBook.bookId
            val revision = UUID.randomUUID().toString()
            revisionId = revision
            val blank = WritingDocumentEditor.blank(provisionalBook)
            val saved = contentStore.writeDocumentRevision(
                bookId = provisionalBook.bookId,
                revisionId = revision,
                text = blank.text,
                resources = emptyList(),
            )
            val book = provisionalBook.copy(
                contentLocation = saved.location,
                contentSize = saved.sizeBytes,
                contentHash = saved.sha256,
            )
            val document = blank.copy(book = book)
            repository.insertDocument(book, document.document).fold(
                onSuccess = { Result.success(document) },
                onFailure = { error ->
                    contentStore.deleteRevision(book.bookId, revision)
                    Result.failure(error)
                },
            )
        } catch (error: Throwable) {
            revisionId?.let { revision ->
                createdBookId?.let { bookId ->
                    runCatching { contentStore.deleteRevision(bookId, revision) }
                }
            }
            Result.failure(error)
        }
    }

    suspend fun load(bookId: String): Result<WritingDocument> = withContext(Dispatchers.IO) {
        try {
            val book = repository.getBook(bookId)
                ?: return@withContext Result.failure(IllegalStateException("书籍不存在"))
            if (book.shelfType != ShelfType.WRITING) {
                return@withContext Result.failure(IllegalStateException("不是写作作品"))
            }
            val location = book.contentLocation?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(IllegalStateException("作品没有正文"))
            val text = TextOffsets.normalizeNewlines(contentStore.readText(location))
            val document = repository.loadDocument(bookId)
                ?: return@withContext Result.failure(IllegalStateException("作品文档索引缺失"))
            Result.success(
                WritingDocumentEditor.validate(
                    WritingDocument(book = book, text = text, document = document),
                ),
            )
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /**
     * 用已构造好的文档和资源创建一本新 writing 书籍。
     * 只用于导入/副本等“新身份”场景；已有书籍必须走 saveChecked。
     */
    suspend fun createFrom(
        book: Book,
        text: String,
        document: com.xtt.toolbox.domain.content.BookDocument,
        resources: List<com.xtt.toolbox.domain.content.ExtractedResource>,
    ): Result<WritingDocument> = withContext(Dispatchers.IO) {
        var revisionId: String? = null
        try {
            require(book.shelfType == ShelfType.WRITING) { "新作品必须属于写作书架" }
            require(book.format == BookFormat.DRAFT) { "新作品必须是 DRAFT" }
            val revision = UUID.randomUUID().toString()
            revisionId = revision
            val saved = contentStore.writeDocumentRevision(
                bookId = book.bookId,
                revisionId = revision,
                text = text,
                resources = resources,
            )
            val savedBook = book.copy(
                contentLocation = saved.location,
                contentSize = saved.sizeBytes,
                contentHash = saved.sha256,
            )
            val writing = WritingDocumentEditor.validate(
                WritingDocument(book = savedBook, text = text, document = document),
            )
            repository.insertDocument(savedBook, document).fold(
                onSuccess = { Result.success(writing) },
                onFailure = { error ->
                    contentStore.deleteRevision(book.bookId, revision)
                    Result.failure(error)
                },
            )
        } catch (error: Throwable) {
            revisionId?.let { contentStore.deleteRevision(book.bookId, it) }
            Result.failure(error)
        }
    }

    /**
     * 兼容既有调用方的 Result 包装；版本冲突会返回带 [WritingDocumentConflictException] 的 failure。
     * 新调用方应优先使用 [saveChecked] 以区分 Conflict/Failed。
     */
    suspend fun save(document: WritingDocument): Result<WritingDocument> {
        return when (val result = saveChecked(document)) {
            is WritingSaveResult.Saved -> Result.success(result.document)
            is WritingSaveResult.Conflict -> Result.failure(
                WritingDocumentConflictException(result.message),
            )

            is WritingSaveResult.Failed -> Result.failure(
                result.cause ?: IllegalStateException(result.message),
            )
        }
    }

    /**
     * 带预期内容哈希的保存。写入新 revision 后，数据库只在当前 contentHash 仍等于
     * [expectedContentHash] 时提交；否则新 revision 被删除并返回 Conflict。
     */
    suspend fun saveChecked(
        document: WritingDocument,
        expectedContentHash: String? = document.book.contentHash,
    ): WritingSaveResult = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            saveLocked(document, expectedContentHash, emptyList())
        }
    }

    /**
     * 保存快照，并把尚未存在于旧 revision 的新资源（例如刚选中的封面）一并写入。
     * [document] 必须已包含这些资源的 metadata，且 revision 写入会校验引用。
     */
    suspend fun saveCheckedWithResources(
        document: WritingDocument,
        expectedContentHash: String? = document.book.contentHash,
        newResources: List<com.xtt.toolbox.domain.content.ExtractedResource>,
    ): WritingSaveResult = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            saveLocked(document, expectedContentHash, newResources)
        }
    }

    private suspend fun saveLocked(
        document: WritingDocument,
        expectedContentHash: String?,
        newResources: List<com.xtt.toolbox.domain.content.ExtractedResource>,
    ): WritingSaveResult {
        var revisionId: String? = null
        var committed = false
        try {
            WritingDocumentEditor.validate(document)
            val oldBook = document.book
            val currentBook = repository.getBook(oldBook.bookId)
                ?: return WritingSaveResult.Failed("书籍不存在")
            if (currentBook.contentHash != expectedContentHash) {
                return WritingSaveResult.Conflict(
                    expectedContentHash = expectedContentHash,
                    currentContentHash = currentBook.contentHash,
                )
            }
            val oldDocument = document.document
            val oldLocation = oldBook.contentLocation?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("作品没有旧正文")
            val oldDirectory = oldLocation.substringBeforeLast('/', "")
            val revision = UUID.randomUUID().toString()
            revisionId = revision
            val oldResourcesById = oldDocument.resources.associateBy { it.resourceId }
            val newResourcesById = newResources.associateBy { it.resource.resourceId }
            val resourcesToWrite = document.document.resources.map { resource ->
                newResourcesById[resource.resourceId]?.let { return@map it }
                val oldResource = oldResourcesById[resource.resourceId]
                    ?: throw IllegalStateException("资源不存在：${resource.relativePath}")
                val resourceLocation = if (oldDirectory.isBlank()) {
                    oldResource.relativePath
                } else {
                    "$oldDirectory/${oldResource.relativePath}"
                }
                val bytes = contentStore.resolveResource(resourceLocation)?.readBytes()
                    ?: throw IllegalStateException(
                        "资源不存在：${oldResource.relativePath}",
                    )
                com.xtt.toolbox.domain.content.ExtractedResource(resource, bytes)
            }.toMutableList()
            val saved = contentStore.writeDocumentRevision(
                bookId = oldBook.bookId,
                revisionId = revision,
                text = document.text,
                resources = resourcesToWrite,
            )
            val updatedBook = oldBook.copy(
                contentLocation = saved.location,
                contentSize = saved.sizeBytes,
                contentHash = saved.sha256,
                updatedAt = maxOf(System.currentTimeMillis(), oldBook.updatedAt),
            )
            val updatedDocument = document.copy(book = updatedBook)
            val commit = repository.commitDocumentIfUnchanged(
                book = updatedBook,
                document = updatedDocument.document,
                expectedContentHash = expectedContentHash,
            )
            if (commit.isFailure) {
                contentStore.deleteRevision(oldBook.bookId, revision)
                return WritingSaveResult.Failed(
                    message = commit.exceptionOrNull()?.message ?: "文档保存失败",
                    cause = commit.exceptionOrNull(),
                )
            }
            if (commit.getOrDefault(false).not()) {
                contentStore.deleteRevision(oldBook.bookId, revision)
                return WritingSaveResult.Conflict(
                    expectedContentHash = expectedContentHash,
                    currentContentHash = repository.getBook(oldBook.bookId)?.contentHash,
                )
            }
            committed = true
            runCatching { cleanupOldRevision(oldBook, oldDocument) }
            return WritingSaveResult.Saved(updatedDocument)
        } catch (error: Throwable) {
            if (!committed && revisionId != null) {
                contentStore.deleteRevision(document.book.bookId, revisionId)
            }
            return WritingSaveResult.Failed(
                message = error.message ?: "文档保存失败",
                cause = error,
            )
        }
    }

    private fun cleanupOldRevision(oldBook: Book, oldDocument: com.xtt.toolbox.domain.content.BookDocument) {
        val oldLocation = oldBook.contentLocation ?: return
        val prefix = "books/${oldBook.bookId}/revisions/"
        if (oldLocation.startsWith(prefix)) {
            val revisionId = oldLocation.removePrefix(prefix).substringBefore('/')
            if (revisionId.isNotBlank()) {
                contentStore.deleteRevision(oldBook.bookId, revisionId)
            }
        } else {
            contentStore.delete(oldLocation)
            oldDocument.resources.forEach { resource ->
                contentStore.deleteResource(oldBook.bookId, resource.relativePath)
            }
        }
    }
}
