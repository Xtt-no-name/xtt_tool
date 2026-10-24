package com.xtt.toolbox.ui.writing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.ui.components.BackArrowIcon
import com.xtt.toolbox.ui.components.ChevronRightIcon
import com.xtt.toolbox.ui.components.DeleteIcon
import com.xtt.toolbox.ui.components.PlusIcon

@Composable
fun WritingEditorSettingsScreen(
    showFormatBar: Boolean,
    showQuickBar: Boolean,
    autoPairEnabled: Boolean,
    excludePunctuation: Boolean,
    punctuation: List<String>,
    onBack: () -> Unit,
    onShowFormatBarChange: (Boolean) -> Unit,
    onShowQuickBarChange: (Boolean) -> Unit,
    onAutoPairChange: (Boolean) -> Unit,
    onExcludePunctuationChange: (Boolean) -> Unit,
    onAddPunctuation: (String) -> Unit,
    onRemovePunctuation: (Int) -> Unit,
    onMovePunctuation: (Int, Int) -> Unit,
    onResetPunctuation: () -> Unit,
) {
    var showPunctuationSettings by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (showPunctuationSettings) {
            showPunctuationSettings = false
        } else {
            onBack()
        }
    }

    if (showPunctuationSettings) {
        WritingPunctuationSettingsScreen(
            punctuation = punctuation,
            onBack = { showPunctuationSettings = false },
            onAddPunctuation = onAddPunctuation,
            onRemovePunctuation = onRemovePunctuation,
            onMovePunctuation = onMovePunctuation,
            onResetPunctuation = onResetPunctuation,
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
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
                Text("编辑器设置", style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                item(key = "switches") {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        SettingSwitchRow(
                            title = "显示基础格式栏",
                            checked = showFormatBar,
                            onCheckedChange = onShowFormatBarChange,
                        )
                        SettingSwitchRow(
                            title = "显示快捷栏",
                            checked = showQuickBar,
                            onCheckedChange = onShowQuickBarChange,
                        )
                        SettingSwitchRow(
                            title = "自动补齐成对标点",
                            description = "输入左侧标点后自动添加右侧标点；再次输入右侧标点会跳过已有闭合标点。",
                            checked = autoPairEnabled,
                            onCheckedChange = onAutoPairChange,
                        )
                        SettingSwitchRow(
                            title = "字数统计不计标点符号",
                            description = "开启后，所有字数统计都不把标点符号计入。",
                            checked = excludePunctuation,
                            onCheckedChange = onExcludePunctuationChange,
                        )
                    }
                }
                item(key = "punctuation-entry") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClick = { showPunctuationSettings = true },
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("常用标点设置", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "编辑快捷栏左侧显示的常用标点、顺序和恢复默认列表",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ChevronRightIcon()
                    }
                }
            }
        }
    }
}

@Composable
private fun WritingPunctuationSettingsScreen(
    punctuation: List<String>,
    onBack: () -> Unit,
    onAddPunctuation: (String) -> Unit,
    onRemovePunctuation: (Int) -> Unit,
    onMovePunctuation: (Int, Int) -> Unit,
    onResetPunctuation: () -> Unit,
) {
    var newPunctuation by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "返回编辑器设置" },
                ) { BackArrowIcon() }
                Text("常用标点设置", style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                item(key = "punctuation-header") {
                    Text(
                        text = "按此顺序显示在快捷栏左侧；支持多字符标点。",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "punctuation-add") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = newPunctuation,
                            onValueChange = {
                                newPunctuation = it
                                inputError = null
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("添加标点或字符") },
                            singleLine = true,
                            isError = inputError != null,
                            supportingText = inputError?.let { error -> { Text(error) } },
                        )
                        IconButton(
                            onClick = {
                                val value = newPunctuation.trim()
                                when {
                                    value.isEmpty() -> inputError = "不能为空"
                                    punctuation.contains(value) -> inputError = "该标点已存在"
                                    else -> {
                                        onAddPunctuation(value)
                                        newPunctuation = ""
                                    }
                                }
                            },
                            modifier = Modifier.semantics { contentDescription = "添加标点" },
                        ) { PlusIcon() }
                    }
                }
                if (punctuation.isEmpty()) {
                    item(key = "punctuation-empty") {
                        Text(
                            text = "暂无常用标点；快捷栏左侧会保留空白区域。",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    itemsIndexed(
                        items = punctuation,
                        key = { _, item -> item },
                    ) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = { onMovePunctuation(index, -1) },
                                enabled = index > 0,
                                modifier = Modifier.semantics {
                                    contentDescription = "上移标点 $item"
                                },
                            ) {
                                ChevronRightIcon(
                                    modifier = Modifier.size(18.dp).rotate(-90f),
                                )
                            }
                            IconButton(
                                onClick = { onMovePunctuation(index, 1) },
                                enabled = index < punctuation.lastIndex,
                                modifier = Modifier.semantics {
                                    contentDescription = "下移标点 $item"
                                },
                            ) {
                                ChevronRightIcon(
                                    modifier = Modifier.size(18.dp).rotate(90f),
                                )
                            }
                            IconButton(
                                onClick = { onRemovePunctuation(index) },
                                modifier = Modifier.semantics {
                                    contentDescription = "删除标点 $item"
                                },
                            ) {
                                DeleteIcon(
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                item(key = "punctuation-reset") {
                    TextButton(
                        onClick = onResetPunctuation,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text("恢复默认列表")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
