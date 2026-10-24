package com.xtt.toolbox.domain.reading

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingShelfOrderingTest {
    private fun book(title: String, openedAt: Long? = null): Book =
        Book.create(
            title = title,
            shelfType = ShelfType.READING,
            sourceType = SourceType.IMPORTED,
            now = 1_000L,
        ).copy(lastOpenedAt = openedAt)

    @Test
    fun mostRecentlyOpenedComesFirstAndOthersKeepRelativeOrder() {
        val neverA = book("A")
        val openedOld = book("B", 100L)
        val neverB = book("C")
        val openedNew = book("D", 300L)
        val ordered = ReadingShelfOrdering.ordered(
            listOf(neverA, openedOld, neverB, openedNew),
        )
        assertEquals(listOf("D", "B", "A", "C"), ordered.map { it.title })
    }

    @Test
    fun repeatedTimestampIsStable() {
        val first = book("first", 100L)
        val second = book("second", 100L)
        val newer = book("newer", 200L)
        val ordered = ReadingShelfOrdering.ordered(listOf(first, second, newer))
        assertEquals(listOf("newer", "first", "second"), ordered.map { it.title })
    }

    @Test
    fun reopeningMovesBookToFront() {
        val a = book("A", 100L)
        val b = book("B", 200L)
        val c = book("C", 300L)
        val before = ReadingShelfOrdering.ordered(listOf(a, b, c))
        assertEquals(listOf("C", "B", "A"), before.map { it.title })

        val reopened = b.copy(lastOpenedAt = 400L)
        val after = ReadingShelfOrdering.ordered(listOf(a, reopened, c))
        assertEquals(listOf("B", "C", "A"), after.map { it.title })
    }
}
