package com.xtt.toolbox.domain.content

import com.xtt.toolbox.data.model.ChapterDetectionSource
import com.xtt.toolbox.domain.chapter.ChapterCandidate
import com.xtt.toolbox.domain.chapter.TextOffsets
import java.util.Locale

data class ParsedMarkdown(
    val title: String,
    val author: String?,
    val text: String,
    val chapters: List<ChapterCandidate>,
    val blocks: List<BookBlock>,
    val images: List<MarkdownImageReference>,
    val warnings: List<String>,
)

/** Markdown 图片引用；[key] 临时关联 Block，导入成功后再替换为 BookResource id。 */
data class MarkdownImageReference(
    val key: String,
    val path: String,
    val alt: String?,
)

/**
 * 无第三方依赖的 Markdown 解析器。
 *
 * 只生成应用已有的 [BookBlock] / [BookBlockStyle] / [InlineSpan]；图片语法保留为占位块，
 * 因为 SAF 单文件授权不能可靠读取原文件同目录资源，也不会自动访问网络。
 */
class MarkdownParser {
    private data class FrontMatter(
        val body: String,
        val title: String?,
        val author: String?,
        val warning: String?,
    )

    private data class InlineResult(
        val text: String,
        val spans: List<InlineSpan>,
    )

    private data class PendingChapter(
        val title: String,
        val start: Int,
        val level: Int,
        val source: ChapterDetectionSource,
    )

    private data class FootnoteExtraction(
        val body: String,
        val definitions: LinkedHashMap<String, String>,
        val warnings: List<String>,
    )

    fun parse(rawText: String, sourceName: String): ParsedMarkdown {
        val normalized = TextOffsets.normalizeNewlines(rawText).removePrefix("\uFEFF")
        val front = parseFrontMatter(normalized)
        val footnoteData = extractFootnotes(front.body)
        val lines = footnoteData.body.split('\n')
        val footnoteNumbers = collectFootnoteNumbers(footnoteData.body)
        val footnoteTargets = footnoteData.definitions.keys.mapNotNull { id ->
            footnoteNumbers[id]?.let { number -> id to "md-footnote-$number" }
        }.toMap()
        val footnoteRefBlocks = LinkedHashMap<String, MutableList<String>>()
        val textBuilder = StringBuilder()
        val blocks = mutableListOf<BookBlock>()
        val pendingChapters = mutableListOf<PendingChapter>()
        val images = mutableListOf<MarkdownImageReference>()
        val warnings = mutableListOf<String>()
        if (front.warning != null) warnings += front.warning
        warnings += footnoteData.warnings
        footnoteNumbers.keys
            .filterNot(footnoteData.definitions::containsKey)
            .forEach { warnings += "脚注引用缺少定义：$it，已显示为占位标记。" }
        var blockOrder = 0
        var firstH1: String? = null

        fun inlineResult(value: String): InlineResult =
            parseInline(
                value,
                footnoteNumbers,
                footnoteData.definitions.keys,
                footnoteTargets,
            )

        fun appendBlock(
            value: String,
            type: BookBlockType,
            level: Int = 0,
            style: BookBlockStyle? = null,
            spans: List<InlineSpan> = emptyList(),
            blockId: String = "md-block-${blockOrder}",
            footnoteRefIds: List<String> = emptyList(),
        ) {
            if (value.isBlank() && type != BookBlockType.RULE) return
            val start = textBuilder.length
            textBuilder.append(value)
            if (!value.endsWith('\n')) textBuilder.append('\n')
            val end = textBuilder.length
            if (start == end) return
            val newBlock = BookBlock(
                blockId = blockId,
                order = blockOrder++,
                type = type,
                startOffset = start,
                endOffset = end,
                level = level,
                style = style,
                spans = spans,
                footnoteRefIds = footnoteRefIds,
            )
            blocks += newBlock
            newBlock.spans.mapNotNull { it.targetBlockId }.forEach { target ->
                footnoteRefBlocks.getOrPut(target) { mutableListOf() }.add(newBlock.blockId)
            }
        }

        fun appendRule() {
            val offset = textBuilder.length
            blocks += BookBlock(
                blockId = "md-block-${blockOrder}",
                order = blockOrder++,
                type = BookBlockType.RULE,
                startOffset = offset,
                endOffset = offset,
            )
        }

        fun appendParagraph(value: String, type: BookBlockType = BookBlockType.PARAGRAPH) {
            val inline = inlineResult(value.trim())
            if (inline.text.isBlank()) return
            appendBlock(
                inline.text,
                type,
                0,
                if (type == BookBlockType.QUOTE) {
                    BookBlockStyle(marginTopEm = 0.25f, marginBottomEm = 0.25f)
                } else {
                    null
                },
                inline.spans,
            )
        }

        fun splitTableRow(line: String): List<String> {
            val body = line.trim().removePrefix("|").removeSuffix("|")
            val cells = mutableListOf<String>()
            val current = StringBuilder()
            var escaped = false
            body.forEach { ch ->
                when {
                    escaped -> {
                        current.append(ch)
                        escaped = false
                    }

                    ch == '\\' -> escaped = true
                    ch == '|' -> {
                        cells += current.toString().trim()
                        current.clear()
                    }

                    else -> current.append(ch)
                }
            }
            if (escaped) current.append('\\')
            cells += current.toString().trim()
            return cells
        }

        fun separatorAlignments(line: String): List<BookTextAlign>? {
            val cells = splitTableRow(line)
            if (cells.isEmpty()) return null
            return cells.map { cell ->
                if (!SEPARATOR_CELL.matches(cell.trim())) return null
                val left = cell.trim().startsWith(":")
                val right = cell.trim().endsWith(":")
                when {
                    left && right -> BookTextAlign.CENTER
                    right -> BookTextAlign.END
                    else -> BookTextAlign.START
                }
            }
        }

        fun parseTable(start: Int): Pair<BookTable, Int>? {
            if (start + 1 >= lines.size) return null
            val headerLine = lines[start].trim()
            val separatorLine = lines[start + 1].trim()
            if (!headerLine.startsWith("|") || !headerLine.endsWith("|") ||
                !separatorLine.startsWith("|") || !separatorLine.endsWith("|")
            ) {
                return null
            }
            val headerCells = splitTableRow(headerLine)
            val alignments = separatorAlignments(separatorLine) ?: return null
            if (headerCells.size != alignments.size || headerCells.isEmpty()) return null

            val rows = mutableListOf<List<BookTableCell>>()
            var next = start + 2
            while (next < lines.size) {
                val rowLine = lines[next].trim()
                if (!rowLine.startsWith("|") || !rowLine.endsWith("|")) break
                if (separatorAlignments(rowLine) != null) break
                val rawCells = splitTableRow(rowLine)
                val normalized = List(headerCells.size) { index ->
                    rawCells.getOrNull(index).orEmpty()
                }
                rows += normalized.map { cell ->
                    val parsed = inlineResult(cell)
                    BookTableCell(parsed.text, parsed.spans)
                }
                next++
            }
            val header = headerCells.map { cell ->
                val parsed = inlineResult(cell)
                BookTableCell(parsed.text, parsed.spans)
            }
            return BookTable(header = header, rows = rows, alignments = alignments) to next
        }

        fun isTableStart(index: Int): Boolean = parseTable(index) != null

        fun isDefinitionStart(index: Int): Boolean {
            if (index + 1 >= lines.size) return false
            val term = lines[index].trim()
            if (term.isEmpty() || term.startsWith("|") || term.startsWith(">") ||
                HEADING.matches(lines[index].trimEnd()) || LIST_ITEM.matches(lines[index]) ||
                isTableStart(index)
            ) {
                return false
            }
            return DEFINITION_LINE.matches(lines[index + 1])
        }

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                index++
                continue
            }

            val fenceMarker = when {
                trimmed.startsWith("```") -> "```"
                trimmed.startsWith("~~~") -> "~~~"
                else -> null
            }
            if (fenceMarker != null) {
                index++
                val code = mutableListOf<String>()
                while (index < lines.size && !lines[index].trim().startsWith(fenceMarker)) {
                    code += lines[index]
                    index++
                }
                if (index < lines.size) index++
                appendBlock(
                    code.joinToString("\n"),
                    BookBlockType.CODE,
                    spans = emptyList(),
                )
                continue
            }

            val heading = HEADING.matchEntire(line.trimEnd())
            if (heading != null) {
                val level = heading.groupValues[1].length
                val inline = inlineResult(heading.groupValues[2].trim())
                if (inline.text.isNotBlank()) {
                    val start = textBuilder.length
                    appendBlock(inline.text, BookBlockType.HEADING, level, spans = inline.spans)
                    if (level == 1 && firstH1 == null) firstH1 = inline.text
                    pendingChapters += PendingChapter(
                        title = inline.text,
                        start = start,
                        level = (level - 1).coerceAtLeast(0),
                        source = ChapterDetectionSource.MARKDOWN_HEADING,
                    )
                }
                index++
                continue
            }

            if (RULE.matches(trimmed)) {
                appendRule()
                index++
                continue
            }

            if (trimmed.startsWith(">")) {
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                    quote += lines[index].trimStart().removePrefix(">").trimStart()
                    index++
                }
                appendParagraph(quote.joinToString(" "), BookBlockType.QUOTE)
                continue
            }

            val unordered = LIST_ITEM.matchEntire(line)
            if (unordered != null) {
                val indent = unordered.groupValues[1].replace("\t", "    ").length
                val depth = (indent / 2).coerceIn(0, 8)
                val marker = unordered.groupValues[2]
                val listNumber = marker.trimEnd('.', ')').toIntOrNull() ?: 0
                val content = unordered.groupValues[3]
                val task = TASK_ITEM.matchEntire(content.trim())
                val taskChecked = task?.groupValues?.get(1)?.let {
                    it.equals("x", ignoreCase = true)
                }
                val inline = inlineResult(task?.groupValues?.get(2) ?: content.trim())
                if (inline.text.isNotBlank()) {
                    appendBlock(
                        inline.text,
                        BookBlockType.PARAGRAPH,
                        style = BookBlockStyle(
                            listItem = true,
                            listDepth = depth,
                            listNumber = listNumber,
                            taskChecked = taskChecked,
                        ),
                        spans = inline.spans,
                    )
                }
                index++
                continue
            }

            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val parsedTable = parseTable(index)
                if (parsedTable != null) {
                    val offset = textBuilder.length
                    blocks += BookBlock(
                        blockId = "md-block-${blockOrder}",
                        order = blockOrder++,
                        type = BookBlockType.TABLE,
                        startOffset = offset,
                        endOffset = offset,
                        table = parsedTable.first,
                    )
                    index = parsedTable.second
                    continue
                }
            }

            if (isDefinitionStart(index)) {
                val termInline = inlineResult(lines[index].trim())
                if (termInline.text.isNotBlank()) {
                    appendBlock(
                        termInline.text,
                        BookBlockType.DEFINITION_TERM,
                        spans = termInline.spans,
                    )
                }
                index++
                while (index < lines.size) {
                    val match = DEFINITION_LINE.matchEntire(lines[index]) ?: break
                    val definitionInline = inlineResult(match.groupValues[1].trim())
                    if (definitionInline.text.isNotBlank()) {
                        appendBlock(
                            definitionInline.text,
                            BookBlockType.DEFINITION,
                            spans = definitionInline.spans,
                        )
                    }
                    index++
                }
                continue
            }

            val image = IMAGE_ONLY.matchEntire(trimmed)
            if (image != null) {
                val alt = image.groupValues[1].ifBlank { "图片" }
                val path = image.groupValues[2].trim()
                val key = "md-image-${images.size}"
                images += MarkdownImageReference(key = key, path = path, alt = alt)
                val offset = textBuilder.length
                blocks += BookBlock(
                    blockId = "md-block-${blockOrder}",
                    order = blockOrder++,
                    type = BookBlockType.IMAGE,
                    startOffset = offset,
                    endOffset = offset,
                    resourceId = key,
                    alt = "图片：$alt",
                    style = BookBlockStyle(imageSourcePath = path),
                )
                index++
                continue
            }

            val paragraph = mutableListOf<String>()
            while (index < lines.size) {
                val next = lines[index].trim()
                if (next.isEmpty() || HEADING.matches(lines[index].trimEnd()) ||
                    next.startsWith("```") || next.startsWith("~~~") ||
                    RULE.matches(next) ||
                    next.startsWith(">") || LIST_ITEM.matches(lines[index]) ||
                    IMAGE_ONLY.matches(next) ||
                    isTableStart(index) || isDefinitionStart(index)
                ) {
                    break
                }
                paragraph += lines[index].trim()
                index++
            }
            if (paragraph.isNotEmpty()) {
                appendParagraph(paragraph.joinToString(" "))
            } else {
                // 防御性前进，避免异常行卡住循环。
                index++
            }
        }

        footnoteData.definitions.forEach { (id, content) ->
            val number = footnoteNumbers[id] ?: return@forEach
            val parsed = inlineResult(content)
            if (parsed.text.isBlank()) return@forEach
            val prefix = "[脚注 $number] "
            val shifted = parsed.spans.map {
                it.copy(start = it.start + prefix.length, end = it.end + prefix.length)
            }
            val blockId = "md-footnote-$number"
            appendBlock(
                prefix + parsed.text,
                BookBlockType.FOOTNOTE,
                style = BookBlockStyle(marginTopEm = 0.15f),
                spans = shifted,
                blockId = blockId,
                footnoteRefIds = footnoteRefBlocks[blockId].orEmpty().distinct(),
            )
        }

        val text = textBuilder.toString()
        if (text.isBlank() && blocks.none {
                it.type == BookBlockType.RULE || it.type == BookBlockType.IMAGE ||
                    it.type == BookBlockType.TABLE || it.type == BookBlockType.FOOTNOTE ||
                    it.type == BookBlockType.DEFINITION_TERM ||
                    it.type == BookBlockType.DEFINITION
            }
        ) {
            throw IllegalArgumentException("Markdown 没有可显示的正文")
        }

        val title = front.title?.takeIf { it.isNotBlank() }
            ?: firstH1?.takeIf { it.isNotBlank() }
            ?: sourceName.let {
                when {
                    it.endsWith(".markdown", ignoreCase = true) -> it.dropLast(9)
                    it.endsWith(".md", ignoreCase = true) -> it.dropLast(3)
                    else -> it
                }
            }.trim().ifBlank { "未命名 Markdown" }
        val chapters = buildChapters(text, pendingChapters, title)
        return ParsedMarkdown(
            title = title,
            author = front.author?.takeIf { it.isNotBlank() },
            text = text,
            chapters = chapters,
            blocks = blocks,
            images = images,
            warnings = warnings,
        )
    }

    private fun buildChapters(
        text: String,
        pending: List<PendingChapter>,
        fallbackTitle: String,
    ): List<ChapterCandidate> {
        if (pending.isEmpty()) {
            return listOf(
                ChapterCandidate(
                    title = "",
                    order = 0,
                    startOffset = 0,
                    endOffset = text.length,
                    headingOffset = 0,
                    detectionSource = ChapterDetectionSource.MARKDOWN_FALLBACK,
                    confidence = 0.7f,
                    level = 0,
                ),
            )
        }
        val starts = pending.sortedBy { it.start }
        val result = mutableListOf<ChapterCandidate>()
        if (starts.first().start > 0 && text.substring(0, starts.first().start).isNotBlank()) {
            result += ChapterCandidate(
                title = "前言",
                order = 0,
                startOffset = 0,
                endOffset = starts.first().start,
                headingOffset = 0,
                detectionSource = ChapterDetectionSource.MARKDOWN_FALLBACK,
                confidence = 0.6f,
                level = 0,
            )
        }
        starts.forEachIndexed { i, chapter ->
            val end = starts.getOrNull(i + 1)?.start ?: text.length
            result += ChapterCandidate(
                title = chapter.title,
                order = result.size,
                startOffset = chapter.start,
                endOffset = end,
                headingOffset = chapter.start,
                detectionSource = chapter.source,
                confidence = 1f,
                level = chapter.level,
            )
        }
        return result
    }

    private fun parseFrontMatter(text: String): FrontMatter {
        val lines = text.split('\n')
        if (lines.firstOrNull()?.trim() != "---") {
            return FrontMatter(text, null, null, null)
        }
        val close = (1 until lines.size).firstOrNull {
            val value = lines[it].trim()
            value == "---" || value == "..."
        } ?: return FrontMatter(text, null, null, null)
        var title: String? = null
        var author: String? = null
        var malformed = false
        for (i in 1 until close) {
            val line = lines[i].trim()
            if (line.isBlank() || line.startsWith("#")) continue
            val key = line.substringBefore(':', "").trim().lowercase(Locale.ROOT)
            if (key.isEmpty()) {
                malformed = true
                continue
            }
            val value = line.substringAfter(':', "").trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
            when (key) {
                "title" -> title = value.ifBlank { null }
                "author", "creator" -> author = value.ifBlank { null }
            }
        }
        val body = lines.drop(close + 1).joinToString("\n")
        return FrontMatter(
            body = body,
            title = title,
            author = author,
            warning = if (malformed) "Front Matter 存在无法识别的内容，已忽略并继续导入。" else null,
        )
    }

    private fun extractFootnotes(body: String): FootnoteExtraction {
        val lines = body.split('\n')
        val kept = mutableListOf<String>()
        val definitions = LinkedHashMap<String, String>()
        val warnings = mutableListOf<String>()
        var fence: String? = null
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            val openingFence = when {
                trimmed.startsWith("```") -> "```"
                trimmed.startsWith("~~~") -> "~~~"
                else -> null
            }
            if (fence != null) {
                kept += line
                if (openingFence == fence) fence = null
                index++
                continue
            }
            if (openingFence != null) {
                fence = openingFence
                kept += line
                index++
                continue
            }
            val match = FOOTNOTE_DEF.matchEntire(line)
            if (match == null) {
                kept += line
                index++
                continue
            }
            val id = match.groupValues[1].trim()
            val content = StringBuilder(match.groupValues[2].trim())
            var next = index + 1
            while (next < lines.size) {
                val continuation = lines[next]
                if (continuation.startsWith("    ") || continuation.startsWith("\t")) {
                    if (content.isNotEmpty()) content.append(' ')
                    content.append(continuation.trim())
                    next++
                } else {
                    break
                }
            }
            if (definitions.containsKey(id)) {
                warnings += "脚注定义重复：$id，已保留第一次定义。"
            } else {
                definitions[id] = content.toString()
                if (content.isBlank()) warnings += "脚注定义为空：$id。"
            }
            index = next
        }
        return FootnoteExtraction(
            body = kept.joinToString("\n"),
            definitions = definitions,
            warnings = warnings,
        )
    }

    private fun collectFootnoteNumbers(body: String): LinkedHashMap<String, Int> {
        val numbers = LinkedHashMap<String, Int>()
        FOOTNOTE_REF.findAll(body).forEach { match ->
            val id = match.groupValues[1]
            if (!numbers.containsKey(id)) numbers[id] = numbers.size + 1
        }
        return numbers
    }

    private fun parseInline(
        raw: String,
        footnoteNumbers: Map<String, Int> = emptyMap(),
        definedFootnotes: Set<String> = emptySet(),
        footnoteTargets: Map<String, String> = emptyMap(),
    ): InlineResult {
        val chars = StringBuilder()
        val flags = mutableListOf<InlineFlags>()

        fun append(value: String, flag: InlineFlags) {
            value.forEach {
                chars.append(it)
                flags += flag
            }
        }

        fun walk(value: String, head: Int, end: Int, inherited: InlineFlags) {
            var i = head
            while (i < end) {
                val c = value[i]
                if (c == '\\' && i + 1 < end) {
                    append(value[i + 1].toString(), inherited)
                    i += 2
                    continue
                }
                if (c == '`') {
                    val close = value.indexOf('`', i + 1)
                    if (close in i + 1 until end) {
                        append(value.substring(i + 1, close), inherited.copy(code = true))
                        i = close + 1
                        continue
                    }
                }
                if (i + 1 < end) {
                    val pair = value.substring(i, i + 2)
                    val wrapped = when (pair) {
                        "**", "__" -> "**" to inherited.copy(bold = true)
                        "~~" -> "~~" to inherited.copy(strike = true)
                        else -> null
                    }
                    if (wrapped != null) {
                        val close = value.indexOf(wrapped.first, i + 2)
                        if (close in i + 2 until end) {
                            walk(value, i + 2, close, wrapped.second)
                            i = close + 2
                            continue
                        }
                    }
                }
                if (c == '*' || c == '_') {
                    val close = value.indexOf(c, i + 1)
                    if (close in i + 1 until end) {
                        walk(value, i + 1, close, inherited.copy(italic = true))
                        i = close + 1
                        continue
                    }
                }
                if (c == '[' && i + 2 < end && value[i + 1] == '^') {
                    val textEnd = value.indexOf(']', i + 2)
                    if (textEnd in i + 2 until end) {
                        val id = value.substring(i + 2, textEnd).trim()
                        val number = footnoteNumbers[id]
                        val target = footnoteTargets[id]
                        val label = if (number != null && id in definedFootnotes) {
                            "[脚注 $number]"
                        } else {
                            "[脚注?]"
                        }
                        append(
                            label,
                            inherited.copy(
                                footnote = true,
                                targetBlockId = target,
                            ),
                        )
                        i = textEnd + 1
                        continue
                    }
                }
                if (c == '[') {
                    val textEnd = value.indexOf(']', i + 1)
                    if (textEnd in i + 1 until end && textEnd + 1 < end &&
                        value[textEnd + 1] == '('
                    ) {
                        val urlEnd = value.indexOf(')', textEnd + 2)
                        if (urlEnd in textEnd + 2 until end) {
                            val target = value.substring(textEnd + 2, urlEnd).trim()
                                .takeIf { it.isNotBlank() }
                            walk(
                                value,
                                i + 1,
                                textEnd,
                                inherited.copy(link = true, linkTarget = target),
                            )
                            i = urlEnd + 1
                            continue
                        }
                    }
                }
                if (c == '!' && i + 1 < end && value[i + 1] == '[') {
                    val textEnd = value.indexOf(']', i + 2)
                    if (textEnd in i + 2 until end) {
                        val urlEnd = if (textEnd + 1 < end && value[textEnd + 1] == '(') {
                            value.indexOf(')', textEnd + 2)
                        } else {
                            -1
                        }
                        val alt = value.substring(i + 2, textEnd)
                        append("[图片：${alt.ifBlank { "图片" }}]", inherited)
                        i = if (urlEnd in textEnd + 2 until end) urlEnd + 1 else textEnd + 1
                        continue
                    }
                }
                append(c.toString(), inherited)
                i++
            }
        }
        walk(raw, 0, raw.length, InlineFlags())
        val merged = mutableListOf<InlineSpan>()
        var runStart = 0
        var current = flags.firstOrNull()
        for (i in 1..flags.size) {
            val next = flags.getOrNull(i)
            if (next != current) {
                if (current != null && current.isStyled) {
                    merged += InlineSpan(
                        start = runStart,
                        end = i,
                        bold = current.bold,
                        italic = current.italic,
                        underline = current.underline,
                        strike = current.strike,
                        link = current.link,
                        code = current.code,
                        footnote = current.footnote,
                        targetBlockId = current.targetBlockId,
                        linkTarget = current.linkTarget,
                    )
                }
                runStart = i
                current = next
            }
        }
        val text = chars.toString()
        return InlineResult(
            text = text,
            spans = merged.map { it.copy(end = it.end.coerceAtMost(text.length)) }
                .filter { it.end > it.start },
        )
    }

    private data class InlineFlags(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val link: Boolean = false,
        val code: Boolean = false,
        val footnote: Boolean = false,
        val targetBlockId: String? = null,
        val linkTarget: String? = null,
    ) {
        val isStyled: Boolean
            get() = bold || italic || underline || strike || link || code || footnote
    }

    private companion object {
        val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
        val FENCE = Regex("^(`{3,}|~{3,})(?:\\s+.*)?$")
        val RULE = Regex("^\\s{0,3}([-*_])(?:\\s*\\1){2,}\\s*$")
        val LIST_ITEM = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
        val TASK_ITEM = Regex("^\\[([ xX])]\\s*(.*)$")
        val DEFINITION_LINE = Regex("^\\s{0,3}:\\s+(.*)$")
        val SEPARATOR_CELL = Regex("^:?-{3,}:?$")
        val IMAGE_ONLY = Regex("^!\\[([^]]*)]\\(([^)]*)\\)\\s*$")
        val FOOTNOTE_DEF = Regex("^\\s{0,3}\\[\\^([^\\]]+)]:\\s*(.*)$")
        val FOOTNOTE_REF = Regex("\\[\\^([^\\]\\s]+)]")
    }
}
