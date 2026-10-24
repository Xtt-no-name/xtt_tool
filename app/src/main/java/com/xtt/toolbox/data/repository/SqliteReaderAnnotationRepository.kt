package com.xtt.toolbox.data.repository

import com.xtt.toolbox.data.local.BookDatabaseHelper
import com.xtt.toolbox.data.model.Bookmark
import com.xtt.toolbox.data.model.Comment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqliteReaderAnnotationRepository(
    private val database: BookDatabaseHelper,
) : ReaderAnnotationRepository {
    override suspend fun load(bookId: String): Result<ReaderAnnotations> =
        withContext(Dispatchers.IO) {
            runCatching {
                ReaderAnnotations(
                    bookmarks = database.readBookmarks(bookId),
                    comments = database.readComments(bookId),
                )
            }
        }

    override suspend fun addBookmark(bookmark: Bookmark): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { database.insertBookmark(bookmark) }
        }

    override suspend fun addComment(comment: Comment): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { database.insertComment(comment) }
        }
}
