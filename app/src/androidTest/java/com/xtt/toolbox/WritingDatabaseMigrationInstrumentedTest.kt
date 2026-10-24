package com.xtt.toolbox

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xtt.toolbox.data.local.BookDatabaseHelper
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.BookVolume
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.SqliteBookRepository
import com.xtt.toolbox.data.repository.SqliteReaderAnnotationRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WritingDatabaseMigrationInstrumentedTest {
    private lateinit var context: Context
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "xtt-migration-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratesV4BooksAndAnnotationsWithoutLosingData() = runBlocking {
        createV4Database()
        val helper = BookDatabaseHelper(context, databaseName)
        try {
            val repository = SqliteBookRepository(helper)
            repository.reload()
            val book = repository.books.value.single()
            assertEquals("legacy-book", book.bookId)
            assertEquals("旧版书", book.title)
            assertNull(book.originBookId)

            val document = repository.loadDocument("legacy-book")!!
            assertEquals("chapter-1", document.chapters.single().chapterId)
            assertEquals("block-1", document.blocks.single().blockId)
            assertTrue(repository.loadVolumes("legacy-book").isEmpty())
            repository.insertVolume(
                BookVolume(
                    volumeId = "volume-1",
                    bookId = "legacy-book",
                    title = "旧版分卷",
                    order = 0,
                    createdAt = 1,
                ),
            ).getOrThrow()
            assertEquals("volume-1", repository.loadVolumes("legacy-book").single().volumeId)

            val annotations = SqliteReaderAnnotationRepository(helper)
                .load("legacy-book")
                .getOrThrow()
            assertEquals("bookmark-1", annotations.bookmarks.single().bookmarkId)
            assertEquals("comment-1", annotations.comments.single().commentId)
            assertEquals("旧段落", annotations.comments.single().quotedText)
        } finally {
            helper.close()
        }
    }

    @Test
    fun originBookIdPersistsForEditableCopy() = runBlocking {
        val helper = BookDatabaseHelper(context, databaseName)
        try {
            val repository = SqliteBookRepository(helper)
            repository.reload()
            val copy = Book.create(
                title = "副本",
                shelfType = ShelfType.WRITING,
                sourceType = SourceType.IMPORTED,
                format = BookFormat.DRAFT,
                now = 1,
                originBookId = "origin-book",
            )
            repository.insert(copy).getOrThrow()
            repository.reload()
            assertEquals("origin-book", repository.getBook(copy.bookId)?.originBookId)
        } finally {
            helper.close()
        }
    }

    private fun createV4Database() {
        val db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            db.execSQL(
                """
                CREATE TABLE books (
                    book_id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    author TEXT,
                    language TEXT,
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
            db.execSQL(
                """
                CREATE TABLE book_chapters (
                    chapter_id TEXT PRIMARY KEY NOT NULL,
                    book_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    chapter_order INTEGER NOT NULL,
                    start_offset INTEGER NOT NULL,
                    end_offset INTEGER NOT NULL,
                    heading_offset INTEGER NOT NULL,
                    level INTEGER NOT NULL DEFAULT 0,
                    detection_source TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    FOREIGN KEY(book_id) REFERENCES books(book_id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE book_resources (
                    resource_id TEXT PRIMARY KEY NOT NULL,
                    book_id TEXT NOT NULL,
                    relative_path TEXT NOT NULL,
                    media_type TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    sha256 TEXT NOT NULL,
                    resource_kind TEXT NOT NULL,
                    alt TEXT,
                    FOREIGN KEY(book_id) REFERENCES books(book_id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE book_blocks (
                    block_id TEXT PRIMARY KEY NOT NULL,
                    book_id TEXT NOT NULL,
                    block_order INTEGER NOT NULL,
                    block_type TEXT NOT NULL,
                    start_offset INTEGER NOT NULL,
                    end_offset INTEGER NOT NULL,
                    level INTEGER NOT NULL DEFAULT 0,
                    resource_id TEXT,
                    alt TEXT,
                    block_style TEXT,
                    inline_spans TEXT,
                    FOREIGN KEY(book_id) REFERENCES books(book_id) ON DELETE CASCADE,
                    FOREIGN KEY(resource_id) REFERENCES book_resources(resource_id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE bookmarks (
                    bookmark_id TEXT PRIMARY KEY NOT NULL,
                    book_id TEXT NOT NULL,
                    chapter_id TEXT,
                    chapter_title TEXT,
                    chapter_start_offset INTEGER,
                    position INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    note TEXT,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(book_id) REFERENCES books(book_id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE comments (
                    comment_id TEXT PRIMARY KEY NOT NULL,
                    book_id TEXT NOT NULL,
                    chapter_id TEXT NOT NULL,
                    chapter_title TEXT,
                    chapter_start_offset INTEGER,
                    comment_type TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    anchor_start INTEGER,
                    anchor_end INTEGER,
                    quoted_text TEXT,
                    prefix_text TEXT,
                    suffix_text TEXT,
                    content_revision TEXT,
                    FOREIGN KEY(book_id) REFERENCES books(book_id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO books(book_id,title,shelf_type,source_type,content_location,source_name,content_hash,content_size,format,created_at,updated_at) " +
                    "VALUES('legacy-book','旧版书','READING','IMPORTED','books/legacy/content.txt','旧版.epub','legacy-hash',10,'EPUB',1,1)",
            )
            db.execSQL(
                "INSERT INTO book_chapters(chapter_id,book_id,title,chapter_order,start_offset,end_offset,heading_offset,level,detection_source,confidence) " +
                    "VALUES('chapter-1','legacy-book','第一章',0,0,8,0,1,'EPUB_NAV',1.0)",
            )
            db.execSQL(
                "INSERT INTO book_blocks(block_id,book_id,block_order,block_type,start_offset,end_offset,level,inline_spans) " +
                    "VALUES('block-1','legacy-book',0,'HEADING',0,4,1,'[]')",
            )
            db.execSQL(
                "INSERT INTO bookmarks(bookmark_id,book_id,chapter_id,position,title,created_at) " +
                    "VALUES('bookmark-1','legacy-book','offset:0',0,'旧书签',1)",
            )
            db.execSQL(
                "INSERT INTO comments(comment_id,book_id,chapter_id,comment_type,content,created_at,updated_at,anchor_start,anchor_end,quoted_text,content_revision) " +
                    "VALUES('comment-1','legacy-book','offset:0','PASSAGE','旧评论',1,1,0,4,'旧段落','legacy-hash')",
            )
            db.version = 4
        } finally {
            db.close()
        }
    }
}
