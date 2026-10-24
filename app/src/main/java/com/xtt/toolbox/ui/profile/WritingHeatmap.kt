package com.xtt.toolbox.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.domain.writing.WritingDailyStat
import com.xtt.toolbox.domain.writing.WritingHeatmapCell
import com.xtt.toolbox.domain.writing.WritingHeatmapModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun WritingHeatmap(
    stats: List<WritingDailyStat>,
    selectedDate: String?,
    onSelectedDateChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val cells = remember(stats, nowMillis) {
        WritingHeatmapModel.build(stats, nowMillis)
    }
    val range = remember(cells) { WritingHeatmapModel.rangeLabel(cells) }
    val selectedCell = cells.firstOrNull { it.date == selectedDate }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelectedDateChange(null) },
    ) {
        val targetWidth = minOf(maxWidth, 420.dp)
        val gap = 3.dp
        val cellSize = (targetWidth - gap * (WritingHeatmapModel.COLUMNS - 1)) /
            WritingHeatmapModel.COLUMNS
        Column(
            modifier = Modifier
                .width(targetWidth)
                .align(Alignment.Center)
                .clickable { onSelectedDateChange(null) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("写作热力图", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = range,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectedDateChange(null) },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(WritingHeatmapModel.ROWS) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(WritingHeatmapModel.COLUMNS) { column ->
                                val cell = cells[row * WritingHeatmapModel.COLUMNS + column]
                                val isSelected = selectedCell?.date == cell.date
                                val shape = RoundedCornerShape(3.dp)
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .clip(shape)
                                        .background(cellColor(cell))
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(
                                                    alpha = if (cell.recorded) 0.65f else 0.95f,
                                                )
                                            },
                                            shape = shape,
                                        )
                                        .clickable { onSelectedDateChange(cell.date) }
                                        .semantics {
                                            contentDescription =
                                                "${cell.date}，${WritingHeatmapModel.detailText(cell)}"
                                        },
                                )
                            }
                        }
                    }
                }
            }
            selectedCell?.let { cell ->
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .clickable { }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = cell.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.padding(start = 10.dp))
                    Text(
                        text = WritingHeatmapModel.detailText(cell),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "少",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(1, 2, 3, 4).forEach { level ->
                    HeatmapLegendBox(level)
                }
                Text(
                    text = "多",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "  红=净减少 · 空心=无记录 · 灰=0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (stats.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "暂无写作记录；热力图只统计新机制启用后的数据。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeatmapLegendBox(level: Int) {
    Box(
        modifier = Modifier
            .padding(start = 3.dp)
            .size(9.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(levelColor(level)),
    )
}

@Composable
private fun cellColor(cell: WritingHeatmapCell): Color {
    if (!cell.recorded) return Color.Transparent
    if (cell.netWords < 0) return MaterialTheme.colorScheme.errorContainer
    if (cell.netWords == 0) return MaterialTheme.colorScheme.surfaceVariant
    return levelColor(WritingHeatmapModel.level(cell))
}

@Composable
private fun levelColor(level: Int): Color {
    val primary = MaterialTheme.colorScheme.primary
    return when (level) {
        1 -> primary.copy(alpha = 0.22f)
        2 -> primary.copy(alpha = 0.42f)
        3 -> primary.copy(alpha = 0.68f)
        else -> primary
    }
}
