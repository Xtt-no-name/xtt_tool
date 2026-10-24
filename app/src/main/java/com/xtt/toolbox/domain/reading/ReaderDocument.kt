package com.xtt.toolbox.domain.reading

import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.domain.chapter.ChapterCandidate
import com.xtt.toolbox.domain.chapter.TextOffsets
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockStyle
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookTable
import com.xtt.toolbox.domain.content.InlineSpan
import kotlin.math.min

/** 阅读器渲染块；[startOffset] 包含，[endOffset] 不包含，单位是 UTF-16 String 索引。 */
enum class ReaderBlockKind {
    TEXT,
    HEADING,
    IMAGE,
    CODE,
    QUOTE,
    RULE,
    TABLE,
    DEFINITION_TERM,
    DEFINITION,
    FOOTNOTE,
}

data class ReaderBlock(
    val index: Int,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val kind: ReaderBlockKind = ReaderBlockKind.TEXT,
    val level: Int = 0,
    val imagePath: String? = null,
    val imageAlt: String? = null,
    val style: BookBlockStyle? = null,
    val spans: List<InlineSpan> = emptyList(),
    val table: BookTable? = null,
    val blockId: String? = null,
    val footnoteRefIds: List<String> = emptyList(),
) {
    init {
        require(index >= 0) { "块索引不能为负数" }
        require(startOffset >= 0) { "块起始偏移不能为负数" }
        require(endOffset >= startOffset) { "块结束偏移不能早于起始偏移" }
        require(text.length == endOffset - startOffset) { "块文本长度必须与偏移区间一致" }
        if (kind == ReaderBlockKind.IMAGE || kind == ReaderBlockKind.RULE ||
            kind == ReaderBlockKind.TABLE
        ) {
            require(text.isEmpty() && startOffset == endOffset) { "无文本块不能占用文本区间" }
            require(spans.isEmpty()) { "无文本块不能带行内样式" }
            if (kind == ReaderBlockKind.TABLE) {
                require(table != null) { "表格块必须包含表格数据" }
            }
        } else {
            require(imagePath == null) { "文本块不能带图片路径" }
            require(table == null) { "文本块不能包含表格数据" }
            require(spans.all { it.end <= text.length }) { "行内样式范围超出块文本" }
        }
    }
}

/**
 * 连续阅读的分块文档。
 *
 * 锚点方案：
 * - 正文先经过 [TextOffsets.normalizeNewlines]，与章节解析使用同一份字符串；
 * - 按换行切成行块；单行超过 [MAX_BLOCK_CHARS] 时才在块内继续切分；
 * - 持久化阅读位置使用“当前首个可见块的起始 UTF-16 偏移”，而不是滚动像素；
 * - 恢复时找到包含该偏移的块，并滚动到块顶部。
 *
 * 这是行/段级近似，不是字符级滚动映射；恢复会回到可见段落的行首，
 * 不会把整个块内已经滚过的内容重复显示。这样可保证重启后稳定回到同一阅读区域，
 * 避免用像素比例猜测字符位置。未来如需更细粒度，可在行内使用 TextLayoutResult
 * 的字符行映射，但仍需用 UTF-16 偏移保存。
 */
data class ReaderDocument(
    val text: String,
    val blocks: List<ReaderBlock>,
) {
    val length: Int
        get() = text.length

    fun blockIndexForOffset(offset: Int): Int {
        if (blocks.isEmpty()) return 0
        val target = ReaderPositionPolicy.clampOffset(length, offset)
        var low = 0
        var high = blocks.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (blocks[mid].startOffset <= target) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun blockForOffset(offset: Int): ReaderBlock? =
        blocks.getOrNull(blockIndexForOffset(offset))

    companion object {
        const val MAX_BLOCK_CHARS = 400

        fun fromText(rawText: String): ReaderDocument {
            val text = TextOffsets.normalizeNewlines(rawText)
            if (text.isEmpty()) return ReaderDocument(text = text, blocks = emptyList())

            val blocks = ArrayList<ReaderBlock>()
            var start = 0
            while (start < text.length) {
                var end = start
                while (end < text.length && end - start < MAX_BLOCK_CHARS) {
                    end++
                    if (text[end - 1] == '\n') break
                }

                // 不把代理对拆到两个块，避免渲染出替换字符。
                if (end < text.length && end > start &&
                    text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()
                ) {
                    end--
                }
                if (end <= start) {
                    end = min(start + MAX_BLOCK_CHARS, text.length)
                }
                blocks += ReaderBlock(
                    index = blocks.size,
                    startOffset = start,
                    endOffset = end,
                    text = text.substring(start, end),
                )
                start = end
            }
            return ReaderDocument(text = text, blocks = blocks)
        }

        /**
         * 将 EPUB 统一内容块映射为阅读器块；图片路径由调用方从内部资源解析。
         * 文本偏移仍指向规范化全文，注释/进度继续使用同一套 UTF-16 锚点。
         */
        fun fromContentBlocks(
            text: String,
            bookBlocks: List<BookBlock>,
            imagePaths: Map<String, String?>,
        ): ReaderDocument {
            val ordered = bookBlocks.sortedBy { it.order }
            val blocks = ordered.mapIndexedNotNull { index, block ->
                when (block.type) {
                    BookBlockType.IMAGE -> ReaderBlock(
                        index = index,
                        startOffset = block.startOffset,
                        endOffset = block.startOffset,
                        text = "",
                        kind = ReaderBlockKind.IMAGE,
                        imagePath = block.resourceId?.let(imagePaths::get),
                        imageAlt = block.alt,
                        style = block.style,
                    )

                    BookBlockType.RULE -> ReaderBlock(
                        index = index,
                        startOffset = block.startOffset,
                        endOffset = block.startOffset,
                        text = "",
                        kind = ReaderBlockKind.RULE,
                    )

                    BookBlockType.TABLE -> block.table?.let { table ->
                        ReaderBlock(
                            index = index,
                            startOffset = block.startOffset,
                            endOffset = block.startOffset,
                            text = "",
                            kind = ReaderBlockKind.TABLE,
                            table = table,
                            blockId = block.blockId,
                        )
                    }

                    BookBlockType.HEADING, BookBlockType.PARAGRAPH,
                    BookBlockType.CODE, BookBlockType.QUOTE,
                    BookBlockType.DEFINITION_TERM, BookBlockType.DEFINITION,
                    BookBlockType.FOOTNOTE -> {
                        if (block.startOffset !in 0..text.length ||
                            block.endOffset !in block.startOffset..text.length
                        ) {
                            null
                        } else {
                            ReaderBlock(
                                index = index,
                                startOffset = block.startOffset,
                                endOffset = block.endOffset,
                                text = text.substring(block.startOffset, block.endOffset),
                                kind = when (block.type) {
                                    BookBlockType.HEADING -> ReaderBlockKind.HEADING
                                    BookBlockType.CODE -> ReaderBlockKind.CODE
                                    BookBlockType.QUOTE -> ReaderBlockKind.QUOTE
                                    BookBlockType.DEFINITION_TERM -> ReaderBlockKind.DEFINITION_TERM
                                    BookBlockType.DEFINITION -> ReaderBlockKind.DEFINITION
                                    BookBlockType.FOOTNOTE -> ReaderBlockKind.FOOTNOTE
                                    else -> ReaderBlockKind.TEXT
                                },
                                level = block.level,
                                style = block.style,
                                spans = block.spans,
                                blockId = block.blockId,
                                footnoteRefIds = block.footnoteRefIds,
                            )
                        }
                    }
                }
            }.mapIndexed { index, block -> block.copy(index = index) }
            return ReaderDocument(text = text, blocks = blocks)
        }
    }
}

/** 阅读位置的范围修正与进度计算；空正文始终返回安全默认值。 */
object ReaderPositionPolicy {
    fun clampOffset(textLength: Int, offset: Int): Int {
        if (textLength <= 0) return 0
        return offset.coerceIn(0, textLength)
    }

    fun percent(textLength: Int, offset: Int): Float {
        if (textLength <= 0) return 0f
        return (clampOffset(textLength, offset).toFloat() / textLength.toFloat())
            .coerceIn(0f, 1f)
    }

    fun position(
        textLength: Int,
        offset: Int,
        chapterId: String? = null,
    ): ReadingPosition {
        val safeOffset = clampOffset(textLength, offset)
        return ReadingPosition(
            chapterId = chapterId,
            offset = safeOffset,
            percent = percent(textLength, safeOffset),
        )
    }
}

/**
 * 合并短时间内的多个位置更新。
 *
 * 只保留最后一个待保存偏移，重复值不触发新的保存；[markSaved] 不会清除
 * 在保存期间到达的更新值。
 */
class ReaderPositionBuffer {
    private var pending: Int? = null
    private var lastSaved: Int? = null

    fun initialize(offset: Int) {
        pending = null
        lastSaved = offset
    }

    /** 返回 true 表示出现了需要保存的新位置。 */
    fun update(offset: Int): Boolean {
        if (offset == pending) return false
        if (pending == null && offset == lastSaved) return false
        pending = offset
        return true
    }

    fun peek(): Int? = pending

    fun markSaved(offset: Int) {
        if (pending == offset) pending = null
        lastSaved = offset
    }

    fun lastSavedOffset(): Int? = lastSaved
}

/** 根据临时解析章节和当前字符偏移得到当前章节标题；没有可靠章节时返回 null。 */
object ReaderChapterTitle {
    fun titleAt(chapters: List<ChapterCandidate>, offset: Int): String? =
        chapterAt(chapters, offset)?.title

    fun chapterAt(chapters: List<ChapterCandidate>, offset: Int): ChapterCandidate? =
        chapters.lastOrNull { it.startOffset <= offset }

    /**
     * 持久化章节优先使用稳定 chapterId；TXT 临时章节才退回起始偏移。
     * 旧记录中已存在的 `offset:` key 仍可读取，不会被重写。
     */
    fun keyOf(chapter: ChapterCandidate): String =
        chapter.chapterId ?: "offset:${chapter.startOffset}"

    /**
     * 章节结束偏移通常落在下一章标题行首；行块可能不含行尾换行，
     * 因此允许“块尾”或“块尾后一个换行”对应章末。仅用于显示章末入口。
     */
    fun endsAtBlock(chapter: ChapterCandidate, block: ReaderBlock): Boolean =
        chapter.endOffset == block.endOffset || chapter.endOffset == block.endOffset + 1

    const val UNASSIGNED_CHAPTER_KEY = "unassigned"
}
