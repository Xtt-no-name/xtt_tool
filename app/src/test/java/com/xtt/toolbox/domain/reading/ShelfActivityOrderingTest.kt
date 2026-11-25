package com.xtt.toolbox.domain.reading

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfActivityOrderingTest {
    @Test
    fun groupActivityUsesLatestBookOpenIncludingHiddenBooks() {
        val books = listOf(
            book("a", groupId = "group-a", lastOpened = 10),
            book("a-hidden", groupId = "group-a", lastOpened = 30, hidden = true),
            book("b", groupId = "group-b", lastOpened = 20),
        )
        assertEquals(30L, ShelfActivityOrdering.groupActivity("group-a", books))
        assertEquals(20L, ShelfActivityOrdering.groupActivity("group-b", books))
        assertEquals(Long.MIN_VALUE, ShelfActivityOrdering.groupActivity("empty", books))
    }

    @Test
    fun booksAndGroupsCanBeOrderedByTheSameActivityClock() {
        val books = listOf(
            book("a1", groupId = "group-a", lastOpened = 10),
            book("b1", groupId = "group-b", lastOpened = 30),
            book("ungrouped", groupId = null, lastOpened = 20),
        )
        val groups = listOf("group-a", "group-b")
        val order = (groups.map { "group:$it" } + books.filter { it.groupId == null }.map { "book:${it.bookId}" })
            .sortedByDescending { key ->
                if (key.startsWith("group:")) {
                    ShelfActivityOrdering.groupActivity(key.removePrefix("group:"), books)
                } else {
                    books.first { "book:${it.bookId}" == key }.lastOpenedAt ?: Long.MIN_VALUE
                }
            }
        assertEquals(listOf("group:group-b", "book:ungrouped", "group:group-a"), order)
    }

    @Test
    fun groupInternalBooksAreOrderedByLastOpened() {
        val books = listOf(
            book("a1", groupId = "group-a", lastOpened = 10),
            book("a2", groupId = "group-a", lastOpened = 30),
            book("other", groupId = "group-b", lastOpened = 20),
        )
        val ordered = ShelfActivityOrdering.orderedBooks(books.filter { it.groupId == "group-a" })
        assertEquals(listOf("a2", "a1"), ordered.map { it.bookId })
        assertEquals(
            listOf("other"),
            ShelfActivityOrdering.orderedBooks(books.filter { it.groupId == "group-b" })
                .map { it.bookId },
        )
    }

    private fun book(
        id: String,
        groupId: String?,
        lastOpened: Long,
        hidden: Boolean = false,
    ): Book = Book(
        bookId = id,
        title = id,
        shelfType = ShelfType.READING,
        sourceType = SourceType.CREATED,
        format = BookFormat.TXT,
        createdAt = 0,
        updatedAt = 0,
        lastOpenedAt = lastOpened,
        groupId = groupId,
        isHidden = hidden,
    )
}
