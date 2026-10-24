package com.xtt.toolbox.ui.writing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.InlineSpan
import com.xtt.toolbox.domain.writing.WritingChapterDraft
import com.xtt.toolbox.domain.writing.WritingChapterImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface EditorNode {
    data class Text(
        val key: String,
        val start: Int,
        val text: String,
        val beforeImageId: String?,
        val afterImageId: String?,
    ) : EditorNode

    data class ImageNode(val image: WritingChapterImage) : EditorNode
}

private const val BOUNDARY_SENTINEL = "\u00A0"


@Composable
fun ImageChapterEditorBody(
    draft: WritingChapterDraft,
    selectionStart: Int,
    selectionEnd: Int,
    imagePaths: Map<String, String>,
    findMatches: List<IntRange>,
    currentMatchIndex: Int,
    lastInsertedImageId: String?,
    bodyStart: Int,
    selectedImageId: String?,
    onSelectImage: (String) -> Unit,
    onClearImageSelection: () -> Unit,
    onTextChange: (String, Int, Int, Boolean) -> Unit,
    onDeleteImage: (String) -> Unit,
    onConsumeInsertedImageFocus: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    header: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val nodes = remember(draft.text, draft.images, bodyStart) {
        buildNodes(draft, bodyStart)
    }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val focusManager = LocalFocusManager.current
    var pendingFocusOffset by remember { mutableStateOf<Int?>(null) }
    var initialFocusDone by remember(draft.chapterId) { mutableStateOf(false) }

    fun requester(key: String): FocusRequester = focusRequesters.getOrPut(key) { FocusRequester() }

    fun focusTextAt(offset: Int) {
        val target = nodes.filterIsInstance<EditorNode.Text>().firstOrNull {
            offset in it.start..(it.start + it.text.length)
        } ?: nodes.filterIsInstance<EditorNode.Text>().lastOrNull()
        target?.let { requester(it.key).requestFocus() }
    }

    LaunchedEffect(draft.chapterId, nodes) {
        if (!initialFocusDone && nodes.isNotEmpty()) {
            withFrameNanos { }
            focusManager.clearFocus(force = true)
            focusTextAt(selectionStart)
            initialFocusDone = true
        }
    }

    LaunchedEffect(lastInsertedImageId, nodes) {
        val insertedId = lastInsertedImageId ?: return@LaunchedEffect
        val imageIndex = nodes.indexOfFirst { it is EditorNode.ImageNode && it.image.blockId == insertedId }
        if (imageIndex < 0) return@LaunchedEffect
        val followingText = nodes.drop(imageIndex + 1).filterIsInstance<EditorNode.Text>().firstOrNull()
        if (followingText != null) requester(followingText.key).requestFocus()
        onConsumeInsertedImageFocus()
    }

    LaunchedEffect(pendingFocusOffset, nodes) {
        val offset = pendingFocusOffset ?: return@LaunchedEffect
        focusTextAt(offset)
        pendingFocusOffset = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        header()
        nodes.forEach { node ->
            when (node) {
                is EditorNode.Text -> {
                    val hasLeadingBoundary = node.beforeImageId != null
                    val hasTrailingBoundary = node.afterImageId != null
                    val displayText = buildString {
                        if (hasLeadingBoundary) append(BOUNDARY_SENTINEL)
                        append(node.text)
                        if (hasTrailingBoundary) append(BOUNDARY_SENTINEL)
                    }
                    val displaySelectionStart = if (
                        selectionStart in node.start..(node.start + node.text.length)
                    ) {
                        (if (hasLeadingBoundary) 1 else 0) + selectionStart - node.start
                    } else {
                        if (hasLeadingBoundary) 1 else 0
                    }
                    val displaySelectionEnd = if (
                        selectionEnd in node.start..(node.start + node.text.length)
                    ) {
                        (if (hasLeadingBoundary) 1 else 0) + selectionEnd - node.start
                    } else {
                        displaySelectionStart
                    }
                    val value = remember(
                        node.key,
                        displayText,
                        displaySelectionStart,
                        displaySelectionEnd,
                    ) {
                        TextFieldValue(
                            text = displayText,
                            selection = TextRange(displaySelectionStart, displaySelectionEnd),
                        )
                    }
                    val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val matchColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.62f)
                    val currentMatchColor = MaterialTheme.colorScheme.primaryContainer
                    val transformation = remember(
                        node.key,
                        displayText,
                        draft,
                        quoteColor,
                        matchColor,
                        currentMatchColor,
                    ) {
                        SegmentVisualTransformation(
                            draft = draft,
                            nodeStart = node.start,
                            text = displayText,
                            textPrefix = if (hasLeadingBoundary) 1 else 0,
                            quoteColor = quoteColor,
                            matchColor = matchColor,
                            currentMatchColor = currentMatchColor,
                            findMatches = findMatches,
                            currentMatchIndex = currentMatchIndex,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { updated ->
                            if (hasLeadingBoundary &&
                                !updated.text.startsWith(BOUNDARY_SENTINEL) &&
                                updated.selection.start == 0
                            ) {
                                pendingFocusOffset = node.start
                                onDeleteImage(node.beforeImageId!!)
                                return@BasicTextField
                            }
                            if (hasTrailingBoundary &&
                                !updated.text.endsWith(BOUNDARY_SENTINEL) &&
                                updated.selection.start >= updated.text.length
                            ) {
                                pendingFocusOffset = node.start + node.text.length
                                onDeleteImage(node.afterImageId!!)
                                return@BasicTextField
                            }
                            val prefix = if (hasLeadingBoundary) 1 else 0
                            val suffix = if (hasTrailingBoundary) 1 else 0
                            val stripped = updated.text
                                .removePrefix(if (hasLeadingBoundary) BOUNDARY_SENTINEL else "")
                                .removeSuffix(if (hasTrailingBoundary) BOUNDARY_SENTINEL else "")
                            val globalText = draft.text.substring(0, node.start) +
                                stripped + draft.text.substring(node.start + node.text.length)
                            val localStart = (updated.selection.start - prefix)
                                .coerceIn(0, stripped.length)
                            val localEnd = (updated.selection.end - prefix)
                                .coerceIn(localStart, stripped.length)
                            onTextChange(
                                globalText,
                                node.start + localStart,
                                node.start + localEnd,
                                updated.composition != null,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 30.dp)
                            .focusRequester(requester(node.key))
                            .onFocusChanged { focus ->
                                if (focus.isFocused) onClearImageSelection()
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val selection = value.selection
                                val prefix = if (hasLeadingBoundary) 1 else 0
                                val endBeforeSuffix = value.text.length -
                                    (if (hasTrailingBoundary) 1 else 0)
                                if (event.key == Key.Backspace && selection.collapsed &&
                                    selection.start == prefix && hasLeadingBoundary
                                ) {
                                    pendingFocusOffset = node.start
                                    onDeleteImage(node.beforeImageId!!)
                                    true
                                } else if (event.key == Key.Delete && selection.collapsed &&
                                    selection.start == endBeforeSuffix && hasTrailingBoundary
                                ) {
                                    pendingFocusOffset = node.start + node.text.length
                                    onDeleteImage(node.afterImageId!!)
                                    true
                                } else {
                                    false
                                }
                            },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 28.sp,
                            // 与普通正文一致：长段落软换行时保留行首两个全角空格。
                            lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.primary,
                        ),
                        visualTransformation = transformation,
                    )
                }

                is EditorNode.ImageNode -> {
                    val image = node.image
                    val isSelected = image.blockId == selectedImageId
                    WritingBlockImage(
                        image = image,
                        path = imagePaths[image.resourceId],
                        selected = isSelected,
                        focusRequester = requester("image-${image.blockId}"),
                        onClick = {
                            onSelectImage(image.blockId)
                            requester("image-${image.blockId}").requestFocus()
                        },
                        onDelete = {
                            pendingFocusOffset = image.anchor
                            onDeleteImage(image.blockId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WritingBlockImage(
    image: WritingChapterImage,
    path: String?,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onDelete: () -> Unit,
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    Modifier
                },
            )
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Backspace || event.key == Key.Delete)
                ) {
                    onDelete()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = image.alt ?: "正文图片",
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = "图片无法加载",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildNodes(draft: WritingChapterDraft, bodyStart: Int): List<EditorNode> {
    val startOffset = bodyStart.coerceIn(0, draft.text.length)
    val bodyImages = draft.images.filter { it.anchor >= startOffset }.sortedBy { it.anchor }
    if (bodyImages.isEmpty()) {
        return listOf(
            EditorNode.Text(
                key = "text-$startOffset",
                start = startOffset,
                text = draft.text.substring(startOffset),
                beforeImageId = null,
                afterImageId = null,
            ),
        )
    }
    val nodes = mutableListOf<EditorNode>()
    var cursor = startOffset
    var lastImageId: String? = null
    bodyImages.forEach { image ->
        val start = image.anchor.coerceIn(cursor, draft.text.length)
        nodes += EditorNode.Text(
            key = "text-$cursor-${lastImageId ?: "start"}",
            start = cursor,
            text = draft.text.substring(cursor, start),
            beforeImageId = lastImageId,
            afterImageId = image.blockId,
        )
        nodes += EditorNode.ImageNode(image)
        cursor = start
        lastImageId = image.blockId
    }
    nodes += EditorNode.Text(
        key = "text-$cursor-${lastImageId ?: "start"}",
        start = cursor,
        text = draft.text.substring(cursor),
        beforeImageId = lastImageId,
        afterImageId = null,
    )
    return nodes
}

private class SegmentVisualTransformation(
    private val draft: WritingChapterDraft,
    private val nodeStart: Int,
    private val text: String,
    private val textPrefix: Int,
    private val quoteColor: androidx.compose.ui.graphics.Color,
    private val matchColor: androidx.compose.ui.graphics.Color,
    private val currentMatchColor: androidx.compose.ui.graphics.Color,
    private val findMatches: List<IntRange>,
    private val currentMatchIndex: Int,
) : VisualTransformation {
    override fun filter(textValue: AnnotatedString): TransformedText {
        val annotated = buildAnnotatedString {
            append(text)
            val startLine = draft.text.substring(0, nodeStart.coerceIn(0, draft.text.length))
                .count { it == '\n' }
            var offset = 0
            text.split('\n').forEachIndexed { index, line ->
                val start = offset
                val end = (offset + line.length).coerceAtMost(text.length)
                when (draft.lineTypes.getOrNull(startLine + index)) {
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
                        SpanStyle(fontStyle = FontStyle.Italic, color = quoteColor),
                        start,
                        end,
                    )

                    else -> Unit
                }
                offset = end + 1
            }
            draft.spans.forEach { span ->
                val localStart = maxOf(span.start - nodeStart + textPrefix, 0)
                val localEnd = minOf(span.end - nodeStart + textPrefix, text.length)
                if (localEnd > localStart) {
                    addStyle(
                        SpanStyle(
                            fontWeight = if (span.bold) FontWeight.Bold else null,
                            fontStyle = if (span.italic) FontStyle.Italic else null,
                        ),
                        localStart,
                        localEnd,
                    )
                }
            }
            findMatches.forEachIndexed { index, range ->
                val localStart = maxOf(range.first - nodeStart + textPrefix, 0)
                val localEnd = minOf(range.last + 1 - nodeStart + textPrefix, text.length)
                if (localEnd > localStart) {
                    addStyle(
                        SpanStyle(
                            background = if (index == currentMatchIndex) {
                                currentMatchColor
                            } else {
                                matchColor
                            },
                        ),
                        localStart,
                        localEnd,
                    )
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
