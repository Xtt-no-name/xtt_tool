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
        6 -> ReleaseNotes(
            versionCode = 6,
            versionName = "1.0.5",
            title = "本次更新内容",
            items = listOf(
                "阅读器目录占满屏幕高度，打开时自动定位当前章节，并支持正序/倒序切换。",
                "目录顶部的排序和关闭按钮改为简洁图标，排序状态更直观。",
                "应用图标更新为“夜空启智之书”黑白书本与星光图案。",
            ),
        )
        5 -> ReleaseNotes(
            versionCode = 5,
            versionName = "1.0.4",
            title = "本次更新内容",
            items = listOf(
                "写作图片裁剪改为独立的源图裁剪：支持原图、1:1、4:3、3:4、16:9、9:16 和自由比例。",
                "图片裁剪会应用 EXIF 方向，并从源图重新取样；预览缩放不再降低最终裁剪精度。",
                "TXT 章节解析支持正文/书名前缀、无空格标题、目录式重复标题和真实小说分节。",
                "已用三本本地真实小说验证章节恢复和正文边界；真实手机仍建议自行确认排版效果。",
            ),
        )
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
