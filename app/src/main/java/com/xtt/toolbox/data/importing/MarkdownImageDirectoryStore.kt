package com.xtt.toolbox.data.importing

import android.content.Context

/** 每本书的 Markdown 图片目录 URI；仅保存 SAF tree URI，不保存文件内容。 */
interface MarkdownImageDirectoryStore {
    fun get(bookId: String): String?
    fun put(bookId: String, directoryUri: String)
    fun remove(bookId: String)
}

class SharedPreferencesMarkdownImageDirectoryStore(context: Context) :
    MarkdownImageDirectoryStore {
    private val preferences = context.getSharedPreferences(
        "markdown_image_directories",
        Context.MODE_PRIVATE,
    )

    override fun get(bookId: String): String? =
        preferences.getString(bookId, null)?.takeIf { it.isNotBlank() }

    override fun put(bookId: String, directoryUri: String) {
        preferences.edit().putString(bookId, directoryUri).apply()
    }

    override fun remove(bookId: String) {
        preferences.edit().remove(bookId).apply()
    }
}
