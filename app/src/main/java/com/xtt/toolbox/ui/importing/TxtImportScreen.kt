package com.xtt.toolbox.ui.importing

import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.R
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.domain.importing.EncodingConfidence
import com.xtt.toolbox.domain.importing.TxtImportPreview
import com.xtt.toolbox.ui.components.AppSectionCard
import com.xtt.toolbox.ui.components.InsetDivider
import com.xtt.toolbox.ui.components.ScreenNote

@Composable
fun TxtImportScreen(
    state: TxtImportUiState,
    onConfirm: (String) -> Unit,
    onRetryEncoding: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onPickImageDirectory: () -> Unit = {},
) {
    val preview = state.preview
    when {
        state.isLoading -> ImportLoading(modifier)
        preview != null -> ImportPreviewContent(
            state = state,
            preview = preview,
            onConfirm = onConfirm,
            onRetryEncoding = onRetryEncoding,
            onCancel = onCancel,
            onPickImageDirectory = onPickImageDirectory,
            modifier = modifier,
        )

        else -> ImportErrorContent(
            state = state,
            onRetryEncoding = onRetryEncoding,
            onCancel = onCancel,
            modifier = modifier,
        )
    }
}

@Composable
private fun ImportLoading(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.import_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportErrorContent(
    state: TxtImportUiState,
    onRetryEncoding: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.import_failed_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.errorMessage ?: stringResource(R.string.import_failed_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        if (state.availableEncodings.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.import_encoding_retry),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            EncodingButtons(
                encodings = state.availableEncodings,
                onRetryEncoding = onRetryEncoding,
            )
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.import_cancel))
        }
        Spacer(Modifier.height(12.dp))
        ScreenNote(text = stringResource(R.string.import_error_retry_hint))
    }
}

@Composable
private fun ImportPreviewContent(
    state: TxtImportUiState,
    preview: TxtImportPreview,
    onConfirm: (String) -> Unit,
    onRetryEncoding: (String) -> Unit,
    onCancel: () -> Unit,
    onPickImageDirectory: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var title by rememberSaveable(preview.contentHash) { mutableStateOf(preview.defaultTitle) }
    var showEncodingChooser by rememberSaveable(preview.contentHash) { mutableStateOf(false) }
    val titleError = title.isBlank()
    val isEpub = preview.format == BookFormat.EPUB
    val isMarkdown = preview.format == BookFormat.MARKDOWN
    val isStructured = isEpub || isMarkdown
    val bomLabel = stringResource(R.string.import_encoding_bom)
    val encodingText = when {
        isEpub -> "EPUB"
        isMarkdown -> "Markdown · UTF-8"
        preview.hadBom -> "${preview.encoding} · $bomLabel"
        else -> preview.encoding
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "title") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.import_book_title)) },
                    singleLine = true,
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text(stringResource(R.string.import_title_required)) }
                    } else {
                        null
                    },
                )
            }
            item(key = "metadata") {
                AppSectionCard {
                    PreviewValueRow(
                        label = stringResource(R.string.import_source_label),
                        value = preview.sourceName,
                    )
                    InsetDivider()
                    PreviewValueRow(
                        label = stringResource(R.string.import_file_size_label),
                        value = Formatter.formatShortFileSize(context, preview.originalSizeBytes),
                    )
                    InsetDivider()
                    PreviewValueRow(
                        label = stringResource(
                            if (isStructured) R.string.import_format_label
                            else R.string.import_encoding_label,
                        ),
                        value = encodingText,
                    )
                    if (preview.author != null) {
                        InsetDivider()
                        PreviewValueRow(
                            label = stringResource(R.string.import_author_label),
                            value = preview.author,
                        )
                    }
                }
                if (!isStructured && preview.encodingConfidence != EncodingConfidence.HIGH) {
                    Text(
                        text = stringResource(R.string.import_encoding_uncertain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (!isStructured) {
                    TextButton(onClick = { showEncodingChooser = !showEncodingChooser }) {
                        Text(stringResource(R.string.import_change_encoding))
                    }
                    if (showEncodingChooser) {
                        EncodingButtons(
                            encodings = preview.availableEncodings,
                            onRetryEncoding = onRetryEncoding,
                        )
                    }
                }
            }
            if (preview.duplicateBook != null) {
                item(key = "duplicate") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.import_duplicate_warning,
                                preview.duplicateBook.title,
                            ),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            item(key = "chapters") {
                AppSectionCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.import_chapter_count, preview.chapterCount),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (preview.chapters.isEmpty()) {
                            Text(
                                text = stringResource(R.string.import_no_chapters),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.import_chapters_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            preview.chapters.take(PREVIEW_CHAPTER_LIMIT).forEachIndexed { index, chapter ->
                                Text(
                                    text = "${index + 1}. ${chapter.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                            val remaining = preview.chapterCount - PREVIEW_CHAPTER_LIMIT
                            if (remaining > 0) {
                                Text(
                                    text = stringResource(R.string.import_more_chapters, remaining),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (isMarkdown && (preview.parsedMarkdown?.images?.isNotEmpty() == true)) {
                item(key = "markdown_images") {
                    AppSectionCard {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.import_markdown_images_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.import_markdown_images_stats,
                                    preview.markdownImageStats?.total
                                        ?: preview.parsedMarkdown?.images?.size
                                        ?: 0,
                                    preview.markdownImageStats?.accessible ?: 0,
                                    preview.markdownImageStats?.missing
                                        ?: preview.parsedMarkdown?.images?.size
                                        ?: 0,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onPickImageDirectory) {
                                Text(
                                    stringResource(
                                        if (preview.resourceDirectoryUri == null) {
                                            R.string.import_markdown_images_pick
                                        } else {
                                            R.string.import_markdown_images_change
                                        },
                                    ),
                                )
                            }
                            Text(
                                text = stringResource(R.string.import_markdown_images_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (preview.warnings.isNotEmpty()) {
                item(key = "warnings") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        preview.warnings.forEach { warning ->
                            Text(
                                text = "• $warning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (state.errorMessage != null) {
                item(key = "save_error") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Text(
                            text = state.errorMessage,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            item(key = "note") {
                ScreenNote(text = stringResource(R.string.import_note))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !state.isSaving,
            ) {
                Text(
                    stringResource(
                        if (preview.duplicateBook != null) {
                            R.string.import_skip_duplicate
                        } else {
                            R.string.import_cancel
                        },
                    ),
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onConfirm(title) },
                enabled = !state.isSaving && !titleError,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        stringResource(
                            if (preview.duplicateBook != null) {
                                R.string.import_import_anyway
                            } else {
                                R.string.import_confirm
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EncodingButtons(
    encodings: List<String>,
    onRetryEncoding: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        encodings.forEach { encoding ->
            OutlinedButton(onClick = { onRetryEncoding(encoding) }) {
                Text(encoding)
            }
        }
    }
}

@Composable
private fun PreviewValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(82.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val PREVIEW_CHAPTER_LIMIT = 8
