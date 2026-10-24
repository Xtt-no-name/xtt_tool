package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.data.model.ChapterDetectionSource
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookDocument
import java.util.UUID

/**
 * 纯领域章节编辑。所有操作都从完整快照重建正文范围，不修改原 BookDocument。
 * 每个章节的标题使用一个 HEADING 块表示；章节无内容时仍保留可重命名的标题块。
 */
object WritingDocumentEditor {
    fun validate(document: WritingDocument): WritingDocument {
        val book = document.book
        require(book.shelfType == ShelfType.WRITING) { "只有写作作品可以编辑" }
        val chapters = document.document.chapters
        require(chapters.isNotEmpty()) { "至少需要一个章节" }
        require(chapters.map { it.chapterId }.distinct().size == chapters.size) { "章节 ID 不能重复" }
        require(chapters.map { it.order } == chapters.indices.toList()) { "章节顺序必须连续" }
        require(chapters.zipWithNext().all { (a, b) ->
            a.endOffset <= b.startOffset
        }) { "章节范围不能重叠" }
        val blocks = document.document.blocks
        require(blocks.map { it.blockId }.distinct().size == blocks.size) { "块 ID 不能重复" }
        require(blocks.map { it.order } == blocks.indices.toList()) { "块顺序必须连续" }
        require(blocks.all { it.endOffset <= document.text.length }) { "块偏移超出正文" }
        require(blocks.all { block ->
            chapters.any { chapter ->
                block.startOffset >= chapter.startOffset &&
                    block.startOffset <= chapter.endOffset
            }
        }) { "存在不属于任何章节的块" }
        val resourceIds = document.document.resources.map { it.resourceId }.toSet()
        require(blocks.all { it.resourceId == null || it.resourceId in resourceIds }) {
            "块引用了不存在的资源"
        }
        return document
    }

    fun addChapter(
        document: WritingDocument,
        title: String,
        index: Int = document.document.chapters.size,
        volumeId: String? = null,
    ): WritingDocument {
        validate(document)
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "章节标题不能为空" }
        require(index in 0..document.document.chapters.size) { "章节插入位置无效" }
        val chapterId = UUID.randomUUID().toString()
        val headingText = "$normalizedTitle\n"
        val insertAt = document.document.chapters.getOrNull(index)?.startOffset
            ?: document.text.length
        val delta = headingText.length
        val newText = document.text.substring(0, insertAt) + headingText +
            document.text.substring(insertAt)
        val blocks = document.document.blocks.map { block ->
            if (block.startOffset >= insertAt) {
                block.copy(
                    startOffset = block.startOffset + delta,
                    endOffset = block.endOffset + delta,
                )
            } else {
                block
            }
        }.toMutableList()
        blocks += BookBlock(
            blockId = "$chapterId-heading",
            order = 0,
            type = BookBlockType.HEADING,
            startOffset = insertAt,
            endOffset = insertAt + delta,
            level = 1,
            spans = emptyList(),
        )
        val chapters = document.document.chapters.map { chapter ->
            if (chapter.startOffset >= insertAt) {
                chapter.copy(
                    startOffset = chapter.startOffset + delta,
                    endOffset = chapter.endOffset + delta,
                    headingOffset = chapter.headingOffset + delta,
                )
            } else {
                chapter
            }
        }.toMutableList()
        chapters += Chapter(
            chapterId = chapterId,
            bookId = document.book.bookId,
            title = normalizedTitle,
            order = index,
            startOffset = insertAt,
            endOffset = insertAt + delta,
            headingOffset = insertAt,
            detectionSource = ChapterDetectionSource.MANUAL,
            confidence = 1f,
            level = 1,
            volumeId = volumeId,
        )
        val orderedChapters = chapters.sortedBy { it.startOffset }.mapIndexed { order, chapter ->
            chapter.copy(order = order)
        }
        val orderedBlocks = blocks.sortedWith(
            compareBy<BookBlock>({ it.startOffset }, { if (it.type == BookBlockType.HEADING) 0 else 1 }),
        ).mapIndexed { order, block -> block.copy(order = order) }
        val updated = document.copy(
            text = newText,
            document = document.document.copy(
                chapters = orderedChapters,
                blocks = orderedBlocks,
                resources = document.document.resources,
            ),
        )
        return validate(updated)
    }

    fun renameChapter(
        document: WritingDocument,
        chapterId: String,
        title: String,
    ): WritingDocument {
        validate(document)
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "章节标题不能为空" }
        val chapter = document.document.chapters.firstOrNull { it.chapterId == chapterId }
            ?: throw IllegalArgumentException("章节不存在")
        val heading = document.document.blocks.firstOrNull {
            it.type == BookBlockType.HEADING &&
                it.startOffset >= chapter.startOffset &&
                it.startOffset < chapter.endOffset
        }
        if (heading == null) {
            val chapters = document.document.chapters.map {
                if (it.chapterId == chapterId) it.copy(title = normalizedTitle) else it
            }
            return validate(document.copy(document = document.document.copy(chapters = chapters)))
        }
        val headingText = document.text.substring(heading.startOffset, heading.endOffset)
        val replacement = "$normalizedTitle\n"
        val delta = replacement.length - headingText.length
        val newText = document.text.substring(0, heading.startOffset) +
            replacement + document.text.substring(heading.endOffset)
        val blocks = document.document.blocks.map { block ->
            when {
                block.blockId == heading.blockId -> block.copy(
                    endOffset = block.startOffset + replacement.length,
                    spans = emptyList(),
                )

                block.startOffset >= heading.endOffset -> block.copy(
                    startOffset = block.startOffset + delta,
                    endOffset = block.endOffset + delta,
                )

                else -> block
            }
        }
        val chapters = document.document.chapters.map { item ->
            when {
                item.chapterId == chapterId -> item.copy(
                    title = normalizedTitle,
                    endOffset = item.endOffset + delta,
                )

                item.startOffset >= heading.endOffset -> item.copy(
                    startOffset = item.startOffset + delta,
                    endOffset = item.endOffset + delta,
                    headingOffset = item.headingOffset + delta,
                )

                else -> item
            }
        }
        val updated = document.copy(
            text = newText,
            document = document.document.copy(chapters = chapters, blocks = blocks),
        )
        return validate(updated)
    }

    fun deleteChapter(document: WritingDocument, chapterId: String): WritingDocument {
        validate(document)
        require(document.document.chapters.size > 1) { "至少保留一个章节" }
        val chapter = document.document.chapters.firstOrNull { it.chapterId == chapterId }
            ?: throw IllegalArgumentException("章节不存在")
        val removedLength = chapter.endOffset - chapter.startOffset
        val newText = document.text.removeRange(chapter.startOffset, chapter.endOffset)
        val blocks = document.document.blocks
            .filterNot {
                it.startOffset >= chapter.startOffset && it.startOffset < chapter.endOffset
            }
            .map { block ->
                if (block.startOffset >= chapter.endOffset) {
                    block.copy(
                        startOffset = block.startOffset - removedLength,
                        endOffset = block.endOffset - removedLength,
                    )
                } else {
                    block
                }
            }
            .mapIndexed { order, block -> block.copy(order = order) }
        val chapters = document.document.chapters
            .filterNot { it.chapterId == chapterId }
            .map { item ->
                if (item.startOffset >= chapter.endOffset) {
                    item.copy(
                        startOffset = item.startOffset - removedLength,
                        endOffset = item.endOffset - removedLength,
                        headingOffset = item.headingOffset - removedLength,
                    )
                } else {
                    item
                }
            }
            .mapIndexed { order, item -> item.copy(order = order) }
        val usedResourceIds = blocks.mapNotNull { it.resourceId }.toSet()
        val updated = document.copy(
            text = newText,
            document = document.document.copy(
                chapters = chapters,
                blocks = blocks,
                resources = document.document.resources.filter {
                    it.resourceId in usedResourceIds
                },
            ),
        )
        return validate(updated)
    }

    fun reorderChapters(
        document: WritingDocument,
        orderedChapterIds: List<String>,
    ): WritingDocument {
        validate(document)
        require(orderedChapterIds.size == document.document.chapters.size) {
            "排序必须包含全部章节"
        }
        require(orderedChapterIds.distinct().size == orderedChapterIds.size) {
            "排序中章节 ID 不能重复"
        }
        val byId = document.document.chapters.associateBy { it.chapterId }
        require(orderedChapterIds.all { it in byId }) { "存在不属于当前作品的章节" }

        val buckets = document.document.chapters.associate { chapter ->
            chapter.chapterId to document.document.blocks.filter { block ->
                block.startOffset >= chapter.startOffset &&
                    (block.startOffset < chapter.endOffset ||
                        (chapter.startOffset == chapter.endOffset &&
                            block.startOffset == chapter.startOffset))
            }
        }
        val newChapters = mutableListOf<Chapter>()
        val newBlocks = mutableListOf<BookBlock>()
        val text = StringBuilder()
        orderedChapterIds.forEachIndexed { order, id ->
            val chapter = byId.getValue(id)
            val start = text.length
            val blockStartIndex = newBlocks.size
            val chapterBlocks = buckets[id].orEmpty().sortedBy { it.order }
            chapterBlocks.forEach { block ->
                val blockText = if (block.type == BookBlockType.IMAGE || block.type == BookBlockType.RULE ||
                    block.type == BookBlockType.TABLE
                ) {
                    ""
                } else {
                    document.text.substring(block.startOffset, block.endOffset)
                }
                text.append(blockText)
                newBlocks += block.copy(
                    order = newBlocks.size,
                    startOffset = text.length - blockText.length,
                    endOffset = text.length,
                )
            }
            val firstHeading = newBlocks
                .subList(blockStartIndex, newBlocks.size)
                .firstOrNull { it.type == BookBlockType.HEADING }
            newChapters += chapter.copy(
                order = order,
                startOffset = start,
                endOffset = text.length,
                headingOffset = firstHeading?.startOffset ?: start,
            )
        }
        val resultText = text.toString()
        val usedResourceIds = newBlocks.mapNotNull { it.resourceId }.toSet()
        val updated = document.copy(
            text = resultText,
            document = document.document.copy(
                chapters = newChapters,
                blocks = newBlocks,
                resources = document.document.resources.filter {
                    it.resourceId in usedResourceIds
                },
            ),
        )
        return validate(updated)
    }

    fun renameBook(document: WritingDocument, title: String): WritingDocument {
        val normalized = title.trim()
        require(normalized.isNotEmpty()) { "作品标题不能为空" }
        return document.copy(book = document.book.copy(title = normalized))
    }

    /** 只改变章节的分卷归属；调用方负责确认 volumeId 属于同一本书。 */
    fun moveChapterToVolume(
        document: WritingDocument,
        chapterId: String,
        volumeId: String?,
    ): WritingDocument {
        validate(document)
        require(document.document.chapters.any { it.chapterId == chapterId }) {
            "章节不存在"
        }
        return validate(
            document.copy(
                document = document.document.copy(
                    chapters = document.document.chapters.map {
                        if (it.chapterId == chapterId) it.copy(volumeId = volumeId) else it
                    },
                ),
            ),
        )
    }

    fun blank(book: Book): WritingDocument {
        require(book.shelfType == ShelfType.WRITING) { "只有写作作品可以创建文档" }
        val chapterId = UUID.randomUUID().toString()
        val chapter = Chapter(
            chapterId = chapterId,
            bookId = book.bookId,
            title = "第一章",
            order = 0,
            startOffset = 0,
            endOffset = "第一章\n".length,
            headingOffset = 0,
            detectionSource = ChapterDetectionSource.MANUAL,
            confidence = 1f,
            level = 1,
        )
        return WritingDocument(
            book = book,
            text = "第一章\n",
            document = BookDocument(
                chapters = listOf(chapter),
                blocks = listOf(
                    BookBlock(
                        blockId = "$chapterId-heading",
                        order = 0,
                        type = BookBlockType.HEADING,
                        startOffset = 0,
                        endOffset = "第一章\n".length,
                        level = 1,
                        spans = emptyList(),
                    ),
                ),
                resources = emptyList(),
            ),
        )
    }
}
