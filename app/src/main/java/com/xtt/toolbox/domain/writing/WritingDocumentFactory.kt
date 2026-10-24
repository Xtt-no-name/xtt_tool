package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.data.model.ChapterDetectionSource
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookDocument
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.ExtractedResource
import java.util.UUID

/** 从纯文本/已有文档构造新作品文档；所有新作品 ID 重新生成。 */
object WritingDocumentFactory {
    data class CopyResult(
        val text: String,
        val document: BookDocument,
        val resources: List<ExtractedResource>,
    )

    fun plainText(book: Book, text: String, chapterTitle: String): WritingDocument {
        val chapterId = UUID.randomUUID().toString()
        val title = chapterTitle.trim()
        val blocks = if (text.isEmpty()) {
            emptyList()
        } else {
            listOf(
                BookBlock(
                    blockId = UUID.randomUUID().toString(),
                    order = 0,
                    type = BookBlockType.PARAGRAPH,
                    startOffset = 0,
                    endOffset = text.length,
                    spans = emptyList(),
                ),
            )
        }
        val chapter = Chapter(
            chapterId = chapterId,
            bookId = book.bookId,
            title = title,
            order = 0,
            startOffset = 0,
            endOffset = text.length,
            headingOffset = 0,
            detectionSource = ChapterDetectionSource.MANUAL,
            confidence = 1f,
        )
        return WritingDocument(
            book = book,
            text = text,
            document = BookDocument(
                chapters = listOf(chapter),
                blocks = blocks,
                resources = emptyList(),
            ),
        )
    }

    /**
     * 复制已有文档到新 bookId。章节/块/资源 ID 全部重生成，资源引用同步映射。
     * [resourceBytes] 以旧 resourceId 为 key；缺资源时返回失败，不创建半成品。
     */
    fun copy(
        book: Book,
        text: String,
        source: BookDocument?,
        resourceBytes: Map<String, ByteArray>,
    ): Result<CopyResult> {
        if (source == null) {
            val plain = plainText(book, text, "")
            return Result.success(
                CopyResult(
                    text = plain.text,
                    document = plain.document,
                    resources = emptyList(),
                ),
            )
        }
        val resourceIdMap = source.resources.associate { resource ->
            resource.resourceId to UUID.randomUUID().toString()
        }
        val resources = source.resources.map { resource ->
            val bytes = resourceBytes[resource.resourceId]
                ?: return Result.failure(
                    IllegalStateException("资源缺失：${resource.relativePath}"),
                )
            ExtractedResource(
                resource.copy(resourceId = resourceIdMap.getValue(resource.resourceId)),
                bytes,
            )
        }
        val chapters = source.chapters.map { chapter ->
            chapter.copy(
                chapterId = UUID.randomUUID().toString(),
                bookId = book.bookId,
                volumeId = null,
            )
        }
        val blocks = source.blocks.map { block ->
            block.copy(
                blockId = UUID.randomUUID().toString(),
                resourceId = block.resourceId?.let { resourceIdMap[it] },
            )
        }
        return Result.success(
            CopyResult(
                text = text,
                document = BookDocument(
                    chapters = chapters,
                    blocks = blocks,
                    resources = resources.map { it.resource },
                ),
                resources = resources,
            ),
        )
    }
}
