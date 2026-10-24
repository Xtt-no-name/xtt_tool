package com.xtt.toolbox.ui.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xtt.toolbox.R
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.Bookmark
import com.xtt.toolbox.data.model.Comment
import com.xtt.toolbox.data.model.CommentType
import com.xtt.toolbox.domain.chapter.ChapterCandidate
import com.xtt.toolbox.domain.content.BookTextAlign
import com.xtt.toolbox.domain.content.BookTableCell
import com.xtt.toolbox.domain.content.InlineSpan
import com.xtt.toolbox.domain.reading.ReaderBlock
import com.xtt.toolbox.domain.reading.ReaderBlockKind
import com.xtt.toolbox.domain.reading.ReaderChapterTitle
import com.xtt.toolbox.domain.reading.ReaderSettings
import com.xtt.toolbox.ui.components.AppActionMenu
import com.xtt.toolbox.ui.components.AppActionMenuItem
import com.xtt.toolbox.ui.components.AppIconButton
import com.xtt.toolbox.ui.components.BackArrowIcon
import com.xtt.toolbox.ui.components.BookIcon
import com.xtt.toolbox.ui.components.BookmarkIcon
import com.xtt.toolbox.ui.components.FolderIcon
import com.xtt.toolbox.ui.components.SettingsIcon
import com.xtt.toolbox.ui.components.TextLinesIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
    onOpenPanel: (ReaderPanel) -> Unit,
    onClosePanel: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onAddBookmark: (ReaderBlock) -> Unit,
    onAddPassageComment: (ReaderBlock, String) -> Unit,
    onAddChapterComment: (ChapterCandidate, String) -> Unit,
    onVisiblePositionChanged: (offset: Int, atEnd: Boolean) -> Unit,
    onOpenLink: (String) -> Unit = {},
    onPickMarkdownImageDirectory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.isLoading -> ReaderFallback(
                state = state,
                title = state.title.ifBlank { stringResource(R.string.reader_title) },
                message = stringResource(R.string.reader_loading),
                onBack = onBack,
            )

            state.errorMessage != null -> ReaderFallback(
                state = state,
                title = state.title.ifBlank { stringResource(R.string.reader_title) },
                heading = stringResource(R.string.reader_error_title),
                message = state.errorMessage,
                onBack = onBack,
                onRetry = onRetry,
            )

            state.text.isEmpty() -> ReaderFallback(
                state = state,
                title = state.title.ifBlank { stringResource(R.string.reader_title) },
                heading = stringResource(R.string.reader_empty_title),
                message = stringResource(R.string.reader_empty_desc),
                onBack = onBack,
            )

            else -> key(state.bookId) {
                ReaderContent(
                    state = state,
                    onBack = onBack,
                    onToggleControls = onToggleControls,
                    onOpenPanel = onOpenPanel,
                    onClosePanel = onClosePanel,
                    onSettingsChange = onSettingsChange,
                    onAddBookmark = onAddBookmark,
                    onAddPassageComment = onAddPassageComment,
                    onAddChapterComment = onAddChapterComment,
                    onVisiblePositionChanged = onVisiblePositionChanged,
                    onOpenLink = onOpenLink,
                    onPickMarkdownImageDirectory = onPickMarkdownImageDirectory,
                )
            }
        }
    }
}

@Composable
private fun ReaderFallback(
    state: ReaderUiState,
    title: String,
    heading: String = title,
    message: String,
    onBack: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        ReaderTopBar(
            title = title,
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = if (state.isLoading) stringResource(R.string.reader_loading) else heading,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.reader_retry))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderContent(
    state: ReaderUiState,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
    onOpenPanel: (ReaderPanel) -> Unit,
    onClosePanel: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onAddBookmark: (ReaderBlock) -> Unit,
    onAddPassageComment: (ReaderBlock, String) -> Unit,
    onAddChapterComment: (ChapterCandidate, String) -> Unit,
    onVisiblePositionChanged: (offset: Int, atEnd: Boolean) -> Unit,
    onOpenLink: (String) -> Unit = {},
    onPickMarkdownImageDirectory: () -> Unit = {},
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.initialBlockIndex.coerceIn(
            0,
            state.blocks.lastIndex.coerceAtLeast(0),
        ),
    )
    var restored by rememberSaveable(state.bookId, state.initialBlockIndex) {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    val bodyInteraction = remember { MutableInteractionSource() }
    var selectedBlock by remember { mutableStateOf<ReaderBlock?>(null) }
    var commentTarget by remember { mutableStateOf<CommentTarget?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    var showDiscardComment by remember { mutableStateOf(false) }

    fun openPassageComment(block: ReaderBlock) {
        commentTarget = CommentTarget(
            type = CommentType.PASSAGE,
            chapter = ReaderChapterTitle.chapterAt(state.chapters, block.startOffset),
            block = block,
            quote = block.text.trim(),
        )
        commentDraft = ""
    }

    fun openChapterComment(chapter: ChapterCandidate) {
        commentTarget = CommentTarget(
            type = CommentType.CHAPTER,
            chapter = chapter,
            block = null,
            quote = chapter.title,
        )
        commentDraft = ""
    }

    fun requestCloseCommentEditor() {
        if (commentDraft.isBlank()) {
            commentTarget = null
        } else {
            showDiscardComment = true
        }
    }

    fun submitComment() {
        val target = commentTarget ?: return
        val content = commentDraft.trim()
        if (content.isEmpty()) return
        when (target.type) {
            CommentType.PASSAGE -> target.block?.let { onAddPassageComment(it, content) }
            CommentType.CHAPTER -> target.chapter?.let { onAddChapterComment(it, content) }
        }
        commentTarget = null
        commentDraft = ""
    }

    fun jumpToOffset(offset: Int) {
        val index = state.blocks.indexOfFirst { block ->
            offset >= block.startOffset && offset < block.endOffset
        }.takeIf { it >= 0 } ?: state.blocks.lastIndex.coerceAtLeast(0)
        scope.launch { listState.scrollToItem(index, 0) }
        onClosePanel()
    }

    fun jumpToBlockId(blockId: String) {
        val index = state.blocks.indexOfFirst { it.blockId == blockId }
        if (index >= 0) {
            scope.launch { listState.animateScrollToItem(index, 0) }
        }
    }

    LaunchedEffect(state.bookId, state.initialBlockIndex, state.blocks.size) {
        if (!restored && state.blocks.isNotEmpty()) {
            listState.scrollToItem(
                state.initialBlockIndex.coerceIn(0, state.blocks.lastIndex),
                0,
            )
            restored = true
        }
    }

    LaunchedEffect(state.bookId, restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val rawIndex = first?.index ?: 0
            // 首项被向上滚出屏幕时，保存下一项的行首，避免恢复时把已滚过的行重新露出。
            val anchorIndex = if (
                first != null && first.offset < 0 && rawIndex < state.blocks.lastIndex
            ) {
                rawIndex + 1
            } else {
                rawIndex
            }
            // 控制栏切换会改变底部 Footer/控制栏占用的高度，不能只用
            // canScrollForward 判定末尾，否则在章末显示控制栏时进度会倒退。
            // 只有最后一个块完整露出时才视为末尾；部分露出的块不提前算 100%。
            val lastFullyVisible = last != null &&
                last.index == state.blocks.lastIndex &&
                last.offset + last.size <= listState.layoutInfo.viewportEndOffset
            anchorIndex to (!listState.canScrollForward || lastFullyVisible)
        }.distinctUntilChanged().collect { (index, atEnd) ->
            val offset = state.blocks.getOrNull(index)?.startOffset ?: 0
            onVisiblePositionChanged(offset, atEnd)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ReaderIdleChapterHeader(state)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("reader_body")
                    .clickable(
                        interactionSource = bodyInteraction,
                        indication = null,
                        onClick = onToggleControls,
                    ),
                contentPadding = PaddingValues(
                    start = state.settings.horizontalPaddingDp.dp,
                    end = state.settings.horizontalPaddingDp.dp,
                    // 顶部章节标题已在 Column 中实际占位，列表内只需少量间距。
                    top = 8.dp,
                    // 底部控制栏是覆盖层；隐藏控制栏时底部由 Footer 实际占位，
                    // 因此只需保留少量末尾留白，不能让正文从时间和进度后方穿过。
                    bottom = if (state.controlsVisible) 116.dp else 8.dp,
                ),
            ) {
            itemsIndexed(
                items = state.blocks,
                key = { _, block -> block.index },
            ) { _, block ->
                if (block.kind == ReaderBlockKind.TABLE) {
                    MarkdownTableBlock(
                        block = block,
                        fontSizeSp = state.settings.fontSizeSp,
                        lineHeightSp = state.settings.lineHeightSp,
                        onOpenLink = onOpenLink,
                    )
                } else if (block.kind == ReaderBlockKind.IMAGE) {
                    EpubImageBlock(
                        block = block,
                        onClick = onToggleControls,
                    )
                } else if (block.kind == ReaderBlockKind.RULE) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                } else {
                val selected = selectedBlock?.startOffset == block.startOffset
                val annotationsEnabled = state.supportsAnnotations
                val commentCount = if (annotationsEnabled) {
                    state.passageCommentCounts[block.startOffset] ?: 0
                } else {
                    0
                }
                val endingChapter = state.chapters.firstOrNull {
                    ReaderChapterTitle.endsAtBlock(it, block)
                }
                val blockClickModifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    // 关闭时长按仍要消费，但不能出现 Press ripple/灰色高亮。
                    indication = if (state.settings.enableLongPressMenu && annotationsEnabled) {
                        LocalIndication.current
                    } else {
                        null
                    },
                    onClick = onToggleControls,
                    onLongClick = {
                        if (state.settings.enableLongPressMenu && annotationsEnabled) {
                            selectedBlock = block
                        }
                    },
                )
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(blockClickModifier),
                    ) {
                        val linkColor = MaterialTheme.colorScheme.primary
                        val codeBackground = MaterialTheme.colorScheme.surfaceVariant
                        val annotated = remember(
                            block.text,
                            block.spans,
                            block.style?.listItem,
                            block.style?.listDepth,
                            block.style?.listNumber,
                            block.style?.taskChecked,
                            linkColor,
                            codeBackground,
                        ) {
                            block.annotatedReaderText(linkColor, codeBackground)
                        }
                        val blockStyle = block.style
                        val isCode = block.kind == ReaderBlockKind.CODE
                        val isQuote = block.kind == ReaderBlockKind.QUOTE
                        val isDefinition = block.kind == ReaderBlockKind.DEFINITION
                        val isFootnote = block.kind == ReaderBlockKind.FOOTNOTE
                        val isDefinitionTerm = block.kind == ReaderBlockKind.DEFINITION_TERM
                        val textColor = when {
                            isQuote || isDefinition || isFootnote ->
                                MaterialTheme.colorScheme.onSurfaceVariant
                            blockStyle?.taskChecked == true ->
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        var textLayout by remember(block.blockId, annotated) {
                            mutableStateOf<TextLayoutResult?>(null)
                        }
                        val listPrefixLength = when {
                            blockStyle?.taskChecked != null -> 3
                            blockStyle?.listItem != true -> 0
                            (blockStyle.listNumber) > 0 -> blockStyle.listNumber.toString().length + 2
                            else -> 3
                        }
                        val footnoteTapModifier = if (
                            block.spans.any { (it.footnote || it.link) && it.targetBlockId != null } ||
                            block.spans.any { it.link && it.linkTarget != null }
                        ) {
                            Modifier.pointerInput(annotated, block.spans) {
                                detectTapGestures { position ->
                                    val offset = textLayout?.getOffsetForPosition(position)
                                        ?: return@detectTapGestures
                                    val localOffset = offset - listPrefixLength
                                    val span = block.spans.firstOrNull { candidate ->
                                        (candidate.footnote || candidate.link) &&
                                            localOffset >= candidate.start &&
                                            localOffset < candidate.end
                                    }
                                    when {
                                        span?.footnote == true &&
                                            span.targetBlockId != null ->
                                            jumpToBlockId(span.targetBlockId)

                                        span?.link == true &&
                                            span.linkTarget != null ->
                                            onOpenLink(span.linkTarget)
                                    }
                                }
                            }
                        } else {
                            Modifier
                        }
                        Text(
                            text = annotated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = ((blockStyle?.listDepth ?: 0) * 12 +
                                        if (isDefinition) 14 else 0).dp,
                                )
                                .padding(
                                    top = (
                                        (blockStyle?.marginTopEm ?: 0f) * state.settings.fontSizeSp
                                        ).dp.coerceIn(0.dp, 24.dp) +
                                        (state.settings.paragraphSpacingDp / 2f).dp,
                                    bottom = (
                                        (blockStyle?.marginBottomEm ?: 0f) * state.settings.fontSizeSp
                                        ).dp.coerceIn(0.dp, 24.dp) +
                                        (state.settings.paragraphSpacingDp / 2f).dp,
                                )
                                .then(footnoteTapModifier)
                                .then(
                                    when {
                                        isCode -> Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(codeBackground.copy(alpha = 0.55f))
                                            .padding(horizontal = 10.dp, vertical = 8.dp)

                                        isQuote -> Modifier
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                RoundedCornerShape(4.dp),
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)

                                        else -> Modifier
                                    },
                                ),
                            onTextLayout = { textLayout = it },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = when {
                                    block.kind == ReaderBlockKind.HEADING -> {
                                        (state.settings.fontSizeSp * headingScale(block.level)).sp
                                    }
                                    isFootnote -> (state.settings.fontSizeSp * 0.86f).sp
                                    isDefinition -> (state.settings.fontSizeSp * 0.96f).sp
                                    else -> state.settings.fontSizeSp.sp
                                },
                                fontWeight = when {
                                    block.kind == ReaderBlockKind.HEADING -> FontWeight.Bold
                                    isDefinitionTerm -> FontWeight.SemiBold
                                    else -> null
                                },
                                fontFamily = if (isCode) {
                                    androidx.compose.ui.text.font.FontFamily.Monospace
                                } else {
                                    null
                                },
                                fontStyle = if (isQuote) FontStyle.Italic else null,
                                lineHeight = state.settings.lineHeightSp.sp,
                                textAlign = blockStyle?.textAlign?.toComposeTextAlign()
                                    ?: TextAlign.Start,
                                textIndent = blockStyle
                                    ?.takeIf { it.textIndentEm > 0f }
                                    ?.let {
                                        TextIndent(
                                            firstLine = (it.textIndentEm * state.settings.fontSizeSp).sp,
                                        )
                                    },
                                color = textColor,
                            ),
                            overflow = TextOverflow.Clip,
                        )
                        if (isFootnote && block.footnoteRefIds.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                block.footnoteRefIds.forEachIndexed { index, target ->
                                    TextButton(
                                        onClick = { jumpToBlockId(target) },
                                        modifier = Modifier.testTag("reader_footnote_back_$index"),
                                    ) {
                                        Text(
                                            stringResource(
                                                R.string.reader_footnote_back,
                                                index + 1,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        if (state.settings.showCommentBubbles && commentCount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                CommentCountBubble(
                                    count = commentCount,
                                    onClick = { onOpenPanel(ReaderPanel.NOTES) },
                                )
                            }
                        }
                        if (annotationsEnabled &&
                            state.settings.showChapterCommentEntry && endingChapter != null
                        ) {
                            val chapterCount = state.chapterCommentCounts[
                                ReaderChapterTitle.keyOf(endingChapter)
                            ] ?: 0
                            ChapterCommentEntry(
                                count = chapterCount,
                                onClick = { openChapterComment(endingChapter) },
                            )
                        }
                    }
                    AppActionMenu(
                        expanded = selected && annotationsEnabled,
                        onDismissRequest = { selectedBlock = null },
                    ) {
                        AppActionMenuItem(
                            text = stringResource(R.string.reader_add_bookmark),
                            icon = { BookmarkIcon(modifier = Modifier.size(18.dp)) },
                            onClick = {
                                selectedBlock = null
                                onAddBookmark(block)
                            },
                        )
                        AppActionMenuItem(
                            text = stringResource(R.string.reader_add_comment),
                            icon = { TextLinesIcon(modifier = Modifier.size(18.dp)) },
                            onClick = {
                                selectedBlock = null
                                openPassageComment(block)
                            },
                        )
                    }
                }
                }
            }
        }
        if (!state.controlsVisible) {
            ReaderIdleFooter(state)
        }
    }

    AnimatedVisibility(
            visible = state.controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        ) {
            ReaderTopBar(
                title = state.currentChapterTitle ?: state.title,
                onBack = onBack,
                onPickMarkdownImageDirectory = if (state.bookFormat == BookFormat.MARKDOWN) {
                    onPickMarkdownImageDirectory
                } else {
                    null
                },
                imageAttachmentBusy = state.imageAttachmentBusy,
            )
        }

        AnimatedVisibility(
            visible = state.controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        ) {
            ReaderBottomBar(
                state = state,
                annotationsEnabled = state.supportsAnnotations,
                onOpenPanel = onOpenPanel,
            )
        }

        AnimatedVisibility(
            visible = state.activePanel != null,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PanelScrim(onClose = onClosePanel)
        }

        AnimatedVisibility(
            visible = state.activePanel == ReaderPanel.DIRECTORY,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(0.5f)
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        ) {
            DirectoryPanel(
                state = state,
                annotationsEnabled = state.supportsAnnotations,
                onChapterClick = { chapter ->
                    val index = state.blocks.indexOfFirst { block ->
                        chapter.startOffset >= block.startOffset &&
                            chapter.startOffset < block.endOffset
                    }.takeIf { it >= 0 } ?: state.blocks.lastIndex.coerceAtLeast(0)
                    scope.launch { listState.scrollToItem(index, 0) }
                    onClosePanel()
                },
                onAddChapterComment = { chapter ->
                    onClosePanel()
                    openChapterComment(chapter)
                },
                onClose = onClosePanel,
            )
        }

        AnimatedVisibility(
            visible = state.activePanel == ReaderPanel.NOTES && state.supportsAnnotations,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            NotesPanel(
                state = state,
                onJumpToOffset = { jumpToOffset(it) },
                onClose = onClosePanel,
            )
        }

        AnimatedVisibility(
            visible = state.activePanel == ReaderPanel.SETTINGS,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.66f),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            ReaderSettingsPanel(
                settings = state.settings,
                annotationsEnabled = state.supportsAnnotations,
                onSettingsChange = onSettingsChange,
                onClose = onClosePanel,
            )
        }

        commentTarget?.let { target ->
            CommentEditorDialog(
                target = target,
                draft = commentDraft,
                onDraftChange = { commentDraft = it },
                onSave = { submitComment() },
                onRequestClose = { requestCloseCommentEditor() },
            )
        }

        if (showDiscardComment) {
            AlertDialog(
                onDismissRequest = { showDiscardComment = false },
                title = { Text(stringResource(R.string.reader_discard_comment_title)) },
                text = { Text(stringResource(R.string.reader_discard_comment_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardComment = false
                            commentTarget = null
                            commentDraft = ""
                        },
                    ) {
                        Text(stringResource(R.string.reader_discard_comment_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardComment = false }) {
                        Text(stringResource(R.string.reader_continue_editing))
                    }
                },
            )
        }
    }
}

private fun headingScale(level: Int): Float = when (level.coerceAtLeast(1)) {
    1 -> 1.45f
    2 -> 1.3f
    3 -> 1.18f
    4 -> 1.08f
    else -> 1f
}

private fun ReaderBlock.annotatedReaderText(
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString {
    val prefix = when {
        style?.taskChecked != null ->
            if (style.taskChecked) "☑  " else "☐  "
        style?.listItem != true -> ""
        (style.listNumber) > 0 -> "${style.listNumber}.  "
        else -> "•  "
    }
    return buildAnnotatedString {
        append(prefix)
        append(text)
        val shift = prefix.length
        spans.forEach { span ->
            addStyle(
                span.toSpanStyle(linkColor, codeBackground),
                span.start + shift,
                span.end + shift,
            )
        }
    }
}

private fun InlineSpan.toSpanStyle(linkColor: Color, codeBackground: Color): SpanStyle {
    val decorations = buildList {
        if (underline) add(TextDecoration.Underline)
        if (strike) add(TextDecoration.LineThrough)
        if (link && !underline) add(TextDecoration.Underline)
    }
    return SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        color = if (link || footnote) linkColor else Color.Unspecified,
        background = if (code) codeBackground.copy(alpha = 0.45f) else Color.Unspecified,
        fontFamily = if (code) androidx.compose.ui.text.font.FontFamily.Monospace else null,
        baselineShift = if (footnote) BaselineShift.Superscript else null,
    )
}

private fun BookTextAlign.toComposeTextAlign(): TextAlign = when (this) {
    BookTextAlign.START -> TextAlign.Start
    BookTextAlign.CENTER -> TextAlign.Center
    BookTextAlign.END -> TextAlign.End
    BookTextAlign.JUSTIFY -> TextAlign.Justify
}

@Composable
private fun EpubImageBlock(    block: ReaderBlock,
    onClick: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, block.imagePath) {
        value = withContext(Dispatchers.IO) {
            block.imagePath?.let { path ->
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    var sample = 1
                    while (
                        bounds.outWidth / sample > MAX_IMAGE_DIMENSION ||
                        bounds.outHeight / sample > MAX_IMAGE_DIMENSION
                    ) {
                        sample *= 2
                    }
                    BitmapFactory.decodeFile(
                        path,
                        BitmapFactory.Options().apply {
                            inSampleSize = sample
                        },
                    )
                }.getOrNull()
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                top = ((block.style?.marginTopEm ?: 0f) * 16f).dp.coerceIn(0.dp, 24.dp),
                bottom = ((block.style?.marginBottomEm ?: 0f) * 16f).dp.coerceIn(0.dp, 24.dp) + 8.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            val decoded = bitmap!!
            val widthFraction = minOf(
                block.style?.imageWidthFraction ?: 1f,
                block.style?.imageMaxWidthFraction ?: 1f,
            ).coerceIn(0.1f, 1f)
            val maxHeight = (block.style?.imageHeightDp ?: 480f).dp.coerceIn(80.dp, 480.dp)
            Image(
                bitmap = decoded.asImageBitmap(),
                contentDescription = block.imageAlt,
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .heightIn(max = maxHeight),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = block.imageAlt ?: stringResource(R.string.reader_image_unavailable),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MarkdownTableBlock(
    block: ReaderBlock,
    fontSizeSp: Float,
    lineHeightSp: Float,
    onOpenLink: (String) -> Unit = {},
) {
    val table = block.table ?: return
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val cellFontSize = (fontSizeSp * 0.92f).sp
    val cellLineHeight = lineHeightSp.sp
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("markdown_table_scroll"),
    ) {
        val availablePx = with(density) { maxWidth.toPx() }
        val paddingPx = with(density) { 16.dp.toPx() }
        val minColumnPx = with(density) { 88.dp.toPx() }
        val maxColumnPx = with(density) { 220.dp.toPx() }
        val widths = remember(table, cellFontSize, cellLineHeight, availablePx) {
            val allRows = listOf(table.header) + table.rows
            val natural = List(table.columnCount) { column ->
                val textWidth = allRows.maxOfOrNull { row ->
                    val cell = row.getOrNull(column) ?: BookTableCell("")
                    measurer.measure(
                        text = AnnotatedString(cell.text),
                        style = TextStyle(fontSize = cellFontSize, lineHeight = cellLineHeight),
                        maxLines = 1,
                        constraints = Constraints(maxWidth = maxColumnPx.toInt()),
                    ).size.width
                } ?: 0
                (textWidth + paddingPx).coerceIn(minColumnPx, maxColumnPx)
            }
            val naturalTotal = natural.sum()
            if (naturalTotal < availablePx && table.columnCount > 0) {
                val extra = (availablePx - naturalTotal) / table.columnCount
                natural.map { it + extra }
            } else {
                natural
            }
        }
        val totalPx = widths.sum()
        val scrollable = totalPx > availablePx + 1f
        val tableWidth = with(density) { totalPx.toDp() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier),
        ) {
            Column(
                modifier = Modifier
                    .width(tableWidth)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
            ) {
                MarkdownTableRow(
                    cells = table.header,
                    alignments = table.alignments,
                    widths = widths.map { with(density) { it.toDp() } },
                    header = true,
                    rowColor = headerColor,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    linkColor = linkColor,
                    codeBackground = codeBackground,
                    onOpenLink = onOpenLink,
                )
                table.rows.forEach { row ->
                    HorizontalDivider(color = borderColor)
                    MarkdownTableRow(
                        cells = row,
                        alignments = table.alignments,
                        widths = widths.map { with(density) { it.toDp() } },
                        header = false,
                        rowColor = Color.Transparent,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        linkColor = linkColor,
                        codeBackground = codeBackground,
                        onOpenLink = onOpenLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<BookTableCell>,
    alignments: List<BookTextAlign>,
    widths: List<androidx.compose.ui.unit.Dp>,
    header: Boolean,
    rowColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    linkColor: Color,
    codeBackground: Color,
    onOpenLink: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor),
    ) {
        cells.forEachIndexed { index, cell ->
            val annotated = remember(cell.text, cell.spans, linkColor, codeBackground) {
                buildAnnotatedString {
                    append(cell.text)
                    cell.spans.forEach { span ->
                        addStyle(span.toSpanStyle(linkColor, codeBackground), span.start, span.end)
                    }
                }
            }
            val linkSpans = cell.spans.filter { it.link && it.linkTarget != null }
            var textLayout by remember(cell.text, cell.spans) {
                mutableStateOf<TextLayoutResult?>(null)
            }
            val linkTapModifier = if (linkSpans.isNotEmpty()) {
                Modifier.pointerInput(annotated, cell.spans) {
                    detectTapGestures { position ->
                        val offset = textLayout?.getOffsetForPosition(position)
                            ?: return@detectTapGestures
                        val target = linkSpans.firstOrNull {
                            offset >= it.start && offset < it.end
                        }?.linkTarget
                        if (target != null) onOpenLink(target)
                    }
                }
            } else {
                Modifier
            }
            Text(
                text = annotated,
                modifier = Modifier
                    .width(widths.getOrElse(index) { widths.lastOrNull() ?: 88.dp })
                    .padding(horizontal = 8.dp, vertical = 7.dp)
                    .then(linkTapModifier),
                onTextLayout = { textLayout = it },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = if (header) FontWeight.SemiBold else null,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                textAlign = when (alignments.getOrNull(index)) {
                    BookTextAlign.CENTER -> TextAlign.Center
                    BookTextAlign.END -> TextAlign.End
                    else -> TextAlign.Start
                },
            )
        }
    }
}

private const val MAX_IMAGE_DIMENSION = 2048

@Composable
private fun CommentCountBubble(
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = "💬 $count",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ChapterCommentEntry(
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = if (count > 0) {
                    stringResource(R.string.reader_chapter_comment_count, count)
                } else {
                    stringResource(R.string.reader_add_chapter_comment)
                },
            )
        }
    }
}

@Composable
private fun ReaderIdleFooter(state: ReaderUiState) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var timeText by remember { mutableStateOf(formatter.format(Date())) }
    var started by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            started = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            timeText = formatter.format(Date())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        while (isActive) {
            delay(30_000L)
            timeText = formatter.format(Date())
        }
    }
    // Footer 直接贴住阅读器底部，必须显式避让系统导航栏；
    // 不能假设外层 Scaffold 的 innerPadding 已经覆盖手势/三键导航区域。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(
                R.string.reader_progress,
                (state.percent.coerceIn(0f, 1f) * 100).roundToInt(),
            ),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun ReaderIdleChapterHeader(state: ReaderUiState) {
    // 固定高度占位：无论控制栏是否显示，正文起始位置都不跳变；
    // 控制栏显示时由顶部栏负责章节名，这里只保留空白。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (!state.controlsVisible) {
            Text(
                text = state.currentChapterTitle ?: state.title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class CommentTarget(
    val type: CommentType,
    val chapter: ChapterCandidate?,
    val block: ReaderBlock?,
    val quote: String,
)

@Composable
private fun CommentEditorDialog(
    target: CommentTarget,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onRequestClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onRequestClose,
        title = {
            Text(
                stringResource(
                    if (target.type == CommentType.CHAPTER) {
                        R.string.reader_add_chapter_comment
                    } else {
                        R.string.reader_add_comment
                    },
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = target.chapter?.title
                        ?: stringResource(R.string.reader_unassigned_chapter),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = target.quote,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reader_comment_input)) },
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = draft.isNotBlank(),
            ) {
                Text(stringResource(R.string.reader_save_comment))
            }
        },
        dismissButton = {
            TextButton(onClick = onRequestClose) {
                Text(stringResource(R.string.reader_cancel))
            }
        },
    )
}

@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    onPickMarkdownImageDirectory: (() -> Unit)? = null,
    imageAttachmentBusy: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            ) {
                BackArrowIcon()
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onPickMarkdownImageDirectory != null) {
                AppIconButton(
                    contentDescription = stringResource(R.string.reader_set_image_directory),
                    onClick = onPickMarkdownImageDirectory,
                    enabled = !imageAttachmentBusy,
                ) {
                    FolderIcon()
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: ReaderUiState,
    annotationsEnabled: Boolean,
    onOpenPanel: (ReaderPanel) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.reader_progress,
                        (state.percent.coerceIn(0f, 1f) * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = state.currentChapterTitle ?: state.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { state.percent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReaderControlButton(
                    label = stringResource(R.string.reader_directory),
                    icon = { TextLinesIcon() },
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenPanel(ReaderPanel.DIRECTORY) },
                )
                if (annotationsEnabled) {
                    ReaderControlButton(
                        label = stringResource(R.string.reader_notes),
                        icon = { BookmarkIcon() },
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenPanel(ReaderPanel.NOTES) },
                    )
                }
                ReaderControlButton(
                    label = stringResource(R.string.reader_settings),
                    icon = { SettingsIcon() },
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenPanel(ReaderPanel.SETTINGS) },
                )
            }
        }
    }
}

@Composable
private fun ReaderControlButton(
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PanelScrim(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DirectoryPanel(
    state: ReaderUiState,
    annotationsEnabled: Boolean,
    onChapterClick: (ChapterCandidate) -> Unit,
    onAddChapterComment: (ChapterCandidate) -> Unit,
    onClose: () -> Unit,
) {
    var menuChapter by remember { mutableStateOf<ChapterCandidate?>(null) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 12.dp, top = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.reader_directory_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.reader_close))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp, 62.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    BookIcon(
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    text = state.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (state.chapters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.reader_no_chapters_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.reader_no_chapters_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(
                        items = state.chapters,
                        key = { it.startOffset },
                    ) { chapter ->
                        val selected = chapter.startOffset <= state.currentOffset &&
                            state.currentOffset < chapter.endOffset
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reader_chapter_${chapter.startOffset}")
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .combinedClickable(
                                        onClick = { onChapterClick(chapter) },
                                        onLongClick = {
                                            if (annotationsEnabled) menuChapter = chapter
                                        },
                                    )
                                    .padding(
                                        start = (8 + chapter.level.coerceIn(0, 6) * 12).dp,
                                        end = 8.dp,
                                        top = 11.dp,
                                        bottom = 11.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = chapter.title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            AppActionMenu(
                                expanded = annotationsEnabled &&
                                    menuChapter?.startOffset == chapter.startOffset,
                                onDismissRequest = { menuChapter = null },
                            ) {
                                AppActionMenuItem(
                                    text = stringResource(R.string.reader_add_chapter_comment),
                                    icon = { TextLinesIcon(modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuChapter = null
                                        onAddChapterComment(chapter)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesPanel(
    state: ReaderUiState,
    onJumpToOffset: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val groups = remember(state.bookmarks, state.comments, state.title) {
        buildNoteGroups(state)
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.reader_notes_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.reader_close))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (groups.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reader_notes_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.reader_notes_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.reader_notes_quote_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    groups.forEach { group ->
                        Text(
                            text = group.title,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        group.bookmarks.forEach { bookmark ->
                            NoteBookmarkRow(
                                bookmark = bookmark,
                                onClick = { onJumpToOffset(bookmark.position) },
                            )
                        }
                        group.comments.forEach { comment ->
                            NoteCommentRow(
                                comment = comment,
                                onClick = {
                                    onJumpToOffset(
                                        comment.anchorStart
                                            ?: comment.chapterStartOffset
                                            ?: 0,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class NoteGroup(
    val key: String,
    val title: String,
    val chapterStart: Int?,
    val bookmarks: MutableList<Bookmark> = mutableListOf(),
    val comments: MutableList<Comment> = mutableListOf(),
)

private fun buildNoteGroups(state: ReaderUiState): List<NoteGroup> {
    val groups = linkedMapOf<String, NoteGroup>()
    fun groupFor(key: String, title: String?, chapterStart: Int?): NoteGroup =
        groups.getOrPut(key) {
            NoteGroup(
                key = key,
                title = title ?: "未归属章节",
                chapterStart = chapterStart,
            )
        }

    state.bookmarks.forEach { bookmark ->
        val key = bookmark.chapterId ?: ReaderChapterTitle.UNASSIGNED_CHAPTER_KEY
        groupFor(key, bookmark.chapterTitle, bookmark.chapterStartOffset)
            .bookmarks += bookmark
    }
    state.comments.forEach { comment ->
        groupFor(comment.chapterId, comment.chapterTitle, comment.chapterStartOffset)
            .comments += comment
    }
    return groups.values.sortedWith(
        compareBy(nullsLast()) { it.chapterStart },
    )
}

// 分组只保存可排序数据；章节标题优先来自保存记录。

@Composable
private fun NoteBookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NoteTypeBadge(
                text = stringResource(R.string.reader_note_type_bookmark),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = bookmark.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatNoteTime(bookmark.createdAt),
            modifier = Modifier.padding(start = 4.dp, top = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoteCommentRow(
    comment: Comment,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NoteTypeBadge(
                text = stringResource(
                    if (comment.commentType == CommentType.CHAPTER) {
                        R.string.reader_note_type_chapter
                    } else {
                        R.string.reader_note_type_passage
                    },
                ),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatNoteTime(comment.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        comment.quotedText?.takeIf { it.isNotBlank() }?.let { quote ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = quote,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = comment.content,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NoteTypeBadge(
    text: String,
    container: Color,
    content: Color,
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = container,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

private fun formatNoteTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun ReaderSettingsPanel(
    settings: ReaderSettings,
    annotationsEnabled: Boolean,
    onSettingsChange: (ReaderSettings) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.reader_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.reader_close))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                ReaderSettingSlider(
                    label = stringResource(R.string.reader_font_size),
                    value = settings.fontSizeSp,
                    valueRange = ReaderSettings.MIN_FONT_SIZE_SP..ReaderSettings.MAX_FONT_SIZE_SP,
                    steps = 7,
                    valueText = stringResource(
                        R.string.reader_sp_value,
                        settings.fontSizeSp.roundToInt(),
                    ),
                    onValueChange = {
                        onSettingsChange(settings.copy(fontSizeSp = it).normalized())
                    },
                )
                ReaderSettingSlider(
                    label = stringResource(R.string.reader_line_height),
                    value = settings.lineHeightSp,
                    valueRange = ReaderSettings.MIN_LINE_HEIGHT_SP..ReaderSettings.MAX_LINE_HEIGHT_SP,
                    steps = 7,
                    valueText = stringResource(
                        R.string.reader_sp_value,
                        settings.lineHeightSp.roundToInt(),
                    ),
                    onValueChange = {
                        onSettingsChange(settings.copy(lineHeightSp = it).normalized())
                    },
                )
                ReaderSettingSlider(
                    label = stringResource(R.string.reader_paragraph_spacing),
                    value = settings.paragraphSpacingDp,
                    valueRange = ReaderSettings.MIN_PARAGRAPH_SPACING_DP..
                        ReaderSettings.MAX_PARAGRAPH_SPACING_DP,
                    steps = 7,
                    valueText = stringResource(
                        R.string.reader_dp_value,
                        settings.paragraphSpacingDp.roundToInt(),
                    ),
                    onValueChange = {
                        onSettingsChange(settings.copy(paragraphSpacingDp = it).normalized())
                    },
                )
                ReaderSettingSlider(
                    label = stringResource(R.string.reader_horizontal_padding),
                    value = settings.horizontalPaddingDp,
                    valueRange = ReaderSettings.MIN_HORIZONTAL_PADDING_DP..
                        ReaderSettings.MAX_HORIZONTAL_PADDING_DP,
                    steps = 13,
                    valueText = stringResource(
                        R.string.reader_dp_value,
                        settings.horizontalPaddingDp.roundToInt(),
                    ),
                    onValueChange = {
                        onSettingsChange(settings.copy(horizontalPaddingDp = it).normalized())
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                if (annotationsEnabled) {
                    ReaderSettingToggle(
                        title = stringResource(R.string.reader_show_comment_bubbles),
                        description = stringResource(R.string.reader_show_comment_bubbles_desc),
                        checked = settings.showCommentBubbles,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(showCommentBubbles = it).normalized())
                        },
                    )
                    ReaderSettingToggle(
                        title = stringResource(R.string.reader_show_chapter_comment_entry),
                        description = stringResource(R.string.reader_show_chapter_comment_entry_desc),
                        checked = settings.showChapterCommentEntry,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(showChapterCommentEntry = it).normalized())
                        },
                    )
                }
                ReaderSettingToggle(
                    title = stringResource(R.string.reader_enable_long_press),
                    description = stringResource(R.string.reader_enable_long_press_desc),
                    checked = settings.enableLongPressMenu,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(enableLongPressMenu = it).normalized())
                    },
                )
                Text(
                    text = stringResource(R.string.reader_settings_toggle_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.reader_settings_session_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.reader_settings_theme_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReaderSettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun ReaderSettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
