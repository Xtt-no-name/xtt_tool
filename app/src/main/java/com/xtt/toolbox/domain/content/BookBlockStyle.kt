package com.xtt.toolbox.domain.content

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** EPUB 块级文本对齐；不引入 Compose 类型，保证内容模型可在 JVM 测试。 */
enum class BookTextAlign {
    START,
    CENTER,
    END,
    JUSTIFY,
}

/**
 * 一个块内的行内样式范围；[start] 和 [end] 相对于块文本，不改变全文 UTF-16 锚点。
 */
data class InlineSpan(
    val start: Int,
    val end: Int,
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
    init {
        require(start >= 0) { "行内样式起点不能为负数" }
        require(end > start) { "行内样式终点必须大于起点" }
        require(bold || italic || underline || strike || link || code || footnote) {
            "行内样式不能为空"
        }
        require(linkTarget == null || link) { "链接目标必须属于链接样式" }
    }
}

/**
 * EPUB 块级样式。边距/缩进使用 em，渲染时受用户阅读设置和硬上限约束。
 * 作者 CSS 不允许覆盖用户字号和行距；[lineHeightMultiplier] 仅保留语义，当前不渲染。
 */
data class BookBlockStyle(
    val textAlign: BookTextAlign? = null,
    val textIndentEm: Float = 0f,
    val marginTopEm: Float = 0f,
    val marginBottomEm: Float = 0f,
    val lineHeightMultiplier: Float? = null,
    val listItem: Boolean = false,
    val listDepth: Int = 0,
    val listNumber: Int = 0,
    val taskChecked: Boolean? = null,
    val imageWidthFraction: Float? = null,
    val imageMaxWidthFraction: Float = 1f,
    val imageHeightDp: Float? = null,
    val imageSourcePath: String? = null,
) {
    init {
        require(textIndentEm in 0f..8f) { "段落缩进超出范围" }
        require(marginTopEm in 0f..4f) { "上边距超出范围" }
        require(marginBottomEm in 0f..4f) { "下边距超出范围" }
        require(lineHeightMultiplier == null || lineHeightMultiplier in 0.8f..2.4f) {
            "行距缩放超出范围"
        }
        require(listDepth in 0..8) { "列表层级超出范围" }
        require(listNumber in 0..9999) { "列表序号超出范围" }
        require(imageWidthFraction == null || imageWidthFraction in 0.1f..1f) {
            "图片宽度超出范围"
        }
        require(imageMaxWidthFraction in 0.1f..1f) { "图片最大宽度超出范围" }
        require(imageHeightDp == null || imageHeightDp in 80f..1200f) { "图片高度超出范围" }
    }

    val hasTextLayout: Boolean
        get() = textAlign != null || textIndentEm > 0f || marginTopEm > 0f ||
            marginBottomEm > 0f || listItem || listDepth > 0 || listNumber > 0 ||
            taskChecked != null

    val hasImageLayout: Boolean
        get() = imageWidthFraction != null || imageMaxWidthFraction < 1f ||
            imageHeightDp != null || imageSourcePath != null
}

/**
 * 块样式/行内范围的紧凑持久化编码。数据库只保存字符串，不把 Compose 或 XML 类型写入 DB。
 */
object BookBlockStyleCodec {
    fun encodeStyle(style: BookBlockStyle?): String? {
        if (style == null) return null
        val parts = buildList {
            style.textAlign?.let { add("a=${it.name}") }
            if (style.textIndentEm > 0f) add("i=${style.textIndentEm}")
            if (style.marginTopEm > 0f) add("t=${style.marginTopEm}")
            if (style.marginBottomEm > 0f) add("b=${style.marginBottomEm}")
            style.lineHeightMultiplier?.let { add("l=$it") }
            if (style.listItem) add("li=1")
            if (style.listDepth > 0) add("d=${style.listDepth}")
            if (style.listNumber > 0) add("n=${style.listNumber}")
            if (style.taskChecked != null) add("tc=${if (style.taskChecked) 1 else 0}")
            style.imageWidthFraction?.let { add("w=$it") }
            if (style.imageMaxWidthFraction != 1f) add("mw=${style.imageMaxWidthFraction}")
            style.imageHeightDp?.let { add("h=$it") }
            style.imageSourcePath?.let {
                add("src=" + URLEncoder.encode(it, StandardCharsets.UTF_8.name()))
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(";")
    }

    fun decodeStyle(raw: String?): BookBlockStyle? {
        if (raw.isNullOrBlank()) return null
        var align: BookTextAlign? = null
        var indent = 0f
        var top = 0f
        var bottom = 0f
        var lineHeight: Float? = null
        var listItem = false
        var listDepth = 0
        var listNumber = 0
        var taskChecked: Boolean? = null
        var width: Float? = null
        var maxWidth = 1f
        var height: Float? = null
        var imageSourcePath: String? = null
        raw.split(';').forEach { part ->
            val key = part.substringBefore('=', "").trim()
            val value = part.substringAfter('=', "").trim()
            if (key.isEmpty() || value.isEmpty()) return@forEach
            when (key) {
                "a" -> align = runCatching { BookTextAlign.valueOf(value) }.getOrNull()
                "i" -> indent = value.toFloatOrNull()?.coerceIn(0f, 8f) ?: indent
                "t" -> top = value.toFloatOrNull()?.coerceIn(0f, 4f) ?: top
                "b" -> bottom = value.toFloatOrNull()?.coerceIn(0f, 4f) ?: bottom
                "l" -> lineHeight = value.toFloatOrNull()?.coerceIn(0.8f, 2.4f)
                "li" -> listItem = value == "1"
                "d" -> listDepth = value.toIntOrNull()?.coerceIn(0, 8) ?: listDepth
                "n" -> listNumber = value.toIntOrNull()?.coerceIn(0, 9999) ?: listNumber
                "tc" -> taskChecked = value == "1"
                "w" -> width = value.toFloatOrNull()?.coerceIn(0.1f, 1f)
                "mw" -> maxWidth = value.toFloatOrNull()?.coerceIn(0.1f, 1f) ?: maxWidth
                "h" -> height = value.toFloatOrNull()?.coerceIn(80f, 1200f)
                "src" -> imageSourcePath = runCatching {
                    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        }
        val result = BookBlockStyle(
            textAlign = align,
            textIndentEm = indent,
            marginTopEm = top,
            marginBottomEm = bottom,
            lineHeightMultiplier = lineHeight,
            listItem = listItem,
            listDepth = listDepth,
            listNumber = listNumber,
            taskChecked = taskChecked,
            imageWidthFraction = width,
            imageMaxWidthFraction = maxWidth,
            imageHeightDp = height,
            imageSourcePath = imageSourcePath,
        )
        return result.takeIf {
            it.textAlign != null || it.textIndentEm > 0f || it.marginTopEm > 0f ||
                it.marginBottomEm > 0f || it.lineHeightMultiplier != null || it.listItem ||
                it.listDepth > 0 || it.listNumber > 0 || it.taskChecked != null ||
                it.imageWidthFraction != null ||
                it.imageMaxWidthFraction != 1f || it.imageHeightDp != null ||
                it.imageSourcePath != null
        }
    }

    fun encodeSpans(spans: List<InlineSpan>): String? {
        if (spans.isEmpty()) return null
        return spans.joinToString(";") { span ->
            val flags = buildString {
                if (span.bold) append('b')
                if (span.italic) append('i')
                if (span.underline) append('u')
                if (span.strike) append('s')
                if (span.link) append('l')
                if (span.code) append('c')
                if (span.footnote) append('f')
            }
            val target = span.targetBlockId?.let { "@$it" }.orEmpty()
            val link = span.linkTarget?.let {
                "~" + URLEncoder.encode(it, StandardCharsets.UTF_8.name())
            }.orEmpty()
            "${span.start}-${span.end}:$flags$target$link"
        }
    }

    fun decodeSpans(raw: String?): List<InlineSpan> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { part ->
            val range = part.substringBefore(':', "").trim()
            val rest = part.substringAfter(':', "").trim()
            val flags = rest.substringBefore('@').substringBefore('~')
            val target = rest.substringAfter('@', "").substringBefore('~')
                .takeIf { it.isNotBlank() }
            val linkTarget = rest.substringAfter('~', "")
                .takeIf { it.isNotBlank() }
                ?.let {
                    runCatching {
                        URLDecoder.decode(it, StandardCharsets.UTF_8.name())
                    }.getOrNull()
                }
            val start = range.substringBefore('-').toIntOrNull() ?: return@mapNotNull null
            val end = range.substringAfter('-', "").toIntOrNull() ?: return@mapNotNull null
            if (start < 0 || end <= start || flags.isEmpty()) return@mapNotNull null
            val span = runCatching {
                InlineSpan(
                    start = start,
                    end = end,
                    bold = flags.contains('b'),
                    italic = flags.contains('i'),
                    underline = flags.contains('u'),
                    strike = flags.contains('s'),
                    link = flags.contains('l'),
                    code = flags.contains('c'),
                    footnote = flags.contains('f'),
                    targetBlockId = target,
                    linkTarget = linkTarget,
                )
            }.getOrNull()
            span
        }
    }
}
