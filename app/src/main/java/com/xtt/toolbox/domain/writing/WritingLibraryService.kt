package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.content.BookContentStore
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.domain.chapter.TextOffsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 阅读书架转入写作书架（同一身份）和创建独立可编辑副本。
 * 不修改 sourceUri 指向的原始文件，不复制批注/阅读位置。
 */
class WritingLibraryService(
    private val repository: BookRepository,
    private val contentStore: BookContentStore,
    private val documentService: WritingDocumentService,
) {
    suspend fun moveToWriting(bookId: String): Result<WritingDetail> = withContext(Dispatchers.IO) {
        val book = repository.getBook(bookId)
            ?: return@withContext Result.failure(IllegalStateException("书籍不存在"))
        val location = book.contentLocation
            ?: return@withContext Result.failure(IllegalStateException("书籍没有内部正文"))
        val text = TextOffsets.normalizeNewlines(contentStore.readText(location))
        val sourceDocument = repository.loadDocument(bookId)
        val writingDocument = if (sourceDocument == null) {
            WritingDocumentFactory.plainText(
                book.copy(shelfType = ShelfType.WRITING, format = BookFormat.DRAFT),
                text,
                "",
            )
        } else {
            WritingDocument(
                book = book.copy(shelfType = ShelfType.WRITING, format = BookFormat.DRAFT),
                text = text,
                document = sourceDocument,
            )
        }
        val saved = documentService.saveChecked(writingDocument)
        when (saved) {
            is WritingSaveResult.Saved -> Result.success(
                WritingDetail(saved.document, repository.loadVolumes(bookId)),
            )

            is WritingSaveResult.Conflict -> Result.failure(
                WritingDocumentConflictException(saved.message),
            )

            is WritingSaveResult.Failed -> Result.failure(
                saved.cause ?: IllegalStateException(saved.message),
            )
        }
    }

    suspend fun copyToWriting(bookId: String): Result<WritingDetail> = withContext(Dispatchers.IO) {
        val sourceBook = repository.getBook(bookId)
            ?: return@withContext Result.failure(IllegalStateException("来源书籍不存在"))
        val location = sourceBook.contentLocation
            ?: return@withContext Result.failure(IllegalStateException("来源书籍没有内部正文"))
        val text = TextOffsets.normalizeNewlines(contentStore.readText(location))
        val sourceDocument = repository.loadDocument(bookId)
        val sourceDir = location.substringBeforeLast('/', "")
        val resourceBytes = sourceDocument?.resources.orEmpty().associate { resource ->
            val resourceLocation = if (sourceDir.isBlank()) {
                resource.relativePath
            } else {
                "$sourceDir/${resource.relativePath}"
            }
            val bytes = contentStore.resolveResource(resourceLocation)?.readBytes()
                ?: return@withContext Result.failure(
                    IllegalStateException("来源资源缺失：${resource.relativePath}"),
                )
            resource.resourceId to bytes
        }
        val newBook = Book.create(
            title = sourceBook.title,
            author = sourceBook.author,
            language = sourceBook.language,
            shelfType = ShelfType.WRITING,
            sourceType = sourceBook.sourceType,
            sourceUri = sourceBook.sourceUri,
            sourceName = sourceBook.sourceName,
            sourceEncoding = sourceBook.sourceEncoding,
            format = BookFormat.DRAFT,
            originBookId = sourceBook.bookId,
            now = System.currentTimeMillis(),
        )
        val copy = WritingDocumentFactory.copy(
            book = newBook,
            text = text,
            source = sourceDocument,
            resourceBytes = resourceBytes,
        ).getOrElse { return@withContext Result.failure(it) }
        documentService.createFrom(
            book = newBook,
            text = copy.text,
            document = copy.document,
            resources = copy.resources,
        ).map { writing ->
            WritingDetail(writing, emptyList())
        }
    }
}
