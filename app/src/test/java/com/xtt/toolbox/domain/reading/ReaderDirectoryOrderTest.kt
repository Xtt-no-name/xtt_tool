package com.xtt.toolbox.domain.reading

import com.xtt.toolbox.data.model.ChapterDetectionSource
import com.xtt.toolbox.domain.chapter.ChapterCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderDirectoryOrderTest {
    private val chapters = listOf(
        chapter("第一章", 0, 100, "chapter-1"),
        chapter("第二章", 100, 200, "chapter-2"),
        chapter("第三章", 200, 300, "chapter-3"),
    )

    @Test
    fun forwardAndReverseOnlyChangeDisplayOrder() {
        assertEquals(
            listOf("chapter-1", "chapter-2", "chapter-3"),
            ReaderDirectoryOrder.ordered(chapters, false).map { it.chapterId },
        )
        assertEquals(
            listOf("chapter-3", "chapter-2", "chapter-1"),
            ReaderDirectoryOrder.ordered(chapters, true).map { it.chapterId },
        )
        assertEquals(listOf("chapter-1", "chapter-2", "chapter-3"), chapters.map { it.chapterId })
    }

    @Test
    fun locatesCurrentChapterInBothDirections() {
        assertEquals(0, ReaderDirectoryOrder.currentIndex(chapters, 0, reversed = false))
        assertEquals(1, ReaderDirectoryOrder.currentIndex(chapters, 150, reversed = false))
        assertEquals(2, ReaderDirectoryOrder.currentIndex(chapters, 299, reversed = false))
        assertEquals(2, ReaderDirectoryOrder.currentIndex(chapters, 0, reversed = true))
        assertEquals(1, ReaderDirectoryOrder.currentIndex(chapters, 150, reversed = true))
        assertEquals(0, ReaderDirectoryOrder.currentIndex(chapters, 299, reversed = true))
    }

    @Test
    fun usesStableChapterIdInsteadOfTitle() {
        val duplicateTitles = listOf(
            chapter("同标题", 0, 100, "chapter-a"),
            chapter("同标题", 100, 200, "chapter-b"),
        )
        assertEquals(1, ReaderDirectoryOrder.currentIndex(duplicateTitles, 120, false))
        assertEquals(0, ReaderDirectoryOrder.currentIndex(duplicateTitles, 120, true))
        assertEquals("chapter-b", ReaderDirectoryOrder.currentKey(duplicateTitles, 120))
    }

    @Test
    fun beforeFirstChapterFallsBackToFirstItemAndEmptyListIsSafe() {
        val first = ReaderDirectoryOrder.currentChapter(chapters, -1)
        assertEquals("chapter-1", first?.chapterId)
        assertEquals(0, ReaderDirectoryOrder.currentIndex(chapters, -1, false))
        assertEquals(-1, ReaderDirectoryOrder.currentIndex(emptyList(), 0, false))
        assertNull(ReaderDirectoryOrder.currentKey(emptyList(), 0))
    }

    @Test
    fun centerOffsetUsesMeasuredItemHeightAndHandlesBounds() {
        assertEquals(400, ReaderDirectoryOrder.centerOffset(viewportHeight = 1000, itemHeight = 200))
        assertEquals(0, ReaderDirectoryOrder.centerOffset(1000, 0))
        assertEquals(0, ReaderDirectoryOrder.centerOffset(0, 200))
        assertEquals(-100, ReaderDirectoryOrder.centerOffset(1000, 1200))
    }

    private fun chapter(
        title: String,
        start: Int,
        end: Int,
        id: String,
    ): ChapterCandidate = ChapterCandidate(
        title = title,
        order = start / 100,
        startOffset = start,
        endOffset = end,
        headingOffset = start,
        detectionSource = ChapterDetectionSource.REGEX,
        confidence = 0.9f,
        chapterId = id,
    )
}
