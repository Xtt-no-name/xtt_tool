package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.domain.content.BookBlockType

/** 轻量字数统计：UTF-16 文本按 Unicode code point 计数，排除空白/控制字符。 */
object WritingTextStats {
    fun count(text: String, excludePunctuation: Boolean = false): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val ignored = Character.isWhitespace(codePoint) ||
                Character.isISOControl(codePoint) ||
                (excludePunctuation && isPunctuation(codePoint))
            if (!ignored) count++
            index += Character.charCount(codePoint)
        }
        return count
    }

    fun chapterCount(
        document: WritingDocument,
        chapterId: String,
        excludePunctuation: Boolean = false,
    ): Int {
        val chapter = document.document.chapters.firstOrNull { it.chapterId == chapterId }
            ?: return 0
        return document.document.blocks
            .filter { block ->
                block.type != BookBlockType.HEADING &&
                    block.startOffset >= chapter.startOffset &&
                    block.startOffset < chapter.endOffset
            }
            .sumOf { block ->
                count(
                    document.text.substring(block.startOffset, block.endOffset),
                    excludePunctuation,
                )
            }
    }

    fun totalCount(document: WritingDocument, excludePunctuation: Boolean = false): Int =
        document.document.chapters.sumOf {
            chapterCount(document, it.chapterId, excludePunctuation)
        }

    fun chapterCounts(
        document: WritingDocument,
        excludePunctuation: Boolean = false,
    ): Map<String, Int> =
        document.document.chapters.associate {
            it.chapterId to chapterCount(document, it.chapterId, excludePunctuation)
        }

    /** Unicode 标点类别；Emoji、字母、数字、符号不在此列。 */
    private fun isPunctuation(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        -> true
        else -> false
    }
}
