package com.xtt.toolbox.data.repository

import com.xtt.toolbox.data.local.BookDatabaseHelper
import com.xtt.toolbox.domain.writing.WritingDailyStat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 写作每日净新增。
 *
 * 每个作品/本地日期只保留一条记录：首次保存建立 baseline，后续保存更新
 * net = 当前总字数 - baseline。这里不读取或扫描正文文件，只使用保存后字数。
 */
class WritingHistoryRepository(
    private val helper: BookDatabaseHelper,
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun recordSave(
        bookId: String,
        beforeCount: Int,
        afterCount: Int,
        now: Long = System.currentTimeMillis(),
    ): Boolean = helper.recordWritingDailyStat(
        bookId = bookId,
        date = dateFormat.format(Date(now)),
        beforeCount = beforeCount,
        afterCount = afterCount,
    )

    fun loadLastYear(now: Long = System.currentTimeMillis()): List<WritingDailyStat> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -364)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val from = dateFormat.format(calendar.time)
        calendar.timeInMillis = now
        val to = dateFormat.format(calendar.time)
        return helper.loadWritingDailyStats(from, to).map {
            WritingDailyStat(date = it.date, netWords = it.netWords)
        }
    }

    fun today(): String = dateFormat.format(Date())
}
