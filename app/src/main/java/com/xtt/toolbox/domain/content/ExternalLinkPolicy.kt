package com.xtt.toolbox.domain.content

import java.net.URI
import java.net.URISyntaxException

/**
 * Markdown 外部链接的统一安全边界。只允许可解析、具有有效主机的 HTTP/HTTPS URI；
 * 不依赖调用方传入的字符串前缀或渲染颜色。
 */
object ExternalLinkPolicy {
    fun validate(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.any { it.isISOControl() }) return null
        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.rawUserInfo?.contains('@') == true && uri.host.isBlank()) return null
        return value
    }

    fun isAllowed(raw: String?): Boolean = validate(raw) != null
}
