package com.xtt.toolbox.data.search

import android.content.Context
import com.xtt.toolbox.domain.search.SearchHistoryRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** 阅读书架搜索历史：只保存最近提交的非空书名关键词。 */
class SearchHistoryStore(
    context: Context,
    enabledInitially: Boolean,
) {
    private val preferences = context.getSharedPreferences("reading_search", Context.MODE_PRIVATE)
    private var enabled = enabledInitially
    private val _history = MutableStateFlow(
        if (enabledInitially) loadHistory() else emptyList(),
    )
    val history: StateFlow<List<String>> = _history.asStateFlow()

    init {
        if (!enabledInitially) clear()
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) clear()
    }

    fun record(raw: String): Boolean {
        if (!enabled) return false
        val query = raw.trim()
        if (query.isEmpty()) return false
        val updated = SearchHistoryRules.updated(_history.value, query, enabled = true)
        _history.value = updated
        persist(updated)
        return true
    }

    fun clear() {
        _history.value = emptyList()
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    private fun loadHistory(): List<String> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val result = mutableListOf<String>()
            for (index in 0 until array.length()) {
                val item = array.optString(index).trim()
                if (item.isNotEmpty() && result.none { it.equals(item, ignoreCase = true) }) {
                    result += item
                }
                if (result.size >= MAX_HISTORY) break
            }
            result
        }.getOrDefault(emptyList())
    }

    private fun persist(history: List<String>) {
        val array = JSONArray()
        history.forEach(array::put)
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        const val MAX_HISTORY = SearchHistoryRules.MAX_HISTORY
        private const val KEY_HISTORY = "history"
    }
}
