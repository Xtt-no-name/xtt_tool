package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.InlineSpan

data class SmartFormatResult(
    val draft: WritingChapterDraft,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/**
 * 当前章节的保守式智能排版：
 * - 普通正文段落统一为恰好两个全角空格首行缩进；
 * - 相邻普通正文段落之间统一为恰好一个空行；
 * - 标题、引用、列表、代码围栏、表格、图片/HTML 全部跳过；
 * - 不修改正文内部文字、标点、链接或资源路径。
 */
object WritingSmartFormat {
    private enum class LineKind { ORDINARY, PROTECTED, BLANK }

    private data class OldLine(
        val text: String,
        val type: BookBlockType,
        val start: Int,
        val kind: LineKind,
    )

    private data class Mapping(
        val oldStart: Int,
        val oldLength: Int,
        val oldIndentLength: Int,
        val newStart: Int,
        val newIndentLength: Int,
    )

    fun format(
        draft: WritingChapterDraft,
        selectionStart: Int,
        selectionEnd: Int,
    ): SmartFormatResult? {
        val normalized = draft.normalized()
        val oldLines = inspectLines(normalized)
        if (oldLines.isEmpty()) return null

        val output = mutableListOf<String>()
        val outputTypes = mutableListOf<BookBlockType>()
        val mappings = ArrayList<Mapping>(oldLines.size)
        var previousNonBlank: LineKind? = null
        var blankSincePrevious = false
        var index = 0

        fun addBlank() {
            output += ""
            outputTypes += BookBlockType.PARAGRAPH
        }

        while (index < oldLines.size) {
            val old = oldLines[index]
            if (old.kind == LineKind.BLANK) {
                val runStart = index
                while (index < oldLines.size && oldLines[index].kind == LineKind.BLANK) {
                    index++
                }
                val runSize = index - runStart
                val nextKind = oldLines.getOrNull(index)?.kind
                val compress = previousNonBlank == LineKind.ORDINARY &&
                    nextKind == LineKind.ORDINARY
                val keep = if (compress) 1 else runSize
                val blankStart = outputLength(output)
                repeat(keep) { addBlank() }
                repeat(runSize) { offset ->
                    val oldBlank = oldLines[runStart + offset]
                    mappings += Mapping(
                        oldStart = oldBlank.start,
                        oldLength = oldBlank.text.length,
                        oldIndentLength = 0,
                        newStart = blankStart,
                        newIndentLength = 0,
                    )
                }
                blankSincePrevious = keep > 0
                continue
            }

            if (old.kind == LineKind.ORDINARY &&
                previousNonBlank == LineKind.ORDINARY &&
                !blankSincePrevious
            ) {
                addBlank()
            }

            val oldIndent = leadingIndent(old.text)
            val body = old.text.substring(oldIndent)
            val outputLine = if (old.kind == LineKind.ORDINARY) {
                WritingParagraph.INDENT + body
            } else {
                old.text
            }
            val newStart = outputLength(output)
            output += outputLine
            outputTypes += old.type
            mappings += Mapping(
                oldStart = old.start,
                oldLength = old.text.length,
                oldIndentLength = oldIndent,
                newStart = newStart,
                newIndentLength = if (old.kind == LineKind.ORDINARY) {
                    WritingParagraph.INDENT.length
                } else {
                    0
                },
            )
            previousNonBlank = old.kind
            blankSincePrevious = false
            index++
        }

        val newText = output.joinToString("\n")
        if (newText == normalized.text && outputTypes == normalized.lineTypes) return null

        fun mapOffset(value: Int, keepBeforeIndent: Boolean = false): Int {
            val clamped = value.coerceIn(0, normalized.text.length)
            val lineIndex = oldLines.indexOfLast { it.start <= clamped }.coerceAtLeast(0)
            val mapping = mappings.getOrNull(lineIndex) ?: return clamped
            val column = (clamped - mapping.oldStart).coerceIn(0, mapping.oldLength)
            if (keepBeforeIndent && column == 0) {
                return mapping.newStart.coerceIn(0, newText.length)
            }
            val newColumn = if (column < mapping.oldIndentLength) {
                mapping.newIndentLength
            } else {
                mapping.newIndentLength + (column - mapping.oldIndentLength)
            }
            return (mapping.newStart + newColumn).coerceIn(0, newText.length)
        }

        val newSpans = normalized.spans.mapNotNull { span ->
            val start = mapOffset(span.start)
            val end = mapOffset(span.end)
            if (end <= start) null else span.copy(start = start, end = end)
        }
        val newImages = normalized.images.map { image ->
            image.copy(anchor = mapOffset(image.anchor, keepBeforeIndent = true))
        }
        val formatted = normalized.copy(
            text = newText,
            lineTypes = outputTypes,
            spans = newSpans,
            images = newImages,
        ).normalized()
        return SmartFormatResult(
            draft = formatted,
            selectionStart = mapOffset(selectionStart),
            selectionEnd = mapOffset(selectionEnd),
        )
    }

    private fun inspectLines(draft: WritingChapterDraft): List<OldLine> {
        val lines = if (draft.text.isEmpty()) listOf("") else draft.text.split('\n')
        var start = 0
        var inFence = false
        return lines.mapIndexed { index, line ->
            val type = draft.lineTypes.getOrNull(index) ?: BookBlockType.PARAGRAPH
            val old = OldLine(
                text = line,
                type = type,
                start = start,
                kind = when {
                    line.isBlank() && inFence -> LineKind.PROTECTED
                    line.isBlank() -> LineKind.BLANK
                    else -> {
                        val fence = detectFence(line, inFence)
                        if (fence.open != null) inFence = fence.open
                        if (inFence || fence.isFence ||
                            type != BookBlockType.PARAGRAPH || isMarkdownProtected(line)
                        ) {
                            LineKind.PROTECTED
                        } else {
                            LineKind.ORDINARY
                        }
                    }
                },
            )
            start += line.length + 1
            old
        }
    }

    private fun outputLength(lines: List<String>): Int =
        if (lines.isEmpty()) 0 else lines.sumOf { it.length + 1 } - 1

    private fun leadingIndent(line: String): Int =
        line.indexOfFirst { it != ' ' && it != '\t' && it != '\u3000' }
            .let { if (it < 0) line.length else it }

    private data class FenceState(
        val isFence: Boolean,
        val open: Boolean?,
    )

    private fun detectFence(line: String, inFence: Boolean): FenceState {
        val trimmed = line.trimStart()
        val fence = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }
        if (fence == null) return FenceState(false, if (inFence) true else null)
        return if (inFence) FenceState(true, false) else FenceState(true, true)
    }

    private fun isMarkdownProtected(line: String): Boolean {
        val trimmed = line.trim()
        if (Regex("^#{1,6}\\s+.*$").matches(trimmed)) return true
        if (trimmed.startsWith(">")) return true
        if (Regex("^(?:[-*+]|\\d+[.)])\\s+.*$").matches(trimmed)) return true
        if (Regex("^(?:-{3,}|\\*{3,}|_{3,})$").matches(trimmed)) return true
        if (trimmed.startsWith("|") || trimmed.startsWith("<")) return true
        if (trimmed.startsWith("![")) return true
        return false
    }
}
