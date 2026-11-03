package com.xtt.toolbox.data.update

import android.content.Context

data class ReleaseNotes(
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val items: List<String>,
)

object ReleaseNotesCatalog {
    fun forVersion(versionCode: Int): ReleaseNotes? = when (versionCode) {
        4 -> ReleaseNotes(
            versionCode = 4,
            versionName = "1.0.3",
            title = "本次更新内容",
            items = listOf(
                "TXT 章节解析升级：支持更多标题格式、长标题和 999 章目录。",
                "写作编辑器顶部布局调整，减少多余空白。",
                "正文输入时光标自动跟随滚动，持续输入更顺畅。",
                "插入图片默认适配正文宽度，并可复用裁剪界面调整图片。",
            ),
        )
        else -> null
    }
}

/** 公告只记录“已关闭”的目标版本，避免网络失败或页面重组造成重复标记。 */
class ReleaseNoticeStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val lastShownReleaseNotesVersionCode: Int
        get() = preferences.getInt(KEY_SHOWN_NOTES, 0)

    val lastNotifiedAvailableVersionCode: Int
        get() = preferences.getInt(KEY_NOTIFIED_UPDATE, 0)

    fun markReleaseNotesShown(versionCode: Int) {
        preferences.edit().putInt(KEY_SHOWN_NOTES, versionCode).apply()
    }

    fun markAvailableVersionNotified(versionCode: Int) {
        preferences.edit().putInt(KEY_NOTIFIED_UPDATE, versionCode).apply()
    }

    private companion object {
        const val PREFERENCES = "release_notices"
        const val KEY_SHOWN_NOTES = "lastShownReleaseNotesVersionCode"
        const val KEY_NOTIFIED_UPDATE = "lastNotifiedAvailableVersionCode"
    }
}

object ReleaseNoticePolicy {
    fun shouldShowLocal(
        currentVersionCode: Int,
        lastShownVersionCode: Int,
        notes: ReleaseNotes?,
    ): Boolean = notes != null && currentVersionCode > lastShownVersionCode

    fun shouldShowRemote(
        currentVersionCode: Int,
        latestVersionCode: Int,
        lastNotifiedVersionCode: Int,
    ): Boolean = latestVersionCode > currentVersionCode &&
        latestVersionCode > lastNotifiedVersionCode
}
