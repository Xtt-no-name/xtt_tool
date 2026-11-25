package com.xtt.toolbox.data.repository

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfGroupRepositoryTest {
    @Test
    fun groupsAreSeparatedByShelfAndNamesCanRepeatAcrossShelves() = runBlocking {
        val repository = InMemoryShelfGroupRepository()
        val reading = repository.create(ShelfType.READING, "最近阅读").getOrThrow()
        val writing = repository.create(ShelfType.WRITING, "最近阅读").getOrThrow()

        assertEquals(ShelfType.READING, reading.shelfType)
        assertEquals(ShelfType.WRITING, writing.shelfType)
        assertEquals(2, repository.groups.value.size)
        assertTrue(repository.create(ShelfType.READING, " 最近阅读 ").isFailure)
    }

    @Test
    fun renameAndHiddenAreIndependentOfOtherGroups() = runBlocking {
        val repository = InMemoryShelfGroupRepository()
        val first = repository.create(ShelfType.READING, "甲组").getOrThrow()
        val second = repository.create(ShelfType.READING, "乙组").getOrThrow()

        repository.rename(first.groupId, "新甲组").getOrThrow()
        repository.setHidden(first.groupId, true).getOrThrow()

        val groups = repository.groups.value.associateBy { it.groupId }
        assertEquals("新甲组", groups.getValue(first.groupId).name)
        assertTrue(groups.getValue(first.groupId).isHidden)
        assertEquals("乙组", groups.getValue(second.groupId).name)
        assertFalse(groups.getValue(second.groupId).isHidden)
    }

    @Test
    fun bookOrganizationUpdatePreservesContentAndWorksIndependently() = runBlocking {
        val book = Book.create(
            title = "一本书",
            shelfType = ShelfType.READING,
            sourceType = SourceType.CREATED,
            format = BookFormat.TXT,
            now = 10,
        )
        val books = InMemoryBookRepository(listOf(book))
        val group = InMemoryShelfGroupRepository()
            .create(ShelfType.READING, "分组").getOrThrow()

        books.updateBookOrganization(book.bookId, group.groupId, true).getOrThrow()
        val updated = books.getBook(book.bookId)!!
        assertEquals(group.groupId, updated.groupId)
        assertTrue(updated.isHidden)
        assertEquals(book.title, updated.title)
        assertEquals(book.contentLocation, updated.contentLocation)

        books.updateBookOrganization(book.bookId, null, false).getOrThrow()
        val restored = books.getBook(book.bookId)!!
        assertNull(restored.groupId)
        assertFalse(restored.isHidden)
    }

    @Test
    fun touchLastOpenedUpdatesOnlyActivity() = runBlocking {
        val book = Book.create(
            title = "一本书",
            shelfType = ShelfType.WRITING,
            sourceType = SourceType.CREATED,
            format = BookFormat.DRAFT,
            now = 10,
        )
        val books = InMemoryBookRepository(listOf(book))
        books.touchLastOpenedAt(book.bookId, 99).getOrThrow()
        val updated = books.getBook(book.bookId)!!
        assertEquals(99L, updated.lastOpenedAt)
        assertEquals(book.updatedAt, updated.updatedAt)
        assertEquals(book.readingPosition, updated.readingPosition)
        assertEquals(book.groupId, updated.groupId)
        assertEquals(book.isHidden, updated.isHidden)
    }
}
