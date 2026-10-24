package com.xtt.toolbox.ui.writing

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.R
import com.xtt.toolbox.data.model.BookVolume
import com.xtt.toolbox.data.model.Chapter
import com.xtt.toolbox.domain.writing.WritingChapterNaming
import com.xtt.toolbox.domain.writing.WritingDetail
import com.xtt.toolbox.domain.writing.WritingTextStats
import com.xtt.toolbox.ui.components.AppActionMenu
import com.xtt.toolbox.ui.components.AppActionMenuItem
import com.xtt.toolbox.ui.components.AppConfirmDialog
import com.xtt.toolbox.ui.components.BackArrowIcon
import com.xtt.toolbox.ui.components.BookIcon
import com.xtt.toolbox.ui.components.ChevronRightIcon
import com.xtt.toolbox.ui.components.DeleteIcon
import com.xtt.toolbox.ui.components.ExportIcon
import com.xtt.toolbox.ui.components.FileIcon
import com.xtt.toolbox.ui.components.PenIcon
import com.xtt.toolbox.ui.components.PlusIcon
import com.xtt.toolbox.ui.reading.BookCover
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WritingDetailTopBar(
    title: String,
    onBack: () -> Unit,
    onExportTxt: () -> Unit,
    onExportEpub: () -> Unit,
) {
    var exportMenuExpanded by remember { mutableStateOf(false) }
    val backDescription = stringResource(R.string.back)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics {
                        contentDescription = backDescription
                    },
                ) {
                    BackArrowIcon()
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(
                        onClick = { exportMenuExpanded = true },
                        modifier = Modifier.semantics {
                            contentDescription = "导出作品"
                        },
                    ) {
                        ExportIcon(Modifier.size(22.dp))
                    }
                    DropdownMenu(
                        expanded = exportMenuExpanded,
                        onDismissRequest = { exportMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("导出 TXT") },
                            onClick = {
                                exportMenuExpanded = false
                                onExportTxt()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("导出 EPUB") },
                            onClick = {
                                exportMenuExpanded = false
                                onExportEpub()
                            },
                        )
                    }
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
fun WritingDetailScreen(
    detail: WritingDetail,
    coverPath: String?,
    onPickCover: () -> Unit,
    onOpenChapter: (Chapter) -> Unit,
    onCreateVolume: (String) -> Unit,
    onRenameVolume: (String, String) -> Unit,
    onDeleteVolume: (String) -> Unit,
    onCreateChapter: (String, String?) -> Unit,
    onRenameChapter: (String, String) -> Unit,
    onDeleteChapter: (String) -> Unit,
    onMoveChapter: (String, String?) -> Unit,
    onMoveChapterUp: (String) -> Unit,
    onMoveChapterDown: (String) -> Unit,
    excludePunctuation: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showAddMenu by remember { mutableStateOf(false) }
    var showCreateVolume by remember { mutableStateOf(false) }
    var showCreateChapter by remember { mutableStateOf(false) }
    var renameVolume by remember { mutableStateOf<BookVolume?>(null) }
    var deleteVolume by remember { mutableStateOf<BookVolume?>(null) }
    var renameChapter by remember { mutableStateOf<Chapter?>(null) }
    var deleteChapter by remember { mutableStateOf<Chapter?>(null) }
    var moveChapter by remember { mutableStateOf<Chapter?>(null) }
    var collapsedVolumes by remember { mutableStateOf<Set<String>>(emptySet()) }

    val document = detail.document
    val counts = WritingTextStats.chapterCounts(document, excludePunctuation)
    val totalCount = WritingTextStats.totalCount(document, excludePunctuation)
    val chapterCountsByVolume = document.document.chapters
        .groupingBy { it.volumeId }
        .eachCount()
    val unassigned = document.document.chapters.filter { it.volumeId == null }
    val inputDialogVisible = showCreateChapter || showCreateVolume ||
        renameChapter != null || renameVolume != null ||
        deleteChapter != null || deleteVolume != null || moveChapter != null

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            item(key = "info") {
                WritingInfoHeader(
                    detail = detail,
                    coverPath = coverPath,
                    totalCount = totalCount,
                    onPickCover = onPickCover,
                )
                HorizontalDivider(
                    Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
            item(key = "directory_header") {
                Text("目录", style = MaterialTheme.typography.titleMedium)
            }
            detail.volumes.sortedBy { it.order }.forEach { volume ->
                val chapters = document.document.chapters.filter { it.volumeId == volume.volumeId }
                val collapsed = volume.volumeId in collapsedVolumes
                item(key = "volume-${volume.volumeId}") {
                    VolumeHeader(
                        volume = volume,
                        collapsed = collapsed,
                        onToggle = {
                            collapsedVolumes = if (collapsed) {
                                collapsedVolumes - volume.volumeId
                            } else {
                                collapsedVolumes + volume.volumeId
                            }
                        },
                        onRename = { renameVolume = volume },
                        onDelete = { deleteVolume = volume },
                    )
                }
                if (!collapsed) {
                    if (chapters.isEmpty()) {
                        item(key = "volume-empty-${volume.volumeId}") {
                            Text(
                                "暂无章节",
                                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(chapters, key = { "chapter-${it.chapterId}" }) { chapter ->
                            ChapterRow(
                                chapter = chapter,
                                wordCount = counts[chapter.chapterId] ?: 0,
                                onClick = { onOpenChapter(chapter) },
                                onRename = { renameChapter = chapter },
                                onMove = { moveChapter = chapter },
                                onMoveUp = { onMoveChapterUp(chapter.chapterId) },
                                onMoveDown = { onMoveChapterDown(chapter.chapterId) },
                                onDelete = { deleteChapter = chapter },
                            )
                        }
                    }
                }
            }
            if (unassigned.isNotEmpty()) {
                item(key = "unassigned-header") {
                    Text(
                        "未分卷",
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(unassigned, key = { "chapter-${it.chapterId}" }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        wordCount = counts[chapter.chapterId] ?: 0,
                        onClick = { onOpenChapter(chapter) },
                        onRename = { renameChapter = chapter },
                        onMove = { moveChapter = chapter },
                        onMoveUp = { onMoveChapterUp(chapter.chapterId) },
                        onMoveDown = { onMoveChapterDown(chapter.chapterId) },
                        onDelete = { deleteChapter = chapter },
                    )
                }
            }
        }

        if (!inputDialogVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                FloatingActionButton(
                    onClick = { showAddMenu = true },
                    modifier = Modifier.semantics { contentDescription = "添加章节或分卷" },
                ) {
                    PlusIcon(Modifier.size(24.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                }
                AppActionMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false },
                ) {
                    AppActionMenuItem(
                        text = "新建章节",
                        onClick = {
                            showAddMenu = false
                            showCreateChapter = true
                        },
                        icon = { PlusIcon(Modifier.size(18.dp)) },
                    )
                    AppActionMenuItem(
                        text = "新建分卷",
                        onClick = {
                            showAddMenu = false
                            showCreateVolume = true
                        },
                        icon = { BookIcon(Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }

    if (showCreateVolume) {
        NameDialog(
            title = "新建分卷",
            label = "分卷名称",
            onDismiss = { showCreateVolume = false },
            onConfirm = { showCreateVolume = false; onCreateVolume(it) },
        )
    }
    if (showCreateChapter) {
        CreateChapterDialog(
            volumes = detail.volumes,
            chapterCounts = chapterCountsByVolume,
            onDismiss = { showCreateChapter = false },
            onConfirm = { title, volumeId ->
                showCreateChapter = false
                onCreateChapter(title, volumeId)
            },
        )
    }
    renameVolume?.let { volume ->
        NameDialog(
            title = "重命名分卷",
            label = "分卷名称",
            initial = volume.title,
            onDismiss = { renameVolume = null },
            onConfirm = { renameVolume = null; onRenameVolume(volume.volumeId, it) },
        )
    }
    deleteVolume?.let { volume ->
        AppConfirmDialog(
            title = "删除分卷",
            message = "删除「${volume.title}」后，其下章节会转为未分卷，正文不会删除。",
            confirmLabel = "删除分卷",
            onConfirm = { deleteVolume = null; onDeleteVolume(volume.volumeId) },
            onDismiss = { deleteVolume = null },
            dismissLabel = "取消",
            destructive = true,
        )
    }
    renameChapter?.let { chapter ->
        NameDialog(
            title = "重命名章节",
            label = "章节名称",
            initial = chapter.title,
            onDismiss = { renameChapter = null },
            onConfirm = { renameChapter = null; onRenameChapter(chapter.chapterId, it) },
        )
    }
    deleteChapter?.let { chapter ->
        AppConfirmDialog(
            title = "删除章节",
            message = "确定删除「${chapter.title.ifBlank { "未命名章节" }}」及其正文吗？此操作不可撤销。",
            confirmLabel = "删除章节",
            onConfirm = { deleteChapter = null; onDeleteChapter(chapter.chapterId) },
            onDismiss = { deleteChapter = null },
            dismissLabel = "取消",
            destructive = true,
        )
    }
    moveChapter?.let { chapter ->
        ChoiceDialog(
            title = "移动到分卷",
            choices = buildList {
                add("未分卷" to { moveChapter = null; onMoveChapter(chapter.chapterId, null) })
                detail.volumes.sortedBy { it.order }.forEach { volume ->
                    add(volume.title to {
                        moveChapter = null
                        onMoveChapter(chapter.chapterId, volume.volumeId)
                    })
                }
            },
            onDismiss = { moveChapter = null },
        )
    }
}

@Composable
private fun WritingInfoHeader(
    detail: WritingDetail,
    coverPath: String?,
    totalCount: Int,
    onPickCover: () -> Unit,
) {
    val textStyle = MaterialTheme.typography.bodyLarge
    val coverDescription = stringResource(R.string.writing_change_cover)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box {
            BookCover(
                coverPath = coverPath,
                modifier = Modifier.size(112.dp),
                iconSize = 36.dp,
            )
            IconButton(
                onClick = onPickCover,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .semantics { contentDescription = coverDescription },
            ) {
                PenIcon(
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = detail.book.title,
                style = textStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "创建于 ${formatDate(detail.book.createdAt)}",
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${detail.document.document.chapters.size} 章",
                style = textStyle,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "总字数 $totalCount",
                style = textStyle,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VolumeHeader(
    volume: BookVolume,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(volume.volumeId) { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClick = onToggle,
                    onLongClick = { menuExpanded = true },
                )
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = volume.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            ChevronRightIcon(
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (collapsed) 0f else 90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppActionMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            AppActionMenuItem(
                text = "重命名分卷",
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            AppActionMenuItem(
                text = "删除分卷",
                danger = true,
                icon = { DeleteIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.error) },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: Chapter,
    wordCount: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(chapter.chapterId) { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookIcon(Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = chapter.title.ifBlank { "未命名章节" },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "$wordCount 字",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppActionMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            AppActionMenuItem(text = "重命名", onClick = { menuExpanded = false; onRename() })
            AppActionMenuItem(text = "上移", onClick = { menuExpanded = false; onMoveUp() })
            AppActionMenuItem(text = "下移", onClick = { menuExpanded = false; onMoveDown() })
            AppActionMenuItem(text = "移动到分卷", onClick = { menuExpanded = false; onMove() })
            AppActionMenuItem(
                text = "删除章节",
                danger = true,
                icon = { DeleteIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.error) },
                onClick = { menuExpanded = false; onDelete() },
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text(label) },
                    singleLine = true,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (value.trim().isEmpty()) error = "名称不能为空" else onConfirm(value.trim())
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CreateChapterDialog(
    volumes: List<BookVolume>,
    chapterCounts: Map<String?, Int>,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var title by remember {
        mutableStateOf(WritingChapterNaming.defaultTitle(chapterCounts[null] ?: 0))
    }
    var titleEdited by remember { mutableStateOf(false) }
    var volumeId by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun selectVolume(nextVolumeId: String?) {
        volumeId = nextVolumeId
        expanded = false
        if (!titleEdited) {
            title = WritingChapterNaming.defaultTitle(chapterCounts[nextVolumeId] ?: 0)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建章节") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleEdited = true
                        error = null
                    },
                    label = { Text("章节名称") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text("所属分卷：${volumes.firstOrNull { it.volumeId == volumeId }?.title ?: "未分卷"}")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("未分卷") },
                            onClick = { selectVolume(null) },
                        )
                        volumes.sortedBy { it.order }.forEach { volume ->
                            DropdownMenuItem(
                                text = { Text(volume.title) },
                                onClick = { selectVolume(volume.volumeId) },
                            )
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.trim().isEmpty()) error = "章节名称不能为空"
                else onConfirm(title.trim(), volumeId)
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { (label, action) ->
                    TextButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMillis))
