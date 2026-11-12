package com.xtt.toolbox.domain.reading

import com.xtt.toolbox.domain.chapter.ChapterCandidate

/**
 * 阅读器目录的纯排序/定位逻辑。
 *
 * - 只改变显示顺序，不修改章节原始列表、chapterId 或阅读位置；
 * - 当前章节优先使用 chapterId 稳定标识；TXT 临时章节才退回 startOffset key；
 * - 当前阅读位置在第一标题之前时，回退到第一章，用于前言/目录区域。
 */
object ReaderDirectoryOrder {
    fun ordered(
        chapters: List<ChapterCandidate>,
        reversed: Boolean,
    ): List<ChapterCandidate> = if (reversed) chapters.asReversed() else chapters

    fun currentChapter(
        chapters: List<ChapterCandidate>,
        currentOffset: Int,
    ): ChapterCandidate? =
        ReaderChapterTitle.chapterAt(chapters, currentOffset) ?: chapters.firstOrNull()

    fun currentKey(
        chapters: List<ChapterCandidate>,
        currentOffset: Int,
        persistedChapterId: String? = null,
    ): String? {
        val current = currentChapter(chapters, currentOffset)
        if (current != null) return ReaderChapterTitle.keyOf(current)
        return persistedChapterId?.takeIf { id -> chapters.any { it.chapterId == id } }
    }

    fun currentIndex(
        chapters: List<ChapterCandidate>,
        currentOffset: Int,
        reversed: Boolean,
        persistedChapterId: String? = null,
    ): Int {
        val key = currentKey(chapters, currentOffset, persistedChapterId) ?: return -1
        return ordered(chapters, reversed).indexOfFirst {
            ReaderChapterTitle.keyOf(it) == key
        }
    }

    /** 将条目中心放到视口中心的 scrollOffset；首尾由 LazyList 自身边界夹紧。 */
    fun centerOffset(viewportHeight: Int, itemHeight: Int): Int {
        if (viewportHeight <= 0 || itemHeight <= 0) return 0
        return (viewportHeight - itemHeight) / 2
    }
}
