package com.xtt.toolbox.domain.reading

import com.xtt.toolbox.data.model.Book

/**
 * 书架历史活动排序。
 *
 * 分组没有独立的“最后阅读”字段，分组活动时间取组内所有作品（包括隐藏作品）
 * 的 [Book.lastOpenedAt] 最大值；这直接复用既有阅读历史，不修改组内其他作品的
 * 时间，也不引入与阅读历史不一致的第二套排序数据。
 */
object ShelfActivityOrdering {
    fun bookActivity(book: Book): Long = book.lastOpenedAt ?: Long.MIN_VALUE

    fun groupActivity(groupId: String, books: List<Book>): Long =
        books.asSequence()
            .filter { it.groupId == groupId }
            .mapNotNull { it.lastOpenedAt }
            .maxOrNull()
            ?: Long.MIN_VALUE

    /** 分组内部以及未分组作品使用同一套最近打开优先规则。 */
    fun orderedBooks(books: List<Book>): List<Book> = ReadingShelfOrdering.ordered(books)
}
