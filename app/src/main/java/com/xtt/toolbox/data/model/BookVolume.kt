package com.xtt.toolbox.data.model

/** 作品下的分卷；未分卷不是真实分卷记录，章节 volumeId 为 null 即表示未分卷。 */
data class BookVolume(
    val volumeId: String,
    val bookId: String,
    val title: String,
    val order: Int,
    val createdAt: Long,
) {
    init {
        require(volumeId.isNotBlank()) { "volumeId 不能为空" }
        require(bookId.isNotBlank()) { "bookId 不能为空" }
        require(title.isNotBlank()) { "分卷标题不能为空" }
        require(order >= 0) { "分卷顺序不能为负数" }
        require(createdAt >= 0) { "createdAt 不能为负数" }
    }
}
