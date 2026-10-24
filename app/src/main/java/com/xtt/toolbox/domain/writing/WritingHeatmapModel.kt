package com.xtt.toolbox.domain.writing

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 热力图最近 84 天的纯数据；日期按旧到新连续排列，不插入星期占位。 */
data class WritingHeatmapCell(
    val date: String,
    val netWords: Int,
    val recorded: Boolean,
)

object WritingHeatmapModel {
    const val DAYS = 84
    const val COLUMNS = 12
    const val ROWS = 7

    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun rangeFormat() = SimpleDateFormat("yyyy/MM/dd", Locale.US)

    fun build(
        stats: List<WritingDailyStat>,
        todayMillis: Long = System.currentTimeMillis(),
    ): List<WritingHeatmapCell> {
        val format = dateFormat()
        val byDate = stats.groupBy { it.date }
            .mapValues { (_, values) -> values.sumOf { it.netWords } }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = todayMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(DAYS - 1))
        }
        return buildList {
            repeat(DAYS) {
                val date = format.format(calendar.time)
                val net = byDate[date]
                add(
                    WritingHeatmapCell(
                        date = date,
                        netWords = net ?: 0,
                        recorded = net != null,
                    ),
                )
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    fun rangeLabel(cells: List<WritingHeatmapCell>): String {
        if (cells.isEmpty()) return ""
        val format = rangeFormat()
        val parse = dateFormat()
        fun label(value: String): String = runCatching {
            format.format(parse.parse(value)!!)
        }.getOrDefault(value)
        return "${label(cells.first().date)} – ${label(cells.last().date)}"
    }

    fun detailText(cell: WritingHeatmapCell): String = when {
        !cell.recorded -> "无记录"
        cell.netWords > 0 -> "净新增 ${cell.netWords} 字"
        cell.netWords < 0 -> "减少 ${-cell.netWords} 字"
        else -> "0 字"
    }

    fun level(cell: WritingHeatmapCell): Int = when {
        !cell.recorded -> 0
        cell.netWords <= 0 -> 0
        cell.netWords >= 1000 -> 4
        cell.netWords >= 300 -> 3
        cell.netWords >= 100 -> 2
        else -> 1
    }
}
