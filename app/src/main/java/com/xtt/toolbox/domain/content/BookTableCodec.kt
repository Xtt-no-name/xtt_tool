package com.xtt.toolbox.domain.content

/**
 * 表格在数据库中的紧凑编码。
 *
 * 复用 book_blocks.alt 列保存，不新增数据库列/迁移。行/单元格/样式分隔符使用
 * 不会出现在正常 Markdown 文本中的控制字符；单元格文本与行内范围分别保存。
 */
object BookTableCodec {
    private const val ROW = '\u001E'
    private const val CELL = '\u001F'
    private const val SPAN = '\u001D'

    fun encode(table: BookTable): String = buildString {
        append(table.alignments.joinToString(",") { it.name })
        append(ROW)
        append(table.header.joinToString(CELL.toString()) { encodeCell(it) })
        table.rows.forEach { row ->
            append(ROW)
            append(row.joinToString(CELL.toString()) { encodeCell(it) })
        }
    }

    fun decode(raw: String?): BookTable? {
        if (raw.isNullOrBlank()) return null
        val rows = raw.split(ROW)
        if (rows.size < 2) return null
        val alignments = rows.first().split(',').mapNotNull {
            runCatching { BookTextAlign.valueOf(it) }.getOrNull()
        }
        if (alignments.isEmpty()) return null
        val dataRows = rows.drop(1).mapNotNull { row ->
            val cells = row.split(CELL).map { decodeCell(it) }
            if (cells.size == alignments.size) cells else null
        }
        if (dataRows.size != rows.size - 1 || dataRows.isEmpty()) return null
        return BookTable(
            header = dataRows.first(),
            rows = dataRows.drop(1),
            alignments = alignments,
        )
    }

    private fun encodeCell(cell: BookTableCell): String {
        val spans = BookBlockStyleCodec.encodeSpans(cell.spans).orEmpty()
        return cell.text + SPAN + spans
    }

    private fun decodeCell(raw: String): BookTableCell {
        val split = raw.split(SPAN, limit = 2)
        val text = split.firstOrNull().orEmpty()
        val spans = BookBlockStyleCodec.decodeSpans(split.getOrNull(1))
            .filter { it.end <= text.length }
        return BookTableCell(text = text, spans = spans)
    }
}
