package com.xtt.toolbox.ui.writing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.writing.WritingChapterDraft
import com.xtt.toolbox.domain.writing.WritingChapterImage
import com.xtt.toolbox.ui.components.BackArrowIcon
import com.xtt.toolbox.ui.components.BoldIcon
import com.xtt.toolbox.ui.components.BookIcon
import com.xtt.toolbox.ui.components.GearIcon
import com.xtt.toolbox.ui.components.HeadingIcon
import com.xtt.toolbox.ui.components.ImageIcon
import com.xtt.toolbox.ui.components.ItalicIcon
import com.xtt.toolbox.ui.components.QuoteIcon
import com.xtt.toolbox.ui.components.RedoIcon
import com.xtt.toolbox.ui.components.SearchIcon
import com.xtt.toolbox.ui.components.SmartFormatIcon
import com.xtt.toolbox.ui.components.TextLinesIcon
import com.xtt.toolbox.ui.components.ToolboxIcon
import com.xtt.toolbox.ui.components.UndoIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingEditorScreen(
    state: WritingEditorUiState,
    callbacks: WritingEditorCallbacks,
    modifier: Modifier = Modifier,
) {
    val onTextChange = callbacks.onTextChange
    val onTitleChange = callbacks.onTitleChange
    val onBack = callbacks.onBack
    val onPreview = callbacks.onPreview
    val onPickImage = callbacks.onPickImage
    val onRetrySave = callbacks.onRetrySave
    val onShowFormatBarChange = callbacks.onShowFormatBarChange
    val onShowQuickBarChange = callbacks.onShowQuickBarChange
    val onAutoPairChange = callbacks.onAutoPairChange
    val excludePunctuation = callbacks.excludePunctuation
    val onExcludePunctuationChange = callbacks.onExcludePunctuationChange
    val onInsertPunctuation = callbacks.onInsertPunctuation
    val onDeleteImage = callbacks.onDeleteImage
    val onConsumeInsertedImageFocus = callbacks.onConsumeInsertedImageFocus
    val onAddPunctuation = callbacks.onAddPunctuation
    val onRemovePunctuation = callbacks.onRemovePunctuation
    val onMovePunctuation = callbacks.onMovePunctuation
    val onResetPunctuation = callbacks.onResetPunctuation
    val onUndo = callbacks.onUndo
    val onRedo = callbacks.onRedo
    val onBold = callbacks.onBold
    val onItalic = callbacks.onItalic
    val onHeading = callbacks.onHeading
    val onQuote = callbacks.onQuote
    val onParagraph = callbacks.onParagraph
    val onSmartFormat = callbacks.onSmartFormat
    val onFindQuery = callbacks.onFindQuery
    val onReplaceQuery = callbacks.onReplaceQuery
    val onFindNext = callbacks.onFindNext
    val onReplaceCurrent = callbacks.onReplaceCurrent
    val onReplaceAll = callbacks.onReplaceAll
    val onClearFind = callbacks.onClearFind
    var showToolbox by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var overlayWasOpen by remember { mutableStateOf(false) }
    var selectedImageId by remember { mutableStateOf<String?>(null) }
    var titleEditing by remember { mutableStateOf(false) }
    val editorScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val editorFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val draft = state.draft
    val titleLineEnd = draft?.text?.indexOf('\n') ?: -1
    val hasTitleLine = draft != null &&
        draft.lineTypes.firstOrNull() == BookBlockType.HEADING &&
        titleLineEnd >= 0
    val titleText = if (hasTitleLine && draft != null) {
        draft.text.substring(0, titleLineEnd)
    } else {
        state.chapterTitle
    }
    val bodyStart = if (hasTitleLine) titleLineEnd + 1 else 0
    val bodyText = draft?.text?.substring(bodyStart).orEmpty()
    val bodyLineStart = draft?.text?.substring(0, bodyStart)?.count { it == '\n' } ?: 0
    val bodyLineTypes = draft?.lineTypes?.drop(bodyLineStart)?.take(
        if (bodyText.isEmpty()) 1 else bodyText.count { it == '\n' } + 1,
    ).orEmpty()
    val bodySpans = draft?.spans?.mapNotNull { span ->
        val start = span.start - bodyStart
        val end = span.end - bodyStart
        if (end <= 0 || start >= bodyText.length) null else span.copy(
            start = start.coerceAtLeast(0),
            end = end.coerceAtMost(bodyText.length),
        )
    }.orEmpty()
    val bodyDraft = draft?.copy(
        text = bodyText,
        lineTypes = bodyLineTypes,
        spans = bodySpans,
    )
    val bodySelectionStart = (state.selectionStart - bodyStart).coerceIn(0, bodyText.length)
    val bodySelectionEnd = (state.selectionEnd - bodyStart).coerceIn(0, bodyText.length)
    val textFieldValue = remember(bodyText, bodySelectionStart, bodySelectionEnd) {
        TextFieldValue(
            text = bodyText,
            selection = TextRange(bodySelectionStart, bodySelectionEnd),
        )
    }
    val bodyMatches = remember(state.findMatches, bodyStart, bodyText.length) {
        state.findMatches.mapNotNull { range ->
            if (range.first < bodyStart || range.last >= bodyStart + bodyText.length) {
                null
            } else {
                (range.first - bodyStart)..(range.last - bodyStart)
            }
        }
    }
    val currentGlobalMatch = state.findMatches.getOrNull(state.findIndex)
    val titleMatches = if (hasTitleLine) {
        state.findMatches.filter { it.last < titleText.length }
    } else {
        emptyList()
    }
    val titleCurrentMatchIndex = titleMatches.indexOfFirst {
        currentGlobalMatch != null &&
            it.first == currentGlobalMatch.first &&
            it.last == currentGlobalMatch.last
    }
    val bodyCurrentMatchIndex = currentGlobalMatch?.let { range ->
        bodyMatches.indexOfFirst {
            it.first == range.first - bodyStart && it.last == range.last - bodyStart
        }
    } ?: -1
    val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant
    val matchColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.62f)
    val currentMatchColor = MaterialTheme.colorScheme.primaryContainer
    val transformation = remember(
        bodyDraft,
        quoteColor,
        matchColor,
        currentMatchColor,
        bodyMatches,
        bodyCurrentMatchIndex,
    ) {
        bodyDraft?.let {
            WritingEditorVisualTransformation(
                draft = it,
                quoteColor = quoteColor,
                matchColor = matchColor,
                currentMatchColor = currentMatchColor,
                findMatches = bodyMatches,
                currentMatchIndex = bodyCurrentMatchIndex,
            )
        }
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var editorBodyHeight by remember { mutableStateOf(0) }

    LaunchedEffect(
        showFindReplace,
        state.findIndex,
        state.findMatches,
        state.selectionStart,
        textLayoutResult,
        editorBodyHeight,
        state.draft?.text,
        bodyStart,
    ) {
        if (!showFindReplace || state.findQuery.isEmpty()) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val offset = (state.selectionStart - bodyStart)
            .coerceIn(0, layout.layoutInput.text.length)
        val box = runCatching {
            layout.getBoundingBox(offset)
        }.getOrNull() ?: return@LaunchedEffect
        if (editorBodyHeight <= 0) return@LaunchedEffect
        val target = (box.top + 16f - editorBodyHeight / 3f)
            .toInt()
            .coerceIn(0, editorScroll.maxValue)
        editorScroll.animateScrollTo(target)
    }

    LaunchedEffect(showToolbox, showSettings, showFindReplace) {
        if (showToolbox || showSettings || showFindReplace) {
            overlayWasOpen = true
        } else if (overlayWasOpen) {
            overlayWasOpen = false
            delay(60)
            editorFocus.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(state.chapterId, state.loading) {
        selectedImageId = null
        titleEditing = false
    }
    LaunchedEffect(state.draft?.images) {
        val images = state.draft?.images.orEmpty()
        if (selectedImageId != null && images.none { it.blockId == selectedImageId }) {
            selectedImageId = null
        }
    }
    LaunchedEffect(state.chapterId, state.loading, state.draft?.images?.size) {
        if (!state.loading && state.draft != null && state.draft.images.isEmpty()) {
            withFrameNanos { }
            focusManager.clearFocus(force = true)
            editorFocus.requestFocus()
        }
    }

    fun closeToolbox() {
        showToolbox = false
        showFindReplace = false
    }

    fun openSettings() {
        showSettings = true
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun keepEditorFocus() {
        editorFocus.requestFocus()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            WritingEditorTopBar(
                wordCount = state.wordCount,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                onBack = onBack,
                onUndo = { onUndo(); keepEditorFocus() },
                onRedo = { onRedo(); keepEditorFocus() },
                onPreview = {
                    selectedImageId = null
                    titleEditing = false
                    onPreview()
                },
                onSmartFormat = {
                    selectedImageId = null
                    titleEditing = false
                    onSmartFormat()
                    keepEditorFocus()
                },
                onToolbox = {
                    showToolbox = true
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
            )
            val chapterTitleContent: @Composable () -> Unit = {
                if (draft != null) {
                    WritingChapterTitleField(
                        title = titleText,
                        selectionStart = if (state.selectionStart in 0..titleText.length) {
                            state.selectionStart
                        } else {
                            0
                        },
                        selectionEnd = if (state.selectionEnd in 0..titleText.length) {
                            state.selectionEnd
                        } else {
                            0
                        },
                        canFocus = titleEditing,
                        onRequestTitleFocus = { titleEditing = true },
                        onClearImageSelection = { selectedImageId = null },
                        findMatches = titleMatches,
                        currentMatchIndex = titleCurrentMatchIndex,
                        matchColor = matchColor,
                        currentMatchColor = currentMatchColor,
                        onTitleChange = { newTitle, selectionStart, selectionEnd ->
                            onTitleChange(newTitle, selectionStart, selectionEnd)
                        },
                    )
                }
            }
            // Title field is part of the same scroll content as the body.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (state.loading || draft == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (draft.images.isNotEmpty()) {
                    ImageChapterEditorBody(
                        draft = draft,
                        selectionStart = state.selectionStart,
                        selectionEnd = state.selectionEnd,
                        imagePaths = state.imagePaths,
                        findMatches = state.findMatches,
                        currentMatchIndex = state.findIndex,
                        lastInsertedImageId = state.lastInsertedImageId,
                        bodyStart = bodyStart,
                        selectedImageId = selectedImageId,
                        onSelectImage = { selectedImageId = it },
                        onClearImageSelection = { selectedImageId = null },
                        onTextChange = onTextChange,
                        onDeleteImage = onDeleteImage,
                        onConsumeInsertedImageFocus = onConsumeInsertedImageFocus,
                        scrollState = editorScroll,
                        header = chapterTitleContent,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { editorBodyHeight = it.height }
                            .verticalScroll(editorScroll)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        chapterTitleContent()
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { value ->
                                onTextChange(
                                    draft.text.substring(0, bodyStart) + value.text,
                                    bodyStart + value.selection.start,
                                    bodyStart + value.selection.end,
                                    value.composition != null,
                                )
                            },
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(editorFocus),
                            visualTransformation = transformation ?: VisualTransformation.None,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 28.sp,
                                // Simple 策略在长段落软换行时会吞掉行首 U+3000，Paragraph 保留真实缩进。
                                lineBreak = LineBreak.Paragraph,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.primary,
                            ),
                            decorationBox = { inner ->
                                Box {
                                    if (draft.text.isEmpty()) {
                                        Text(
                                            "开始写作…",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }

            if (state.saveStatus == EditorSaveStatus.FAILED) {
                SaveFailureBar(onRetrySave = onRetrySave)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (!imeVisible) Modifier.navigationBarsPadding() else Modifier,
                    ),
            ) {
                WritingContextBar(
                    bookTitle = state.bookTitle,
                    chapterTitle = state.chapterTitle,
                )
                if (state.showFormatBar) {
                    WritingFormatBar(
                        onBold = { onBold(); keepEditorFocus() },
                        onItalic = { onItalic(); keepEditorFocus() },
                        onHeading = { onHeading(); keepEditorFocus() },
                        onQuote = { onQuote(); keepEditorFocus() },
                        onParagraph = { onParagraph(); keepEditorFocus() },
                    )
                }
                if (state.showQuickBar) {
                    WritingQuickBar(
                        punctuation = state.punctuation,
                        onInsertPunctuation = { value ->
                            onInsertPunctuation(value)
                            keepEditorFocus()
                        },
                        onImage = onPickImage,
                        onSettings = { openSettings() },
                    )
                }
            }
        }

        if (showToolbox) {
            WritingToolboxScreen(
                onBack = { closeToolbox() },
                onFindReplace = {
                    showToolbox = false
                    showFindReplace = true
                },
            )
        }
        if (showSettings) {
            WritingEditorSettingsScreen(
                showFormatBar = state.showFormatBar,
                showQuickBar = state.showQuickBar,
                autoPairEnabled = state.autoPairEnabled,
                excludePunctuation = excludePunctuation,
                punctuation = state.punctuation,
                onBack = { showSettings = false },
                onShowFormatBarChange = onShowFormatBarChange,
                onShowQuickBarChange = onShowQuickBarChange,
                onAutoPairChange = onAutoPairChange,
                onExcludePunctuationChange = onExcludePunctuationChange,
                onAddPunctuation = onAddPunctuation,
                onRemovePunctuation = onRemovePunctuation,
                onMovePunctuation = onMovePunctuation,
                onResetPunctuation = onResetPunctuation,
            )
        }
    }

    if (showFindReplace) {
        ModalBottomSheet(
            onDismissRequest = {
                showFindReplace = false
                onClearFind()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            WritingFindPanel(
                state = state,
                onFindQuery = onFindQuery,
                onReplaceQuery = onReplaceQuery,
                onFindNext = onFindNext,
                onReplaceCurrent = onReplaceCurrent,
                onReplaceAll = onReplaceAll,
                onDismiss = {
                    showFindReplace = false
                    onClearFind()
                },
            )
        }
    }

    BackHandler(enabled = showToolbox) {
        closeToolbox()
    }
}

@Composable
private fun WritingEditorTopBar(
    wordCount: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPreview: () -> Unit,
    onSmartFormat: () -> Unit,
    onToolbox: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "返回" },
            ) { BackArrowIcon() }
            Spacer(Modifier.weight(1f))
            Text(
                text = "$wordCount 字",
                modifier = Modifier.padding(end = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.semantics { contentDescription = "撤销" },
            ) { UndoIcon() }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.semantics { contentDescription = "重做" },
            ) { RedoIcon() }
            IconButton(
                onClick = onPreview,
                modifier = Modifier.semantics { contentDescription = "阅读预览" },
            ) { BookIcon() }
            IconButton(
                onClick = onSmartFormat,
                modifier = Modifier.semantics { contentDescription = "智能排版" },
            ) { SmartFormatIcon() }
            IconButton(
                onClick = onToolbox,
                modifier = Modifier.semantics { contentDescription = "工具箱" },
            ) { ToolboxIcon() }
        }
    }
}

@Composable
private fun WritingFormatBar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onHeading: () -> Unit,
    onQuote: () -> Unit,
    onParagraph: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBold,
                modifier = Modifier.semantics { contentDescription = "粗体" },
            ) { BoldIcon() }
            IconButton(
                onClick = onItalic,
                modifier = Modifier.semantics { contentDescription = "斜体" },
            ) { ItalicIcon() }
            IconButton(
                onClick = onHeading,
                modifier = Modifier.semantics { contentDescription = "标题" },
            ) { HeadingIcon() }
            IconButton(
                onClick = onQuote,
                modifier = Modifier.semantics { contentDescription = "引用" },
            ) { QuoteIcon() }
            IconButton(
                onClick = onParagraph,
                modifier = Modifier.semantics { contentDescription = "正文" },
            ) { TextLinesIcon() }
        }
    }
}

@Composable
private fun WritingQuickBar(
    punctuation: List<String>,
    onInsertPunctuation: (String) -> Unit,
    onImage: () -> Unit,
    onSettings: () -> Unit,
) {    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (punctuation.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        punctuation.forEach { value ->
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .widthIn(min = 42.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(
                                        role = Role.Button,
                                        onClick = { onInsertPunctuation(value) },
                                    )
                                    .semantics {
                                        contentDescription = "插入标点 $value"
                                    }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(value, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onImage,
                    modifier = Modifier.semantics { contentDescription = "插入图片" },
                ) { ImageIcon() }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.semantics { contentDescription = "编辑器设置" },
                ) { GearIcon() }
            }
        }
    }
}

@Composable
private fun WritingContextBar(
    bookTitle: String,
    chapterTitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = bookTitle,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = chapterTitle.ifBlank { "未命名章节" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SaveFailureBar(onRetrySave: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "保存失败，当前草稿仍保留",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetrySave) { Text("重试") }
        }
    }
}

@Composable
private fun WritingToolboxScreen(
    onBack: () -> Unit,
    onFindReplace: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "返回编辑器" },
                ) { BackArrowIcon() }
                Text(
                    text = "写作工具",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onFindReplace)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchIcon()
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("查找与替换", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "在当前章节中查找、定位和替换文字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }
    }
}

@Composable
private fun WritingEditorImageCard(
    path: String?,
    alt: String,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            path?.let { filePath ->
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(filePath, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) {
                        sample *= 2
                    }
                    BitmapFactory.decodeFile(
                        filePath,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                }.getOrNull()
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = alt,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "图片无法加载",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WritingChapterTitleField(
    title: String,
    selectionStart: Int,
    selectionEnd: Int,
    canFocus: Boolean,
    onRequestTitleFocus: () -> Unit,
    onClearImageSelection: () -> Unit,
    findMatches: List<IntRange>,
    currentMatchIndex: Int,
    matchColor: Color,
    currentMatchColor: Color,
    onTitleChange: (String, Int, Int) -> Unit,
) {
    if (!canFocus) {
        val display = buildAnnotatedString {
            append(title.ifEmpty { "章节标题" })
            findMatches.forEachIndexed { index, range ->
                val start = range.first.coerceIn(0, title.length)
                val end = (range.last + 1).coerceIn(start, title.length)
                if (end > start) {
                    addStyle(
                        SpanStyle(
                            background = if (index == currentMatchIndex) {
                                currentMatchColor
                            } else {
                                matchColor
                            },
                        ),
                        start,
                        end,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onClearImageSelection()
                    onRequestTitleFocus()
                }
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = display,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
        }
        return
    }
    val value = remember(title, selectionStart, selectionEnd) {
        TextFieldValue(
            text = title,
            selection = TextRange(
                selectionStart.coerceIn(0, title.length),
                selectionEnd.coerceIn(0, title.length),
            ),
        )
    }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        titleFocus.requestFocus()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { updated ->
                val sanitized = updated.text.replace('\n', ' ')
                val start = updated.selection.start.coerceIn(0, sanitized.length)
                val end = updated.selection.end.coerceIn(0, sanitized.length)
                onTitleChange(sanitized, start, end)
            },
            modifier = Modifier
                .focusRequester(titleFocus)
                .onFocusChanged { focus ->
                    if (focus.isFocused) onClearImageSelection()
                },
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 3,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

private class TitleVisualTransformation(
    private val text: String,
    private val findMatches: List<IntRange>,
    private val currentMatchIndex: Int,
    private val matchColor: Color,
    private val currentMatchColor: Color,
) : VisualTransformation {
    override fun filter(textValue: AnnotatedString): TransformedText {
        val centered = buildAnnotatedString {
            append(text)
            if (text.isNotEmpty()) {
                addStyle(
                    ParagraphStyle(textAlign = TextAlign.Center),
                    0,
                    text.length,
                )
            }
            findMatches.forEachIndexed { index, range ->
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(start, text.length)
                if (end > start) {
                    addStyle(
                        SpanStyle(
                            background = if (index == currentMatchIndex) {
                                currentMatchColor
                            } else {
                                matchColor
                            },
                        ),
                        start,
                        end,
                    )
                }
            }
        }
        return TransformedText(centered, OffsetMapping.Identity)
    }
}

private class WritingEditorVisualTransformation(
    private val draft: WritingChapterDraft,
    private val quoteColor: Color,
    private val matchColor: Color,
    private val currentMatchColor: Color,
    private val findMatches: List<IntRange>,
    private val currentMatchIndex: Int,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val annotated = buildAnnotatedString {
            append(draft.text)
            val lines = if (draft.text.isEmpty()) listOf("") else draft.text.split('\n')
            var offset = 0
            lines.forEachIndexed { index, line ->
                val start = offset
                val end = (offset + line.length).coerceAtMost(draft.text.length)
                when (draft.lineTypes.getOrNull(index)) {
                    BookBlockType.HEADING -> {
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                            start,
                            end,
                        )
                        addStyle(
                            ParagraphStyle(textAlign = TextAlign.Center),
                            start,
                            end,
                        )
                    }

                    BookBlockType.QUOTE -> addStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            color = quoteColor,
                        ),
                        start,
                        end,
                    )

                    else -> Unit
                }
                offset = end + 1
            }
            draft.spans.forEach { span ->
                addStyle(
                    SpanStyle(
                        fontWeight = if (span.bold) FontWeight.Bold else null,
                        fontStyle = if (span.italic) FontStyle.Italic else null,
                    ),
                    span.start.coerceIn(0, draft.text.length),
                    span.end.coerceIn(0, draft.text.length),
                )
            }
            findMatches.forEachIndexed { index, range ->
                val start = range.first.coerceIn(0, draft.text.length)
                val end = (range.last + 1).coerceIn(0, draft.text.length)
                if (end > start) {
                    addStyle(
                        SpanStyle(
                            background = if (index == currentMatchIndex) {
                                currentMatchColor
                            } else {
                                matchColor
                            },
                        ),
                        start,
                        end,
                    )
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

@Composable
private fun WritingFindPanel(
    state: WritingEditorUiState,
    onFindQuery: (String) -> Unit,
    onReplaceQuery: (String) -> Unit,
    onFindNext: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "查找与替换",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
        OutlinedTextField(
            value = state.findQuery,
            onValueChange = onFindQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("查找") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.replaceQuery,
            onValueChange = onReplaceQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("替换为") },
            singleLine = true,
        )
        Text(
            text = when {
                state.findQuery.isEmpty() -> "输入查找内容"
                state.findMatches.isEmpty() -> "未找到匹配内容"
                else -> "第 ${state.findIndex + 1} / ${state.findMatches.size} 处匹配"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (state.findQuery.isNotEmpty() && state.findMatches.isEmpty()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onFindNext, enabled = state.findMatches.isNotEmpty()) {
                Text("下一个")
            }
            TextButton(onClick = onReplaceCurrent, enabled = state.findMatches.isNotEmpty()) {
                Text("替换")
            }
            TextButton(onClick = onReplaceAll, enabled = state.findMatches.isNotEmpty()) {
                Text("全部替换")
            }
        }
    }
}
