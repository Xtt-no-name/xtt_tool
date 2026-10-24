package com.xtt.toolbox.data.settings

import android.content.Context
import com.xtt.toolbox.domain.reading.ReaderSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** 应用级设置：主题模式与阅读器共用设置。 */
class AppSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    private val _readerSettings = MutableStateFlow(loadReaderSettings())
    val readerSettings: StateFlow<ReaderSettings> = _readerSettings.asStateFlow()
    private val _searchHistoryEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_SEARCH_HISTORY, true),
    )
    val searchHistoryEnabled: StateFlow<Boolean> = _searchHistoryEnabled.asStateFlow()
    private val _excludePunctuation = MutableStateFlow(
        preferences.getBoolean(KEY_EXCLUDE_PUNCTUATION, false),
    )
    val excludePunctuation: StateFlow<Boolean> = _excludePunctuation.asStateFlow()

    fun setExcludePunctuation(enabled: Boolean) {
        _excludePunctuation.value = enabled
        preferences.edit().putBoolean(KEY_EXCLUDE_PUNCTUATION, enabled).apply()
    }

    fun setSearchHistoryEnabled(enabled: Boolean) {
        _searchHistoryEnabled.value = enabled
        preferences.edit().putBoolean(KEY_SEARCH_HISTORY, enabled).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setReaderSettings(settings: ReaderSettings) {
        val normalized = settings.normalized()
        _readerSettings.value = normalized
        preferences.edit()
            .putFloat(KEY_FONT_SIZE, normalized.fontSizeSp)
            .putFloat(KEY_LINE_HEIGHT, normalized.lineHeightSp)
            .putFloat(KEY_PARAGRAPH_SPACING, normalized.paragraphSpacingDp)
            .putFloat(KEY_HORIZONTAL_PADDING, normalized.horizontalPaddingDp)
            .putBoolean(KEY_COMMENT_BUBBLES, normalized.showCommentBubbles)
            .putBoolean(KEY_CHAPTER_COMMENT, normalized.showChapterCommentEntry)
            .putBoolean(KEY_LONG_PRESS, normalized.enableLongPressMenu)
            .apply()
    }

    private fun loadThemeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, null) ?: ThemeMode.SYSTEM.name)
    }.getOrDefault(ThemeMode.SYSTEM)

    private fun loadReaderSettings(): ReaderSettings = ReaderSettings(
        fontSizeSp = preferences.getFloat(
            KEY_FONT_SIZE,
            ReaderSettings.DEFAULT_FONT_SIZE_SP,
        ),
        lineHeightSp = preferences.getFloat(
            KEY_LINE_HEIGHT,
            ReaderSettings.DEFAULT_LINE_HEIGHT_SP,
        ),
        paragraphSpacingDp = preferences.getFloat(
            KEY_PARAGRAPH_SPACING,
            ReaderSettings.DEFAULT_PARAGRAPH_SPACING_DP,
        ),
        horizontalPaddingDp = preferences.getFloat(
            KEY_HORIZONTAL_PADDING,
            ReaderSettings.DEFAULT_HORIZONTAL_PADDING_DP,
        ),
        showCommentBubbles = preferences.getBoolean(KEY_COMMENT_BUBBLES, true),
        showChapterCommentEntry = preferences.getBoolean(KEY_CHAPTER_COMMENT, true),
        enableLongPressMenu = preferences.getBoolean(KEY_LONG_PRESS, true),
    ).normalized()

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SEARCH_HISTORY = "search_history_enabled"
        const val KEY_EXCLUDE_PUNCTUATION = "word_count_exclude_punctuation"
        const val KEY_FONT_SIZE = "reader_font_size"
        const val KEY_LINE_HEIGHT = "reader_line_height"
        const val KEY_PARAGRAPH_SPACING = "reader_paragraph_spacing"
        const val KEY_HORIZONTAL_PADDING = "reader_horizontal_padding"
        const val KEY_COMMENT_BUBBLES = "reader_comment_bubbles"
        const val KEY_CHAPTER_COMMENT = "reader_chapter_comment"
        const val KEY_LONG_PRESS = "reader_long_press"
    }
}
