package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookDocument
import com.xtt.toolbox.domain.content.InlineSpan
import java.util.UUID

/**
 * 单章节编辑草稿。text 为章节正文（不含末尾换行），lineTypes 与 text 的行一一对应，
 * spans 使用相对章节 text 的 UTF-16 偏移；images 保存图片块的正文锚点和资源引用，
 * 图片本身不进入 text。
 */
data class WritingChapterImage(
    val anchor: Int,
    val blockId: String,
    val resourceId: String,
    val alt: String? = null,
)

data class WritingChapterDraft(
    val chapterId: String,
    val text: String,
    val lineTypes: List<BookBlockType>,
    val spans: List<InlineSpan>,
    val images: List<WritingChapterImage> = emptyList(),
) {
    fun normalized(): WritingChapterDraft {
        val newlineCount = if (text.isEmpty()) 1 else text.count { it == '\n' } + 1
        val types = List(newlineCount) { index ->
            lineTypes.getOrNull(index)?.takeIf { it == BookBlockType.HEADING || it == BookBlockType.QUOTE }
                ?: BookBlockType.PARAGRAPH
        }
        return copy(
            lineTypes = types,
            spans = spans.filter { it.start < text.length }
                .map { it.copy(end = it.end.coerceAtMost(text.length)) }
                .filter { it.end > it.start },
            images = images
                .map { it.copy(anchor = it.anchor.coerceIn(0, text.length)) }
                .distinctBy { it.blockId },
        )
    }
}

object WritingChapterEditor {
    fun initialBodyOffset(draft: WritingChapterDraft): Int {
        val firstLineEnd = draft.text.indexOf('\n')
        if (draft.lineTypes.firstOrNull() != BookBlockType.HEADING) return 0
        if (firstLineEnd < 0) return draft.text.length
        var offset = firstLineEnd + 1
        while (offset < draft.text.length && draft.text[offset] == '\n') {
            offset++
        }
        while (offset < draft.text.length && draft.text[offset] in charArrayOf('　', ' ', '\t')) {
            offset++
        }
        return offset
    }

    fun load(document: WritingDocument, chapterId: String): WritingChapterDraft? {
        val chapter = document.document.chapters.firstOrNull { it.chapterId == chapterId }
            ?: return null
        val raw = document.text.substring(chapter.startOffset, chapter.endOffset)
        val text = raw.trimEnd('\n')
        val lines = if (text.isEmpty()) listOf("") else text.split('\n')
        val chapterBlocks = document.document.blocks.filter { block ->
            block.startOffset >= chapter.startOffset &&
                (block.startOffset < chapter.endOffset ||
                    (block.type == BookBlockType.IMAGE &&
                        block.startOffset == chapter.endOffset))
        }
        var cursor = 0
        val types = lines.map { line ->
            val absolute = chapter.startOffset + cursor
            cursor += line.length + 1
            chapterBlocks.firstOrNull { block ->
                block.startOffset <= absolute && absolute < block.endOffset
            }?.type?.takeIf { it == BookBlockType.HEADING || it == BookBlockType.QUOTE }
                ?: BookBlockType.PARAGRAPH
        }
        val spans = chapterBlocks.flatMap { block ->
            block.spans.map { span ->
                span.copy(
                    start = span.start + block.startOffset - chapter.startOffset,
                    end = span.end + block.startOffset - chapter.startOffset,
                )
            }
        }
        val images = chapterBlocks.mapNotNull { block ->
            if (block.type != BookBlockType.IMAGE) return@mapNotNull null
            val resourceId = block.resourceId ?: return@mapNotNull null
            WritingChapterImage(
                anchor = (block.startOffset - chapter.startOffset).coerceIn(0, text.length),
                blockId = block.blockId,
                resourceId = resourceId,
                alt = block.alt,
            )
        }
        return WritingChapterDraft(
            chapterId = chapterId,
            text = text,
            lineTypes = types,
            spans = spans,
            images = images,
        ).normalized()
    }

    fun updateText(draft: WritingChapterDraft, text: String): WritingChapterDraft {
        val old = draft.normalized()
        val newlineCount = if (text.isEmpty()) 1 else text.count { it == '\n' } + 1
        val types = List(newlineCount) { index ->
            old.lineTypes.getOrNull(index) ?: BookBlockType.PARAGRAPH
        }
        return WritingChapterDraft(
            chapterId = old.chapterId,
            text = text,
            lineTypes = types,
            spans = adjustSpans(old, text),
            images = adjustImageAnchors(old.text, text, old.images),
        ).normalized()
    }

    fun setLineType(
        draft: WritingChapterDraft,
        selectionStart: Int,
        selectionEnd: Int,
        type: BookBlockType,
    ): WritingChapterDraft {
        val normalized = draft.normalized()
        if (normalized.text.isEmpty()) {
            return normalized.copy(lineTypes = listOf(type))
        }
        val startLine = lineIndexAt(normalized.text, selectionStart.coerceIn(0, normalized.text.length))
        val end = selectionEnd.coerceIn(selectionStart, normalized.text.length)
        val endLine = lineIndexAt(normalized.text, end)
        val types = normalized.lineTypes.toMutableList()
        for (index in startLine..endLine.coerceAtMost(types.lastIndex)) {
            types[index] = type
        }
        return normalized.copy(lineTypes = types)
    }

    fun addInlineStyle(
        draft: WritingChapterDraft,
        selectionStart: Int,
        selectionEnd: Int,
        bold: Boolean = false,
        italic: Boolean = false,
    ): WritingChapterDraft {
        val normalized = draft.normalized()
        val start = selectionStart.coerceIn(0, normalized.text.length)
        val end = selectionEnd.coerceIn(start, normalized.text.length)
        if (end <= start || (!bold && !italic)) return normalized
        val span = InlineSpan(start = start, end = end, bold = bold, italic = italic)
        return normalized.copy(spans = normalized.spans + span)
    }

    fun replaceText(
        draft: WritingChapterDraft,
        start: Int,
        end: Int,
        replacement: String,
    ): WritingChapterDraft {
        val normalized = draft.normalized()
        val from = start.coerceIn(0, normalized.text.length)
        val to = end.coerceIn(from, normalized.text.length)
        val text = normalized.text.replaceRange(from, to, replacement)
        val lineTypes = normalized.lineTypes.toMutableList()
        val oldLines = normalized.text.count { it == '\n' }
        val newLines = text.count { it == '\n' }
        if (newLines > oldLines) {
            repeat(newLines - oldLines) { lineTypes += BookBlockType.PARAGRAPH }
        }
        while (lineTypes.size > newLines + 1) lineTypes.removeAt(lineTypes.lastIndex)
        return updateText(
            normalized.copy(lineTypes = lineTypes),
            text,
        )
    }

    /**
     * 把当前章节草稿写回完整 BookDocument。保留章节 ID、块 ID（按行可复用时）、
     * 分卷归属和资源；其他章节/块整体平移。
     */
    fun toDocument(document: WritingDocument, draft: WritingChapterDraft): WritingDocument {
        val chapter = document.document.chapters.firstOrNull { it.chapterId == draft.chapterId }
            ?: throw IllegalArgumentException("章节不存在")
        val normalized = draft.normalized()
        val lines = if (normalized.text.isEmpty()) emptyList() else normalized.text.split('\n')
        val newChapterText = buildString {
            lines.forEach { line ->
                append(line)
                append('\n')
            }
        }
        val oldChapterText = document.text.substring(chapter.startOffset, chapter.endOffset)
        val delta = newChapterText.length - oldChapterText.length
        val newText = document.text.substring(0, chapter.startOffset) +
            newChapterText + document.text.substring(chapter.endOffset)
        val oldBlocks = document.document.blocks.filter { block ->
            block.startOffset >= chapter.startOffset &&
                (block.startOffset < chapter.endOffset ||
                    (block.type == BookBlockType.IMAGE &&
                        block.startOffset == chapter.endOffset))
        }
        val oldTextBlocks = oldBlocks.filter { it.type != BookBlockType.IMAGE }
        val oldImageBlocks = oldBlocks.filter { it.type == BookBlockType.IMAGE }
        val chapterSpans = normalized.spans
        val newChapterBlocks = mutableListOf<BookBlock>()
        var lineOffset = 0
        lines.forEachIndexed { index, line ->
            val type = normalized.lineTypes.getOrNull(index) ?: BookBlockType.PARAGRAPH
            val lineStart = chapter.startOffset + lineOffset
            val lineEnd = lineStart + line.length + 1
            val spans = chapterSpans.mapNotNull { span ->
                val start = maxOf(span.start, lineOffset)
                val end = minOf(span.end, lineOffset + line.length)
                if (end <= start) return@mapNotNull null
                span.copy(
                    start = start - lineOffset,
                    end = end - lineOffset,
                )
            }
            newChapterBlocks += BookBlock(
                blockId = oldTextBlocks.getOrNull(index)?.blockId ?: UUID.randomUUID().toString(),
                order = 0,
                type = type,
                startOffset = lineStart,
                endOffset = lineEnd,
                level = if (type == BookBlockType.HEADING) 1 else 0,
                spans = spans,
            )
            lineOffset += line.length + 1
        }
        normalized.images.forEach { image ->
            val oldImage = oldImageBlocks.firstOrNull { it.blockId == image.blockId }
            newChapterBlocks += BookBlock(
                blockId = image.blockId,
                order = 0,
                type = BookBlockType.IMAGE,
                startOffset = chapter.startOffset + image.anchor,
                endOffset = chapter.startOffset + image.anchor,
                resourceId = image.resourceId,
                alt = image.alt,
                style = oldImage?.style,
            )
        }
        val oldBlocksOutside = document.document.blocks
            .filterNot { block ->
                block.startOffset >= chapter.startOffset &&
                    (block.startOffset < chapter.endOffset ||
                        (block.type == BookBlockType.IMAGE &&
                            block.startOffset == chapter.endOffset))
            }
            .map { block ->
                if (block.startOffset >= chapter.endOffset) {
                    block.copy(startOffset = block.startOffset + delta, endOffset = block.endOffset + delta)
                } else {
                    block
                }
            }
        val allBlocks = (oldBlocksOutside + newChapterBlocks).sortedBy { it.startOffset }
            .mapIndexed { order, block -> block.copy(order = order) }
        val firstHeading = newChapterBlocks.firstOrNull { it.type == BookBlockType.HEADING }
        val newTitle = firstHeading?.let { block ->
            newText.substring(block.startOffset, block.endOffset).trim()
        } ?: chapter.title
        val newChapters = document.document.chapters.map { item ->
            when {
                item.chapterId == chapter.chapterId -> item.copy(
                    title = newTitle,
                    endOffset = chapter.startOffset + newChapterText.length,
                    headingOffset = firstHeading?.startOffset ?: chapter.startOffset,
                )

                item.startOffset >= chapter.endOffset -> item.copy(
                    startOffset = item.startOffset + delta,
                    endOffset = item.endOffset + delta,
                    headingOffset = item.headingOffset + delta,
                )

                else -> item
            }
        }
        return WritingDocumentEditor.validate(
            document.copy(
                text = newText,
                document = document.document.copy(
                    chapters = newChapters,
                    blocks = allBlocks,
                    resources = document.document.resources,
                ),
            ),
        )
    }

    fun wordCount(
        draft: WritingChapterDraft,
        excludePunctuation: Boolean = false,
    ): Int {
        val normalized = draft.normalized()
        val lines = if (normalized.text.isEmpty()) emptyList() else normalized.text.split('\n')
        return lines.mapIndexed { index, line ->
            if (normalized.lineTypes.getOrNull(index) == BookBlockType.HEADING) 0
            else WritingTextStats.count(line, excludePunctuation)
        }.sum()
    }

    private fun lineIndexAt(text: String, offset: Int): Int {
        if (text.isEmpty()) return 0
        return text.substring(0, offset.coerceIn(0, text.length)).count { it == '\n' }
            .coerceAtMost(text.count { it == '\n' })
    }

    private fun adjustImageAnchors(
        oldText: String,
        newText: String,
        images: List<WritingChapterImage>,
    ): List<WritingChapterImage> {
        var prefix = 0
        while (prefix < oldText.length && prefix < newText.length &&
            oldText[prefix] == newText[prefix]
        ) {
            prefix++
        }
        var suffix = 0
        while (suffix < oldText.length - prefix && suffix < newText.length - prefix &&
            oldText[oldText.lastIndex - suffix] == newText[newText.lastIndex - suffix]
        ) {
            suffix++
        }
        val oldEnd = oldText.length - suffix
        val delta = newText.length - oldText.length
        return images.map { image ->
            val anchor = when {
                image.anchor <= prefix -> image.anchor
                image.anchor >= oldEnd -> image.anchor + delta
                else -> prefix
            }
            image.copy(anchor = anchor.coerceIn(0, newText.length))
        }
    }

    private fun adjustSpans(old: WritingChapterDraft, newText: String): List<InlineSpan> {
        val oldText = old.text
        var prefix = 0
        while (prefix < oldText.length && prefix < newText.length &&
            oldText[prefix] == newText[prefix]
        ) {
            prefix++
        }
        var suffix = 0
        while (suffix < oldText.length - prefix && suffix < newText.length - prefix &&
            oldText[oldText.lastIndex - suffix] == newText[newText.lastIndex - suffix]
        ) {
            suffix++
        }
        val oldEnd = oldText.length - suffix
        val newEnd = newText.length - suffix
        return old.spans.mapNotNull { span ->
            when {
                span.end <= prefix -> span
                span.start >= oldEnd -> span.copy(
                    start = span.start + (newEnd - oldEnd),
                    end = span.end + (newEnd - oldEnd),
                )

                else -> {
                    val start = if (span.start < prefix) span.start else prefix
                    val end = if (span.end > oldEnd) {
                        span.end + (newEnd - oldEnd)
                    } else {
                        newEnd
                    }
                    if (end > start) span.copy(start = start, end = end) else null
                }
            }
        }.filter { it.start < newText.length }
            .map { it.copy(end = it.end.coerceAtMost(newText.length)) }
            .filter { it.end > it.start }
    }
}
