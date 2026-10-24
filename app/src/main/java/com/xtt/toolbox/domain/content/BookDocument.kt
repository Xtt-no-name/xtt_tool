package com.xtt.toolbox.domain.content

import com.xtt.toolbox.data.model.Chapter

/** 统一内容模型中的块类型。文本块偏移仍指向规范化全文的 UTF-16 区间。 */
enum class BookBlockType {
    PARAGRAPH,
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

enum class BookResourceKind {
    IMAGE,
    COVER,
}

/**
 * 应用内部资源描述。二进制内容不进入数据库，只保存应用内部相对路径和摘要。
 */
data class BookResource(
    val resourceId: String,
    val relativePath: String,
    val mediaType: String,
    val sizeBytes: Long,
    val sha256: String,
    val kind: BookResourceKind,
    val alt: String? = null,
) {
    init {
        require(resourceId.isNotBlank()) { "resourceId 不能为空" }
        require(relativePath.isNotBlank()) { "资源路径不能为空" }
        require(mediaType.isNotBlank()) { "资源 MIME 不能为空" }
        require(sizeBytes >= 0) { "资源大小不能为负数" }
        require(sha256.isNotBlank()) { "资源哈希不能为空" }
    }
}

/**
 * EPUB 的统一内容块。
 *
 * - 文本块 [startOffset]..[endOffset] 对应 [com.xtt.toolbox.domain.reading.ReaderDocument.text]；
 * - 图片块没有文本，[startOffset] == [endOffset] 表示它在该文本位置之前；
 * - 图片位置通过 [resourceId] 关联内部资源，不把图片二进制塞进数据库。
 */
data class BookTableCell(
    val text: String,
    val spans: List<InlineSpan> = emptyList(),
) {
    init {
        require(spans.all { it.end <= text.length }) { "表格单元格样式范围超出文本" }
    }
}

/**
 * 结构化 Markdown 表格。单元格样式相对单元格文本；列对齐使用 [BookTextAlign] 的
 * START/CENTER/END 子集。表格不把单元格拼接成正文段落，渲染器按行列布局。
 */
data class BookTable(
    val header: List<BookTableCell>,
    val rows: List<List<BookTableCell>>,
    val alignments: List<BookTextAlign>,
) {
    init {
        require(header.isNotEmpty()) { "表格至少需要一列" }
        require(rows.all { it.size == header.size }) { "表格行列数必须与表头一致" }
        require(alignments.size == header.size) { "表格对齐数量必须与列数一致" }
    }

    val columnCount: Int
        get() = header.size
}

data class BookBlock(
    val blockId: String,
    val order: Int,
    val type: BookBlockType,
    val startOffset: Int,
    val endOffset: Int,
    val level: Int = 0,
    val resourceId: String? = null,
    val alt: String? = null,
    val style: BookBlockStyle? = null,
    val spans: List<InlineSpan> = emptyList(),
    val table: BookTable? = null,
    val footnoteRefIds: List<String> = emptyList(),
) {
    init {
        require(blockId.isNotBlank()) { "blockId 不能为空" }
        require(order >= 0) { "块顺序不能为负数" }
        require(startOffset >= 0) { "块起点不能为负数" }
        require(endOffset >= startOffset) { "块终点不能早于起点" }
        require(level >= 0) { "标题层级不能为负数" }
        require(type == BookBlockType.FOOTNOTE || footnoteRefIds.isEmpty()) {
            "只有脚注块可以保存引用块"
        }
        if (type == BookBlockType.IMAGE || type == BookBlockType.RULE ||
            type == BookBlockType.TABLE
        ) {
            require(startOffset == endOffset) { "无文本块不能占用文本区间" }
            require(spans.isEmpty()) { "无文本块不能带行内样式" }
            if (type == BookBlockType.TABLE) {
                require(table != null) { "表格块必须包含表格数据" }
                require(resourceId == null) { "表格块不能关联图片资源" }
            }
            // 缺失图片也保留占位块，resourceId 为空时由阅读器显示占位。
        } else {
            require(endOffset >= startOffset) { "文本块区间无效" }
            require(resourceId == null) { "文本块不能关联图片资源" }
            require(table == null) { "文本块不能包含表格数据" }
            val textLength = endOffset - startOffset
            require(spans.all { it.end <= textLength }) { "行内样式范围超出块文本" }
        }
    }
}

/** 从 EPUB 解析出的可持久化内容描述；正文文本单独保存在内容文件中。 */
data class BookDocument(
    val chapters: List<Chapter>,
    val blocks: List<BookBlock>,
    val resources: List<BookResource>,
) {
    private val resourceIds: Set<String> = resources.mapTo(HashSet()) { it.resourceId }

    init {
        require(chapters.all { it.bookId.isNotBlank() }) { "章节必须关联书籍" }
        require(blocks.zipWithNext().all { (a, b) -> a.order < b.order }) { "块顺序必须递增" }
        require(blocks.all { block ->
            block.resourceId == null || block.resourceId in resourceIds
        }) { "图片块引用了不存在的资源" }
        require(chapters.map { it.chapterId }.distinct().size == chapters.size) {
            "章节 ID 不能重复"
        }
    }

    fun resource(resourceId: String?): BookResource? =
        resourceId?.let { id -> resources.firstOrNull { it.resourceId == id } }
}
