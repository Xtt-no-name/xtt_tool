package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookDocument

/**
 * 写作编辑的完整、可验证快照：正文与统一文档结构必须同时存在。
 * [text] 使用与 Reader 相同的规范化 UTF-16 文本约定；章节/块偏移都指向该文本。
 */
data class WritingDocument(
    val book: Book,
    val text: String,
    val document: BookDocument,
) {
    init {
        require(book.bookId.isNotBlank()) { "bookId 不能为空" }
        require(book.shelfType == com.xtt.toolbox.data.model.ShelfType.WRITING) {
            "只有写作书架作品可以编辑"
        }
        require(document.chapters.all { it.bookId == book.bookId }) {
            "章节必须属于当前作品"
        }
        require(document.blocks.all {
            it.startOffset in 0..text.length && it.endOffset in it.startOffset..text.length
        }) { "内容块偏移超出正文" }
        require(document.chapters.all {
            it.startOffset in 0..text.length && it.endOffset in it.startOffset..text.length
        }) { "章节偏移超出正文" }
    }

    fun chapterBlocks(chapterId: String): List<BookBlock> {
        val chapter = document.chapters.firstOrNull { it.chapterId == chapterId }
            ?: return emptyList()
        return document.blocks.filter { block ->
            block.startOffset >= chapter.startOffset && block.startOffset < chapter.endOffset
        }
    }
}
