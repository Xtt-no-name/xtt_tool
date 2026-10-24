package com.xtt.toolbox.domain.writing

/** 编辑器查找替换的纯文本算法；按字面量匹配，不使用正则。 */
object WritingSearch {
    fun matches(text: String, query: String): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val result = mutableListOf<IntRange>()
        var start = 0
        while (start <= text.length - query.length) {
            val index = text.indexOf(query, start, ignoreCase = false)
            if (index < 0) break
            result += index until index + query.length
            start = index + query.length
        }
        return result
    }

    fun replaceAll(text: String, query: String, replacement: String): String {
        val ranges = matches(text, query)
        if (ranges.isEmpty()) return text
        var updated = text
        ranges.asReversed().forEach { range ->
            updated = updated.replaceRange(range.first, range.last + 1, replacement)
        }
        return updated
    }
}
