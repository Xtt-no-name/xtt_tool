package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.content.ContentHash
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import com.xtt.toolbox.domain.content.ExtractedResource
import com.xtt.toolbox.domain.content.ImageFormatDetector
import java.util.UUID

/** 自定义封面写入书籍专属内部资源，并随当前写作 revision 持久化。 */
class WritingCoverService(
    private val catalogService: WritingCatalogService,
    private val documentService: WritingDocumentService,
) {
    suspend fun setCover(bookId: String, bytes: ByteArray): Result<WritingDetail> {
        val detail = catalogService.load(bookId).getOrElse { return Result.failure(it) }
        val format = ImageFormatDetector.detect(bytes)
            ?: return Result.failure(IllegalArgumentException("不支持的封面图片格式"))
        val resourceId = UUID.randomUUID().toString()
        val resource = BookResource(
            resourceId = resourceId,
            relativePath = "cover/$resourceId.${format.extension}",
            mediaType = format.mediaType,
            sizeBytes = bytes.size.toLong(),
            sha256 = ContentHash.sha256(bytes),
            kind = BookResourceKind.COVER,
        )
        val updated = detail.document.copy(
            document = detail.document.document.copy(
                resources = detail.document.document.resources
                    .filterNot { it.kind == BookResourceKind.COVER } + resource,
            ),
        )
        val result = documentService.saveCheckedWithResources(
            document = updated,
            expectedContentHash = detail.document.book.contentHash,
            newResources = listOf(ExtractedResource(resource, bytes)),
        )
        return when (result) {
            is WritingSaveResult.Saved -> catalogService.load(bookId)
            is WritingSaveResult.Conflict -> Result.failure(
                WritingDocumentConflictException(result.message),
            )

            is WritingSaveResult.Failed -> Result.failure(
                result.cause ?: IllegalStateException(result.message),
            )
        }
    }
}
