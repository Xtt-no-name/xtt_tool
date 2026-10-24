package com.xtt.toolbox.domain.writing

/**
 * 普通正文的一次 Enter 排版规则。
 *
 * 新段落使用真实正文内容：一个空行（两个换行）和两个全角空格缩进；
 * 不依赖 ParagraphStyle、VisualTransformation 或阅读器样式。
 */
data class ParagraphBreakResult(
    val text: String,
    val cursor: Int,
)

data class ImageParagraphResult(
    val text: String,
    val cursor: Int,
)

object WritingParagraph {
    const val INDENT = "\u3000\u3000"
    const val BREAK = "\n\n$INDENT"

    /** 图片块已经独占一行，图片后的段落只需一个换行和段首缩进。 */
    const val IMAGE_BREAK = "\n$INDENT"

    fun replaceSelection(text: String, selectionStart: Int, selectionEnd: Int): ParagraphBreakResult {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)
        val updated = text.substring(0, start) + BREAK + text.substring(end)
        return ParagraphBreakResult(
            text = updated,
            cursor = start + BREAK.length,
        )
    }

    /**
     * 图片是独立块，图片后的空段落只需一个换行；若锚点后已有换行则复用，
     * 避免 \n\n 与图片行叠加造成两个空行。
     */
    fun afterImage(text: String, anchor: Int): ImageParagraphResult {
        val start = anchor.coerceIn(0, text.length)
        val after = text.substring(start)
        if (after.startsWith("\n")) {
            val lineStart = start + 1
            val lineEnd = text.indexOf('\n', lineStart).let {
                if (it == -1) text.length else it
            }
            val lineContent = text.substring(lineStart, lineEnd)
            if (lineContent.isBlank()) {
                if (text.startsWith(INDENT, lineStart)) {
                    return ImageParagraphResult(
                        text = text,
                        cursor = lineStart + INDENT.length,
                    )
                }
                val updated = text.substring(0, lineStart) + INDENT + text.substring(lineStart)
                return ImageParagraphResult(
                    text = updated,
                    cursor = lineStart + INDENT.length,
                )
            }
            // 下一行已有正文：图片后需要新起一个带缩进的行，原有行保留在后面。
            val updated = text.substring(0, start) + IMAGE_BREAK + text.substring(start)
            return ImageParagraphResult(
                text = updated,
                cursor = start + IMAGE_BREAK.length,
            )
        }
        val updated = text.substring(0, start) + IMAGE_BREAK + after
        return ImageParagraphResult(
            text = updated,
            cursor = start + IMAGE_BREAK.length,
        )
    }
}
