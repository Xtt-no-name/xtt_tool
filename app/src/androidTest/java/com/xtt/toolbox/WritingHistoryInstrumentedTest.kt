package com.xtt.toolbox

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xtt.toolbox.data.local.BookDatabaseHelper
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.SqliteBookRepository
import com.xtt.toolbox.data.repository.WritingHistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WritingHistoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var helper: BookDatabaseHelper

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "xtt-history-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        helper = BookDatabaseHelper(context, databaseName)
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun dailyBaselineUsesFirstSaveAndDoesNotDoubleCount() = runBlocking {
        val repository = SqliteBookRepository(helper)
        val book = Book(
            bookId = "history-book",
            title = "历史测试",
            shelfType = ShelfType.WRITING,
            sourceType = SourceType.CREATED,
            format = BookFormat.DRAFT,
            createdAt = 1,
            updatedAt = 1,
        )
        repository.insert(book).getOrThrow()
        val history = WritingHistoryRepository(helper)
        val now = System.currentTimeMillis()

        assertTrue(history.recordSave(book.bookId, 10, 15, now))
        assertTrue(history.recordSave(book.bookId, 15, 20, now))
        assertTrue(history.recordSave(book.bookId, 20, 18, now))
        val stats = history.loadLastYear(now)
        assertEquals(1, stats.size)
        assertEquals(8, stats.single().netWords)
    }

    @Test
    fun unchangedSaveDoesNotCreateFakeHistory() = runBlocking {
        val repository = SqliteBookRepository(helper)
        val book = Book(
            bookId = "history-empty",
            title = "无变化",
            shelfType = ShelfType.WRITING,
            sourceType = SourceType.CREATED,
            format = BookFormat.DRAFT,
            createdAt = 1,
            updatedAt = 1,
        )
        repository.insert(book).getOrThrow()
        val history = WritingHistoryRepository(helper)
        assertFalse(history.recordSave(book.bookId, 10, 10, System.currentTimeMillis()))
        assertTrue(history.loadLastYear().isEmpty())
    }
}
