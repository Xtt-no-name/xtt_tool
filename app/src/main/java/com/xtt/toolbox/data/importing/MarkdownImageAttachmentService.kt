package com.xtt.toolbox.data.importing

import com.xtt.toolbox.data.content.BookContentStore
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import com.xtt.toolbox.domain.content.ExtractedResource
import com.xtt.toolbox.domain.content.ImageFormatDetector
import com.xtt.toolbox.domain.content.MarkdownImagePathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

data class MarkdownImageAttachmentResult(
    val referenced: Int,
    val imported: Int,
    val missing: Int,
    val unsupported: Int,
)

/**
 * 将用户选择的 SAF 目录中的 Markdown 相对图片复制进书籍专属目录，并替换文档图片索引。
 * 失败图片只保留占位，不影响正文；数据库失败会清理本次新写入的文件。
 */
class MarkdownImageAttachmentService(
    private val repository: BookRepository,
    private val contentStore: BookContentStore,
    private val resourceReader: MarkdownResourceReader,
    private val directoryStore: MarkdownImageDirectoryStore,
) {
    suspend fun attach(
        bookId: String,
        directoryUri: String,
    ): Result<MarkdownImageAttachmentResult> = withContext(Dispatchers.IO) {
        val book = repository.getBook(bookId)
            ?: return@withContext Result.failure(IllegalStateException("书籍不存在"))
        if (book.format != BookFormat.MARKDOWN) {
            return@withContext Result.failure(IllegalStateException("只有 Markdown 书籍支持图片目录"))
        }
        val document = repository.loadDocument(bookId)
            ?: return@withContext Result.failure(IllegalStateException("Markdown 内容索引缺失"))

        val imageBlocks = document.blocks.filter { block ->
            block.type == BookBlockType.IMAGE &&
                !block.style?.imageSourcePath.isNullOrBlank()
        }
        val sourcePaths = imageBlocks.mapNotNull { it.style?.imageSourcePath }.distinct()
        if (sourcePaths.isEmpty()) {
            return@withContext Result.success(
                MarkdownImageAttachmentResult(0, 0, 0, 0),
            )
        }

        val base = SafMarkdownImageBaseResolver.baseDirectory(directoryUri, book.sourceUri)
        val resourcesByPath = mutableMapOf<String, BookResource>()
        val newExtracted = mutableListOf<ExtractedResource>()
        var imported = 0
        var missing = 0
        var unsupported = 0

        sourcePaths.forEach { sourcePath ->
            val resolved = MarkdownImagePathResolver.resolve(base, sourcePath)
            if (resolved == null) {
                missing++
                return@forEach
            }
            val bytes = resourceReader.read(directoryUri, resolved)
            if (bytes == null || bytes.isEmpty()) {
                missing++
                return@forEach
            }
            val format = ImageFormatDetector.detect(bytes)
            if (format == null) {
                unsupported++
                return@forEach
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val existing = imageBlocks
                .filter { it.style?.imageSourcePath == sourcePath }
                .mapNotNull { block ->
                    document.resources.firstOrNull { it.resourceId == block.resourceId }
                }
                .firstOrNull { resource ->
                    resource.sha256 == hash &&
                        contentStore.resolveBookResource(bookId, resource.relativePath)?.isFile == true
                }
            val resource = existing ?: run {
                val id = "md-res-${UUID.randomUUID()}"
                val newResource = BookResource(
                    resourceId = id,
                    relativePath = "md/resources/$id.${format.extension}",
                    mediaType = format.mediaType,
                    sizeBytes = bytes.size.toLong(),
                    sha256 = hash,
                    kind = BookResourceKind.IMAGE,
                )
                if (!contentStore.saveResource(bookId, newResource.relativePath, bytes)) {
                    missing++
                    return@forEach
                }
                newExtracted += ExtractedResource(newResource, bytes)
                imported++
                newResource
            }
            resourcesByPath[sourcePath] = resource
        }

        val newBlocks = document.blocks.map { block ->
            if (block.type != BookBlockType.IMAGE) return@map block
            val sourcePath = block.style?.imageSourcePath ?: return@map block
            block.copy(resourceId = resourcesByPath[sourcePath]?.resourceId)
        }
        val usedIds = newBlocks.mapNotNull { it.resourceId }.toSet()
        val oldResources = document.resources
        val keptOld = oldResources.filter { it.resourceId in usedIds }
        val newResources = resourcesByPath.values.distinctBy { it.resourceId }
            .filter { resource -> oldResources.none { it.resourceId == resource.resourceId } }
        val updatedResources = (keptOld + newResources).distinctBy { it.resourceId }
        val updatedDocument = document.copy(blocks = newBlocks, resources = updatedResources)

        val replace = repository.replaceDocument(bookId, updatedDocument)
        if (replace.isFailure) {
            newExtracted.forEach { extracted ->
                contentStore.deleteResource(bookId, extracted.resource.relativePath)
            }
            return@withContext Result.failure(
                replace.exceptionOrNull() ?: IllegalStateException("图片索引保存失败"),
            )
        }
        oldResources.asSequence()
            .filter { it.resourceId !in usedIds }
            .forEach { contentStore.deleteResource(bookId, it.relativePath) }
        directoryStore.put(bookId, directoryUri)
        Result.success(
            MarkdownImageAttachmentResult(
                referenced = sourcePaths.size,
                imported = imported,
                missing = missing,
                unsupported = unsupported,
            ),
        )
    }
}
