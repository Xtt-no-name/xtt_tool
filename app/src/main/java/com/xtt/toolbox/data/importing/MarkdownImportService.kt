package com.xtt.toolbox.data.importing

import com.xtt.toolbox.data.content.BookContentStore
import com.xtt.toolbox.data.content.ContentHash
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.domain.content.BookDocument
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import com.xtt.toolbox.domain.content.ExtractedResource
import com.xtt.toolbox.domain.content.MarkdownImageReference
import com.xtt.toolbox.domain.content.MarkdownParser
import com.xtt.toolbox.domain.content.ParsedMarkdown
import com.xtt.toolbox.domain.importing.BookImportFormat
import com.xtt.toolbox.domain.importing.BookImportFormatGate
import com.xtt.toolbox.domain.importing.BookImportService
import com.xtt.toolbox.domain.importing.EncodingConfidence
import com.xtt.toolbox.domain.importing.MarkdownImageStats
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
import java.security.MessageDigest
import java.util.UUID

/**
 * Markdown 导入：严格 UTF-8/BOM 解码 -> MarkdownParser -> 统一 BookDocument/DB v4。
 *
 * 图片不在本轮复制：SAF 单文件授权不能可靠读取 Markdown 同目录图片，解析器会保留占位并记录告警，
 * 不访问网络、不把未经校验的路径交给本地文件系统。
 */
class MarkdownImportService(
    private val source: TxtDocumentSource,
    private val contentStore: BookContentStore,
    private val repository: BookRepository,
    private val resourceReader: MarkdownResourceReader,
    private val parser: MarkdownParser = MarkdownParser(),
    private val decoder: TxtDecoder = TxtDecoder(),
) : BookImportService {
    override suspend fun prepare(
        uri: String,
        forcedEncoding: String?,
    ): TxtImportPreview = prepareWithResources(uri, forcedEncoding, null)

    override suspend fun prepareWithResources(
        uri: String,
        forcedEncoding: String?,
        resourceDirectoryUri: String?,
    ): TxtImportPreview = withContext(Dispatchers.IO) {
        val document = source.read(uri)
        BookImportFormatGate.require(document.displayName, BookImportFormat.MARKDOWN)
        val decoded = try {
            decoder.decode(document.bytes, forcedEncoding = "UTF-8")
        } catch (error: TxtDecodingException) {
            throw TxtImportException("Markdown 需要 UTF-8 编码", cause = error)
        }
        val parsed = try {
            parser.parse(decoded.text, document.displayName)
        } catch (error: Throwable) {
            throw TxtImportException(error.message ?: "Markdown 解析失败", cause = error)
        }
        val contentHash = ContentHash.sha256(parsed.text.toByteArray(Charsets.UTF_8))
        val stats = imageStats(parsed.images, resourceDirectoryUri)
        val warnings = buildList {
            addAll(parsed.warnings)
            if (parsed.images.isNotEmpty()) {
                if (resourceDirectoryUri == null) {
                    add("发现 ${parsed.images.size} 个图片引用；未选择图片目录，阅读时将显示占位。")
                } else {
                    add(
                        "已授权图片目录：${stats.accessible}/${stats.total} 个图片可读取" +
                            if (stats.missing > 0) "，${stats.missing} 个缺失或无法读取。" else "。",
                    )
                }
            }
        }
        TxtImportPreview(
            sourceUri = uri,
            sourceName = document.displayName,
            defaultTitle = parsed.title,
            originalSizeBytes = document.sizeBytes,
            encoding = "Markdown",
            encodingConfidence = EncodingConfidence.HIGH,
            hadBom = decoded.hadBom,
            text = parsed.text,
            chapters = parsed.chapters,
            contentHash = contentHash,
            contentSizeBytes = parsed.text.toByteArray(Charsets.UTF_8).size.toLong(),
            duplicateBook = repository.findByContentHash(contentHash),
            warnings = warnings,
            availableEncodings = emptyList(),
            format = BookFormat.MARKDOWN,
            author = parsed.author,
            parsedMarkdown = parsed,
            resourceDirectoryUri = resourceDirectoryUri,
            markdownImageStats = if (parsed.images.isEmpty()) null else stats,
        )
    }

    override suspend fun commit(
        preview: TxtImportPreview,
        title: String,
    ): Result<Book> = withContext(Dispatchers.IO) {
        val parsed: ParsedMarkdown = preview.parsedMarkdown
            ?: return@withContext Result.failure(TxtImportException("Markdown 预览数据缺失"))
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) {
            return@withContext Result.failure(TxtImportException("书名不能为空"))
        }

        val bookId = UUID.randomUUID().toString()
        val imageResult = try {
            resolveImages(parsed.images, preview.resourceDirectoryUri)
        } catch (error: Throwable) {
            return@withContext Result.failure(
                TxtImportException("Markdown 图片处理失败，未创建书籍", cause = error),
            )
        }
        val saved = try {
            contentStore.saveDocument(bookId, parsed.text, imageResult.extracted)
        } catch (error: Throwable) {
            return@withContext Result.failure(
                TxtImportException("Markdown 正文或图片保存失败，未创建书籍", cause = error),
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
                chapterId = "md:$bookId:${draft.order}:${draft.startOffset}",
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
                val prefixedBlockId = "md:$bookId:${block.blockId}"
                block.copy(
                    blockId = prefixedBlockId,
                    resourceId = block.resourceId?.let(imageResult.byKey::get),
                    spans = block.spans.map { span ->
                        span.copy(
                            targetBlockId = span.targetBlockId?.let {
                                "md:$bookId:$it"
                            },
                        )
                    },
                    footnoteRefIds = block.footnoteRefIds.map { "md:$bookId:$it" },
                )
            },
            resources = imageResult.extracted.map { it.resource },
        )
        val now = System.currentTimeMillis()
        val book = Book(
            bookId = bookId,
            title = normalizedTitle,
            author = parsed.author,
            language = null,
            shelfType = ShelfType.READING,
            sourceType = SourceType.IMPORTED,
            sourceUri = preview.sourceUri,
            contentLocation = saved.location,
            sourceName = preview.sourceName,
            contentHash = saved.sha256,
            contentSize = saved.sizeBytes,
            sourceEncoding = "UTF-8",
            format = BookFormat.MARKDOWN,
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
                TxtImportException("Markdown 书籍记录保存失败，未创建书架条目", cause = result.exceptionOrNull()),
            )
        }
        Result.success(book)
    }

    private data class ImageResolveResult(
        val extracted: List<ExtractedResource>,
        val byKey: Map<String, String>,
    )

    private fun imageStats(
        images: List<MarkdownImageReference>,
        directoryUri: String?,
    ): MarkdownImageStats {
        if (directoryUri == null) {
            return MarkdownImageStats(total = images.size, accessible = 0, missing = images.size)
        }
        var accessible = 0
        val accessibleByPath = mutableMapOf<String, Boolean>()
        images.forEach { image ->
            val normalized = MarkdownPaths.normalize(image.path) ?: return@forEach
            val found = accessibleByPath.getOrPut(normalized) {
                resourceReader.probe(directoryUri, normalized) != null
            }
            if (found) accessible++
        }
        return MarkdownImageStats(
            total = images.size,
            accessible = accessible,
            missing = images.size - accessible,
        )
    }

    private fun resolveImages(
        images: List<MarkdownImageReference>,
        directoryUri: String?,
    ): ImageResolveResult {
        if (images.isEmpty() || directoryUri == null) {
            return ImageResolveResult(emptyList(), emptyMap())
        }
        val extracted = mutableListOf<ExtractedResource>()
        val byKey = mutableMapOf<String, String>()
        val byPath = mutableMapOf<String, BookResource>()
        var totalBytes = 0L
        images.forEach { image ->
            val normalized = MarkdownPaths.normalize(image.path) ?: return@forEach
            val resource = byPath[normalized] ?: run {
                val bytes = resourceReader.read(directoryUri, normalized) ?: return@forEach
                if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return@forEach
                if (totalBytes + bytes.size > MAX_TOTAL_IMAGE_BYTES) return@forEach
                val detected = detectImageType(bytes) ?: return@forEach
                val id = "md-res-${UUID.randomUUID()}"
                val resource = BookResource(
                    resourceId = id,
                    relativePath = "md/resources/$id.${detected.extension}",
                    mediaType = detected.mediaType,
                    sizeBytes = bytes.size.toLong(),
                    sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                        .joinToString("") { "%02x".format(it) },
                    kind = BookResourceKind.IMAGE,
                    alt = image.alt,
                )
                totalBytes += bytes.size
                byPath[normalized] = resource
                extracted += ExtractedResource(resource, bytes)
                resource
            }
            byKey[image.key] = resource.resourceId
        }
        return ImageResolveResult(extracted, byKey)
    }

    private fun detectImageType(bytes: ByteArray): ImageType? {
        if (bytes.size < 12) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() ->
                ImageType("image/jpeg", "jpg")

            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() ->
                ImageType("image/png", "png")

            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() ->
                ImageType("image/gif", "gif")

            bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) ->
                ImageType("image/webp", "webp")

            else -> null
        }
    }

    private data class ImageType(val mediaType: String, val extension: String)

    private companion object {
        const val MAX_IMAGE_BYTES = 30 * 1024 * 1024
        const val MAX_TOTAL_IMAGE_BYTES = 100 * 1024 * 1024
    }
}
