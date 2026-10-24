package com.xtt.toolbox.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.R
import com.xtt.toolbox.data.profile.AvatarImageState
import com.xtt.toolbox.data.profile.ProfileRules
import com.xtt.toolbox.data.update.UpdateCheckResult
import com.xtt.toolbox.data.update.UpdateChecker
import com.xtt.toolbox.domain.writing.WritingDailyStat
import com.xtt.toolbox.ui.components.AppSectionCard
import com.xtt.toolbox.ui.components.BackupIcon
import com.xtt.toolbox.ui.components.InfoIcon
import com.xtt.toolbox.ui.components.PenIcon
import com.xtt.toolbox.ui.components.SectionHeader
import com.xtt.toolbox.ui.components.TextLinesIcon
import com.xtt.toolbox.ui.reading.ExternalLinkLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    onShowMessage: (String) -> Unit,
    writingHistory: List<WritingDailyStat>,
    profileState: ProfileUiState,
    avatarState: AvatarImageState,
    onPickAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onSaveNickname: (String) -> Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "未知"
    }
    var selectedHeatmapDate by remember { mutableStateOf<String?>(null) }
    var editingNickname by remember { mutableStateOf(false) }
    var nicknameDraft by remember { mutableStateOf(profileState.nickname) }
    var nicknameError by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showUpdateCheck by remember { mutableStateOf(false) }
    val blankInteraction = remember { MutableInteractionSource() }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = blankInteraction,
                indication = null,
            ) {
                selectedHeatmapDate = null
            },
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "profile_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileAvatar(
                    avatarState = avatarState,
                    onClick = onPickAvatar,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profileState.nickname,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable {
                                nicknameDraft = profileState.nickname
                                nicknameError = null
                                editingNickname = true
                            },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.width(6.dp))
                    PenIcon(
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                nicknameDraft = profileState.nickname
                                nicknameError = null
                                editingNickname = true
                            },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "本地个人资料 · 不登录、不联网",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "点击头像更换 · 昵称用于应用内显示",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (profileState.avatarPath != null) {
                    TextButton(onClick = onClearAvatar) {
                        Text("恢复默认头像")
                    }
                }
                Spacer(Modifier.height(18.dp))
                WritingHeatmap(
                    stats = writingHistory,
                    selectedDate = selectedHeatmapDate,
                    onSelectedDateChange = { selectedHeatmapDate = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "settings") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "设置")
                AppSectionCard {
                    ProfileActionRow(
                        icon = { TextLinesIcon(Modifier.size(20.dp)) },
                        title = "外观与阅读设置",
                        description = "",
                        onClick = onOpenSettings,
                    )
                }
            }
        }
        item(key = "data_backup") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "数据管理")
                AppSectionCard {
                    ProfileActionRow(
                        icon = { BackupIcon(Modifier.size(20.dp)) },
                        title = "创建本地备份",
                        description = "导出作品、正文、图片、统计和个人资料",
                        onClick = onBackup,
                    )
                    ProfileActionRow(
                        icon = { BackupIcon(Modifier.size(20.dp)) },
                        title = "恢复本地备份",
                        description = "校验后恢复，恢复前会保护现有数据",
                        onClick = onRestore,
                    )
                }
            }
        }
        item(key = "about") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "关于")
                AppSectionCard {
                    ProfileActionRow(
                        icon = { InfoIcon(Modifier.size(20.dp)) },
                        title = "关于应用",
                        description = "版本、本地存储与隐私说明",
                        onClick = { showAbout = true },
                    )
                    ProfileActionRow(
                        icon = { InfoIcon(Modifier.size(20.dp)) },
                        title = "应用版本",
                        description = versionName,
                        onClick = {},
                    )
                    ProfileActionRow(
                        icon = { InfoIcon(Modifier.size(20.dp)) },
                        title = "检查更新",
                        description = "查询 GitHub 最新稳定版本",
                        onClick = { showUpdateCheck = true },
                    )
                    ProfileActionRow(
                        icon = { TextLinesIcon(Modifier.size(20.dp)) },
                        title = "应用内显示名称",
                        description = profileState.nickname,
                        onClick = {
                            nicknameDraft = profileState.nickname
                            nicknameError = null
                            editingNickname = true
                        },
                    )
                }
            }
        }
    }

    if (editingNickname) {
        AlertDialog(
            onDismissRequest = { editingNickname = false },
            title = { Text("编辑昵称") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nicknameDraft,
                        onValueChange = {
                            nicknameDraft = it
                            nicknameError = null
                        },
                        singleLine = true,
                        label = { Text("昵称") },
                        supportingText = {
                            Text(
                                nicknameError
                                    ?: "1–${ProfileRules.MAX_NICKNAME_LENGTH} 个字符，会自动去掉首尾空格",
                            )
                        },
                        isError = nicknameError != null,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = ProfileRules.normalizeNickname(nicknameDraft)
                        if (normalized == null) {
                            nicknameError =
                                "昵称不能为空，且不能超过 ${ProfileRules.MAX_NICKNAME_LENGTH} 个字符"
                        } else if (onSaveNickname(normalized)) {
                            editingNickname = false
                        } else {
                            nicknameError = "昵称保存失败"
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingNickname = false }) { Text("取消") }
            },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于 ${profileState.nickname}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("应用：${profileState.nickname}")
                    Text("版本：$versionName")
                    Text("用途：本地阅读、写作、统计与本地文件管理。")
                    Text("数据仅保存在本机应用私有目录；不提供账号登录或云端同步。")
                    Text("系统启动器名称仍为“xtt的工具箱”，桌面图标也不会随头像自动变更。")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("关闭") }
            },
        )
    }

    if (showUpdateCheck) {
        UpdateCheckDialog(
            currentVersion = versionName,
            onDismiss = { showUpdateCheck = false },
        )
    }
}

@Composable
private fun UpdateCheckDialog(
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var attempt by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var linkError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        result = null
        linkError = null
        result = withContext(Dispatchers.IO) {
            UpdateChecker.check(currentVersion)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("检查更新") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val current = result) {
                    null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("正在查询 GitHub 最新稳定 Release…")
                        }
                    }

                    is UpdateCheckResult.UpToDate -> {
                        Text("当前已是最新版本。")
                        Text("当前版本：${current.currentVersion}")
                        Text("最新稳定版本：${current.latestVersion}")
                    }

                    is UpdateCheckResult.Available -> {
                        Text("发现新版本 ${current.info.latestVersion}")
                        Text("当前版本：${current.info.currentVersion}")
                        Text(
                            "将通过浏览器打开本项目 GitHub Release 的 APK 下载链接。" +
                                "下载完成后，安装必须由 Android 系统确认；应用不会静默安装。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (current.info.apkSha256 != null) {
                            Text(
                                "SHA-256：${current.info.apkSha256}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        linkError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    is UpdateCheckResult.Failure -> {
                        Text("检查失败：${current.message}")
                        Text(
                            "请检查网络后重试；也可以在 GitHub Release 页面手动下载。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (val current = result) {
                is UpdateCheckResult.Available -> {
                    TextButton(
                        onClick = {
                            if (!ExternalLinkLauncher.open(context, current.info.apkUrl)) {
                                linkError = "无法打开下载链接，请手动访问 GitHub Release"
                            }
                        },
                    ) { Text("打开下载页") }
                }

                is UpdateCheckResult.Failure -> {
                    TextButton(onClick = { attempt += 1 }) { Text("重试") }
                }

                else -> {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        },
        dismissButton = {
            if (result is UpdateCheckResult.Available) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun ProfileActionRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
