package com.xtt.toolbox.data.model

/** 批注类型：章评针对整章，段评针对正文中的一段文本。 */
enum class CommentType {
    CHAPTER,
    PASSAGE,
}

/**
 * 统一批注模型。
 *
 * 章评与段评共用同一张逻辑表；[CommentType.PASSAGE] 额外使用锚点字段：
 * - [anchorStart]/[anchorEnd]：规范化正文中的 UTF-16 code unit 偏移，end 不包含；
 * - [quotedText]：被引用的原文；
 * - [prefixText]/[suffixText]：用于原文变化后重新定位的上下文；
 * - [contentRevision]：锚点对应的正文版本标识（例如内容哈希），正文变化后用于判断锚点是否失效。
 *
 * 无法可靠匹配时，UI 必须提示用户确认，不得把批注错误地关联到其他段落。
 */
data class Comment(
    val commentId: String,
    val bookId: String,
    val chapterId: String,
    val chapterTitle: String? = null,
    val chapterStartOffset: Int? = null,
    val commentType: CommentType,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val anchorStart: Int? = null,
    val anchorEnd: Int? = null,
    val quotedText: String? = null,
    val prefixText: String? = null,
    val suffixText: String? = null,
    val contentRevision: String? = null,
) {
    init {
        require(commentId.isNotBlank()) { "commentId 不能为空" }
        require(bookId.isNotBlank()) { "bookId 不能为空" }
        require(chapterId.isNotBlank()) { "chapterId 不能为空" }
        require(content.isNotBlank()) { "批注内容不能为空" }
        require(createdAt >= 0) { "createdAt 不能为负数" }
        require(updatedAt >= createdAt) { "updatedAt 不能早于 createdAt" }
        require(chapterStartOffset == null || chapterStartOffset >= 0) { "章节起点不能为负数" }

        if (commentType == CommentType.PASSAGE) {
            require(anchorStart != null && anchorEnd != null) { "段评必须提供 anchorStart 和 anchorEnd" }
            require(anchorStart >= 0) { "anchorStart 不能为负数" }
            require(anchorEnd >= anchorStart) { "anchorEnd 不能早于 anchorStart" }
        }
    }
}
