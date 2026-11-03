package com.xtt.toolbox.domain.chapter

import com.xtt.toolbox.data.model.ChapterDetectionSource

/**
 * TXT 章节解析器：先提取候选标题，再做目录区过滤和编号结构校验，最后按原文偏移切分。
 *
 * 设计参考（只借鉴算法思想，不复制代码）：
 * - Qingdu Reader 的 ChapterTitleMatcher / TxtChapterSplitter（Apache-2.0）：
 *   候选结构校验、目录区过滤、编号序列对齐、卷标题与诊断报告；
 * - novel-chapter-splitter（MIT）：候选匹配与干扰行过滤比较简单，未采用其顺序切分。
 *
 * 本类不会修改正文，也不会丢弃候选之外的正文；输出偏移始终指向原始规范化文本。
 */
class RegexChapterParser : ChapterParser {
    private enum class HeadingKind {
        CHAPTER,
        VOLUME,
        SPECIAL,
    }

    private data class Heading(
        val lineIndex: Int,
        val lineStart: Int,
        val headingOffset: Int,
        val title: String,
        val kind: HeadingKind,
        val number: Int?,
        val confidence: Float,
    )

    private data class PatternSpec(
        val regex: Regex,
        val kind: HeadingKind,
        val confidence: Float,
    )

    private val patterns = listOf(
        // 第 1 章、第 一 回、第12节：编号与单位之间允许 Unicode 空白。
        PatternSpec(
            Regex(
                """^第[\s\u3000]*([0-9零〇一二三四五六七八九十百千万两]+)[\s\u3000]*([章节回集话])""" +
                    """(?=$|[\s\u3000:：、.\-—])(.*)$""",
            ),
            HeadingKind.CHAPTER,
            0.95f,
        ),
        // 第 1 卷 / 第 一 部 / 第 2 篇
        PatternSpec(
            Regex(
                """^第[\s\u3000]*([0-9零〇一二三四五六七八九十百千万两]+)[\s\u3000]*([卷部篇册])""" +
                    """(?=$|[\s\u3000:：、.\-—])(.*)$""",
            ),
            HeadingKind.VOLUME,
            0.90f,
        ),
        // 卷一 / 卷 1
        PatternSpec(
            Regex(
                """^卷[\s\u3000]*([0-9零〇一二三四五六七八九十百千万两]+)""" +
                    """(?=$|[\s\u3000:：、.\-—])(.*)$""",
            ),
            HeadingKind.VOLUME,
            0.86f,
        ),
        PatternSpec(
            Regex(
                """^(序章|楔子|引子|前言|序言|尾声|终章|后记|番外)""" +
                    """(?=$|[\s\u3000:：、.\-—])(.*)$""",
            ),
            HeadingKind.SPECIAL,
            0.90f,
        ),
        PatternSpec(
            Regex(
                """^chapter[\s\u3000]+(\d+)(?=$|[\s\u3000:：、.\-—])(.*)$""",
                RegexOption.IGNORE_CASE,
            ),
            HeadingKind.CHAPTER,
            0.90f,
        ),
    )

    override fun parse(text: String): ChapterParseResult {
        if (text.isEmpty()) return ChapterParseResult(emptyList())

        val lines = scanLines(text)
        if (lines.isEmpty()) return ChapterParseResult(emptyList())

        val headings = mutableListOf<Heading>()
        lines.forEachIndexed { lineIndex, line ->
            val rawLine = line.text
            val title = cleanLine(rawLine)
            if (title.isEmpty() || title.length > MAX_LINE_LENGTH) return@forEachIndexed
            if (looksLikeAdvertisement(title)) return@forEachIndexed

            val matched = matchHeading(title) ?: return@forEachIndexed
            val leadingWhitespace = rawLine.indexOfFirst { !it.isWhitespace() && it != '\uFEFF' }
                .let { if (it == -1) 0 else it }
            headings += Heading(
                lineIndex = lineIndex,
                lineStart = line.start,
                headingOffset = line.start + leadingWhitespace,
                title = title,
                kind = matched.kind,
                number = matched.number,
                confidence = matched.confidence,
            )
        }
        if (headings.isEmpty()) return ChapterParseResult(emptyList())

        val warnings = mutableListOf<String>()
        val afterToc = dropLeadingTableOfContents(headings, lines, warnings)
        if (afterToc.isEmpty()) {
            // 整个文件只有标题行时，仍保留这些行；不把它们误判成目录而全部丢弃。
            return buildResult(text, headings, warnings)
        }
        addSequenceWarnings(afterToc, warnings)
        addPrefixWarning(text, afterToc.first(), warnings)
        return buildResult(text, afterToc, warnings)
    }

    private fun buildResult(
        text: String,
        headings: List<Heading>,
        warnings: MutableList<String>,
    ): ChapterParseResult {
        val candidates = headings.mapIndexed { index, heading ->
            ChapterCandidate(
                title = heading.title,
                order = index,
                startOffset = heading.lineStart,
                endOffset = headings.getOrNull(index + 1)?.lineStart ?: text.length,
                headingOffset = heading.headingOffset,
                detectionSource = ChapterDetectionSource.REGEX,
                confidence = heading.confidence,
                level = if (heading.kind == HeadingKind.VOLUME) 1 else 0,
            )
        }
        return ChapterParseResult(candidates = candidates, warnings = warnings)
    }

    private data class MatchedHeading(
        val kind: HeadingKind,
        val number: Int?,
        val confidence: Float,
    )

    private fun matchHeading(title: String): MatchedHeading? {
        patterns.forEach { pattern ->
            val match = pattern.regex.matchEntire(title) ?: return@forEach
            val number = match.groupValues.getOrNull(1)?.let(::parseNumber)
            val rest = match.groupValues.getOrNull(2).orEmpty()
            if (rest.trimStart().firstOrNull()?.let { it == '是' || it == '的' } == true) {
                return null
            }
            return MatchedHeading(pattern.kind, number, pattern.confidence)
        }
        return null
    }

    private fun dropLeadingTableOfContents(
        headings: List<Heading>,
        lines: List<Line>,
        warnings: MutableList<String>,
    ): List<Heading> {
        if (headings.size < MIN_TOC_HEADINGS) return headings

        // 目录区：连续标题之间没有非空正文行。正文短句测试不会满足该条件。
        var end = 0
        while (end + 1 < headings.size) {
            val current = headings[end]
            val next = headings[end + 1]
            if (nonBlankLinesBetween(lines, current.lineIndex, next.lineIndex) > 0) break
            end++
        }
        val tocSize = end + 1
        if (tocSize < MIN_TOC_HEADINGS || tocSize >= headings.size) return headings

        // 只有目录标题在正文中再次出现时才过滤，避免把“前四章都很短”误判成目录。
        // 真实目录通常会在正文第一章处重复同一标题，这一条件比单纯看行距更安全。
        val duplicateInside = (1 until tocSize).firstOrNull { index ->
            headings.take(index).any { previous ->
                normalizeTitle(previous.title) == normalizeTitle(headings[index].title)
            }
        }
        val dropCount = if (duplicateInside != null) {
            duplicateInside
        } else {
            val later = headings.drop(tocSize)
            val firstRealChapter = later.indexOfFirst { candidate ->
                headings.take(tocSize).any { toc ->
                    normalizeTitle(toc.title) == normalizeTitle(candidate.title)
                }
            }
            if (firstRealChapter < 0) {
                warnings += "开头存在疑似目录，但未找到重复标题，未自动过滤"
                return headings
            }
            tocSize + firstRealChapter
        }
        if (dropCount <= 0 || dropCount >= headings.size) return headings
        warnings += "开头目录区已过滤 $dropCount 个候选标题"
        return headings.drop(dropCount)
    }

    private fun normalizeTitle(value: String): String =
        value.filterNot { it.isWhitespace() || it == '\uFEFF' }

    private fun addSequenceWarnings(
        headings: List<Heading>,
        warnings: MutableList<String>,
    ) {
        var previous: Int? = null
        headings.forEach { heading ->
            if (heading.kind != HeadingKind.CHAPTER || heading.number == null) return@forEach
            val current = heading.number
            val last = previous
            if (last != null) {
                when {
                    current <= last -> warnings += "章节编号重复或回退：$last → $current"
                    current > last + 1 -> warnings += "章节编号跳跃：$last → $current"
                }
            }
            previous = current
        }
    }

    private fun addPrefixWarning(
        text: String,
        first: Heading,
        warnings: MutableList<String>,
    ) {
        if (first.lineStart > 0 && text.substring(0, first.lineStart).any { !it.isWhitespace() }) {
            warnings += "第一个章节标题前存在正文/目录文本，已保留在原文中"
        }
    }

    /** 使用与成熟解析器类似的位值规则，而不是逐字符拼数字。 */
    internal fun parseNumber(raw: String): Int? {
        val text = raw.filterNot { it.isWhitespace() }
        if (text.isEmpty()) return null
        val ascii = text.map { char ->
            if (char in '０'..'９') ('0' + (char - '０')) else char
        }.joinToString("")
        if (ascii.all { it.isDigit() }) {
            return ascii.toIntOrNull()
        }

        var total = 0L
        var section = 0L
        var current = 0L
        ascii.forEach { char ->
            val digit = DIGITS[char]
            if (digit != null) {
                current = digit.toLong()
                return@forEach
            }
            val unit = UNITS[char]
            if (unit != null) {
                if (current == 0L) current = 1
                section += current * unit
                current = 0
                return@forEach
            }
            if (char == '万' || char == '亿') {
                val scale = if (char == '万') 10_000L else 100_000_000L
                section += current
                total = (total + section) * scale
                section = 0
                current = 0
                return@forEach
            }
            return null
        }
        val result = total + section + current
        return result.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
    }

    private fun scanLines(text: String): List<Line> {
        val lines = mutableListOf<Line>()
        var start = 0
        while (start <= text.length) {
            val end = text.indexOf('\n', start).let { if (it == -1) text.length else it }
            lines += Line(start = start, text = text.substring(start, end))
            if (end == text.length) break
            start = end + 1
        }
        return lines
    }

    private fun cleanLine(value: String): String =
        value.trim { it.isWhitespace() || it == '\uFEFF' }

    private fun nonBlankLinesBetween(
        lines: List<Line>,
        leftLineIndex: Int,
        rightLineIndex: Int,
    ): Int = (leftLineIndex + 1 until rightLineIndex).count { lines[it].text.isNotBlank() }

    private fun looksLikeAdvertisement(title: String): Boolean {
        val lower = title.lowercase()
        return AD_MARKERS.any { lower.contains(it) }
    }

    private data class Line(val start: Int, val text: String)

    private companion object {
        const val MAX_LINE_LENGTH = 300
        const val MIN_TOC_HEADINGS = 4

        val DIGITS: Map<Char, Int> = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
            '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        val UNITS: Map<Char, Int> = mapOf(
            '十' to 10, '百' to 100, '千' to 1_000,
        )
        val AD_MARKERS = listOf(
            "http://", "https://", "www.", "最新网址", "请收藏", "笔趣阁", "手机阅读",
            "本章未完", "点击下一页", "广告", "加入书签",
        )
    }
}
