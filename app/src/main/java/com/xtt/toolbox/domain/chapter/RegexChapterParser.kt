package com.xtt.toolbox.domain.chapter

import com.xtt.toolbox.data.model.ChapterDetectionSource

/**
 * 第一版章节识别器。
 *
 * 只在“独立成行、长度合理”的标题行上识别；正文中夹带的章节词不会触发。
 * 支持中文数字/阿拉伯数字章节、回、卷、序章/楔子/引子，以及 Chapter N。
 * 没有匹配时返回空候选，调用方必须允许连续阅读。
 */
class RegexChapterParser : ChapterParser {
    private data class Match(
        val lineStart: Int,
        val headingOffset: Int,
        val title: String,
        val confidence: Float,
    )

    private data class Pattern(
        val regex: Regex,
        val confidence: Float,
    )

    private val patterns = listOf(
        Pattern(
            Regex("""^第[0-9零〇一二三四五六七八九十百千万两]+[章节回](?:\s+.*)?$"""),
            0.95f,
        ),
        Pattern(
            Regex("""^第[0-9零〇一二三四五六七八九十百千万两]+卷(?:\s+.*)?$"""),
            0.9f,
        ),
        Pattern(
            Regex("""^卷[0-9零〇一二三四五六七八九十百千万两]+(?:\s+.*)?$"""),
            0.85f,
        ),
        Pattern(
            Regex("""^(序章|楔子|引子)(?:\s+.*)?$"""),
            0.9f,
        ),
        Pattern(
            Regex("""^chapter\s+\d+(?:\s*[:：.\-]?\s*.*)?$""", RegexOption.IGNORE_CASE),
            0.9f,
        ),
    )

    override fun parse(text: String): ChapterParseResult {
        if (text.isEmpty()) return ChapterParseResult(emptyList())

        val matches = mutableListOf<Match>()
        var lineStart = 0
        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
            val rawLine = text.substring(lineStart, lineEnd)
            val title = rawLine.trim()
            if (title.isNotEmpty() && title.length <= MAX_TITLE_LENGTH) {
                val pattern = patterns.firstOrNull { it.regex.matches(title) }
                if (pattern != null) {
                    val leadingWhitespace = rawLine.indexOfFirst { !it.isWhitespace() }
                        .let { if (it == -1) 0 else it }
                    matches += Match(
                        lineStart = lineStart,
                        headingOffset = lineStart + leadingWhitespace,
                        title = title,
                        confidence = pattern.confidence,
                    )
                }
            }
            if (lineEnd == text.length) break
            lineStart = lineEnd + 1
        }

        val candidates = matches.mapIndexed { index, match ->
            ChapterCandidate(
                title = match.title,
                order = index,
                startOffset = match.lineStart,
                endOffset = matches.getOrNull(index + 1)?.lineStart ?: text.length,
                headingOffset = match.headingOffset,
                detectionSource = ChapterDetectionSource.REGEX,
                confidence = match.confidence,
            )
        }
        return ChapterParseResult(candidates = candidates)
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 80
    }
}
