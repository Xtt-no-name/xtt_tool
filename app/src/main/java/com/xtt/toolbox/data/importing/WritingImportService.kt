package com.xtt.toolbox.data.importing

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.domain.content.BookDocument
import com.xtt.toolbox.domain.importing.BookImportFormat
import com.xtt.toolbox.domain.importing.BookImportFormatGate
import com.xtt.toolbox.domain.writing.WritingDetail
import com.xtt.toolbox.domain.writing.WritingDocumentFactory
import com.xtt.toolbox.domain.writing.WritingDocumentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 外部文件导入为内部写作作品。
 *
 * 只复用已有可靠解析器：TXT 走 TxtImportService，EPUB 走 EpubImportService；
 * 原始文件只读，新作品使用新 bookId 和 DRAFT 格式。EPUB 会保留现有解析器支持的
 * 章节、基础文本和图片，但不会保真复刻原始 CSS/字体/固定版式。
 */
class WritingImportService(
    private val resolver: ContentResolver,
    private val txtImportService: TxtImportService,
    private val epubImportService: EpubImportService,
    private val repository: BookRepository,
    private val documentService: WritingDocumentService,
) {
    suspend fun import(uri: String): Result<WritingDetail> = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
        val format = BookImportFormatGate.fromFileName(displayName)
            ?: return@withContext Result.failure(
                IllegalArgumentException("仅支持导入 .txt 或 .epub 文件"),
            )
        when (format) {
            BookImportFormat.TXT -> importTxt(uri)
            BookImportFormat.EPUB -> importEpub(uri)
            BookImportFormat.MARKDOWN -> Result.failure(
                IllegalArgumentException("写作书架不导入 Markdown 源文件"),
            )
        }
    }

    private suspend fun importTxt(uri: String): Result<WritingDetail> {
        val preview = txtImportService.prepare(uri, null)
        val now = System.currentTimeMillis()
        val book = Book.create(
            title = preview.defaultTitle,
            shelfType = ShelfType.WRITING,
            sourceType = SourceType.IMPORTED,
            sourceUri = preview.sourceUri,
            sourceName = preview.sourceName,
            sourceEncoding = preview.encoding,
            format = BookFormat.DRAFT,
            now = now,
        )
        val document = WritingDocumentFactory.plainText(
            book,
            preview.text,
            preview.chapters.firstOrNull()?.title.orEmpty(),
        )
        return documentService.createFrom(
            book = book,
            text = document.text,
            document = document.document,
            resources = emptyList(),
        ).map { WritingDetail(it, emptyList()) }
    }

    private suspend fun importEpub(uri: String): Result<WritingDetail> {
        val preview = epubImportService.prepare(uri, null)
        val parsed = preview.parsedEpub
            ?: return Result.failure(IllegalStateException("EPUB 预览数据缺失"))
        val book = Book.create(
            title = preview.defaultTitle,
            author = preview.author,
            shelfType = ShelfType.WRITING,
            sourceType = SourceType.IMPORTED,
            sourceUri = preview.sourceUri,
            sourceName = preview.sourceName,
            sourceEncoding = preview.encoding,
            format = BookFormat.DRAFT,
            now = System.currentTimeMillis(),
        )
        val sourceDocument = BookDocument(
            chapters = parsed.chapters.map { draft ->
                Chapter(
                    chapterId = UUID.randomUUID().toString(),
                    bookId = book.bookId,
                    title = draft.title,
                    order = draft.order,
                    startOffset = draft.startOffset,
                    endOffset = draft.endOffset,
                    headingOffset = draft.headingOffset,
                    detectionSource = draft.detectionSource,
                    confidence = draft.confidence,
                    level = draft.level,
                )
            },
            blocks = parsed.blocks,
            resources = parsed.resources.map { it.resource },
        )
        val bytes = parsed.resources.associate { it.resource.resourceId to it.bytes }
        val copy = WritingDocumentFactory.copy(
            book = book,
            text = parsed.text,
            source = sourceDocument,
            resourceBytes = bytes,
        ).getOrElse { return Result.failure(it) }
        return documentService.createFrom(
            book = book,
            text = copy.text,
            document = copy.document,
            resources = copy.resources,
        ).map { WritingDetail(it, emptyList()) }
    }

    private fun queryDisplayName(uri: String): String? = runCatching {
        resolver.query(
            Uri.parse(uri),
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: Uri.parse(uri).lastPathSegment?.substringAfterLast('/')
}
