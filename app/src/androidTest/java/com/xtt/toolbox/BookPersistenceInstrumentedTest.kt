package com.xtt.toolbox

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xtt.toolbox.data.local.BookDatabaseHelper
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.Bookmark
import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.data.model.ChapterDetectionSource
import com.xtt.toolbox.data.model.Comment
import com.xtt.toolbox.data.model.CommentType
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.SqliteBookRepository
import com.xtt.toolbox.data.repository.SqliteReaderAnnotationRepository
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockStyle
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookDocument
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import com.xtt.toolbox.domain.content.BookTable
import com.xtt.toolbox.domain.content.BookTableCell
import com.xtt.toolbox.domain.content.BookTextAlign
import com.xtt.toolbox.domain.content.InlineSpan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BookPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "xtt-test-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun bookPersistsAcrossRepositoryInstances() = runBlocking {
        val helper = BookDatabaseHelper(context, databaseName)
        try {
            val repository = SqliteBookRepository(helper)
            repository.reload()
            assertTrue(repository.books.value.isEmpty())

            val book = sampleBook("book-1")
            assertTrue(repository.insert(book).isSuccess)

            val secondHelper = BookDatabaseHelper(context, databaseName)
            try {
                val reloaded = SqliteBookRepository(secondHelper)
                reloaded.reload()

                assertEquals(1, reloaded.books.value.size)
                assertEquals("book-1", reloaded.books.value.single().bookId)
                assertEquals("测试书", reloaded.books.value.single().title)
                assertEquals("hash-book-1", reloaded.books.value.single().contentHash)
                assertEquals("books/book-1/content.txt", reloaded.books.value.single().contentLocation)
                assertEquals(0f, reloaded.books.value.single().readingPosition?.percent)
            } finally {
                secondHelper.close()
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun duplicateBookIdFailsWithoutChangingCache() = runBlocking {
        val helper = BookDatabaseHelper(context, databaseName)
        try {
            val repository = SqliteBookRepository(helper)
            repository.reload()
            val book = sampleBook("book-1")

            assertTrue(repository.insert(book).isSuccess)
            assertTrue(repository.insert(book).isFailure)
            assertEquals(1, repository.books.value.size)

            repository.reload()
            assertEquals(1, repository.books.value.size)
        } finally {
            helper.close()
        }
    }

    @Test
    fun readingPositionsPersistAndRemainIsolated() = runBlocking {
        val helper = BookDatabaseHelper(context, databaseName)
        try {
            val repository = SqliteBookRepository(helper)
            repository.reload()
            val first = sampleBook("book-1")
            val second = sampleBook("book-2")
            assertTrue(repository.insert(first).isSuccess)
            assertTrue(repository.insert(second).isSuccess)

            assertTrue(
                repository.updateReadingPosition(
                    bookId = "book-1",
                    position = ReadingPosition(offset = 42, percent = 0.5f),
                    lastOpenedAt = 5_000L,
                ).isSuccess,
            )
            assertTrue(
                repository.updateReadingPosition(
                    bookId = "book-2",
                    position = ReadingPosition(offset = 7, percent = 0.1f),
                    lastOpenedAt = 6_000L,
                ).isSuccess,
            )

            val secondHelper = BookDatabaseHelper(context, databaseName)
            try {
                val reloaded = SqliteBookRepository(secondHelper)
                reloaded.reload()
                val firstReloaded = reloaded.books.value.first { it.bookId == "book-1" }
                val secondReloaded = reloaded.books.value.first { it.bookId == "book-2" }
                assertEquals(42, firstReloaded.readingPosition?.offset)
                assertEquals(0.5f, firstReloaded.readingPosition?.percent)
                assertEquals(7, secondReloaded.readingPosition?.offset)
                assertEquals(0.1f, secondReloaded.readingPosition?.percent)
                assertEquals(5_000L, firstReloaded.lastOpenedAt)
                assertEquals(6_000L, secondReloaded.lastOpenedAt)
            } finally {
                secondHelper.close()
            }

            assertTrue(
                repository.updateReadingPosition(
                    bookId = "book-1",
                    position = ReadingPosition(offset = 90, percent = 0.9f),
                ).isSuccess,
            )
            val thirdHelper = BookDatabaseHelper(context, databaseName)
            try {
                val reloadedAgain = SqliteBookRepository(thirdHelper)
                reloadedAgain.reload()
                val firstReloaded = reloadedAgain.books.value.first { it.bookId == "book-1" }
                val secondReloaded = reloadedAgain.books.value.first { it.bookId == "book-2" }
                assertEquals(90, firstReloaded.readingPosition?.offset)
                assertEquals(0.9f, firstReloaded.readingPosition?.percent)
                assertEquals(5_000L, firstReloaded.lastOpenedAt)
                assertEquals(7, secondReloaded.readingPosition?.offset)
                assertEquals(0.1f, secondReloaded.readingPosition?.percent)
            } finally {
                thirdHelper.close()
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun annotationsPersistAndRemainIsolated() = runBlocking {
        val helper = BookDatabaseHelper(context, databaseName)
        try {
            val books = SqliteBookRepository(helper)
            books.reload()
            assertTrue(books.insert(sampleBook("book-1")).isSuccess)
            assertTrue(books.insert(sampleBook("book-2")).isSuccess)

            val annotations = SqliteReaderAnnotationRepository(helper)
            assertTrue(
                annotations.addBookmark(
                    Bookmark(
                        bookmarkId = "bm-1",
                        bookId = "book-1",
                        chapterId = "offset:0",
                        chapterTitle = "第一章",
                        chapterStartOffset = 0,
                        position = 10,
                        title = "书签",
                        createdAt = 1000,
                    ),
                ).isSuccess,
            )
            assertTrue(
                annotations.addComment(
                    Comment(
                        commentId = "c-passage",
                        bookId = "book-1",
                        chapterId = "offset:0",
                        chapterTitle = "第一章",
                        chapterStartOffset = 0,
                        commentType = CommentType.PASSAGE,
                        content = "段评内容",
                        createdAt = 1001,
                        updatedAt = 1001,
                        anchorStart = 10,
                        anchorEnd = 20,
                        quotedText = "原文引用",
                        contentRevision = "hash-book-1",
                    ),
                ).isSuccess,
            )
            assertTrue(
                annotations.addComment(
                    Comment(
                        commentId = "c-chapter",
                        bookId = "book-1",
                        chapterId = "offset:0",
                        chapterTitle = "第一章",
                        chapterStartOffset = 0,
                        commentType = CommentType.CHAPTER,
                        content = "章评内容",
                        createdAt = 1002,
                        updatedAt = 1002,
                        contentRevision = "hash-book-1",
                    ),
                ).isSuccess,
            )
            assertTrue(
                annotations.addComment(
                    Comment(
                        commentId = "c-book2",
                        bookId = "book-2",
                        chapterId = "offset:0",
                        chapterTitle = "第一章",
                        chapterStartOffset = 0,
                        commentType = CommentType.PASSAGE,
                        content = "第二本段评",
                        createdAt = 1003,
                        updatedAt = 1003,
                        anchorStart = 5,
                        anchorEnd = 15,
                        quotedText = "第二本原文",
                    ),
                ).isSuccess,
            )

            val secondHelper = BookDatabaseHelper(context, databaseName)
            try {
                val reloaded = SqliteReaderAnnotationRepository(secondHelper)
                val first = reloaded.load("book-1").getOrThrow()
                val second = reloaded.load("book-2").getOrThrow()
                assertEquals(1, first.bookmarks.size)
                assertEquals(2, first.comments.size)
                assertEquals(1, second.comments.size)
                assertEquals(0, second.bookmarks.size)
                assertEquals("原文引用", first.comments.first { it.commentType == CommentType.PASSAGE }.quotedText)

                helper.delete("book-1")
                val afterDelete = reloaded.load("book-1").getOrThrow()
                assertEquals(0, afterDelete.bookmarks.size)
                assertEquals(0, afterDelete.comments.size)
            } finally {
                secondHelper.close()
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun epubDocumentPersistsWithChaptersBlocksAndResources() = runBlocking {
        val helper = BookDatabaseHelper(context, databaseName)
        try {
            val repository = SqliteBookRepository(helper)
            repository.reload()
            val book = sampleBook("epub-book").copy(
                format = BookFormat.EPUB,
                author = "测试作者",
                language = "zh-CN",
            )
            val resource = BookResource(
                resourceId = "res-1",
                relativePath = "epub/resources/res-1.png",
                mediaType = "image/png",
                sizeBytes = 4,
                sha256 = "hash",
                kind = BookResourceKind.IMAGE,
                alt = "插图",
            )
            val cover = BookResource(
                resourceId = "cover-1",
                relativePath = "epub/resources/cover.png",
                mediaType = "image/png",
                sizeBytes = 5,
                sha256 = "cover-hash",
                kind = BookResourceKind.COVER,
                alt = "封面",
            )
            val chapter = Chapter(
                chapterId = "epub:0:0",
                bookId = book.bookId,
                title = "第一章",
                order = 0,
                startOffset = 0,
                endOffset = 12,
                headingOffset = 0,
                detectionSource = ChapterDetectionSource.EPUB_NAV,
                confidence = 1f,
                level = 0,
            )
            val document = BookDocument(
                chapters = listOf(chapter),
                blocks = listOf(
                    BookBlock(
                        blockId = "block-0",
                        order = 0,
                        type = BookBlockType.PARAGRAPH,
                        startOffset = 0,
                        endOffset = 8,
                        style = BookBlockStyle(textAlign = BookTextAlign.CENTER),
                        spans = listOf(InlineSpan(0, 3, bold = true, underline = true)),
                    ),
                    BookBlock(
                        blockId = "block-1",
                        order = 1,
                        type = BookBlockType.TABLE,
                        startOffset = 8,
                        endOffset = 8,
                        table = BookTable(
                            header = listOf(BookTableCell("列一"), BookTableCell("列二")),
                            rows = listOf(
                                listOf(
                                    BookTableCell("值", listOf(InlineSpan(0, 1, italic = true))),
                                    BookTableCell(""),
                                ),
                            ),
                            alignments = listOf(BookTextAlign.CENTER, BookTextAlign.END),
                        ),
                    ),
                    BookBlock(
                        blockId = "block-2",
                        order = 2,
                        type = BookBlockType.IMAGE,
                        startOffset = 8,
                        endOffset = 8,
                        resourceId = resource.resourceId,
                        alt = "插图",
                    ),
                ),
                resources = listOf(resource, cover),
            )
            assertEquals(Result.success(Unit), repository.insertDocument(book, document))
            assertEquals(cover.resourceId, helper.readCoverResources()[book.bookId]?.resourceId)

            val reloaded = SqliteBookRepository(BookDatabaseHelper(context, databaseName))
            reloaded.reload()
            val loadedBook = reloaded.getBook(book.bookId)
            assertEquals("测试作者", loadedBook?.author)
            val loaded = reloaded.loadDocument(book.bookId)
            assertEquals(1, loaded?.chapters?.size)
            assertEquals(3, loaded?.blocks?.size)
            assertEquals(BookBlockType.TABLE, loaded?.blocks?.get(1)?.type)
            assertEquals(listOf("列一", "列二"), loaded?.blocks?.get(1)?.table?.header?.map { it.text })
            assertEquals(BookTextAlign.END, loaded?.blocks?.get(1)?.table?.alignments?.get(1))
            assertEquals(BookBlockType.IMAGE, loaded?.blocks?.get(2)?.type)
            assertEquals(2, loaded?.resources?.size)
            assertEquals(BookTextAlign.CENTER, loaded?.blocks?.get(0)?.style?.textAlign)
            assertEquals(1, loaded?.blocks?.get(0)?.spans?.size)
            assertTrue(loaded?.blocks?.get(0)?.spans?.single()?.bold == true)
        } finally {
            helper.close()
        }
    }

    @Test
    fun versionOneDatabaseUpgradesWithoutLosingBooks() = runBlocking {
        val raw = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        raw.execSQL(
            """
            CREATE TABLE books (
                book_id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                shelf_type TEXT NOT NULL,
                source_type TEXT NOT NULL,
                source_uri TEXT,
                content_location TEXT,
                source_name TEXT,
                content_hash TEXT,
                content_size INTEGER NOT NULL DEFAULT 0,
                source_encoding TEXT,
                format TEXT NOT NULL DEFAULT 'TXT',
                reading_chapter_id TEXT,
                reading_offset INTEGER NOT NULL DEFAULT 0,
                reading_percent REAL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_opened_at INTEGER
            )
            """.trimIndent(),
        )
        raw.insertOrThrow(
            "books",
            null,
            ContentValues().apply {
                put("book_id", "legacy-book")
                put("title", "旧书")
                put("shelf_type", ShelfType.READING.name)
                put("source_type", SourceType.IMPORTED.name)
                put("content_location", "books/legacy-book/content.txt")
                put("content_hash", "legacy-hash")
                put("content_size", 10L)
                put("format", BookFormat.TXT.name)
                put("reading_offset", 3)
                put("reading_percent", 0.3f)
                put("created_at", 1L)
                put("updated_at", 1L)
            },
        )
        raw.version = 1
        raw.close()

        val helper = BookDatabaseHelper(context, databaseName)
        try {
            val books = SqliteBookRepository(helper)
            books.reload()
            assertEquals(1, books.books.value.size)
            assertEquals("旧书", books.books.value.single().title)
            assertEquals(3, books.books.value.single().readingPosition?.offset)

            val annotations = SqliteReaderAnnotationRepository(helper)
            assertTrue(
                annotations.addBookmark(
                    Bookmark(
                        bookmarkId = "bm-legacy",
                        bookId = "legacy-book",
                        position = 3,
                        title = "旧书签",
                        createdAt = 2,
                    ),
                ).isSuccess,
            )
            assertEquals(1, annotations.load("legacy-book").getOrThrow().bookmarks.size)
        } finally {
            helper.close()
        }
    }

    private fun sampleBook(bookId: String): Book = Book(
        bookId = bookId,
        title = "测试书",
        shelfType = ShelfType.READING,
        sourceType = SourceType.IMPORTED,
        sourceUri = "content://test/book.txt",
        contentLocation = "books/$bookId/content.txt",
        sourceName = "测试书.txt",
        contentHash = "hash-$bookId",
        contentSize = 42L,
        sourceEncoding = "UTF-8",
        format = BookFormat.TXT,
        readingPosition = ReadingPosition(offset = 0, percent = 0f),
        createdAt = 1000L,
        updatedAt = 1000L,
        lastOpenedAt = null,
    )
}
