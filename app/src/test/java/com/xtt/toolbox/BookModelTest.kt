package com.xtt.toolbox

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.data.model.ChapterDetectionSource
import com.xtt.toolbox.data.model.Comment
import com.xtt.toolbox.data.model.CommentType
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.domain.chapter.TextOffsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookModelTest {

    @Test
    fun importedSourceCanLiveOnWritingShelfWithoutChangingSource() {
        val book = Book.create(
            title = "导入后转入写作",
            shelfType = ShelfType.WRITING,
            sourceType = SourceType.IMPORTED,
            sourceUri = "content://example/book.txt",
            contentLocation = "/data/user/0/com.xtt.toolbox/files/books/book.txt",
        )

        assertEquals(ShelfType.WRITING, book.shelfType)
        assertEquals(SourceType.IMPORTED, book.sourceType)
        assertTrue(book.isEditable)
        assertNotNull(book.sourceUri)
        assertNotNull(book.contentLocation)
    }

    @Test
    fun textOffsetsUseUtf16IndicesAfterNewlineNormalization() {
        val normalized = TextOffsets.normalizeNewlines("第一行\r\n第二行\r第三行")
        assertEquals("第一行\n第二行\n第三行", normalized)
        assertEquals(4, normalized.indexOf("第二行"))

        val withEmoji = "a\uD83D\uDE00b"
        assertEquals(4, withEmoji.length)
        assertEquals(3, withEmoji.indexOf('b'))
    }

    @Test
    fun chapterOffsetsAndPassageAnchorsRemainConsistent() {
        val chapter = Chapter(
            chapterId = "chapter-1",
            bookId = "book-1",
            title = "第一章",
            order = 0,
            startOffset = 0,
            endOffset = 12,
            headingOffset = 0,
            detectionSource = ChapterDetectionSource.REGEX,
            confidence = 0.92f,
        )
        assertEquals(0, chapter.headingOffset)
        assertEquals(12, chapter.endOffset)

        val comment = Comment(
            commentId = "comment-1",
            bookId = "book-1",
            chapterId = "chapter-1",
            commentType = CommentType.PASSAGE,
            content = "这里写得很好",
            createdAt = 1L,
            updatedAt = 1L,
            anchorStart = 3,
            anchorEnd = 6,
            quotedText = "写得很",
            prefixText = "这里",
            suffixText = "好",
            contentRevision = "revision-1",
        )
        assertEquals(3, comment.anchorStart)
        assertEquals(6, comment.anchorEnd)
        assertEquals(CommentType.PASSAGE, comment.commentType)
    }
}
