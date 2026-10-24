package com.xtt.toolbox.domain.content

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * 少量、确定性的 EPUB CSS 支持。
 *
 * 只解析 XHTML 内嵌 `<style>` 的简单标签/类/ID 选择器和元素 `style` 属性；
 * 不解析外部样式、不执行脚本、不支持复杂选择器/布局。所有尺寸都做上限钳制，
 * 字号和行距不进入内容模型，确保用户阅读设置始终优先。
 */
object EpubCss {
    data class Declarations(
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val underline: Boolean? = null,
        val strike: Boolean? = null,
        val textAlign: BookTextAlign? = null,
        val textIndentEm: Float? = null,
        val marginTopEm: Float? = null,
        val marginBottomEm: Float? = null,
        val lineHeight: Float? = null,
        val imageWidthFraction: Float? = null,
        val imageMaxWidthFraction: Float? = null,
        val imageHeightDp: Float? = null,
    ) {
        fun merge(other: Declarations): Declarations = Declarations(
            bold = other.bold ?: bold,
            italic = other.italic ?: italic,
            underline = other.underline ?: underline,
            strike = other.strike ?: strike,
            textAlign = other.textAlign ?: textAlign,
            textIndentEm = other.textIndentEm ?: textIndentEm,
            marginTopEm = other.marginTopEm ?: marginTopEm,
            marginBottomEm = other.marginBottomEm ?: marginBottomEm,
            lineHeight = other.lineHeight ?: lineHeight,
            imageWidthFraction = other.imageWidthFraction ?: imageWidthFraction,
            imageMaxWidthFraction = other.imageMaxWidthFraction ?: imageMaxWidthFraction,
            imageHeightDp = other.imageHeightDp ?: imageHeightDp,
        )
    }

    data class Rule(
        val tag: String?,
        val id: String?,
        val classes: Set<String>,
        val declarations: Declarations,
    )

    fun parseRules(document: Document): List<Rule> {
        val result = mutableListOf<Rule>()
        walk(document.documentElement) { element ->
            if (element.localNameOrTag().equals("style", ignoreCase = true)) {
                val css = element.textContent
                    .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
                Regex("([^{}]+)\\{([^{}]*)\\}").findAll(css).forEach { match ->
                    val declarations = parseDeclarations(match.groupValues[2])
                    match.groupValues[1].split(',').forEach { raw ->
                        parseSelector(raw.trim())?.let { selector ->
                            result += Rule(
                                tag = selector.tag,
                                id = selector.id,
                                classes = selector.classes,
                                declarations = declarations,
                            )
                        }
                    }
                }
            }
        }
        return result
    }

    fun declarationsFor(element: Element, rules: List<Rule>): Declarations {
        var result = Declarations()
        val tag = element.localNameOrTag().lowercase()
        val id = element.attr("id").takeIf { it.isNotBlank() }
        val classes = element.attr("class")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .toSet()
        rules.forEach { rule ->
            if (rule.tag != null && rule.tag != tag) return@forEach
            if (rule.id != null && rule.id != id) return@forEach
            if (rule.classes.isNotEmpty() && !classes.containsAll(rule.classes)) return@forEach
            result = result.merge(rule.declarations)
        }
        result = result.merge(parseDeclarations(element.attr("style")))
        return result
    }

    fun inlineFlags(
        element: Element,
        inherited: InlineFlags,
        rules: List<Rule>,
    ): InlineFlags {
        val tag = element.localNameOrTag().lowercase()
        val declarations = declarationsFor(element, rules)
        return InlineFlags(
            bold = declarations.bold ?: inherited.bold || tag in BOLD_TAGS,
            italic = declarations.italic ?: inherited.italic || tag in ITALIC_TAGS,
            underline = declarations.underline ?: inherited.underline || tag in UNDERLINE_TAGS,
            strike = declarations.strike ?: inherited.strike || tag in STRIKE_TAGS,
            link = inherited.link || tag == "a",
        )
    }

    fun blockStyle(element: Element, rules: List<Rule>): BookBlockStyle? {
        val d = declarationsFor(element, rules)
        val style = BookBlockStyle(
            textAlign = d.textAlign,
            textIndentEm = d.textIndentEm?.coerceIn(0f, 8f) ?: 0f,
            marginTopEm = d.marginTopEm?.coerceIn(0f, 4f) ?: 0f,
            marginBottomEm = d.marginBottomEm?.coerceIn(0f, 4f) ?: 0f,
            lineHeightMultiplier = d.lineHeight?.coerceIn(0.8f, 2.4f),
            listItem = element.localNameOrTag().equals("li", ignoreCase = true),
            imageWidthFraction = d.imageWidthFraction?.coerceIn(0.1f, 1f),
            imageMaxWidthFraction = d.imageMaxWidthFraction?.coerceIn(0.1f, 1f) ?: 1f,
            imageHeightDp = d.imageHeightDp?.coerceIn(80f, 1200f),
        )
        return style.takeIf {
            it.hasTextLayout || it.hasImageLayout || it.listItem
        }
    }

    fun isBlockTag(tag: String): Boolean = tag.lowercase() in BLOCK_TAGS

    data class InlineFlags(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val link: Boolean = false,
    )

    private data class Selector(
        val tag: String?,
        val id: String?,
        val classes: Set<String>,
    )

    private fun parseSelector(raw: String): Selector? {
        if (raw.isBlank() || raw.any { it.isWhitespace() }) return null
        if (raw.contains('>') || raw.contains('+') || raw.contains('~') ||
            raw.contains(':') || raw.contains('[') || raw.contains(']')
        ) {
            return null
        }
        var rest = raw.trim()
        if (rest.startsWith("*")) rest = rest.drop(1)
        if (rest.isBlank()) return Selector(null, null, emptySet())
        var tag: String? = rest.substringBefore('.').substringBefore('#').takeIf {
            it.isNotBlank() && it.all { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' }
        }
        if (tag == "*") tag = null
        var id: String? = null
        val classes = mutableSetOf<String>()
        rest.split('.').drop(1).forEach { part ->
            val token = part.substringBefore('#')
            if (token.isNotBlank()) classes += token
        }
        val hash = rest.indexOf('#')
        if (hash >= 0) {
            id = rest.substring(hash + 1).substringBefore('.').takeIf { it.isNotBlank() }
        }
        return if (tag == null && id == null && classes.isEmpty()) {
            Selector(null, null, emptySet())
        } else {
            Selector(tag, id, classes)
        }
    }

    private fun parseDeclarations(raw: String): Declarations {
        var bold: Boolean? = null
        var italic: Boolean? = null
        var underline: Boolean? = null
        var strike: Boolean? = null
        var align: BookTextAlign? = null
        var indent: Float? = null
        var top: Float? = null
        var bottom: Float? = null
        var lineHeight: Float? = null
        var width: Float? = null
        var maxWidth: Float? = null
        var height: Float? = null

        raw.split(';').forEach { declaration ->
            val key = declaration.substringBefore(':', "").trim().lowercase()
            val value = declaration.substringAfter(':', "").trim()
                .substringBefore("!important").trim()
            when (key) {
                "font-weight" -> bold = when {
                    value.equals("bold", true) || value.equals("bolder", true) -> true
                    value.equals("normal", true) || value.equals("lighter", true) -> false
                    else -> value.toIntOrNull()?.let { it >= 600 }
                }
                "font-style" -> italic = when {
                    value.startsWith("italic", true) || value.startsWith("oblique", true) -> true
                    value.equals("normal", true) -> false
                    else -> null
                }
                "text-decoration", "text-decoration-line" -> {
                    val lower = value.lowercase()
                    if (lower.contains("underline")) underline = true
                    if (lower.contains("line-through")) strike = true
                    if (lower == "none") {
                        underline = false
                        strike = false
                    }
                }
                "text-align" -> align = when (value.lowercase()) {
                    "left", "start" -> BookTextAlign.START
                    "center" -> BookTextAlign.CENTER
                    "right", "end" -> BookTextAlign.END
                    "justify" -> BookTextAlign.JUSTIFY
                    else -> null
                }
                "text-indent" -> indent = lengthToEm(value)
                "margin-top" -> top = lengthToEm(value)
                "margin-bottom" -> bottom = lengthToEm(value)
                "margin" -> parseMargin(value)?.let { (topValue, bottomValue) ->
                    top = topValue
                    bottom = bottomValue
                }
                "line-height" -> lineHeight = value.toFloatOrNull()?.coerceIn(0.8f, 2.4f)
                "width" -> width = imageFraction(value)
                "max-width" -> maxWidth = imageFraction(value)
                "height" -> height = imageHeight(value)
            }
        }
        return Declarations(
            bold = bold,
            italic = italic,
            underline = underline,
            strike = strike,
            textAlign = align,
            textIndentEm = indent,
            marginTopEm = top,
            marginBottomEm = bottom,
            lineHeight = lineHeight,
            imageWidthFraction = width,
            imageMaxWidthFraction = maxWidth,
            imageHeightDp = height,
        )
    }

    private fun parseMargin(value: String): Pair<Float, Float>? {
        val parts = value.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val top = lengthToEm(parts.first()) ?: return null
        val bottom = when {
            parts.size >= 3 -> lengthToEm(parts[2])
            parts.size == 2 -> lengthToEm(parts[1])
            else -> top
        } ?: top
        return top to bottom
    }

    private fun lengthToEm(value: String): Float? {
        val lower = value.trim().lowercase()
        val number = lower.takeWhile { it.isDigit() || it == '.' || it == '-' }.toFloatOrNull()
            ?: return null
        return when {
            lower.endsWith("em") -> number.coerceIn(0f, 8f)
            lower.endsWith("px") -> (number / 16f).coerceIn(0f, 8f)
            lower.endsWith("%") -> (number / 100f * 4f).coerceIn(0f, 8f)
            else -> null
        }
    }

    private fun imageFraction(value: String): Float? {
        val lower = value.trim().lowercase()
        val number = lower.takeWhile { it.isDigit() || it == '.' }.toFloatOrNull() ?: return null
        return when {
            lower.endsWith("%") -> (number / 100f).coerceIn(0.1f, 1f)
            lower.endsWith("px") -> (number / 600f).coerceIn(0.1f, 1f)
            else -> null
        }
    }

    private fun imageHeight(value: String): Float? {
        val lower = value.trim().lowercase()
        val number = lower.takeWhile { it.isDigit() || it == '.' }.toFloatOrNull() ?: return null
        return when {
            lower.endsWith("px") -> number.coerceIn(80f, 1200f)
            lower.endsWith("%") -> (number / 100f * 480f).coerceIn(80f, 1200f)
            else -> null
        }
    }

    private fun walk(element: Element, action: (Element) -> Unit) {
        action(element)
        val children = element.childNodes
        for (i in 0 until children.length) {
            (children.item(i) as? Element)?.let { walk(it, action) }
        }
    }

    private fun Element.localNameOrTag(): String = localName ?: tagName.substringAfter(':')

    private fun Element.attr(name: String): String =
        getAttribute(name).takeIf { it.isNotBlank() }
            ?: getAttribute("xlink:$name").takeIf { it.isNotBlank() }
            ?: ""

    private val BOLD_TAGS = setOf("strong", "b")
    private val ITALIC_TAGS = setOf("em", "i")
    private val UNDERLINE_TAGS = setOf("u", "ins")
    private val STRIKE_TAGS = setOf("s", "del", "strike")
    private val BLOCK_TAGS = setOf(
        "p", "li", "blockquote", "pre", "div", "section", "article",
        "h1", "h2", "h3", "h4", "h5", "h6",
    )
}
