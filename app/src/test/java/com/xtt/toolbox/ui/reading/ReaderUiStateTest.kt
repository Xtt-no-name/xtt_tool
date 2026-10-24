package com.xtt.toolbox.ui.reading

import com.xtt.toolbox.data.model.Bookmark
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ChapterDetectionSource
import com.xtt.toolbox.data.model.Comment
import com.xtt.toolbox.data.model.CommentType
import com.xtt.toolbox.domain.chapter.ChapterCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderUiStateTest {
    @Test
    fun controlsStartHiddenAndToggle() {
        val state = ReaderUiState()

        assertFalse(state.controlsVisible)
        assertTrue(state.toggleReaderControls().controlsVisible)
        assertFalse(state.toggleReaderControls().toggleReaderControls().controlsVisible)
    }

    @Test
    fun panelsAreMutuallyExclusiveAndKeepControlsVisible() {
        var state = ReaderUiState()
        state = state.openReaderPanel(ReaderPanel.DIRECTORY)
        assertEquals(ReaderPanel.DIRECTORY, state.activePanel)
        assertTrue(state.controlsVisible)

        state = state.openReaderPanel(ReaderPanel.NOTES)
        assertEquals(ReaderPanel.NOTES, state.activePanel)
        assertTrue(state.controlsVisible)

        state = state.openReaderPanel(ReaderPanel.SETTINGS)
        assertEquals(ReaderPanel.SETTINGS, state.activePanel)
    }

    @Test
    fun tappingSamePanelClosesItWithoutHidingControls() {
        val state = ReaderUiState(activePanel = ReaderPanel.DIRECTORY, controlsVisible = true)
        val closed = state.openReaderPanel(ReaderPanel.DIRECTORY)

        assertNull(closed.activePanel)
        assertTrue(closed.controlsVisible)
    }

    @Test
    fun bodyTapClosesPanelBeforeTogglingControls() {
        val state = ReaderUiState(activePanel = ReaderPanel.NOTES, controlsVisible = true)
        val closed = state.toggleReaderControls()

        assertNull(closed.activePanel)
        assertTrue(closed.controlsVisible)
        assertFalse(closed.toggleReaderControls().controlsVisible)
    }

    @Test
    fun currentChapterTitleUsesCurrentOffset() {
        val chapters = listOf(
            chapter("第一章", 0),
            chapter("第二章", 20),
        )
        val state = ReaderUiState(
            chapters = chapters,
            currentOffset = 25,
            chapterOffset = 25,
        )

        assertEquals("第二章", state.currentChapterTitle)
        assertNull(state.copy(currentOffset = 0, chapterOffset = 0, chapters = emptyList()).currentChapterTitle)
    }

    @Test
    fun passageCountsExcludeBookmarksAndChapterComments() {
        val bookmark = Bookmark(
            bookmarkId = "bm-1",
            bookId = "book-1",
            position = 10,
            title = "书签",
            createdAt = 1,
        )
        val passage = comment(
            id = "c-1",
            type = CommentType.PASSAGE,
            anchorStart = 10,
        )
        val chapterComment = comment(
            id = "c-2",
            type = CommentType.CHAPTER,
            anchorStart = null,
        )
        val state = ReaderUiState(
            bookmarks = listOf(bookmark),
            comments = listOf(passage, chapterComment),
        )

        assertEquals(mapOf(10 to 1), state.passageCommentCounts)
        assertEquals(mapOf("offset:0" to 1), state.chapterCommentCounts)
    }

    @Test
    fun disablingUiSettingsKeepsAnnotations() {
        val bookmark = Bookmark(
            bookmarkId = "bm-1",
            bookId = "book-1",
            position = 10,
            title = "书签",
            createdAt = 1,
        )
        val comment = comment("c-1", CommentType.PASSAGE, 10)
        val state = ReaderUiState(bookmarks = listOf(bookmark), comments = listOf(comment))
        val hidden = state.copy(
            settings = state.settings.copy(
                showCommentBubbles = false,
                showChapterCommentEntry = false,
                enableLongPressMenu = false,
            ),
        )

        assertEquals(1, hidden.bookmarks.size)
        assertEquals(1, hidden.comments.size)
        assertFalse(hidden.settings.showCommentBubbles)
        assertFalse(hidden.settings.showChapterCommentEntry)
        assertFalse(hidden.settings.enableLongPressMenu)
    }

    @Test
    fun markdownDisablesAnnotationCapabilityWithoutDeletingData() {
        val bookmark = Bookmark(
            bookmarkId = "bm-1",
            bookId = "book-1",
            position = 10,
            title = "书签",
            createdAt = 1,
        )
        val comment = comment("c-1", CommentType.PASSAGE, 10)
        val markdown = ReaderUiState(
            bookFormat = BookFormat.MARKDOWN,
            bookmarks = listOf(bookmark),
            comments = listOf(comment),
        )
        val draft = ReaderUiState(
            bookFormat = BookFormat.DRAFT,
            bookmarks = listOf(bookmark),
            comments = listOf(comment),
        )
        val txt = ReaderUiState(bookFormat = BookFormat.TXT)
        val epub = ReaderUiState(bookFormat = BookFormat.EPUB)

        assertFalse(markdown.supportsAnnotations)
        assertFalse(draft.supportsAnnotations)
        assertTrue(txt.supportsAnnotations)
        assertTrue(epub.supportsAnnotations)
        assertEquals(1, markdown.bookmarks.size)
        assertEquals(1, markdown.comments.size)
        assertEquals(1, draft.bookmarks.size)
        assertEquals(1, draft.comments.size)
    }

    @Test
    fun blankCommentsAreRejectedByModel() {
        assertThrows(IllegalArgumentException::class.java) {
            comment("c-blank", CommentType.CHAPTER, null, content = "   ")
        }
    }

    @Test
    fun chaptersAndParagraphsKeepCountsSeparate() {
        val first = Comment(
            commentId = "c-first",
            bookId = "book-1",
            chapterId = "offset:0",
            chapterTitle = "第一章",
            chapterStartOffset = 0,
            commentType = CommentType.PASSAGE,
            content = "第一章段评",
            createdAt = 1,
            updatedAt = 1,
            anchorStart = 10,
            anchorEnd = 20,
        )
        val second = Comment(
            commentId = "c-second",
            bookId = "book-1",
            chapterId = "offset:20",
            chapterTitle = "第二章",
            chapterStartOffset = 20,
            commentType = CommentType.PASSAGE,
            content = "第二章段评",
            createdAt = 2,
            updatedAt = 2,
            anchorStart = 30,
            anchorEnd = 40,
        )
        val state = ReaderUiState(comments = listOf(first, second))

        assertEquals(mapOf(10 to 1, 30 to 1), state.passageCommentCounts)
    }

    private fun comment(
        id: String,
        type: CommentType,
        anchorStart: Int?,
        content: String = "评论",
    ): Comment = Comment(
        commentId = id,
        bookId = "book-1",
        chapterId = "offset:0",
        chapterTitle = "第一章",
        chapterStartOffset = 0,
        commentType = type,
        content = content,
        createdAt = 1,
        updatedAt = 1,
        anchorStart = anchorStart,
        anchorEnd = anchorStart?.plus(10),
        quotedText = if (type == CommentType.PASSAGE) "原文" else null,
    )

    private fun chapter(title: String, start: Int): ChapterCandidate = ChapterCandidate(
        title = title,
        order = 0,
        startOffset = start,
        endOffset = start + 10,
        headingOffset = start,
        detectionSource = ChapterDetectionSource.REGEX,
        confidence = 1f,
    )
}
