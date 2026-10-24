package com.xtt.toolbox.domain.search

/** 阅读书架书名搜索与历史规则，纯 Kotlin 便于单元测试。 */
object ReadingSearchRules {
    fun normalizeQuery(raw: String): String = raw.trim()

    fun matches(title: String, query: String): Boolean {
        val normalized = normalizeQuery(query)
        return normalized.isNotEmpty() && title.contains(normalized, ignoreCase = true)
    }
}

object SearchHistoryRules {
    const val MAX_HISTORY = 10

    fun updated(
        current: List<String>,
        raw: String,
        enabled: Boolean,
    ): List<String> {
        if (!enabled) return emptyList()
        val query = raw.trim()
        if (query.isEmpty()) return current
        return buildList {
            add(query)
            current
                .filterNot { it.equals(query, ignoreCase = true) }
                .take(MAX_HISTORY - 1)
                .forEach(::add)
        }
    }
}
