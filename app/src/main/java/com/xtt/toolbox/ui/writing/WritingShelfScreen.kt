package com.xtt.toolbox.ui.writing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.R
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.ShelfGroup
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.domain.reading.ShelfActivityOrdering
import com.xtt.toolbox.ui.components.AppActionMenu
import com.xtt.toolbox.ui.components.AppConfirmDialog
import com.xtt.toolbox.ui.components.AppIconButton
import com.xtt.toolbox.ui.components.DeleteIcon
import com.xtt.toolbox.ui.components.FolderIcon
import com.xtt.toolbox.ui.components.MoreVertIcon
import com.xtt.toolbox.ui.reading.BookCover
import com.xtt.toolbox.ui.shelf.BookOrganizationActionItems
import com.xtt.toolbox.ui.shelf.HiddenBadge
import com.xtt.toolbox.ui.shelf.ShelfGroupDeleteDialog
import com.xtt.toolbox.ui.shelf.ShelfGroupDetailDialog
import com.xtt.toolbox.ui.shelf.ShelfGroupNameDialog
import com.xtt.toolbox.ui.shelf.ShelfGroupPickerDialog
import com.xtt.toolbox.ui.shelf.ShelfGroupRow

/** 写作书架首页；分组与未分组作品共同显示。 */
@Composable
fun WritingShelfScreen(
    books: List<Book>,
    coverPaths: Map<String, String>,
    onOpenBook: (Book) -> Unit,
    onDeleteBook: (Book) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<ShelfGroup> = emptyList(),
    showHiddenContent: Boolean = true,
    onCreateGroup: (String) -> Unit = {},
    onRenameGroup: (ShelfGroup, String) -> Unit = { _, _ -> },
    onDeleteGroup: (ShelfGroup) -> Unit = {},
    onToggleGroupHidden: (ShelfGroup, Boolean) -> Unit = { _, _ -> },
    onAssignBookGroup: (Book, String?) -> Unit = { _, _ -> },
    onToggleBookHidden: (Book, Boolean) -> Unit = { _, _ -> },
) {
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    var groupPickerBook by remember { mutableStateOf<Book?>(null) }
    var createGroupVisible by remember { mutableStateOf(false) }
    var renamingGroup by remember { mutableStateOf<ShelfGroup?>(null) }
    var deletingGroup by remember { mutableStateOf<ShelfGroup?>(null) }
    var openedGroup by remember { mutableStateOf<ShelfGroup?>(null) }

    val visibleGroups = groups
        .filter { it.shelfType == ShelfType.WRITING }
        .filter { showHiddenContent || !it.isHidden }
    val allGroupIds = groups.map { it.groupId }.toSet()
    val orderedBooks = ShelfActivityOrdering.orderedBooks(books)
    val visibleBooks = orderedBooks.filter { showHiddenContent || !it.isHidden }
    val ungroupedBooks = visibleBooks.filter { it.groupId == null || it.groupId !in allGroupIds }
    val groupBooks = visibleGroups.associateWith { group ->
        ShelfActivityOrdering.orderedBooks(
            visibleBooks.filter { it.groupId == group.groupId },
        )
    }
    val entries = buildList {
        visibleGroups.forEach { group ->
            add(WritingEntry.GroupItem(group, groupBooks[group].orEmpty()))
        }
        ungroupedBooks.forEach { add(WritingEntry.BookItem(it)) }
    }.sortedByDescending { entry ->
        when (entry) {
            is WritingEntry.GroupItem ->
                ShelfActivityOrdering.groupActivity(entry.group.groupId, books)
            is WritingEntry.BookItem -> ShelfActivityOrdering.bookActivity(entry.book)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "writing_shelf_group_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("分组", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                AppIconButton(
                    contentDescription = "新建分组",
                    onClick = { createGroupVisible = true },
                ) {
                    FolderIcon()
                }
            }
        }
        items(entries, key = { entry ->
            when (entry) {
                is WritingEntry.GroupItem -> "group-${entry.group.groupId}"
                is WritingEntry.BookItem -> "book-${entry.book.bookId}"
            }
        }) { entry ->
            when (entry) {
                is WritingEntry.GroupItem -> ShelfGroupRow(
                    group = entry.group,
                    books = entry.books,
                    coverPaths = coverPaths,
                    onOpen = { openedGroup = entry.group },
                    onRename = { renamingGroup = entry.group },
                    onToggleHidden = { onToggleGroupHidden(entry.group, !entry.group.isHidden) },
                    onDelete = { deletingGroup = entry.group },
                )

                is WritingEntry.BookItem -> WritingBookRow(
                    book = entry.book,
                    coverPath = coverPaths[entry.book.bookId],
                    groups = visibleGroups,
                    onClick = { onOpenBook(entry.book) },
                    onPickGroup = { groupPickerBook = entry.book },
                    onClearGroup = { onAssignBookGroup(entry.book, null) },
                    onToggleHidden = { onToggleBookHidden(entry.book, !entry.book.isHidden) },
                    onDelete = { pendingDelete = entry.book },
                )
            }
        }
    }

    pendingDelete?.let { book ->
        AppConfirmDialog(
            title = stringResource(R.string.delete_writing_title),
            message = stringResource(R.string.delete_writing_message, book.title),
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

    if (createGroupVisible) {
        ShelfGroupNameDialog(
            title = "新建分组",
            initial = "",
            onDismiss = { createGroupVisible = false },
            onConfirm = { name ->
                createGroupVisible = false
                onCreateGroup(name)
            },
        )
    }

    renamingGroup?.let { group ->
        ShelfGroupNameDialog(
            title = "重命名分组",
            initial = group.name,
            onDismiss = { renamingGroup = null },
            onConfirm = { name ->
                renamingGroup = null
                onRenameGroup(group, name)
            },
        )
    }

    deletingGroup?.let { group ->
        ShelfGroupDeleteDialog(
            group = group,
            onDismiss = { deletingGroup = null },
            onConfirm = {
                deletingGroup = null
                onDeleteGroup(group)
            },
        )
    }

    groupPickerBook?.let { book ->
        ShelfGroupPickerDialog(
            bookTitle = book.title,
            groups = visibleGroups,
            currentGroupId = book.groupId,
            onDismiss = { groupPickerBook = null },
            onSelect = { groupId ->
                groupPickerBook = null
                onAssignBookGroup(book, groupId)
            },
        )
    }

    openedGroup?.let { group ->
        ShelfGroupDetailDialog(
            group = group,
            books = groupBooks[group].orEmpty(),
            coverPaths = coverPaths,
            onOpenBook = { book ->
                openedGroup = null
                onOpenBook(book)
            },
            groups = visibleGroups,
            onPickGroup = { book ->
                openedGroup = null
                groupPickerBook = book
            },
            onClearGroup = { book ->
                openedGroup = null
                onAssignBookGroup(book, null)
            },
            onToggleBookHidden = { book ->
                onToggleBookHidden(book, !book.isHidden)
            },
            onDeleteBook = { book ->
                openedGroup = null
                pendingDelete = book
            },
            onDismiss = { openedGroup = null },
        )
    }
}

private sealed interface WritingEntry {
    data class GroupItem(val group: ShelfGroup, val books: List<Book>) : WritingEntry
    data class BookItem(val book: Book) : WritingEntry
}

@Composable
private fun WritingBookRow(
    book: Book,
    coverPath: String?,
    groups: List<ShelfGroup>,
    onClick: () -> Unit,
    onPickGroup: () -> Unit,
    onClearGroup: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(book.bookId) { mutableStateOf(false) }
    val deleteDescription = stringResource(R.string.delete_book_action, book.title)
    Box {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(
                    coverPath = coverPath,
                    modifier = Modifier.size(44.dp, 58.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = book.title,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (book.isHidden) {
                            Spacer(Modifier.width(4.dp))
                            HiddenBadge()
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.format_draft),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "写作作品",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    MoreVertIcon(Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = deleteDescription },
                ) {
                    DeleteIcon(
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AppActionMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            BookOrganizationActionItems(
                book = book,
                groups = groups,
                onPickGroup = {
                    menuExpanded = false
                    onPickGroup()
                },
                onClearGroup = {
                    menuExpanded = false
                    onClearGroup()
                },
                onToggleHidden = {
                    menuExpanded = false
                    onToggleHidden()
                },
                onDelete = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}
