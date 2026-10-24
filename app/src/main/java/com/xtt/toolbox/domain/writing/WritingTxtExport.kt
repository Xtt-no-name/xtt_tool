package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.domain.content.BookBlockType

/**
 * TXT 导出投影：只读取已保存的 WritingDocument，不修改正文、章节或资源。
 * 图片不导出二进制，只在原锚点写入可读占位 [图片]；绝不写资源路径或 ID。
 * 空章节名不写作品名/占位标题；章节按 order 顺序拼接。
 */
object WritingTxtExport {
    private const val IMAGE_PLACEHOLDER = "[图片]"

    fun build(document: WritingDocument): String {
        val sections = document.document.chapters
            .sortedBy { it.order }
            .mapNotNull { chapter ->
                val raw = document.text.substring(
                    chapter.startOffset.coerceIn(0, document.text.length),
                    chapter.endOffset.coerceIn(0, document.text.length),
                )
                val title = chapter.title.trim()
                val headingStart = if (title.isNotEmpty() && startsWithTitle(raw, title)) {
                    raw.indexOf('\n').let { if (it < 0) raw.length else it + 1 }
                } else {
                    0
                }
                val bodyStart = chapter.startOffset + headingStart
                val body = raw.substring(headingStart)
                val withImages = insertImagePlaceholders(document, chapter, bodyStart, body)
                val section = buildString {
                    if (title.isNotEmpty()) append(title).append('\n')
                    append(withImages.trimEnd('\n'))
                }
                section.takeIf { it.isNotBlank() || title.isNotEmpty() }
            }
        return sections.joinToString("\n\n").trimEnd('\n')
    }

    fun suggestedFileName(bookTitle: String): String {
        val base = bookTitle.trim().ifBlank { "未命名作品" }
        val safe = base.map { character ->
            if (character in "\\/:*?\"<>|\u0000") ' ' else character
        }.joinToString("").trim().trim('.')
        return "${safe.ifBlank { "未命名作品" }}.txt"
    }

    private fun startsWithTitle(raw: String, title: String): Boolean {
        val firstLineEnd = raw.indexOf('\n').let { if (it < 0) raw.length else it }
        val firstLine = raw.substring(0, firstLineEnd)
        return normalizeHeading(firstLine) == normalizeHeading(title) &&
            normalizeHeading(firstLine).isNotEmpty()
    }

    /**
     * 章节标题在编辑器/旧导出文件中可能带有前导空格、制表符或全角空格，
     * 也可能使用不同数量的空白。比较标题时忽略空白字符，但只跳过第一行，
     * 不删除正文中后续合法重复出现的标题文字。
     */
    private fun normalizeHeading(value: String): String =
        value.filterNot { it.isWhitespace() || it == '\uFEFF' }

    private fun insertImagePlaceholders(
        document: WritingDocument,
        chapter: com.xtt.toolbox.data.model.Chapter,
        bodyStart: Int,
        body: String,
    ): String {
        val images = document.document.blocks
            .filter {
                it.type == BookBlockType.IMAGE &&
                    it.startOffset >= chapter.startOffset &&
                    it.startOffset <= chapter.endOffset
            }
            .sortedBy { it.startOffset }
        if (images.isEmpty()) return body
        val builder = StringBuilder()
        var cursor = 0
        images.forEach { image ->
            val index = (image.startOffset - bodyStart).coerceIn(cursor, body.length)
            builder.append(body, cursor, index)
            if (builder.isNotEmpty() && !builder.endsWith("\n")) builder.append('\n')
            builder.append(IMAGE_PLACEHOLDER)
            if (index < body.length && body[index] != '\n') builder.append('\n')
            cursor = index
        }
        builder.append(body, cursor, body.length)
        return builder.toString()
    }
}
