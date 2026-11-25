package com.xtt.toolbox.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.ShelfGroup
import com.xtt.toolbox.ui.components.AppActionMenu
import com.xtt.toolbox.ui.components.AppActionMenuItem
import com.xtt.toolbox.ui.components.AppConfirmDialog
import com.xtt.toolbox.ui.components.CloseIcon
import com.xtt.toolbox.ui.components.DeleteIcon
import com.xtt.toolbox.ui.components.FolderIcon
import com.xtt.toolbox.ui.components.MoreVertIcon
import com.xtt.toolbox.ui.reading.BookCover

/** 动态分组封面：按传入顺序取前四个作品，不保存额外图片。 */
@Composable
fun ShelfGroupCover(
    books: List<Book>,
    coverPaths: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val covers = books.take(4).map { coverPaths[it.bookId] }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        when (covers.size) {
            0 -> FolderIcon(
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            1 -> BookCover(
                coverPath = covers[0],
                modifier = Modifier.fillMaxSize(),
                iconSize = 30.dp,
            )

            2 -> Row(Modifier.fillMaxSize()) {
                covers.forEach { path ->
                    BookCover(
                        coverPath = path,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        iconSize = 24.dp,
                    )
                }
            }

            else -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    covers.take(2).forEach { path ->
                        BookCover(
                            coverPath = path,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            iconSize = 22.dp,
                        )
                    }
                }
                Row(Modifier.weight(1f)) {
                    covers.drop(2).forEach { path ->
                        BookCover(
                            coverPath = path,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            iconSize = 22.dp,
                        )
                    }
                    if (covers.size == 3) {
                        Spacer(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HiddenBadge(modifier: Modifier = Modifier) {
    Text(
        text = "已隐藏",
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

@Composable
fun GroupActionItems(
    group: ShelfGroup,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
) {
    AppActionMenuItem(text = "重命名分组", onClick = onRename)
    AppActionMenuItem(
        text = if (group.isHidden) "取消隐藏分组" else "隐藏分组",
        onClick = onToggleHidden,
    )
    AppActionMenuItem(
        text = "删除分组",
        danger = true,
        icon = { DeleteIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.error) },
        onClick = onDelete,
    )
}

@Composable
fun BookOrganizationActionItems(
    book: Book,
    groups: List<ShelfGroup>,
    onPickGroup: () -> Unit,
    onClearGroup: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
) {
    AppActionMenuItem(
        text = if (groups.isEmpty()) "暂无分组" else "移动到分组",
        onClick = onPickGroup,
        icon = { FolderIcon(Modifier.size(18.dp)) },
    )
    if (book.groupId != null) {
        AppActionMenuItem(
            text = "移出分组",
            onClick = onClearGroup,
        )
    }
    AppActionMenuItem(
        text = if (book.isHidden) "取消隐藏作品" else "隐藏作品",
        onClick = onToggleHidden,
    )
    AppActionMenuItem(
        text = "删除",
        danger = true,
        icon = { DeleteIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.error) },
        onClick = onDelete,
    )
}

@Composable
fun ShelfGroupGridTile(
    group: ShelfGroup,
    books: List<Book>,
    coverPaths: Map<String, String>,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(group.groupId) { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
    ) {
        Column(Modifier.padding(10.dp)) {
            ShelfGroupCover(
                books = books,
                coverPaths = coverPaths,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
            )
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.name,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                        )
                        if (group.isHidden) {
                            Spacer(Modifier.width(4.dp))
                            HiddenBadge()
                        }
                    }
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = "${books.size} 部作品",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        MoreVertIcon(Modifier.size(18.dp))
                    }
                    AppActionMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        GroupActionItems(
                            group = group,
                            onRename = { menuExpanded = false; onRename() },
                            onToggleHidden = { menuExpanded = false; onToggleHidden() },
                            onDelete = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShelfGroupRow(
    group: ShelfGroup,
    books: List<Book>,
    coverPaths: Map<String, String>,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(group.groupId) { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShelfGroupCover(
                books = books,
                coverPaths = coverPaths,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    if (group.isHidden) {
                        Spacer(Modifier.width(6.dp))
                        HiddenBadge()
                    }
                }
                Spacer(Modifier.size(3.dp))
                Text(
                    text = "${books.size} 部作品 · 分组",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    MoreVertIcon(Modifier.size(20.dp))
                }
                AppActionMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    GroupActionItems(
                        group = group,
                        onRename = { menuExpanded = false; onRename() },
                        onToggleHidden = { menuExpanded = false; onToggleHidden() },
                        onDelete = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
fun ShelfGroupNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("分组名称") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value.trim()) },
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
fun ShelfGroupPickerDialog(
    bookTitle: String,
    groups: List<ShelfGroup>,
    currentGroupId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动《$bookTitle》") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    GroupChoiceRow(
                        label = "未分组",
                        selected = currentGroupId == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(groups, key = { it.groupId }) { group ->
                    GroupChoiceRow(
                        label = group.name,
                        selected = currentGroupId == group.groupId,
                        onClick = { onSelect(group.groupId) },
                    )
                }
                if (groups.isEmpty()) {
                    item {
                        Text(
                            text = "还没有分组，请先在书架顶部新建分组。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun GroupChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (selected) {
            Text("当前", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ShelfGroupDeleteDialog(
    group: ShelfGroup,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppConfirmDialog(
        title = "删除分组",
        message = "确定删除分组「${group.name}」吗？组内作品会保留并变为未分组，正文、阅读进度、书签和评论不会删除。",
        confirmLabel = "删除分组",
        dismissLabel = "取消",
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun ShelfGroupDetailDialog(
    group: ShelfGroup,
    books: List<Book>,
    coverPaths: Map<String, String>,
    groups: List<ShelfGroup> = emptyList(),
    onOpenBook: (Book) -> Unit,
    onPickGroup: (Book) -> Unit = {},
    onClearGroup: (Book) -> Unit = {},
    onToggleBookHidden: (Book) -> Unit = {},
    onDeleteBook: (Book) -> Unit = {},
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(group.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${books.size} 部作品",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        CloseIcon()
                    }
                }
                if (books.isEmpty()) {
                    Text(
                        text = "这个分组还没有作品。",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 20.dp,
                            vertical = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(books, key = { it.bookId }) { book ->
                            var menuExpanded by remember(book.bookId) { mutableStateOf(false) }
                            Card(
                                onClick = { onOpenBook(book) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    BookCover(
                                        coverPath = coverPaths[book.bookId],
                                        modifier = Modifier.size(38.dp, 50.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = book.title,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    if (book.isHidden) HiddenBadge()
                                    Box {
                                        IconButton(
                                            onClick = { menuExpanded = true },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            MoreVertIcon(Modifier.size(18.dp))
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
                                                    onPickGroup(book)
                                                },
                                                onClearGroup = {
                                                    menuExpanded = false
                                                    onClearGroup(book)
                                                },
                                                onToggleHidden = {
                                                    menuExpanded = false
                                                    onToggleBookHidden(book)
                                                },
                                                onDelete = {
                                                    menuExpanded = false
                                                    onDeleteBook(book)
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
        }
    }
}
