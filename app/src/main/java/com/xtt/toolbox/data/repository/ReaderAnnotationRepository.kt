package com.xtt.toolbox.data.repository

import com.xtt.toolbox.data.model.Bookmark
import com.xtt.toolbox.data.model.Comment

data class ReaderAnnotations(
    val bookmarks: List<Bookmark> = emptyList(),
    val comments: List<Comment> = emptyList(),
)

/**
 * 书签/段评/章评持久化边界。
 *
 * 读取和写入必须按 bookId 隔离；UI 不直接查 SQLite，也不按段落逐条查询。
 */
interface ReaderAnnotationRepository {
    suspend fun load(bookId: String): Result<ReaderAnnotations>

    suspend fun addBookmark(bookmark: Bookmark): Result<Unit>

    suspend fun addComment(comment: Comment): Result<Unit>
}
