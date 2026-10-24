package com.xtt.toolbox.data.importing

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.xtt.toolbox.data.content.BookContentStore
import com.xtt.toolbox.data.content.ContentHash
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.domain.chapter.ChapterCandidate
import com.xtt.toolbox.domain.content.BookDocument
import com.xtt.toolbox.domain.content.EpubParseException
import com.xtt.toolbox.domain.content.EpubParser
import com.xtt.toolbox.domain.content.ParsedEpub
import com.xtt.toolbox.domain.importing.BookImportFormat
import com.xtt.toolbox.domain.importing.BookImportFormatGate
import com.xtt.toolbox.domain.importing.BookImportService
import com.xtt.toolbox.domain.importing.EncodingConfidence
import com.xtt.toolbox.domain.importing.TxtImportException
import com.xtt.toolbox.domain.importing.TxtImportPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * EPUB 导入编排：SAF URI -> 有界临时副本 -> EPUB 解析 -> 预览 -> 内部资源/正文落盘 ->
 * SQLite v3 书籍/章节/块/资源事务。
 */
class EpubImportService(
    private val resolver: ContentResolver,
    private val cacheDir: File,
    private val contentStore: BookContentStore,
    private val repository: BookRepository,
    private val parser: EpubParser = EpubParser(),
) : BookImportService {
    override suspend fun prepare(
        uri: String,
        forcedEncoding: String?,
    ): TxtImportPreview = withContext(Dispatchers.IO) {
        val document = readSource(uri)
        BookImportFormatGate.require(document.displayName, BookImportFormat.EPUB)
        val temp = File(cacheDir, ".epub-import-${UUID.randomUUID()}")
        try {
            copyBounded(document.uri, temp)
            val parsed = try {
                parser.parse(temp, document.displayName)
            } catch (error: EpubParseException) {
                throw TxtImportException(error.message ?: "EPUB 文件结构无效", cause = error)
            } catch (error: Throwable) {
                throw TxtImportException("EPUB 文件损坏或格式不支持", cause = error)
            }
            val contentHash = ContentHash.sha256(parsed.text.toByteArray(Charsets.UTF_8))
            val duplicate = repository.findByContentHash(contentHash)
            TxtImportPreview(
                sourceUri = uri,
                sourceName = document.displayName,
                defaultTitle = parsed.title,
                originalSizeBytes = document.sizeBytes,
                encoding = "EPUB",
                encodingConfidence = EncodingConfidence.HIGH,
                hadBom = false,
                text = parsed.text,
                chapters = parsed.chapters.map { draft ->
                    ChapterCandidate(
                        title = draft.title,
                        order = draft.order,
                        startOffset = draft.startOffset,
                        endOffset = draft.endOffset,
                        headingOffset = draft.headingOffset,
                        detectionSource = draft.detectionSource,
                        confidence = draft.confidence,
                    )
                },
                contentHash = contentHash,
                contentSizeBytes = parsed.text.toByteArray(Charsets.UTF_8).size.toLong(),
                duplicateBook = duplicate,
                warnings = parsed.warnings + buildList {
                    add("EPUB 已保留章节图片；复杂 CSS、字体和固定版式不会完全复刻。")
                    if (parsed.resources.any { it.resource.kind.name == "IMAGE" }) {
                        add("图片已保存为应用内部资源，阅读时按屏幕宽度缩放。")
                    }
                },
                availableEncodings = emptyList(),
                format = BookFormat.EPUB,
                author = parsed.author,
                parsedEpub = parsed,
            )
        } finally {
            temp.delete()
        }
    }

    override suspend fun commit(
        preview: TxtImportPreview,
        title: String,
    ): Result<Book> = withContext(Dispatchers.IO) {
        val parsed: ParsedEpub = preview.parsedEpub
            ?: return@withContext Result.failure(TxtImportException("EPUB 预览数据缺失"))
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) {
            return@withContext Result.failure(TxtImportException("书名不能为空"))
        }

        val bookId = UUID.randomUUID().toString()
        val saved = try {
            contentStore.saveDocument(bookId, parsed.text, parsed.resources)
        } catch (error: Throwable) {
            return@withContext Result.failure(
                TxtImportException("EPUB 正文或图片保存失败，未创建书籍", cause = error),
            )
        }

        try {
            currentCoroutineContext().ensureActive()
        } catch (error: CancellationException) {
            runCatching { contentStore.deleteBook(bookId) }
            throw error
        }

        val chapters = parsed.chapters.map { draft ->
            Chapter(
                chapterId = "epub:$bookId:${draft.order}:${draft.startOffset}",
                bookId = bookId,
                title = draft.title,
                order = draft.order,
                startOffset = draft.startOffset,
                endOffset = draft.endOffset,
                headingOffset = draft.headingOffset,
                detectionSource = draft.detectionSource,
                confidence = draft.confidence,
                level = draft.level,
            )
        }
        val document = BookDocument(
            chapters = chapters,
            blocks = parsed.blocks.map { block ->
                block.copy(blockId = "epub:$bookId:${block.blockId}")
            },
            resources = parsed.resources.map { it.resource },
        )
        val now = System.currentTimeMillis()
        val book = Book(
            bookId = bookId,
            title = normalizedTitle,
            author = parsed.author,
            language = parsed.language,
            shelfType = ShelfType.READING,
            sourceType = SourceType.IMPORTED,
            sourceUri = preview.sourceUri,
            contentLocation = saved.location,
            sourceName = preview.sourceName,
            contentHash = saved.sha256,
            contentSize = saved.sizeBytes,
            sourceEncoding = "EPUB",
            format = BookFormat.EPUB,
            readingPosition = ReadingPosition(offset = 0, percent = 0f),
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = null,
        )

        val result = try {
            repository.insertDocument(book, document)
        } catch (error: CancellationException) {
            runCatching { contentStore.deleteBook(bookId) }
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
        if (result.isFailure) {
            runCatching { contentStore.deleteBook(bookId) }
            return@withContext Result.failure(
                TxtImportException(
                    "EPUB 书籍记录保存失败，未创建书架条目",
                    cause = result.exceptionOrNull(),
                ),
            )
        }
        Result.success(book)
    }

    private data class SourceDocument(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
    )

    private fun readSource(uri: String): SourceDocument {
        val parsedUri = Uri.parse(uri)
        var displayName: String? = null
        var size = 0L
        resolver.query(
            parsedUri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                displayName = cursor.getString(0)
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
        return SourceDocument(
            uri = parsedUri,
            displayName = displayName?.takeIf { it.isNotBlank() }
                ?: parsedUri.lastPathSegment?.substringAfterLast('/')
                ?: "未命名",
            sizeBytes = size,
        )
    }

    private fun copyBounded(uri: Uri, target: File) {
        val input = resolver.openInputStream(uri)
            ?: throw TxtImportException("无法读取 EPUB 文件")
        input.use { stream ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_EPUB_FILE_BYTES) {
                        throw TxtImportException("EPUB 文件过大，最大支持 100 MiB")
                    }
                    output.write(buffer, 0, read)
                }
                output.flush()
                output.fd.sync()
            }
        }
        if (!target.isFile || target.length() <= 0) {
            throw TxtImportException("EPUB 文件为空或读取失败")
        }
    }

    private companion object {
        const val MAX_EPUB_FILE_BYTES = 100L * 1024 * 1024
    }
}
