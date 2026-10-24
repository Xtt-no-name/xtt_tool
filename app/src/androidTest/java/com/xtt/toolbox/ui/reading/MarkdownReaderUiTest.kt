package com.xtt.toolbox.ui.reading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ChapterDetectionSource
import com.xtt.toolbox.domain.chapter.ChapterCandidate
import com.xtt.toolbox.domain.content.BookTable
import com.xtt.toolbox.domain.content.BookTableCell
import com.xtt.toolbox.domain.content.BookTextAlign
import com.xtt.toolbox.domain.reading.ReaderBlock
import com.xtt.toolbox.domain.reading.ReaderBlockKind
import com.xtt.toolbox.domain.reading.ReaderSettings
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@SdkSuppress(maxSdkVersion = 36)
@RunWith(AndroidJUnit4::class)
class MarkdownReaderUiTest {
    @Test
    fun markdownHidesAnnotationMenusAndNotesButton() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TestReader(BookFormat.MARKDOWN)
            }
        }
        onNodeWithText("正文", substring = true).longPress()
        onNodeWithText("添加书签").assertDoesNotExist()
        onNodeWithText("添加评论").assertDoesNotExist()
        onNodeWithText("笔记").assertDoesNotExist()
    }

    @Test
    fun txtKeepsAnnotationMenusAndNotesButton() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TestReader(BookFormat.TXT)
            }
        }
        onNodeWithText("正文", substring = true).longPress()
        onNodeWithText("添加书签").assertExists()
        onNodeWithText("添加评论").assertExists()
        onNodeWithText("笔记").assertExists()
    }

    @Test
    fun directoryChapterCommentIsHiddenOnlyForMarkdown() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TestReader(BookFormat.MARKDOWN, ReaderPanel.DIRECTORY)
            }
        }
        onNodeWithTag("reader_chapter_0").longPress()
        onNodeWithText("添加章评").assertDoesNotExist()
    }

    @Test
    fun txtDirectoryStillOffersChapterComment() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TestReader(BookFormat.TXT, ReaderPanel.DIRECTORY)
            }
        }
        onNodeWithTag("reader_chapter_0").longPress()
        onNodeWithText("添加章评").assertExists()
    }

    @Test
    fun wideTableHasHorizontalScrollActionAndWrapsInDarkTheme() = runComposeUiTest {
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.width(220.dp)) {
                    MarkdownTableBlock(
                        block = tableBlock(),
                        fontSizeSp = 16f,
                        lineHeightSp = 24f,
                    )
                }
            }
        }
        onNodeWithTag("markdown_table_scroll").assertExists()
        onNodeWithTag("markdown_table_scroll").assert(
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy),
        )
        onNodeWithText("很长的表格列内容").assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun TestReader(
        format: BookFormat,
        panel: ReaderPanel? = null,
    ) {
        ReaderScreen(
            state = readerState(format).copy(activePanel = panel),
            onRetry = {},
            onBack = {},
            onToggleControls = {},
            onOpenPanel = {},
            onClosePanel = {},
            onSettingsChange = {},
            onAddBookmark = {},
            onAddPassageComment = { _, _ -> },
            onAddChapterComment = { _, _ -> },
            onVisiblePositionChanged = { _, _ -> },
        )
    }

    private fun SemanticsNodeInteraction.longPress() {
        performTouchInput {
            down(center)
            advanceEventTime(800)
            up()
        }
    }

    private fun readerState(format: BookFormat): ReaderUiState {
        val text = "正文\n"
        return ReaderUiState(
            isVisible = true,
            bookId = "book-1",
            title = "测试",
            text = text,
            blocks = listOf(
                ReaderBlock(
                    index = 0,
                    startOffset = 0,
                    endOffset = text.length,
                    text = text,
                    kind = ReaderBlockKind.TEXT,
                    blockId = "body-0",
                ),
            ),
            chapters = listOf(
                ChapterCandidate(
                    title = "第一章",
                    order = 0,
                    startOffset = 0,
                    endOffset = text.length,
                    headingOffset = 0,
                    detectionSource = ChapterDetectionSource.MARKDOWN_FALLBACK,
                    confidence = 1f,
                ),
            ),
            controlsVisible = true,
            settings = ReaderSettings.Default,
            bookFormat = format,
        )
    }

    private fun tableBlock(): ReaderBlock = ReaderBlock(
        index = 0,
        startOffset = 0,
        endOffset = 0,
        text = "",
        kind = ReaderBlockKind.TABLE,
        table = BookTable(
            header = listOf("列一", "列二", "列三", "列四").map(::BookTableCell),
            rows = listOf(
                listOf(
                    BookTableCell("很长的表格列内容"),
                    BookTableCell("代码", listOf()),
                    BookTableCell(""),
                    BookTableCell("结尾"),
                ),
            ),
            alignments = listOf(
                BookTextAlign.START,
                BookTextAlign.CENTER,
                BookTextAlign.END,
                BookTextAlign.START,
            ),
        ),
        blockId = "table-0",
    )
}
