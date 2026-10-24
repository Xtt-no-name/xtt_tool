package com.xtt.toolbox.domain.content

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** 将 Markdown 相对图片路径解析为授权根目录内的路径；越界返回 null。 */
object MarkdownImagePathResolver {
    fun resolve(baseDirectory: String?, rawPath: String?): String? {
        val raw = rawPath?.trim().orEmpty()
        if (raw.isBlank() || raw.any { it.isISOControl() }) return null
        if (raw.startsWith("/") || raw.startsWith("\\") || raw.contains('\\') ||
            raw.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))
        ) {
            return null
        }
        val decoded = runCatching {
            URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return null
        if (decoded.startsWith("/") || decoded.contains('\\') || decoded.any { it.isISOControl() }) {
            return null
        }
        val parts = mutableListOf<String>()
        baseDirectory?.split('/')?.forEach { part ->
            if (part.isNotBlank() && part != "." && part != "..") parts += part
        }
        decoded.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex) else return null
                else -> parts += part
            }
        }
        return parts.joinToString("/").takeIf { it.isNotBlank() }
    }
}
