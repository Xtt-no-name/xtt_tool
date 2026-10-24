package com.xtt.toolbox.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.data.settings.ThemeMode
import com.xtt.toolbox.domain.reading.ReaderSettings
import com.xtt.toolbox.ui.components.AppSectionCard
import com.xtt.toolbox.ui.components.SectionHeader

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    readerSettings: ReaderSettings,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    searchHistoryEnabled: Boolean,
    onSearchHistoryEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "外观设置")
                AppSectionCard {
                    ThemeChoice("跟随系统", ThemeMode.SYSTEM, themeMode, onThemeModeChange)
                    ThemeChoice("浅色模式", ThemeMode.LIGHT, themeMode, onThemeModeChange)
                    ThemeChoice("深色模式", ThemeMode.DARK, themeMode, onThemeModeChange)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "阅读设置")
                AppSectionCard {
                    Text(
                        text = "与阅读器共用同一份本地设置；修改后返回阅读器生效。",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ReaderSlider(
                        title = "字号",
                        value = readerSettings.fontSizeSp,
                        range = ReaderSettings.MIN_FONT_SIZE_SP..ReaderSettings.MAX_FONT_SIZE_SP,
                        valueText = "${readerSettings.fontSizeSp.toInt()} sp",
                        onValue = {
                            onReaderSettingsChange(
                                readerSettings.copy(fontSizeSp = it).normalized(),
                            )
                        },
                    )
                    ReaderSlider(
                        title = "行距",
                        value = readerSettings.lineHeightSp,
                        range = ReaderSettings.MIN_LINE_HEIGHT_SP..ReaderSettings.MAX_LINE_HEIGHT_SP,
                        valueText = "${readerSettings.lineHeightSp.toInt()} sp",
                        onValue = {
                            onReaderSettingsChange(
                                readerSettings.copy(lineHeightSp = it).normalized(),
                            )
                        },
                    )
                    ReaderSlider(
                        title = "段间距",
                        value = readerSettings.paragraphSpacingDp,
                        range = ReaderSettings.MIN_PARAGRAPH_SPACING_DP..
                            ReaderSettings.MAX_PARAGRAPH_SPACING_DP,
                        valueText = "${readerSettings.paragraphSpacingDp.toInt()} dp",
                        onValue = {
                            onReaderSettingsChange(
                                readerSettings.copy(paragraphSpacingDp = it).normalized(),
                            )
                        },
                    )
                    ReaderSlider(
                        title = "左右边距",
                        value = readerSettings.horizontalPaddingDp,
                        range = ReaderSettings.MIN_HORIZONTAL_PADDING_DP..
                            ReaderSettings.MAX_HORIZONTAL_PADDING_DP,
                        valueText = "${readerSettings.horizontalPaddingDp.toInt()} dp",
                        onValue = {
                            onReaderSettingsChange(
                                readerSettings.copy(horizontalPaddingDp = it).normalized(),
                            )
                        },
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "搜索设置")
                AppSectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "记录搜索历史",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "开启后保留最近的搜索记录，关闭后不记录和显示搜索历史。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = searchHistoryEnabled,
                            onCheckedChange = onSearchHistoryEnabledChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    value: ThemeMode,
    selected: ThemeMode,
    onChange: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(value) }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == value, onClick = { onChange(value) })
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReaderSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValue: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
        )
        Spacer(Modifier.height(2.dp))
    }
}
