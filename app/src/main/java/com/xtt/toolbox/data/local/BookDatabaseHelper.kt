package com.xtt.toolbox.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.BookVolume
import com.xtt.toolbox.data.model.Bookmark
import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.data.model.ChapterDetectionSource
import com.xtt.toolbox.data.model.Comment
import com.xtt.toolbox.data.model.CommentType
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockStyleCodec
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import com.xtt.toolbox.domain.content.BookTableCodec
import java.io.File

/**
 * 书籍元数据 SQLite 存储。
 *
 * 正文不写入数据库，只保存应用内部相对路径。数据库升级必须保留已有书籍；
 * 版本 2 新增书签/评论表，版本 3 新增 EPUB 章节/内容块/资源表和作者字段，
 * 版本 4 为内容块增加行内样式和块级排版字段，版本 5 增加可编辑副本来源字段。
 */
class BookDatabaseHelper(
    private val appContext: Context,
    private val databaseName: String = DEFAULT_DATABASE_NAME,
) : SQLiteOpenHelper(appContext.applicationContext, databaseName, null, DATABASE_VERSION) {

    fun databaseFile(): File = appContext.getDatabasePath(databaseName)

    fun schemaVersion(): Int = DATABASE_VERSION

    fun closeForSnapshot() {
        close()
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_BOOKS_TABLE)
        db.execSQL(CREATE_CONTENT_HASH_INDEX)
        createAnnotationsSchema(db)
        createDocumentSchema(db)
        createWritingStatsSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 只做增量迁移；绝不删除或重建 books，保留已有书籍和阅读位置。
        if (oldVersion < 2) {
            createAnnotationsSchema(db)
        }
        if (oldVersion < 3) {
            addColumnIfMissing(db, TABLE_BOOKS, COL_AUTHOR, "TEXT")
            addColumnIfMissing(db, TABLE_BOOKS, COL_LANGUAGE, "TEXT")
            createDocumentSchema(db)
        }
        if (oldVersion < 4) {
            // v3 及更早的书籍没有样式信息；新增列为 NULL，读取时降级为纯文本。
            addColumnIfMissing(db, TABLE_BOOK_BLOCKS, COL_BLK_STYLE, "TEXT")
            addColumnIfMissing(db, TABLE_BOOK_BLOCKS, COL_BLK_SPANS, "TEXT")
        }
        if (oldVersion < 5) {
            // 可编辑副本来源；旧书与普通草稿保持 NULL。
            addColumnIfMissing(db, TABLE_BOOKS, COL_ORIGIN_BOOK_ID, "TEXT")
        }
        if (oldVersion < 6) {
            // 分卷归属；旧章节没有分卷，保持 NULL。
            addColumnIfMissing(db, TABLE_CHAPTERS, COL_CH_VOLUME_ID, "TEXT")
            db.execSQL(CREATE_BOOK_VOLUMES_TABLE)
            db.execSQL(CREATE_VOLUMES_BOOK_INDEX)
        }
        if (oldVersion < 7) {
            // 写作每日净新增；历史只从本版本启用后开始积累。
            createWritingStatsSchema(db)
        }
    }

    private fun addColumnIfMissing(
        db: SQLiteDatabase,
        table: String,
        column: String,
        definition: String,
    ) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == column) {
                    found = true
                    break
                }
            }
            found
        }
        if (!exists) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }

    private fun createDocumentSchema(db: SQLiteDatabase) {
        db.execSQL(CREATE_CHAPTERS_TABLE)
        db.execSQL(CREATE_BOOK_BLOCKS_TABLE)
        db.execSQL(CREATE_BOOK_RESOURCES_TABLE)
        db.execSQL(CREATE_BOOK_VOLUMES_TABLE)
        db.execSQL(CREATE_CHAPTERS_BOOK_INDEX)
        db.execSQL(CREATE_BLOCKS_BOOK_INDEX)
        db.execSQL(CREATE_RESOURCES_BOOK_INDEX)
        db.execSQL(CREATE_VOLUMES_BOOK_INDEX)
    }

    private fun createWritingStatsSchema(db: SQLiteDatabase) {
        db.execSQL(CREATE_WRITING_DAILY_STATS_TABLE)
        db.execSQL(CREATE_WRITING_DAILY_STATS_DATE_INDEX)
    }

    /**
     * 记录一次成功保存后的每日净新增。
     * 基准只在当日第一次有效变更时建立；同一天后续保存仍以该基准计算。
     */
    fun recordWritingDailyStat(
        bookId: String,
        date: String,
        beforeCount: Int,
        afterCount: Int,
    ): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val existing = db.query(
                TABLE_WRITING_DAILY_STATS,
                arrayOf(COL_WDS_BASELINE),
                "$COL_WDS_BOOK_ID = ? AND $COL_WDS_DATE = ?",
                arrayOf(bookId, date),
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else null
            }
            if (existing == null && beforeCount == afterCount) {
                db.setTransactionSuccessful()
                false
            } else {
                val baseline = existing ?: beforeCount
                val values = ContentValues().apply {
                    put(COL_WDS_BOOK_ID, bookId)
                    put(COL_WDS_DATE, date)
                    put(COL_WDS_BASELINE, baseline)
                    put(COL_WDS_NET, afterCount - baseline)
                    put(COL_WDS_UPDATED_AT, System.currentTimeMillis())
                }
                db.insertWithOnConflict(
                    TABLE_WRITING_DAILY_STATS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                db.setTransactionSuccessful()
                true
            }
        } finally {
            db.endTransaction()
        }
    }

    fun loadWritingDailyStats(fromDate: String, toDate: String): List<WritingDailyStatRow> {
        val db = readableDatabase
        return db.query(
            TABLE_WRITING_DAILY_STATS,
            arrayOf(COL_WDS_DATE, "SUM($COL_WDS_NET) AS net", "COUNT(*) AS records"),
            "$COL_WDS_DATE >= ? AND $COL_WDS_DATE <= ?",
            arrayOf(fromDate, toDate),
            COL_WDS_DATE,
            null,
            "$COL_WDS_DATE ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(WritingDailyStatRow(cursor.getString(0), cursor.getInt(1)))
                }
            }
        }
    }

    data class WritingDailyStatRow(
        val date: String,
        val netWords: Int,
    )

    private fun createAnnotationsSchema(db: SQLiteDatabase) {
        db.execSQL(CREATE_BOOKMARKS_TABLE)
        db.execSQL(CREATE_COMMENTS_TABLE)
        db.execSQL(CREATE_BOOKMARKS_BOOK_INDEX)
        db.execSQL(CREATE_COMMENTS_BOOK_INDEX)
        db.execSQL(CREATE_COMMENTS_ANCHOR_INDEX)
    }

    fun readAll(): List<Book> {
        val books = mutableListOf<Book>()
        readableDatabase.query(
            TABLE_BOOKS,
            null,
            null,
            null,
            null,
            null,
            "$COL_UPDATED_AT DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                books += cursor.toBook()
            }
        }
        return books
    }

    fun findByBookId(bookId: String): Book? =
        readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COL_BOOK_ID = ?",
            arrayOf(bookId),
            null,
            null,
            null,
            "1",
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toBook() else null }

    fun findByContentHash(contentHash: String): Book? =
        readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COL_CONTENT_HASH = ?",
            arrayOf(contentHash),
            null,
            null,
            "$COL_UPDATED_AT DESC",
            "1",
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toBook() else null }

    /** 使用 ABORT 冲突策略；bookId 重复时抛出异常，由仓库转换为失败结果。 */
    fun insert(book: Book) {
        writableDatabase.insertOrThrow(TABLE_BOOKS, null, book.toValues())
    }

    /**
     * 原子提交 EPUB 书籍元数据、章节、内容块和资源索引。
     * 正文/图片文件由内容存储先写入，任何数据库异常都会回滚整次事务。
     */
    fun insertDocument(
        book: Book,
        chapters: List<Chapter>,
        blocks: List<BookBlock>,
        resources: List<BookResource>,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow(TABLE_BOOKS, null, book.toValues())
            resources.forEach { db.insertOrThrow(TABLE_BOOK_RESOURCES, null, it.toValues(book.bookId)) }
            chapters.forEach { db.insertOrThrow(TABLE_CHAPTERS, null, it.toValues()) }
            blocks.forEach { db.insertOrThrow(TABLE_BOOK_BLOCKS, null, it.toValues(book.bookId)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 只替换某本书的章节/块/资源，不改书籍元数据；用于导入后补充图片。 */
    fun replaceDocument(
        bookId: String,
        chapters: List<Chapter>,
        blocks: List<BookBlock>,
        resources: List<BookResource>,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_BOOK_BLOCKS, "$COL_BLK_BOOK_ID = ?", arrayOf(bookId))
            db.delete(TABLE_BOOK_RESOURCES, "$COL_RES_BOOK_ID = ?", arrayOf(bookId))
            db.delete(TABLE_CHAPTERS, "$COL_CH_BOOK_ID = ?", arrayOf(bookId))
            resources.forEach { db.insertOrThrow(TABLE_BOOK_RESOURCES, null, it.toValues(bookId)) }
            chapters.forEach { db.insertOrThrow(TABLE_CHAPTERS, null, it.toValues()) }
            blocks.forEach { db.insertOrThrow(TABLE_BOOK_BLOCKS, null, it.toValues(bookId)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 同一事务更新书籍元数据（正文位置/hash/时间）并替换章节、块、资源。
     * 章节/块先删除后插入；任何一条失败都会回滚，保留旧版本。
     */
    fun commitDocument(
        book: Book,
        chapters: List<Chapter>,
        blocks: List<BookBlock>,
        resources: List<BookResource>,
    ) {
        commitDocumentInternal(
            book = book,
            chapters = chapters,
            blocks = blocks,
            resources = resources,
            expectedContentHash = null,
            enforceExpectedHash = false,
        )
    }

    /**
     * 仅当 books.content_hash 仍等于 [expectedContentHash] 时提交。
     * 返回 false 表示版本冲突且没有修改任何行；调用方应清理本次新 revision。
     */
    fun commitDocumentIfUnchanged(
        book: Book,
        chapters: List<Chapter>,
        blocks: List<BookBlock>,
        resources: List<BookResource>,
        expectedContentHash: String?,
    ): Boolean = commitDocumentInternal(
        book = book,
        chapters = chapters,
        blocks = blocks,
        resources = resources,
        expectedContentHash = expectedContentHash,
        enforceExpectedHash = true,
    )

    private fun commitDocumentInternal(
        book: Book,
        chapters: List<Chapter>,
        blocks: List<BookBlock>,
        resources: List<BookResource>,
        expectedContentHash: String?,
        enforceExpectedHash: Boolean,
    ): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val updated = if (!enforceExpectedHash) {
                db.update(
                    TABLE_BOOKS,
                    book.toValues(),
                    "$COL_BOOK_ID = ?",
                    arrayOf(book.bookId),
                )
            } else {
                val where = if (expectedContentHash == null) {
                    "$COL_BOOK_ID = ? AND $COL_CONTENT_HASH IS NULL"
                } else {
                    "$COL_BOOK_ID = ? AND $COL_CONTENT_HASH = ?"
                }
                val whereArgs = if (expectedContentHash == null) {
                    arrayOf(book.bookId)
                } else {
                    arrayOf(book.bookId, expectedContentHash)
                }
                db.update(TABLE_BOOKS, book.toValues(), where, whereArgs)
            }
            if (updated != 1) {
                if (enforceExpectedHash) return false
                throw IllegalStateException("书籍记录不存在")
            }
            db.delete(TABLE_BOOK_BLOCKS, "$COL_BLK_BOOK_ID = ?", arrayOf(book.bookId))
            db.delete(TABLE_BOOK_RESOURCES, "$COL_RES_BOOK_ID = ?", arrayOf(book.bookId))
            db.delete(TABLE_CHAPTERS, "$COL_CH_BOOK_ID = ?", arrayOf(book.bookId))
            resources.forEach {
                db.insertOrThrow(TABLE_BOOK_RESOURCES, null, it.toValues(book.bookId))
            }
            chapters.forEach { db.insertOrThrow(TABLE_CHAPTERS, null, it.toValues()) }
            blocks.forEach { db.insertOrThrow(TABLE_BOOK_BLOCKS, null, it.toValues(book.bookId)) }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun readChapters(bookId: String): List<Chapter> {
        val result = mutableListOf<Chapter>()
        readableDatabase.query(
            TABLE_CHAPTERS,
            null,
            "$COL_CH_BOOK_ID = ?",
            arrayOf(bookId),
            null,
            null,
            "$COL_CH_ORDER ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toChapter()
        }
        return result
    }

    fun readBlocks(bookId: String): List<BookBlock> {
        val result = mutableListOf<BookBlock>()
        readableDatabase.query(
            TABLE_BOOK_BLOCKS,
            null,
            "$COL_BLK_BOOK_ID = ?",
            arrayOf(bookId),
            null,
            null,
            "$COL_BLK_ORDER ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toBookBlock()
        }
        return result
    }

    fun readVolumes(bookId: String): List<BookVolume> {
        val result = mutableListOf<BookVolume>()
        readableDatabase.query(
            TABLE_BOOK_VOLUMES,
            null,
            "$COL_VOL_BOOK_ID = ?",
            arrayOf(bookId),
            null,
            null,
            "$COL_VOL_ORDER ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toBookVolume()
        }
        return result
    }

    fun insertVolume(volume: BookVolume) {
        writableDatabase.insertOrThrow(TABLE_BOOK_VOLUMES, null, volume.toValues())
    }

    fun renameVolume(volumeId: String, title: String): Boolean =
        writableDatabase.update(
            TABLE_BOOK_VOLUMES,
            ContentValues().apply { put(COL_VOL_TITLE, title) },
            "$COL_VOL_VOLUME_ID = ?",
            arrayOf(volumeId),
        ) == 1

    fun deleteVolume(volumeId: String): Boolean =
        writableDatabase.delete(
            TABLE_BOOK_VOLUMES,
            "$COL_VOL_VOLUME_ID = ?",
            arrayOf(volumeId),
        ) == 1

    fun readResources(bookId: String): List<BookResource> {
        val result = mutableListOf<BookResource>()
        readableDatabase.query(
            TABLE_BOOK_RESOURCES,
            null,
            "$COL_RES_BOOK_ID = ?",
            arrayOf(bookId),
            null,
            null,
            "$COL_RES_PATH ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toBookResource()
        }
        return result
    }

    /** 每本书最多取一条明确的封面资源；普通插图不会被当作封面。 */
    fun readCoverResources(): Map<String, BookResource> {
        val result = linkedMapOf<String, BookResource>()
        readableDatabase.query(
            TABLE_BOOK_RESOURCES,
            null,
            "$COL_RES_KIND = ?",
            arrayOf(BookResourceKind.COVER.name),
            null,
            null,
            "$COL_RES_BOOK_ID ASC, $COL_RES_RESOURCE_ID ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val bookId = cursor.getString(cursor.getColumnIndexOrThrow(COL_RES_BOOK_ID))
                if (bookId !in result) {
                    result[bookId] = cursor.toBookResource()
                }
            }
        }
        return result
    }

    fun hasDocument(bookId: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE_BOOK_BLOCKS WHERE $COL_BLK_BOOK_ID = ? LIMIT 1",
            arrayOf(bookId),
        ).use { it.moveToFirst() }

    fun upsert(book: Book) {
        writableDatabase.insertWithOnConflict(
            TABLE_BOOKS,
            null,
            book.toValues(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun delete(bookId: String): Int =
        writableDatabase.delete(TABLE_BOOKS, "$COL_BOOK_ID = ?", arrayOf(bookId))

    /**
     * 只更新阅读相关列，避免用整本 Book 覆盖正文路径、来源等元数据。
     * [lastOpenedAt] 为 null 时保留数据库原值。
     */
    fun updateReadingPosition(
        bookId: String,
        readingPosition: ReadingPosition,
        updatedAt: Long,
        lastOpenedAt: Long? = null,
    ): Int {
        val values = ContentValues().apply {
            putNullable(COL_READING_CHAPTER_ID, readingPosition.chapterId)
            put(COL_READING_OFFSET, readingPosition.offset)
            if (readingPosition.percent == null) {
                putNull(COL_READING_PERCENT)
            } else {
                put(COL_READING_PERCENT, readingPosition.percent)
            }
            put(COL_UPDATED_AT, updatedAt)
            if (lastOpenedAt != null) {
                put(COL_LAST_OPENED_AT, lastOpenedAt)
            }
        }
        return writableDatabase.update(
            TABLE_BOOKS,
            values,
            "$COL_BOOK_ID = ?",
            arrayOf(bookId),
        )
    }

    fun readBookmarks(bookId: String): List<Bookmark> {
        val result = mutableListOf<Bookmark>()
        readableDatabase.query(
            TABLE_BOOKMARKS,
            null,
            "$COL_BM_BOOK_ID = ?",
            arrayOf(bookId),
            null,
            null,
            "$COL_BM_CREATED_AT ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toBookmark()
        }
        return result
    }

    fun readComments(bookId: String): List<Comment> {
        val result = mutableListOf<Comment>()
        readableDatabase.query(
            TABLE_COMMENTS,
            null,
            "$COL_CM_BOOK_ID = ?",
            arrayOf(bookId),
            null,
            null,
            "$COL_CM_CREATED_AT ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toComment()
        }
        return result
    }

    fun insertBookmark(bookmark: Bookmark) {
        writableDatabase.insertOrThrow(TABLE_BOOKMARKS, null, bookmark.toValues())
    }

    fun insertComment(comment: Comment) {
        writableDatabase.insertOrThrow(TABLE_COMMENTS, null, comment.toValues())
    }

    private fun Bookmark.toValues(): ContentValues = ContentValues().apply {
        put(COL_BM_BOOKMARK_ID, bookmarkId)
        put(COL_BM_BOOK_ID, bookId)
        putNullable(COL_BM_CHAPTER_ID, chapterId)
        putNullable(COL_BM_CHAPTER_TITLE, chapterTitle)
        if (chapterStartOffset == null) putNull(COL_BM_CHAPTER_START) else put(COL_BM_CHAPTER_START, chapterStartOffset)
        put(COL_BM_POSITION, position)
        put(COL_BM_TITLE, title)
        putNullable(COL_BM_NOTE, note)
        put(COL_BM_CREATED_AT, createdAt)
    }

    private fun Comment.toValues(): ContentValues = ContentValues().apply {
        put(COL_CM_COMMENT_ID, commentId)
        put(COL_CM_BOOK_ID, bookId)
        put(COL_CM_CHAPTER_ID, chapterId)
        putNullable(COL_CM_CHAPTER_TITLE, chapterTitle)
        if (chapterStartOffset == null) putNull(COL_CM_CHAPTER_START) else put(COL_CM_CHAPTER_START, chapterStartOffset)
        put(COL_CM_TYPE, commentType.name)
        put(COL_CM_CONTENT, content)
        put(COL_CM_CREATED_AT, createdAt)
        put(COL_CM_UPDATED_AT, updatedAt)
        if (anchorStart == null) putNull(COL_CM_ANCHOR_START) else put(COL_CM_ANCHOR_START, anchorStart)
        if (anchorEnd == null) putNull(COL_CM_ANCHOR_END) else put(COL_CM_ANCHOR_END, anchorEnd)
        putNullable(COL_CM_QUOTED_TEXT, quotedText)
        putNullable(COL_CM_PREFIX_TEXT, prefixText)
        putNullable(COL_CM_SUFFIX_TEXT, suffixText)
        putNullable(COL_CM_CONTENT_REVISION, contentRevision)
    }

    private fun Chapter.toValues(): ContentValues = ContentValues().apply {
        put(COL_CH_CHAPTER_ID, chapterId)
        put(COL_CH_BOOK_ID, bookId)
        put(COL_CH_TITLE, title)
        put(COL_CH_ORDER, order)
        put(COL_CH_START, startOffset)
        put(COL_CH_END, endOffset)
        put(COL_CH_HEADING, headingOffset)
        put(COL_CH_LEVEL, level)
        putNullable(COL_CH_VOLUME_ID, volumeId)
        put(COL_CH_SOURCE, detectionSource.name)
        put(COL_CH_CONFIDENCE, confidence)
    }

    private fun BookBlock.toValues(bookId: String): ContentValues = ContentValues().apply {
        put(COL_BLK_BLOCK_ID, blockId)
        put(COL_BLK_BOOK_ID, bookId)
        put(COL_BLK_ORDER, order)
        put(COL_BLK_TYPE, type.name)
        put(COL_BLK_START, startOffset)
        put(COL_BLK_END, endOffset)
        put(COL_BLK_LEVEL, level)
        putNullable(COL_BLK_RESOURCE_ID, resourceId)
        putNullable(
            COL_BLK_ALT,
            when (type) {
                BookBlockType.TABLE -> table?.let(BookTableCodec::encode)
                BookBlockType.FOOTNOTE -> footnoteRefIds
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString("\u001F")

                else -> alt
            },
        )
        putNullable(COL_BLK_STYLE, BookBlockStyleCodec.encodeStyle(style))
        putNullable(COL_BLK_SPANS, BookBlockStyleCodec.encodeSpans(spans))
    }

    private fun BookResource.toValues(bookId: String): ContentValues = ContentValues().apply {
        put(COL_RES_RESOURCE_ID, resourceId)
        put(COL_RES_BOOK_ID, bookId)
        put(COL_RES_PATH, relativePath)
        put(COL_RES_MEDIA_TYPE, mediaType)
        put(COL_RES_SIZE, sizeBytes)
        put(COL_RES_SHA256, sha256)
        put(COL_RES_KIND, kind.name)
        putNullable(COL_RES_ALT, alt)
    }

    private fun Book.toValues(): ContentValues = ContentValues().apply {
        put(COL_BOOK_ID, bookId)
        put(COL_TITLE, title)
        putNullable(COL_AUTHOR, author)
        putNullable(COL_LANGUAGE, language)
        put(COL_SHELF_TYPE, shelfType.name)
        put(COL_SOURCE_TYPE, sourceType.name)
        putNullable(COL_SOURCE_URI, sourceUri)
        putNullable(COL_CONTENT_LOCATION, contentLocation)
        putNullable(COL_SOURCE_NAME, sourceName)
        putNullable(COL_CONTENT_HASH, contentHash)
        put(COL_CONTENT_SIZE, contentSize)
        putNullable(COL_SOURCE_ENCODING, sourceEncoding)
        put(COL_FORMAT, format.name)
        putNullable(COL_ORIGIN_BOOK_ID, originBookId)
        putNullable(COL_READING_CHAPTER_ID, readingPosition?.chapterId)
        put(COL_READING_OFFSET, readingPosition?.offset ?: 0)
        if (readingPosition?.percent == null) {
            putNull(COL_READING_PERCENT)
        } else {
            put(COL_READING_PERCENT, readingPosition.percent)
        }
        put(COL_CREATED_AT, createdAt)
        put(COL_UPDATED_AT, updatedAt)
        if (lastOpenedAt == null) {
            putNull(COL_LAST_OPENED_AT)
        } else {
            put(COL_LAST_OPENED_AT, lastOpenedAt)
        }
    }

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun Cursor.toBook(): Book {
        val readingPosition = ReadingPosition(
            chapterId = getStringOrNull(COL_READING_CHAPTER_ID),
            offset = getInt(getColumnIndexOrThrow(COL_READING_OFFSET)),
            percent = if (isNull(getColumnIndexOrThrow(COL_READING_PERCENT))) {
                null
            } else {
                getDouble(getColumnIndexOrThrow(COL_READING_PERCENT)).toFloat()
            },
        )
        return Book(
            bookId = getString(getColumnIndexOrThrow(COL_BOOK_ID)),
            title = getString(getColumnIndexOrThrow(COL_TITLE)),
            author = getStringOrNull(COL_AUTHOR),
            language = getStringOrNull(COL_LANGUAGE),
            shelfType = ShelfType.valueOf(getString(getColumnIndexOrThrow(COL_SHELF_TYPE))),
            sourceType = SourceType.valueOf(getString(getColumnIndexOrThrow(COL_SOURCE_TYPE))),
            sourceUri = getStringOrNull(COL_SOURCE_URI),
            contentLocation = getStringOrNull(COL_CONTENT_LOCATION),
            sourceName = getStringOrNull(COL_SOURCE_NAME),
            contentHash = getStringOrNull(COL_CONTENT_HASH),
            contentSize = getLong(getColumnIndexOrThrow(COL_CONTENT_SIZE)),
            sourceEncoding = getStringOrNull(COL_SOURCE_ENCODING),
            format = runCatching {
                BookFormat.valueOf(getString(getColumnIndexOrThrow(COL_FORMAT)))
            }.getOrDefault(BookFormat.TXT),
            readingPosition = readingPosition,
            createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
            updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT)),
            lastOpenedAt = if (isNull(getColumnIndexOrThrow(COL_LAST_OPENED_AT))) {
                null
            } else {
                getLong(getColumnIndexOrThrow(COL_LAST_OPENED_AT))
            },
            originBookId = getStringOrNull(COL_ORIGIN_BOOK_ID),
        )
    }

    private fun Cursor.toBookmark(): Bookmark = Bookmark(
        bookmarkId = getString(getColumnIndexOrThrow(COL_BM_BOOKMARK_ID)),
        bookId = getString(getColumnIndexOrThrow(COL_BM_BOOK_ID)),
        chapterId = getStringOrNull(COL_BM_CHAPTER_ID),
        chapterTitle = getStringOrNull(COL_BM_CHAPTER_TITLE),
        chapterStartOffset = getIntOrNull(COL_BM_CHAPTER_START),
        position = getInt(getColumnIndexOrThrow(COL_BM_POSITION)),
        title = getString(getColumnIndexOrThrow(COL_BM_TITLE)),
        note = getStringOrNull(COL_BM_NOTE),
        createdAt = getLong(getColumnIndexOrThrow(COL_BM_CREATED_AT)),
    )

    private fun Cursor.toComment(): Comment = Comment(
        commentId = getString(getColumnIndexOrThrow(COL_CM_COMMENT_ID)),
        bookId = getString(getColumnIndexOrThrow(COL_CM_BOOK_ID)),
        chapterId = getString(getColumnIndexOrThrow(COL_CM_CHAPTER_ID)),
        chapterTitle = getStringOrNull(COL_CM_CHAPTER_TITLE),
        chapterStartOffset = getIntOrNull(COL_CM_CHAPTER_START),
        commentType = CommentType.valueOf(getString(getColumnIndexOrThrow(COL_CM_TYPE))),
        content = getString(getColumnIndexOrThrow(COL_CM_CONTENT)),
        createdAt = getLong(getColumnIndexOrThrow(COL_CM_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(COL_CM_UPDATED_AT)),
        anchorStart = getIntOrNull(COL_CM_ANCHOR_START),
        anchorEnd = getIntOrNull(COL_CM_ANCHOR_END),
        quotedText = getStringOrNull(COL_CM_QUOTED_TEXT),
        prefixText = getStringOrNull(COL_CM_PREFIX_TEXT),
        suffixText = getStringOrNull(COL_CM_SUFFIX_TEXT),
        contentRevision = getStringOrNull(COL_CM_CONTENT_REVISION),
    )

    private fun BookVolume.toValues(): ContentValues = ContentValues().apply {
        put(COL_VOL_VOLUME_ID, volumeId)
        put(COL_VOL_BOOK_ID, bookId)
        put(COL_VOL_TITLE, title)
        put(COL_VOL_ORDER, order)
        put(COL_VOL_CREATED_AT, createdAt)
    }

    private fun Cursor.toBookVolume(): BookVolume = BookVolume(
        volumeId = getString(getColumnIndexOrThrow(COL_VOL_VOLUME_ID)),
        bookId = getString(getColumnIndexOrThrow(COL_VOL_BOOK_ID)),
        title = getString(getColumnIndexOrThrow(COL_VOL_TITLE)),
        order = getInt(getColumnIndexOrThrow(COL_VOL_ORDER)),
        createdAt = getLong(getColumnIndexOrThrow(COL_VOL_CREATED_AT)),
    )

    private fun Cursor.toChapter(): Chapter = Chapter(
        chapterId = getString(getColumnIndexOrThrow(COL_CH_CHAPTER_ID)),
        bookId = getString(getColumnIndexOrThrow(COL_CH_BOOK_ID)),
        title = getString(getColumnIndexOrThrow(COL_CH_TITLE)),
        order = getInt(getColumnIndexOrThrow(COL_CH_ORDER)),
        startOffset = getInt(getColumnIndexOrThrow(COL_CH_START)),
        endOffset = getInt(getColumnIndexOrThrow(COL_CH_END)),
        headingOffset = getInt(getColumnIndexOrThrow(COL_CH_HEADING)),
        detectionSource = runCatching {
            ChapterDetectionSource.valueOf(getString(getColumnIndexOrThrow(COL_CH_SOURCE)))
        }.getOrDefault(ChapterDetectionSource.EPUB_FALLBACK),
        confidence = getDouble(getColumnIndexOrThrow(COL_CH_CONFIDENCE)).toFloat(),
        level = getInt(getColumnIndexOrThrow(COL_CH_LEVEL)),
        volumeId = getStringOrNull(COL_CH_VOLUME_ID),
    )

    private fun Cursor.toBookBlock(): BookBlock {
        val start = getInt(getColumnIndexOrThrow(COL_BLK_START))
        val end = getInt(getColumnIndexOrThrow(COL_BLK_END))
        val textLength = (end - start).coerceAtLeast(0)
        val storedType = runCatching {
            BookBlockType.valueOf(getString(getColumnIndexOrThrow(COL_BLK_TYPE)))
        }.getOrDefault(BookBlockType.PARAGRAPH)
        val storedAlt = getStringOrNull(COL_BLK_ALT)
        val table = if (storedType == BookBlockType.TABLE) {
            BookTableCodec.decode(storedAlt)
        } else {
            null
        }
        val type = if (storedType == BookBlockType.TABLE && table == null) {
            BookBlockType.PARAGRAPH
        } else {
            storedType
        }
        val footnoteRefIds = if (type == BookBlockType.FOOTNOTE) {
            storedAlt?.split('\u001F')?.filter { it.isNotBlank() }.orEmpty()
        } else {
            emptyList()
        }
        return BookBlock(
            blockId = getString(getColumnIndexOrThrow(COL_BLK_BLOCK_ID)),
            order = getInt(getColumnIndexOrThrow(COL_BLK_ORDER)),
            type = type,
            startOffset = start,
            endOffset = end,
            level = getInt(getColumnIndexOrThrow(COL_BLK_LEVEL)),
            resourceId = getStringOrNull(COL_BLK_RESOURCE_ID),
            alt = when (type) {
                BookBlockType.TABLE, BookBlockType.FOOTNOTE -> null
                else -> storedAlt
            },
            style = BookBlockStyleCodec.decodeStyle(getStringOrNull(COL_BLK_STYLE)),
            spans = BookBlockStyleCodec.decodeSpans(getStringOrNull(COL_BLK_SPANS))
                .filter { it.end <= textLength },
            table = table.takeIf { type == BookBlockType.TABLE },
            footnoteRefIds = footnoteRefIds,
        )
    }

    private fun Cursor.toBookResource(): BookResource = BookResource(
        resourceId = getString(getColumnIndexOrThrow(COL_RES_RESOURCE_ID)),
        relativePath = getString(getColumnIndexOrThrow(COL_RES_PATH)),
        mediaType = getString(getColumnIndexOrThrow(COL_RES_MEDIA_TYPE)),
        sizeBytes = getLong(getColumnIndexOrThrow(COL_RES_SIZE)),
        sha256 = getString(getColumnIndexOrThrow(COL_RES_SHA256)),
        kind = runCatching {
            BookResourceKind.valueOf(getString(getColumnIndexOrThrow(COL_RES_KIND)))
        }.getOrDefault(BookResourceKind.IMAGE),
        alt = getStringOrNull(COL_RES_ALT),
    )

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getIntOrNull(column: String): Int? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getInt(index)
    }

    companion object {
        const val DEFAULT_DATABASE_NAME = "xtt_library.db"
        private const val DATABASE_VERSION = 7

        private const val TABLE_BOOKS = "books"
        private const val COL_BOOK_ID = "book_id"
        private const val COL_TITLE = "title"
        private const val COL_AUTHOR = "author"
        private const val COL_LANGUAGE = "language"
        private const val COL_SHELF_TYPE = "shelf_type"
        private const val COL_SOURCE_TYPE = "source_type"
        private const val COL_SOURCE_URI = "source_uri"
        private const val COL_CONTENT_LOCATION = "content_location"
        private const val COL_SOURCE_NAME = "source_name"
        private const val COL_CONTENT_HASH = "content_hash"
        private const val COL_CONTENT_SIZE = "content_size"
        private const val COL_SOURCE_ENCODING = "source_encoding"
        private const val COL_FORMAT = "format"
        private const val COL_ORIGIN_BOOK_ID = "origin_book_id"
        private const val COL_READING_CHAPTER_ID = "reading_chapter_id"
        private const val COL_READING_OFFSET = "reading_offset"
        private const val COL_READING_PERCENT = "reading_percent"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_UPDATED_AT = "updated_at"
        private const val COL_LAST_OPENED_AT = "last_opened_at"

        private const val CREATE_BOOKS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOKS (
                $COL_BOOK_ID TEXT PRIMARY KEY NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_AUTHOR TEXT,
                $COL_LANGUAGE TEXT,
                $COL_SHELF_TYPE TEXT NOT NULL,
                $COL_SOURCE_TYPE TEXT NOT NULL,
                $COL_SOURCE_URI TEXT,
                $COL_CONTENT_LOCATION TEXT,
                $COL_SOURCE_NAME TEXT,
                $COL_CONTENT_HASH TEXT,
                $COL_CONTENT_SIZE INTEGER NOT NULL DEFAULT 0,
                $COL_SOURCE_ENCODING TEXT,
                $COL_FORMAT TEXT NOT NULL DEFAULT 'TXT',
                $COL_ORIGIN_BOOK_ID TEXT,
                $COL_READING_CHAPTER_ID TEXT,
                $COL_READING_OFFSET INTEGER NOT NULL DEFAULT 0,
                $COL_READING_PERCENT REAL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_LAST_OPENED_AT INTEGER
            )
        """

        private const val CREATE_CONTENT_HASH_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_books_content_hash ON $TABLE_BOOKS($COL_CONTENT_HASH)"

        private const val TABLE_BOOKMARKS = "bookmarks"
        private const val COL_BM_BOOKMARK_ID = "bookmark_id"
        private const val COL_BM_BOOK_ID = "book_id"
        private const val COL_BM_CHAPTER_ID = "chapter_id"
        private const val COL_BM_CHAPTER_TITLE = "chapter_title"
        private const val COL_BM_CHAPTER_START = "chapter_start_offset"
        private const val COL_BM_POSITION = "position"
        private const val COL_BM_TITLE = "title"
        private const val COL_BM_NOTE = "note"
        private const val COL_BM_CREATED_AT = "created_at"

        private const val TABLE_COMMENTS = "comments"
        private const val COL_CM_COMMENT_ID = "comment_id"
        private const val COL_CM_BOOK_ID = "book_id"
        private const val COL_CM_CHAPTER_ID = "chapter_id"
        private const val COL_CM_CHAPTER_TITLE = "chapter_title"
        private const val COL_CM_CHAPTER_START = "chapter_start_offset"
        private const val COL_CM_TYPE = "comment_type"
        private const val COL_CM_CONTENT = "content"
        private const val COL_CM_CREATED_AT = "created_at"
        private const val COL_CM_UPDATED_AT = "updated_at"
        private const val COL_CM_ANCHOR_START = "anchor_start"
        private const val COL_CM_ANCHOR_END = "anchor_end"
        private const val COL_CM_QUOTED_TEXT = "quoted_text"
        private const val COL_CM_PREFIX_TEXT = "prefix_text"
        private const val COL_CM_SUFFIX_TEXT = "suffix_text"
        private const val COL_CM_CONTENT_REVISION = "content_revision"

        private const val CREATE_BOOKMARKS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOKMARKS (
                $COL_BM_BOOKMARK_ID TEXT PRIMARY KEY NOT NULL,
                $COL_BM_BOOK_ID TEXT NOT NULL,
                $COL_BM_CHAPTER_ID TEXT,
                $COL_BM_CHAPTER_TITLE TEXT,
                $COL_BM_CHAPTER_START INTEGER,
                $COL_BM_POSITION INTEGER NOT NULL,
                $COL_BM_TITLE TEXT NOT NULL,
                $COL_BM_NOTE TEXT,
                $COL_BM_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY($COL_BM_BOOK_ID) REFERENCES $TABLE_BOOKS($COL_BOOK_ID) ON DELETE CASCADE
            )
        """

        private const val CREATE_COMMENTS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_COMMENTS (
                $COL_CM_COMMENT_ID TEXT PRIMARY KEY NOT NULL,
                $COL_CM_BOOK_ID TEXT NOT NULL,
                $COL_CM_CHAPTER_ID TEXT NOT NULL,
                $COL_CM_CHAPTER_TITLE TEXT,
                $COL_CM_CHAPTER_START INTEGER,
                $COL_CM_TYPE TEXT NOT NULL,
                $COL_CM_CONTENT TEXT NOT NULL,
                $COL_CM_CREATED_AT INTEGER NOT NULL,
                $COL_CM_UPDATED_AT INTEGER NOT NULL,
                $COL_CM_ANCHOR_START INTEGER,
                $COL_CM_ANCHOR_END INTEGER,
                $COL_CM_QUOTED_TEXT TEXT,
                $COL_CM_PREFIX_TEXT TEXT,
                $COL_CM_SUFFIX_TEXT TEXT,
                $COL_CM_CONTENT_REVISION TEXT,
                FOREIGN KEY($COL_CM_BOOK_ID) REFERENCES $TABLE_BOOKS($COL_BOOK_ID) ON DELETE CASCADE
            )
        """

        private const val CREATE_BOOKMARKS_BOOK_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_bookmarks_book ON $TABLE_BOOKMARKS($COL_BM_BOOK_ID, $COL_BM_POSITION)"

        private const val CREATE_COMMENTS_BOOK_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_comments_book ON $TABLE_COMMENTS($COL_CM_BOOK_ID, $COL_CM_TYPE)"

        private const val CREATE_COMMENTS_ANCHOR_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_comments_anchor ON $TABLE_COMMENTS($COL_CM_BOOK_ID, $COL_CM_ANCHOR_START)"

        private const val TABLE_CHAPTERS = "book_chapters"
        private const val COL_CH_CHAPTER_ID = "chapter_id"
        private const val COL_CH_BOOK_ID = "book_id"
        private const val COL_CH_TITLE = "title"
        private const val COL_CH_ORDER = "chapter_order"
        private const val COL_CH_START = "start_offset"
        private const val COL_CH_END = "end_offset"
        private const val COL_CH_HEADING = "heading_offset"
        private const val COL_CH_LEVEL = "level"
        private const val COL_CH_VOLUME_ID = "volume_id"
        private const val COL_CH_SOURCE = "detection_source"
        private const val COL_CH_CONFIDENCE = "confidence"

        private const val TABLE_BOOK_BLOCKS = "book_blocks"
        private const val COL_BLK_BLOCK_ID = "block_id"
        private const val COL_BLK_BOOK_ID = "book_id"
        private const val COL_BLK_ORDER = "block_order"
        private const val COL_BLK_TYPE = "block_type"
        private const val COL_BLK_START = "start_offset"
        private const val COL_BLK_END = "end_offset"
        private const val COL_BLK_LEVEL = "level"
        private const val COL_BLK_RESOURCE_ID = "resource_id"
        private const val COL_BLK_ALT = "alt"
        private const val COL_BLK_STYLE = "block_style"
        private const val COL_BLK_SPANS = "inline_spans"

        private const val TABLE_BOOK_RESOURCES = "book_resources"
        private const val COL_RES_RESOURCE_ID = "resource_id"
        private const val COL_RES_BOOK_ID = "book_id"
        private const val COL_RES_PATH = "relative_path"
        private const val COL_RES_MEDIA_TYPE = "media_type"
        private const val COL_RES_SIZE = "size_bytes"
        private const val COL_RES_SHA256 = "sha256"
        private const val COL_RES_KIND = "resource_kind"
        private const val COL_RES_ALT = "alt"

        private const val TABLE_BOOK_VOLUMES = "book_volumes"
        private const val COL_VOL_VOLUME_ID = "volume_id"
        private const val COL_VOL_BOOK_ID = "book_id"
        private const val COL_VOL_TITLE = "title"
        private const val COL_VOL_ORDER = "volume_order"
        private const val COL_VOL_CREATED_AT = "created_at"

        private const val TABLE_WRITING_DAILY_STATS = "writing_daily_stats"
        private const val COL_WDS_BOOK_ID = "book_id"
        private const val COL_WDS_DATE = "stat_date"
        private const val COL_WDS_BASELINE = "baseline_count"
        private const val COL_WDS_NET = "net_count"
        private const val COL_WDS_UPDATED_AT = "updated_at"

        private const val CREATE_WRITING_DAILY_STATS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_WRITING_DAILY_STATS (
                $COL_WDS_BOOK_ID TEXT NOT NULL,
                $COL_WDS_DATE TEXT NOT NULL,
                $COL_WDS_BASELINE INTEGER NOT NULL,
                $COL_WDS_NET INTEGER NOT NULL,
                $COL_WDS_UPDATED_AT INTEGER NOT NULL,
                PRIMARY KEY($COL_WDS_BOOK_ID, $COL_WDS_DATE),
                FOREIGN KEY($COL_WDS_BOOK_ID) REFERENCES $TABLE_BOOKS($COL_BOOK_ID) ON DELETE CASCADE
            )
        """

        private const val CREATE_WRITING_DAILY_STATS_DATE_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_writing_daily_date ON $TABLE_WRITING_DAILY_STATS($COL_WDS_DATE)"

        private const val CREATE_CHAPTERS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_CHAPTERS (
                $COL_CH_CHAPTER_ID TEXT PRIMARY KEY NOT NULL,
                $COL_CH_BOOK_ID TEXT NOT NULL,
                $COL_CH_TITLE TEXT NOT NULL,
                $COL_CH_ORDER INTEGER NOT NULL,
                $COL_CH_START INTEGER NOT NULL,
                $COL_CH_END INTEGER NOT NULL,
                $COL_CH_HEADING INTEGER NOT NULL,
                $COL_CH_LEVEL INTEGER NOT NULL DEFAULT 0,
                $COL_CH_VOLUME_ID TEXT,
                $COL_CH_SOURCE TEXT NOT NULL,
                $COL_CH_CONFIDENCE REAL NOT NULL,
                FOREIGN KEY($COL_CH_BOOK_ID) REFERENCES $TABLE_BOOKS($COL_BOOK_ID) ON DELETE CASCADE
            )
        """

        private const val CREATE_BOOK_RESOURCES_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOK_RESOURCES (
                $COL_RES_RESOURCE_ID TEXT PRIMARY KEY NOT NULL,
                $COL_RES_BOOK_ID TEXT NOT NULL,
                $COL_RES_PATH TEXT NOT NULL,
                $COL_RES_MEDIA_TYPE TEXT NOT NULL,
                $COL_RES_SIZE INTEGER NOT NULL,
                $COL_RES_SHA256 TEXT NOT NULL,
                $COL_RES_KIND TEXT NOT NULL,
                $COL_RES_ALT TEXT,
                FOREIGN KEY($COL_RES_BOOK_ID) REFERENCES $TABLE_BOOKS($COL_BOOK_ID) ON DELETE CASCADE
            )
        """

        private const val CREATE_BOOK_BLOCKS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOK_BLOCKS (
                $COL_BLK_BLOCK_ID TEXT PRIMARY KEY NOT NULL,
                $COL_BLK_BOOK_ID TEXT NOT NULL,
                $COL_BLK_ORDER INTEGER NOT NULL,
                $COL_BLK_TYPE TEXT NOT NULL,
                $COL_BLK_START INTEGER NOT NULL,
                $COL_BLK_END INTEGER NOT NULL,
                $COL_BLK_LEVEL INTEGER NOT NULL DEFAULT 0,
                $COL_BLK_RESOURCE_ID TEXT,
                $COL_BLK_ALT TEXT,
                $COL_BLK_STYLE TEXT,
                $COL_BLK_SPANS TEXT,
                FOREIGN KEY($COL_BLK_BOOK_ID) REFERENCES $TABLE_BOOKS($COL_BOOK_ID) ON DELETE CASCADE,
                FOREIGN KEY($COL_BLK_RESOURCE_ID) REFERENCES $TABLE_BOOK_RESOURCES($COL_RES_RESOURCE_ID) ON DELETE CASCADE
            )
        """

        private const val CREATE_CHAPTERS_BOOK_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_chapters_book ON $TABLE_CHAPTERS($COL_CH_BOOK_ID, $COL_CH_ORDER)"

        private const val CREATE_VOLUMES_BOOK_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_volumes_book ON $TABLE_BOOK_VOLUMES($COL_VOL_BOOK_ID, $COL_VOL_ORDER)"

        private const val CREATE_BOOK_VOLUMES_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOK_VOLUMES (
                $COL_VOL_VOLUME_ID TEXT PRIMARY KEY NOT NULL,
                $COL_VOL_BOOK_ID TEXT NOT NULL,
                $COL_VOL_TITLE TEXT NOT NULL,
                $COL_VOL_ORDER INTEGER NOT NULL,
                $COL_VOL_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY($COL_VOL_BOOK_ID) REFERENCES $TABLE_BOOKS($COL_BOOK_ID) ON DELETE CASCADE
            )
        """

        private const val CREATE_BLOCKS_BOOK_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_blocks_book ON $TABLE_BOOK_BLOCKS($COL_BLK_BOOK_ID, $COL_BLK_ORDER)"

        private const val CREATE_RESOURCES_BOOK_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_resources_book ON $TABLE_BOOK_RESOURCES($COL_RES_BOOK_ID)"
    }
}
