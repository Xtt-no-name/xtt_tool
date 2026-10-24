package com.xtt.toolbox

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xtt.toolbox.data.content.FileBookContentStore
import com.xtt.toolbox.data.local.BookDatabaseHelper
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.Bookmark
import com.xtt.toolbox.data.model.Comment
import com.xtt.toolbox.data.model.CommentType
import com.xtt.toolbox.data.repository.SqliteBookRepository
import com.xtt.toolbox.data.repository.SqliteReaderAnnotationRepository
import com.xtt.toolbox.domain.writing.WritingDocumentEditor
import com.xtt.toolbox.domain.writing.WritingDocumentService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WritingPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var contentRoot: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "xtt-writing-test-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        contentRoot = File(context.cacheDir, "xtt-writing-content-${UUID.randomUUID()}")
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
        contentRoot.deleteRecursively()
    }

    @Test
    fun writingDocumentCommitsAndReloadsAcrossRepositoryInstances() = runBlocking {
        val firstHelper = BookDatabaseHelper(context, databaseName)
        try {
            val store = FileBookContentStore(contentRoot)
            val repository = SqliteBookRepository(firstHelper)
            repository.reload()
            val service = WritingDocumentService(repository, store)

            var document = service.createBlank("持久化作品").getOrThrow()
            document = WritingDocumentEditor.addChapter(document, "第二章")
            document = WritingDocumentEditor.renameChapter(
                document,
                document.document.chapters.first().chapterId,
                "第一章",
            )
            val saved = service.save(document).getOrThrow()
            assertEquals(BookFormat.DRAFT, saved.book.format)

            val firstChapter = saved.document.chapters.first()
            val annotations = SqliteReaderAnnotationRepository(firstHelper)
            annotations.addBookmark(
                Bookmark(
                    bookmarkId = "writing-bm",
                    bookId = saved.book.bookId,
                    chapterId = firstChapter.chapterId,
                    chapterTitle = firstChapter.title,
                    chapterStartOffset = firstChapter.startOffset,
                    position = firstChapter.startOffset,
                    title = "保留书签",
                    createdAt = 1,
                ),
            ).getOrThrow()
            annotations.addComment(
                Comment(
                    commentId = "writing-comment",
                    bookId = saved.book.bookId,
                    chapterId = firstChapter.chapterId,
                    chapterTitle = firstChapter.title,
                    chapterStartOffset = firstChapter.startOffset,
                    commentType = CommentType.CHAPTER,
                    content = "保留评论",
                    createdAt = 1,
                    updatedAt = 1,
                    contentRevision = saved.book.contentHash,
                ),
            ).getOrThrow()
            val resaved = service.save(
                WritingDocumentEditor.addChapter(saved, "第三章"),
            ).getOrThrow()
            val staleCommit = repository.commitDocumentIfUnchanged(
                book = resaved.book.copy(contentHash = "stale-attempt"),
                document = resaved.document,
                expectedContentHash = saved.book.contentHash,
            )
            assertTrue(staleCommit.isSuccess)
            assertFalse(staleCommit.getOrThrow())
            assertEquals(
                resaved.book.contentHash,
                repository.getBook(resaved.book.bookId)?.contentHash,
            )

            val secondHelper = BookDatabaseHelper(context, databaseName)
            try {
                val reloadedStore = FileBookContentStore(contentRoot)
                val reloadedRepository = SqliteBookRepository(secondHelper)
                reloadedRepository.reload()
                val reloaded = WritingDocumentService(reloadedRepository, reloadedStore)
                    .load(resaved.book.bookId)
                    .getOrThrow()
                assertEquals("第一章\n第二章\n第三章\n", reloaded.text)
                assertEquals(
                    listOf("第一章", "第二章", "第三章"),
                    reloaded.document.chapters.map { it.title },
                )
                assertTrue(reloaded.book.contentLocation!!.contains("/revisions/"))
                val loadedAnnotations = SqliteReaderAnnotationRepository(secondHelper)
                    .load(resaved.book.bookId)
                    .getOrThrow()
                assertEquals("writing-bm", loadedAnnotations.bookmarks.single().bookmarkId)
                assertEquals("writing-comment", loadedAnnotations.comments.single().commentId)
            } finally {
                secondHelper.close()
            }
        } finally {
            firstHelper.close()
        }
    }
}
