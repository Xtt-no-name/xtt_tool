package com.xtt.toolbox.domain.chapter

import com.xtt.toolbox.data.model.ChapterDetectionSource

/**
 * TXT 章节解析器。
 *
 * 不是逐行“匹配到第X章就切”的简单正则器，而是：
 * 1. 逐行扫描并保留 UTF-16 原文偏移；
 * 2. 识别行首、书名前缀、正文标记或库标记后的候选标题；
 * 3. 对候选做结构评分，拒绝正文语句、广告和低质量候选；
 * 4. 合并相邻的“标题行 + 标题正文粘连行”；
 * 5. 识别开头目录和重复标题；
 * 6. 基于全文编号做诊断，最后才确定边界。
 *
 * 设计参考（只借鉴算法思想，未复制代码）：
 * - Qingdu Reader 的 ChapterTitleMatcher / TxtChapterSplitter（Apache-2.0）；
 * - novel-chapter-splitter 的候选/干扰行思路（MIT）。
 *
 * 解析器绝不修改正文；[ChapterParseResult.candidates] 的 start/end 始终指向
 * 规范化文本的原始位置。
 */
class RegexChapterParser : ChapterParser {
    private enum class HeadingKind {
        CHAPTER,
        VOLUME,
        SPECIAL,
    }

    private data class Line(val start: Int, val text: String)

    private data class Heading(
        val lineIndex: Int,
        val lineStart: Int,
        val headingOffset: Int,
        val title: String,
        val kind: HeadingKind,
        val number: Int?,
        val confidence: Float,
        val prefix: String,
        val truncatedTitle: Boolean,
        val indented: Boolean,
    )

    private data class Match(
        val tokenStart: Int,
        val tokenEnd: Int,
        val number: Int?,
        val kind: HeadingKind,
        val prefix: String,
    )

    private data class PrefixResult(val allowed: Boolean, val value: String)

    override fun parse(text: String): ChapterParseResult {
        if (text.isEmpty()) return ChapterParseResult(emptyList())
        val started = System.nanoTime()
        val lines = scanLines(text)
        val candidates = mutableListOf<Heading>()
        var rejected = 0
        var lowConfidence = 0

        lines.forEachIndexed { lineIndex, line ->
            val heading = detectHeading(lineIndex, line)
            if (heading != null) {
                if (heading.confidence < MIN_CONFIDENCE) {
                    rejected++
                    lowConfidence++
                } else {
                    candidates += heading
                }
                return@forEachIndexed
            }
            if (line.text.isNotBlank()) {
                // 统计候选被结构规则排除的行，便于诊断；不把普通正文当作候选。
                val trimmed = cleanLine(line.text)
                if (looksLikeHeadingToken(trimmed)) rejected++
            }
        }

        val merged = mergeAdjacentDuplicateHeadings(candidates)
        val warnings = mutableListOf<String>()
        val afterToc = dropRepeatedTocHeadings(merged, lines, warnings)
        val accepted = afterToc
        addSequenceWarnings(accepted, warnings)
        addPrefixWarning(text, accepted.firstOrNull(), warnings)

        val chapterCandidates = accepted.mapIndexed { index, heading ->
            val end = accepted.getOrNull(index + 1)?.lineStart ?: text.length
            ChapterCandidate(
                title = heading.title.ifBlank { cleanLine(lines[heading.lineIndex].text) },
                order = index,
                startOffset = heading.lineStart,
                endOffset = end,
                headingOffset = heading.headingOffset,
                detectionSource = ChapterDetectionSource.REGEX,
                confidence = heading.confidence,
                level = if (heading.kind == HeadingKind.VOLUME) 1 else 0,
            )
        }
        val diagnostics = ChapterParseDiagnostics(
            candidateCount = candidates.size,
            acceptedCount = chapterCandidates.size,
            rejectedCount = rejected,
            lowConfidenceCount = lowConfidence,
            tocDroppedCount = (merged.size - afterToc.size).coerceAtLeast(0),
            duplicateDroppedCount = (candidates.size - merged.size).coerceAtLeast(0),
            emptyChapterCount = chapterCandidates.count { chapter ->
                text.substring(chapter.startOffset, chapter.endOffset)
                    .substring((chapter.headingOffset - chapter.startOffset).coerceIn(0, chapter.endOffset - chapter.startOffset))
                    .isBlank()
            },
            shortChapterCount = chapterCandidates.count { chapter ->
                text.substring(chapter.startOffset, chapter.endOffset).length < 50
            },
            durationMs = (System.nanoTime() - started) / 1_000_000L,
        )
        return ChapterParseResult(chapterCandidates, warnings, diagnostics)
    }

    private fun detectHeading(lineIndex: Int, line: Line): Heading? {
        val raw = line.text
        val trimmed = raw.trimStart()
        if (trimmed.isBlank()) return null
        val leading = raw.length - trimmed.length
        val remark = detectSpecial(trimmed)
        if (remark != null) {
            val prefix = prefixOf(trimmed, remark.tokenStart)
            if (!prefix.allowed) return null
            return Heading(
                lineIndex = lineIndex,
                lineStart = line.start,
                headingOffset = line.start + leading + remark.tokenStart,
                title = cleanTitle(trimmed.substring(remark.tokenStart)),
                kind = HeadingKind.SPECIAL,
                number = null,
                confidence = 0.78f,
                prefix = prefix.value,
                truncatedTitle = false,
                indented = leading > 0,
            )
        }

        val match = findNumberedToken(trimmed)
        if (match == null) {
            val unnumbered = detectUnnumberedHeading(trimmed, lineIndex, line)
            return unnumbered
        }
        val prefix = prefixOf(trimmed, match.tokenStart)
        if (!prefix.allowed) return null

        val rawAfterToken = trimmed.substring(match.tokenEnd)
        val hasSeparator = rawAfterToken.firstOrNull()?.let {
            it.isWhitespace() || it in SEPARATORS
        } == true
        val rest = rawAfterToken.trimStart { it.isWhitespace() || it in SEPARATORS }
        if (!hasSeparator && rest.startsWith('是') && rest.length < 2) {
            return null
        }
        if (looksLikeAnnouncement(rest)) return null
        val extracted = extractTitle(rest, trimmed)
        // 书名/卷名括号里如果直接包含“第X回”，括号本身属于标题边界。
        val bracketClose = if (trimmed.startsWith("【") && match.tokenStart > 0) {
            trimmed.indexOf('】', match.tokenEnd)
        } else {
            -1
        }
        val bracketTitle = bracketClose
            .takeIf { it > match.tokenEnd }
            ?.let { trimmed.substring(match.tokenStart, it).trim() }
        val fullTitle = cleanTitle(trimmed.substring(match.tokenStart))
        val hasVisibleBody = bracketTitle == null && extracted.title.length < rest.length - 2
        val titleResult = when {
            bracketTitle != null -> TitleResult(bracketTitle, false)
            hasVisibleBody -> extracted
            else -> TitleResult(fullTitle, fullTitle.length > MAX_TITLE_LENGTH)
        }
        val title = if (titleResult.title.length > MAX_TITLE_LENGTH) {
            titleResult.title.take(MAX_TITLE_LENGTH).trimEnd()
        } else {
            titleResult.title
        }
        val confidence = confidenceOf(
            prefix = prefix.value,
            title = title,
            lineLength = trimmed.length,
            truncated = titleResult.truncated,
        )
        return Heading(
            lineIndex = lineIndex,
            lineStart = line.start,
            headingOffset = line.start + leading + match.tokenStart,
            title = title,
            kind = match.kind,
            number = match.number,
            confidence = confidence,
            prefix = prefix.value,
            truncatedTitle = titleResult.truncated,
            indented = leading > 0,
        )
    }

    private fun findNumberedToken(line: String): Match? {
        NUMBERED_TOKEN.findAll(line).forEach { match ->
            val tokenStart = match.range.first
            val unit = match.groupValues[2]
            val number = match.groupValues[1].let(::parseNumber)
            val kind = when (unit) {
                "卷", "部", "篇", "册" -> HeadingKind.VOLUME
                else -> HeadingKind.CHAPTER
            }
            val prefix = prefixOf(line, tokenStart)
            if (!prefix.allowed) return@forEach
            return Match(
                tokenStart = tokenStart,
                tokenEnd = match.range.last + 1,
                number = number,
                kind = kind,
                prefix = prefix.value,
            )
        }
        // 卷X 形式
        val volume = VOLUME_TOKEN.find(line)
        if (volume != null) {
            val prefix = prefixOf(line, volume.range.first)
            if (prefix.allowed) {
                return Match(
                    tokenStart = volume.range.first,
                    tokenEnd = volume.range.last + 1,
                    number = parseNumber(volume.groupValues[1]),
                    kind = HeadingKind.VOLUME,
                    prefix = prefix.value,
                )
            }
        }
        // Chapter 1 形式
        val english = ENGLISH_TOKEN.find(line)
        if (english != null) {
            val prefix = prefixOf(line, english.range.first)
            if (prefix.allowed) {
                return Match(
                    tokenStart = english.range.first,
                    tokenEnd = english.range.last + 1,
                    number = english.groupValues[1].toIntOrNull(),
                    kind = HeadingKind.CHAPTER,
                    prefix = prefix.value,
                )
            }
        }
        return null
    }

    private fun detectSpecial(line: String): Match? {
        val match = SPECIAL_TOKEN.find(line) ?: return null
        val prefix = prefixOf(line, match.range.first)
        if (!prefix.allowed) return null
        return Match(
            tokenStart = match.range.first,
            tokenEnd = match.range.last + 1,
            number = null,
            kind = HeadingKind.SPECIAL,
            prefix = prefix.value,
        )
    }

    /**
     * 有明确“正文/VIP”前缀、但无编号的短标题。没有前缀的普通短句不猜测，
     * 因为这类文本与正文无法仅靠单行可靠区分。
     */
    private fun detectUnnumberedHeading(line: String, lineIndex: Int, lineValue: Line): Heading? {
        val match = UNNUMBERED_HEADING.matchEntire(line) ?: return null
        val title = match.groupValues[2].trim()
        if (title.length !in 2..40) return null
        if (title.any { it == '。' || it == '！' || it == '？' || it == '；' }) return null
        if (title.any { it.isDigit() } || title.contains('第')) return null
        if (looksLikeAnnouncement(title)) return null
        val leading = line.length - line.trimStart().length
        return Heading(
            lineIndex = lineIndex,
            lineStart = lineValue.start,
            headingOffset = lineValue.start + leading + match.range.first,
            title = "正文 $title",
            kind = HeadingKind.CHAPTER,
            number = null,
            confidence = 0.58f,
            prefix = match.groupValues[1],
            truncatedTitle = false,
            indented = leading > 0,
        )
    }

    private fun prefixOf(line: String, tokenStart: Int): PrefixResult {
        val before = line.substring(0, tokenStart).trim()
        if (before.isEmpty()) return PrefixResult(true, "")
        if (before.all { it.isWhitespace() || it in PREFIX_PUNCTUATION }) {
            return PrefixResult(true, before)
        }
        val deBracketed = before
            .replace(Regex("""^【[^】]{0,80}】\s*"""), "")
            .replace(Regex("""^\[[^\]]{0,80}\]\s*"""), "")
            .replace(Regex("""^《[^》]{0,80}》\s*"""), "")
            .trim()
        if (deBracketed.isEmpty()) return PrefixResult(true, before)
        if (KNOWN_PREFIX_PATTERN.containsMatchIn(before)) return PrefixResult(true, before)
        if (before.length <= 4 && before.all { it.isWhitespace() || it in PREFIX_PUNCTUATION }) {
            return PrefixResult(true, before)
        }
        return PrefixResult(false, before)
    }

    private data class TitleResult(val title: String, val truncated: Boolean)

    private fun extractTitle(rest: String, line: String): TitleResult {
        var title = rest.trim { it.isWhitespace() || it in SEPARATORS }
        if (title.isEmpty()) return TitleResult("", false)
        var truncated = false

        // 标题与正文粘连时，取第一个明显的句子结束符/叙事起点之前的部分。
        val sentence = title.indexOfFirst { it == '。' || it == '！' || it == '？' || it == '；' }
        if (sentence >= 2 && line.length > 80) {
            title = title.substring(0, sentence).trimEnd()
        } else {
            val bodyMarker = BODY_MARKERS
                .map { marker -> title.indexOf(marker) }
                .filter { it in 2..120 }
                .minOrNull()
            if (bodyMarker != null && line.length > 80) {
                title = title.substring(0, bodyMarker).trimEnd()
            }
        }
        if (title.length > MAX_TITLE_LENGTH) {
            title = title.take(MAX_TITLE_LENGTH).trimEnd()
            truncated = true
        }
        title = title.trim { it.isWhitespace() || it in SEPARATORS }
        return TitleResult(title, truncated)
    }

    private fun confidenceOf(
        prefix: String,
        title: String,
        lineLength: Int,
        truncated: Boolean,
    ): Float {
        var score = 0.86f
        if (prefix.isEmpty()) score += 0.05f
        if (prefix.isNotEmpty() && KNOWN_PREFIX_PATTERN.containsMatchIn(prefix)) score += 0.01f
        if (title.length in 1..60) score += 0.04f
        if (title.length > 120) score -= 0.08f
        if (lineLength > 300) score -= 0.04f
        if (truncated) score -= 0.03f
        if (looksLikeAnnouncement(title)) score -= 0.35f
        return score.coerceIn(0f, 1f)
    }

    private fun mergeAdjacentDuplicateHeadings(headings: List<Heading>): List<Heading> {
        if (headings.isEmpty()) return headings
        val result = mutableListOf<Heading>()
        headings.sortedBy { it.lineIndex }.forEach { current ->
            val previous = result.lastOrNull()
            if (previous != null && current.lineIndex - previous.lineIndex <= 3 &&
                normalizeTitle(previous.title) == normalizeTitle(current.title) &&
                previous.indented != current.indented
            ) {
                return@forEach
            }
            if (previous != null && sameChapter(previous, current)) {
                return@forEach
            }
            if (previous != null && current.lineIndex - previous.lineIndex <= 3 &&
                adjacentMarkerDuplicate(previous, current)
            ) {
                return@forEach
            }
            result += current
        }
        return result
    }

    /** 只有“第X回”标记和下一行同一回目时合并，不能合并“上/中/下”等真实同号分节。 */
    private fun adjacentMarkerDuplicate(left: Heading, right: Heading): Boolean {
        if (left.number == null || left.number != right.number) return false
        if (left.kind != right.kind) return false
        val a = normalizeTitle(left.title)
        val b = normalizeTitle(right.title)
        if (a == b) return false
        val aMarker = isMarkerOnly(a)
        val bMarker = isMarkerOnly(b)
        if (aMarker || bMarker) return true
        return (a.contains("彩蛋") || b.contains("彩蛋"))
    }

    private fun isMarkerOnly(value: String): Boolean =
        value.matches(Regex("""^第[0-9零〇一二三四五六七八九十百千万两]+[章节回集话]$"""))

    private fun isSubsection(left: Heading, right: Heading): Boolean {
        if (left.number == null || left.number != right.number) return false
        if (left.kind != right.kind) return false
        val suffix = Regex("""[（(](上|中|下|上篇|中篇|下篇|[一二三四五六七八九十]+)[)）]$""")
        val a = coreTitle(left.title).replace(suffix, "").trim()
        val b = coreTitle(right.title).replace(suffix, "").trim()
        return a == b
    }

    private fun sameChapter(left: Heading, right: Heading): Boolean {
        if (left.kind != right.kind) return false
        if (left.number != null && right.number != null && left.number != right.number) return false
        if (normalizeTitle(left.title).isNotEmpty() &&
            normalizeTitle(left.title) == normalizeTitle(right.title)
        ) {
            return false
        }
        // “第二回”标题页后面可能隔几十行才出现正文行；只对纯回目标题做远距离合并，
        // 避免把“第一章（上）/（中）”这种真实分节合并。
        if (left.number != null && left.number == right.number &&
            (isMarkerOnly(normalizeTitle(left.title)) || isMarkerOnly(normalizeTitle(right.title)) ||
                left.title.contains("内含") || right.title.contains("内含") ||
                left.title.contains("彩蛋") || right.title.contains("彩蛋"))
        ) {
            return true
        }
        val a = coreTitle(left.title)
        val b = coreTitle(right.title)
        if (a.isNotEmpty() && b.isNotEmpty()) {
            // 完全相同的独立标题可能是作者刻意保留的正文/章节歧义，不能强行合并。
            if (a == b) return false
            if (b.startsWith(a) || a.startsWith(b)) return true
            return false
        }
        return a.isEmpty() && b.isEmpty() && left.number != null && right.number == left.number
    }

    private fun coreTitle(value: String): String =
        normalizeTitle(value).replaceFirst(
            Regex("""^第[0-9零〇一二三四五六七八九十百千万两]+[章节回集话]"""),
            "",
        )

    private fun dropRepeatedTocHeadings(
        headings: List<Heading>,
        lines: List<Line>,
        warnings: MutableList<String>,
    ): List<Heading> {
        if (headings.size < 3) return headings
        val removed = mutableSetOf<Int>()
        headings.forEachIndexed { index, heading ->
            val next = headings.getOrNull(index + 1)
            // 两个完全相同的紧邻标题可能是作者保留的正文/标题歧义，不能当目录删除。
            if (next != null && next.lineIndex - heading.lineIndex <= 1 &&
                normalizeTitle(next.title) == normalizeTitle(heading.title)
            ) {
                return@forEachIndexed
            }
            val bodyLines = nonBlankLinesBetween(
                lines,
                heading.lineIndex,
                next?.lineIndex ?: lines.size,
            )
            if (bodyLines > 1) return@forEachIndexed
            val laterDuplicate = headings.drop(index + 1).any { later ->
                sameTocCandidate(heading, later) &&
                    nonBlankLinesBetween(lines, later.lineIndex, lines.size) > 0
            }
            if (laterDuplicate) removed += index
        }
        if (removed.isEmpty()) return headings
        warnings += "已排除 ${removed.size} 个疑似目录/重复标题"
        return headings.filterIndexed { index, _ -> index !in removed }
    }

    private fun sameTocCandidate(left: Heading, right: Heading): Boolean =
        sameChapter(left, right) ||
            (normalizeTitle(left.title).isNotEmpty() &&
                normalizeTitle(left.title) == normalizeTitle(right.title))

    private fun addSequenceWarnings(
        headings: List<Heading>,
        warnings: MutableList<String>,
    ) {
        var previous: Int? = null
        var previousHeading: Heading? = null
        var duplicates = 0
        var jumps = 0
        var firstDuplicate: String? = null
        var firstJump: String? = null
        headings.forEach { heading ->
            if (heading.kind != HeadingKind.CHAPTER || heading.number == null) return@forEach
            val current = heading.number
            val last = previous
            if (last != null) {
                when {
                    current <= last -> {
                        if (previousHeading == null || !isSubsection(previousHeading!!, heading)) {
                            duplicates++
                            if (firstDuplicate == null) firstDuplicate = "$last → $current"
                        }
                    }
                    current > last + 1 -> {
                        jumps++
                        if (firstJump == null) firstJump = "$last → $current"
                    }
                }
            }
            previous = current
            previousHeading = heading
        }
        if (duplicates > 0) {
            warnings += "章节编号重复或回退 $duplicates 处（例：$firstDuplicate）"
        }
        if (jumps > 0) {
            warnings += "章节编号跳跃 $jumps 处（例：$firstJump）"
        }
    }

    private fun addPrefixWarning(
        text: String,
        first: Heading?,
        warnings: MutableList<String>,
    ) {
        if (first != null && first.lineStart > 0 &&
            text.substring(0, first.lineStart).any { !it.isWhitespace() }
        ) {
            warnings += "第一个章节标题前存在正文/目录文本，已保留在原文中"
        }
    }

    private fun looksLikeHeadingToken(line: String): Boolean =
        NUMBERED_TOKEN.containsMatchIn(line) || SPECIAL_TOKEN.containsMatchIn(line)

    private fun looksLikeAnnouncement(value: String): Boolean {
        val lower = value.lowercase()
        val marker = ANNOUNCEMENT_MARKERS.any { lower.contains(it) }
        val contextHits = ANNOUNCEMENT_CONTEXT.count { lower.contains(it) }
        return marker || contextHits >= 2
    }

    /** 使用位值规则解析中文数字和阿拉伯数字。 */
    internal fun parseNumber(raw: String): Int? {
        val text = raw.filterNot { it.isWhitespace() }
        if (text.isEmpty()) return null
        val ascii = text.map { char ->
            if (char in '０'..'９') ('0' + (char - '０')) else char
        }.joinToString("")
        if (ascii.all { it.isDigit() }) return ascii.toIntOrNull()

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

    private fun cleanTitle(value: String): String =
        cleanLine(value).trim { it in SEPARATORS || it.isWhitespace() }

    private fun normalizeTitle(value: String): String =
        value.filterNot { it.isWhitespace() || it == '\uFEFF' || it in SEPARATORS }

    private fun nonBlankLinesBetween(
        lines: List<Line>,
        leftLineIndex: Int,
        rightLineIndex: Int,
    ): Int = (leftLineIndex + 1 until rightLineIndex.coerceAtMost(lines.size))
        .count { lines[it].text.isNotBlank() }

    private companion object {
        const val MAX_TITLE_LENGTH = 300
        const val MIN_CONFIDENCE = 0.55f
        val SEPARATORS = setOf(':', '：', '、', '.', '，', ',', '-', '—', '·', '•')
        val PREFIX_PUNCTUATION = setOf('【', '】', '[', ']', '《', '》', '"', '「', '」', '『', '』', '*', '-', '—')
        val KNOWN_PREFIX_PATTERN = Regex(
            """^(正文卷?|VIP卷?|作品相关)(?=$|[\s\u3000:：])""",
            RegexOption.IGNORE_CASE,
        )
        val BODY_MARKERS = listOf(
            "却说", "话说", "且说", "再说", "次日", "第二天", "第二日", "此时",
            "原来", "只见", "当下", "且说那", "却说那", "话说那", "后来", "不多时",
            "作者", "首发", "更新时间",
        )
        val ANNOUNCEMENT_CONTEXT = listOf("大家", "请", "投票", "收藏", "更新", "感谢", "本章", "字数")
        val ANNOUNCEMENT_MARKERS = listOf(
            "http://", "https://", "www.", "最新网址", "请收藏", "笔趣阁", "手机阅读",
            "本章未完", "点击下一页", "广告", "加入书签", "投票", "收藏", "字数",
        )
        val NUMBERED_TOKEN = Regex(
            """第[\s\u3000]*([0-9零〇一二三四五六七八九十百千万两]+)[\s\u3000]*([章节回集话卷部篇册])""",
        )
        val VOLUME_TOKEN = Regex(
            """卷[\s\u3000]*([0-9零〇一二三四五六七八九十百千万两]+)""",
        )
        val SPECIAL_TOKEN = Regex(
            """(序章|楔子|引子|前言|序言|序幕|尾声|终章|后记|番外|特别篇)(?=$|[\s\u3000:：、.\-—])""",
        )
        val UNNUMBERED_HEADING = Regex("""^(正文|VIP)[\s\u3000:：]+(.+)$""", RegexOption.IGNORE_CASE)
        val ENGLISH_TOKEN = Regex("""chapter[\s\u3000]+(\d+)""", RegexOption.IGNORE_CASE)
        val DIGITS: Map<Char, Int> = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
            '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        val UNITS: Map<Char, Int> = mapOf(
            '十' to 10, '百' to 100, '千' to 1_000,
        )
    }
}
