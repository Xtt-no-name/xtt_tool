package com.xtt.toolbox.data.model

/**
 * 阅读书签，独立于正文存储。
 *
 * [position] 是规范化正文中的绝对 UTF-16 code unit 偏移，约定见
 * [com.xtt.toolbox.domain.chapter.TextOffsets]；[chapterId] 可为空，用于封面或卷首等
 * 尚未归属章节的位置。
 */
data class Bookmark(
    val bookmarkId: String,
    val bookId: String,
    val chapterId: String? = null,
    val chapterTitle: String? = null,
    val chapterStartOffset: Int? = null,
    val position: Int,
    val title: String,
    val note: String? = null,
    val createdAt: Long,
) {
    init {
        require(bookmarkId.isNotBlank()) { "bookmarkId 不能为空" }
        require(bookId.isNotBlank()) { "bookId 不能为空" }
        require(position >= 0) { "书签位置不能为负数" }
        require(title.isNotBlank()) { "书签标题不能为空" }
        require(createdAt >= 0) { "createdAt 不能为负数" }
        require(chapterStartOffset == null || chapterStartOffset >= 0) { "章节起点不能为负数" }
    }
}
