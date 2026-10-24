package com.xtt.toolbox.data.importing

import com.xtt.toolbox.data.content.BookContentStore
import com.xtt.toolbox.data.content.ContentHash
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.domain.chapter.ChapterParser
import com.xtt.toolbox.domain.chapter.RegexChapterParser
import com.xtt.toolbox.domain.chapter.TextOffsets
import com.xtt.toolbox.domain.importing.BookContentValidator
import com.xtt.toolbox.domain.importing.BookImportFormat
import com.xtt.toolbox.domain.importing.BookImportFormatGate
import com.xtt.toolbox.domain.importing.BookImportService
import com.xtt.toolbox.domain.importing.EncodingConfidence
import com.xtt.toolbox.domain.importing.TxtDecodingException
import com.xtt.toolbox.domain.importing.TxtDecoder
import com.xtt.toolbox.domain.importing.TxtDocumentSource
import com.xtt.toolbox.domain.importing.TxtImportException
import com.xtt.toolbox.domain.importing.TxtImportPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * TXT 导入编排：读取 → 严格解码 → 换行规范化 → 章节识别 → 预览；
 * 确认后再写入内部正文并提交元数据。
 *
 * 正文先落盘，元数据最后提交；元数据失败会删除本次正文。原始 URI 只记录为来源，
 * 后续阅读不依赖它。
 */
class TxtImportService(
    private val source: TxtDocumentSource,
    private val contentStore: BookContentStore,
    private val repository: BookRepository,
    private val decoder: TxtDecoder = TxtDecoder(),
    private val parser: ChapterParser = RegexChapterParser(),
) : BookImportService {
    override suspend fun prepare(
        uri: String,
        forcedEncoding: String?,
    ): TxtImportPreview = withContext(Dispatchers.IO) {
        val document = source.read(uri)
        BookImportFormatGate.require(document.displayName, BookImportFormat.TXT)
        BookContentValidator.requirePlainText(document.bytes)
        val decoded = try {
            decoder.decode(document.bytes, forcedEncoding)
        } catch (error: TxtDecodingException) {
            throw TxtImportException(
                message = error.message ?: "编码识别失败",
                availableEncodings = error.availableEncodings,
                cause = error,
            )
        }

        val text = TextOffsets.normalizeNewlines(decoded.text)
        if (text.isEmpty()) {
            throw TxtImportException("文件为空，没有可导入的文本")
        }
        val contentBytes = text.toByteArray(Charsets.UTF_8)
        val contentHash = ContentHash.sha256(contentBytes)
        val duplicate = repository.findByContentHash(contentHash)
        val parseResult = parser.parse(text)
        val warnings = buildList {
            addAll(decoded.warnings)
            addAll(parseResult.warnings)
            if (parseResult.candidates.isEmpty()) {
                add("未识别到章节标题，将按连续文本导入。")
            }
        }

        TxtImportPreview(
            sourceUri = uri,
            sourceName = document.displayName,
            defaultTitle = defaultTitle(document.displayName),
            originalSizeBytes = document.sizeBytes,
            encoding = decoded.charsetName,
            encodingConfidence = if (decoded.confidence == EncodingConfidence.HIGH) {
                EncodingConfidence.HIGH
            } else {
                EncodingConfidence.MEDIUM
            },
            hadBom = decoded.hadBom,
            text = text,
            chapters = parseResult.candidates,
            contentHash = contentHash,
            contentSizeBytes = contentBytes.size.toLong(),
            duplicateBook = duplicate,
            warnings = warnings,
            availableEncodings = decoder.supportedEncodings,
            format = BookFormat.TXT,
        )
    }

    /**
     * 保存正文并创建书籍。
     *
     * 重复导入由 UI 先提示；这里只负责“作为独立新书导入”，始终生成新 bookId。
     */
    override suspend fun commit(
        preview: TxtImportPreview,
        title: String,
    ): Result<Book> = withContext(Dispatchers.IO) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) {
            return@withContext Result.failure(TxtImportException("书名不能为空"))
        }

        val bookId = UUID.randomUUID().toString()
        val saved = try {
            contentStore.save(bookId, preview.text)
        } catch (error: Throwable) {
            return@withContext Result.failure(
                TxtImportException("正文保存失败，未创建书籍", cause = error),
            )
        }
        try {
            currentCoroutineContext().ensureActive()
        } catch (error: CancellationException) {
            runCatching { contentStore.delete(saved.location) }
            throw error
        }

        val now = System.currentTimeMillis()
        val book = Book(
            bookId = bookId,
            title = normalizedTitle,
            shelfType = ShelfType.READING,
            sourceType = SourceType.IMPORTED,
            sourceUri = preview.sourceUri,
            contentLocation = saved.location,
            sourceName = preview.sourceName,
            contentHash = saved.sha256,
            contentSize = saved.sizeBytes,
            sourceEncoding = preview.encoding,
            format = BookFormat.TXT,
            readingPosition = ReadingPosition(offset = 0, percent = 0f),
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = null,
        )

        val insertResult = try {
            repository.insert(book)
        } catch (error: CancellationException) {
            runCatching { contentStore.delete(saved.location) }
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }

        if (insertResult.isFailure) {
            runCatching { contentStore.delete(saved.location) }
            return@withContext Result.failure(
                insertResult.exceptionOrNull()
                    ?: TxtImportException("书籍元数据保存失败，未创建书架记录"),
            )
        }
        Result.success(book)
    }

    private fun defaultTitle(displayName: String): String {
        val withoutExtension = if (displayName.endsWith(".txt", ignoreCase = true)) {
            displayName.dropLast(4)
        } else {
            displayName
        }
        return withoutExtension.trim().ifBlank { "未命名" }
    }
}
