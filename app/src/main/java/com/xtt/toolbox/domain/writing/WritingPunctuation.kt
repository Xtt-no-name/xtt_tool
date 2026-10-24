package com.xtt.toolbox.domain.writing

/**
 * 成对标点输入和常用标点默认列表的纯逻辑。
 *
 * autoCloseOffsets 记录当前文本中由自动补齐生成、且仍可识别的右侧标点位置；
 * 它只用于跳过重复闭合标点和退格成对删除，不进入 BookDocument 持久化。
 */
data class WritingAutoPairEdit(
    val text: String,
    val cursor: Int,
    val autoCloseOffsets: Set<Int>,
)

object WritingPunctuation {
    val DEFAULT_PUNCTUATION = listOf(
        "，", "。", "！", "？", "、", "；", "：",
        "“", "”", "‘", "’", "（", "）", "——", "……",
    )

    val PAIR_MAP = mapOf(
        "“" to "”",
        "‘" to "’",
        "（" to "）",
        "(" to ")",
        "[" to "]",
        "【" to "】",
        "{" to "}",
        "《" to "》",
        "〈" to "〉",
        "「" to "」",
        "『" to "』",
    )

    fun applyAutoPair(
        oldText: String,
        oldSelectionStart: Int,
        oldSelectionEnd: Int,
        newText: String,
        newSelectionStart: Int,
        newSelectionEnd: Int,
        autoCloseOffsets: Set<Int>,
    ): WritingAutoPairEdit? {
        if (newSelectionStart != newSelectionEnd) return null
        val oldStart = oldSelectionStart
        val oldEnd = oldSelectionEnd

        // 光标在自动生成的闭合标点前再次输入同一闭合标点：只移动光标。
        if (oldStart == oldEnd && newText.length == oldText.length + 1 &&
            newSelectionStart == oldStart + 1 && oldStart in autoCloseOffsets &&
            oldStart < newText.length
        ) {
            val inserted = newText.substring(oldStart, oldStart + 1)
            if (inserted == oldText.getOrNull(oldStart)?.toString() &&
                newText.removeRange(oldStart, oldStart + 1) == oldText
            ) {
                return WritingAutoPairEdit(
                    text = oldText,
                    cursor = oldStart + 1,
                    autoCloseOffsets = autoCloseOffsets,
                )
            }
        }

        // 光标位于一对自动标点之间按退格：同时删除左右标点。
        if (oldStart == oldEnd && oldStart > 0 && newText.length == oldText.length - 1 &&
            newSelectionStart == oldStart - 1
        ) {
            val left = oldText[oldStart - 1]
            val right = oldText.getOrNull(oldStart)
            if (oldStart in autoCloseOffsets && right != null &&
                PAIR_MAP[left.toString()] == right.toString() &&
                newText == oldText.removeRange(oldStart - 1, oldStart)
            ) {
                val pairedText = oldText.removeRange(oldStart - 1, oldStart + 1)
                return WritingAutoPairEdit(
                    text = pairedText,
                    cursor = oldStart - 1,
                    autoCloseOffsets = adjustOffsets(oldText, pairedText, autoCloseOffsets),
                )
            }
        }

        // 选中文字后输入左侧标点：把选区包在左右标点之间。
        if (oldStart < oldEnd && newText.length == oldText.length - (oldEnd - oldStart) + 1 &&
            newSelectionStart == oldStart + 1 && oldStart < newText.length
        ) {
            val inserted = newText.substring(oldStart, oldStart + 1)
            val close = PAIR_MAP[inserted] ?: return null
            val expected = oldText.substring(0, oldStart) + inserted + oldText.substring(oldEnd)
            if (newText == expected) {
                val selected = oldText.substring(oldStart, oldEnd)
                val pairedText = oldText.substring(0, oldStart) + inserted + selected + close +
                    oldText.substring(oldEnd)
                val offsets = adjustOffsetsForRange(
                    oldStart = oldStart,
                    oldEnd = oldEnd,
                    replacementLength = selected.length + 2,
                    offsets = autoCloseOffsets,
                ).toMutableSet()
                val closeIndex = oldStart + 1 + selected.length
                offsets += closeIndex
                return WritingAutoPairEdit(
                    text = pairedText,
                    cursor = closeIndex + 1,
                    autoCloseOffsets = offsets,
                )
            }
        }

        // 普通左侧标点：插入左右标点，光标放在中间。
        if (oldStart == oldEnd && newText.length == oldText.length + 1 &&
            newSelectionStart == oldStart + 1 && oldStart < newText.length
        ) {
            val inserted = newText.substring(oldStart, oldStart + 1)
            val close = PAIR_MAP[inserted] ?: return null
            val expected = oldText.substring(0, oldStart) + inserted + oldText.substring(oldStart)
            if (newText == expected) {
                val pairedText = oldText.substring(0, oldStart) + inserted + close +
                    oldText.substring(oldStart)
                val offsets = adjustOffsetsForRange(
                    oldStart = oldStart,
                    oldEnd = oldStart,
                    replacementLength = 2,
                    offsets = autoCloseOffsets,
                ).toMutableSet()
                offsets += oldStart + 1
                return WritingAutoPairEdit(
                    text = pairedText,
                    cursor = oldStart + 1,
                    autoCloseOffsets = offsets,
                )
            }
        }
        return null
    }

    fun adjustOffsets(oldText: String, newText: String, offsets: Set<Int>): Set<Int> {
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
        return offsets.mapNotNull { offset ->
            when {
                offset < prefix -> offset
                offset >= oldEnd -> offset + delta
                else -> null
            }
        }.toSet()
    }

    fun adjustOffsetsForRange(
        oldStart: Int,
        oldEnd: Int,
        replacementLength: Int,
        offsets: Set<Int>,
    ): Set<Int> {
        val delta = replacementLength - (oldEnd - oldStart)
        return offsets.mapNotNull { offset ->
            when {
                offset < oldStart -> offset
                offset >= oldEnd -> offset + delta
                else -> null
            }
        }.toSet()
    }
}
