package com.xtt.toolbox.domain.reading

import com.xtt.toolbox.data.model.Book

/**
 * 阅读书架顺序：最近打开优先；从未打开的书籍保持输入顺序。
 * Java/Kotlin 的稳定排序保证 lastOpenedAt 相同的条目相对顺序不变。
 */
object ReadingShelfOrdering {
    fun ordered(books: List<Book>): List<Book> =
        books.sortedByDescending { it.lastOpenedAt ?: Long.MIN_VALUE }
}
