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
        if (!raw.startsWith(title)) return false
        return raw.length == title.length || raw[title.length] == '\n'
    }

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
