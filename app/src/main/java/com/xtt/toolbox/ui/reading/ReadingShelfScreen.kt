package com.xtt.toolbox.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.R
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.domain.reading.ReadingShelfOrdering
import com.xtt.toolbox.ui.components.AppActionMenu
import com.xtt.toolbox.ui.components.AppActionMenuItem
import com.xtt.toolbox.ui.components.AppConfirmDialog
import com.xtt.toolbox.ui.components.AppIconButton
import com.xtt.toolbox.ui.components.BookIcon
import com.xtt.toolbox.ui.components.DeleteIcon
import com.xtt.toolbox.ui.components.EmptyStateCard
import com.xtt.toolbox.ui.components.ImportIcon
import com.xtt.toolbox.ui.components.ScreenNote
import com.xtt.toolbox.ui.components.SectionHeader
import com.xtt.toolbox.ui.components.ViewModeToggle
import kotlin.math.roundToInt

@Composable
fun ReadingShelfScreen(
    books: List<Book>,
    onImport: () -> Unit,
    onOpenBook: (Book) -> Unit,
    modifier: Modifier = Modifier,
    coverPaths: Map<String, String> = emptyMap(),
    onDeleteBook: (Book) -> Unit = {},
    importInProgress: Boolean = false,
) {
    var isGridView by rememberSaveable { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    val orderedBooks = ReadingShelfOrdering.ordered(books)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "bookshelf_header") {
            SectionHeader(
                title = stringResource(R.string.my_bookshelf),
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AppIconButton(
                            contentDescription = stringResource(R.string.import_book),
                            onClick = onImport,
                            enabled = !importInProgress,
                        ) {
                            ImportIcon()
                        }
                        ViewModeToggle(
                            isGrid = isGridView,
                            onModeChange = { isGridView = it },
                        )
                    }
                },
            )
        }
        if (orderedBooks.isEmpty()) {
            item(key = "empty_bookshelf") {
                EmptyStateCard(
                    icon = { BookIcon(tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.empty_reading_title),
                    message = stringResource(R.string.empty_reading_desc),
                )
            }
        } else if (isGridView) {
            item(key = "bookshelf_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    orderedBooks.chunked(2).forEach { rowBooks ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowBooks.forEach { book ->
                                BookTile(
                                    book = book,
                                    coverPath = coverPaths[book.bookId],
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenBook(book) },
                                    onDelete = { pendingDelete = book },
                                )
                            }
                            if (rowBooks.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            items(orderedBooks, key = { it.bookId }) { book ->
                BookRow(
                    book = book,
                    coverPath = coverPaths[book.bookId],
                    onClick = { onOpenBook(book) },
                    onDelete = { pendingDelete = book },
                )
            }
        }
        item(key = "reading_note") {
            ScreenNote(text = stringResource(R.string.reading_note))
        }
    }

    pendingDelete?.let { book ->
        AppConfirmDialog(
            title = stringResource(R.string.delete_book_title),
            message = stringResource(R.string.delete_book_message, book.title),
            confirmLabel = stringResource(R.string.delete_book_confirm),
            dismissLabel = stringResource(R.string.delete_book_cancel),
            destructive = true,
            onConfirm = {
                pendingDelete = null
                onDeleteBook(book)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun BookTile(
    book: Book,
    coverPath: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(book.bookId) { mutableStateOf(false) }
    val longClickLabel = stringResource(R.string.book_actions)

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClickLabel = stringResource(R.string.continue_reading),
                    onLongClickLabel = longClickLabel,
                    role = Role.Button,
                    onLongClick = { menuExpanded = true },
                    onClick = onClick,
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
        ) {
            Column(Modifier.padding(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    BookCover(
                        coverPath = coverPath,
                        modifier = Modifier.fillMaxSize(),
                        iconSize = 36.dp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sourceLabel(book),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = progressLabel(book),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AppActionMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            AppActionMenuItem(
                text = stringResource(R.string.delete_book_confirm),
                danger = true,
                icon = {
                    DeleteIcon(
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
internal fun BookRow(
    book: Book,
    coverPath: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean = true,
) {
    val deleteDescription = stringResource(R.string.delete_book_action, book.title)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                coverPath = coverPath,
                modifier = Modifier.size(44.dp, 58.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sourceLabel(book),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = progressLabel(book),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.continue_reading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (showDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = deleteDescription },
                ) {
                    DeleteIcon(
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun progress(book: Book): Float =
    (book.readingPosition?.percent ?: 0f).coerceIn(0f, 1f)

@Composable
private fun progressLabel(book: Book): String {
    val percent = (progress(book) * 100).roundToInt()
    return stringResource(R.string.book_progress, percent)
}

@Composable
private fun sourceLabel(book: Book): String = when {
    book.format == BookFormat.TXT && book.sourceType == SourceType.IMPORTED ->
        stringResource(R.string.book_source_txt_imported)

    book.format == BookFormat.TXT && book.sourceType == SourceType.CREATED ->
        stringResource(R.string.book_source_txt_created)

    book.format == BookFormat.DRAFT -> stringResource(R.string.format_draft)

    else -> "${book.format.name} · ${book.sourceType.name}"
}