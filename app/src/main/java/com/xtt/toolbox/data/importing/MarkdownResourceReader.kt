package com.xtt.toolbox.data.importing

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** 已授权的 SAF 目录中解析出的资源元信息。 */
data class MarkdownResourceInfo(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
)

/** 授权目录中的资源读取端口；单元测试可替换为内存实现。 */
interface MarkdownResourceReader {
    fun probe(directoryUri: String, relativePath: String): MarkdownResourceInfo?

    fun read(directoryUri: String, relativePath: String): ByteArray?
}

/** Markdown 相对路径规范化，不进行文件系统拼接。 */
object MarkdownPaths {
    fun normalize(raw: String): String? {
        if (raw.isBlank()) return null
        val noSuffix = raw.substringBefore('#').substringBefore('?')
        if (noSuffix.isBlank()) return null
        if (noSuffix.startsWith("/") || noSuffix.startsWith("\\") ||
            noSuffix.contains('\\') ||
            noSuffix.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))
        ) {
            return null
        }
        val decoded = try {
            // URLDecoder 会把 + 当作空格；先用 %2B 保护文件名中的加号。
            URLDecoder.decode(noSuffix.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (decoded.contains('\u0000') || decoded.startsWith("/") || decoded.contains('\\')) {
            return null
        }
        val parts = mutableListOf<String>()
        decoded.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> return null
                else -> parts += part
            }
        }
        return parts.joinToString("/").takeIf { it.isNotBlank() }
    }
}
