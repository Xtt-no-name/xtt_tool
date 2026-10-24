package com.xtt.toolbox.domain.writing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WritingHeatmapModelTest {
    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Test
    fun mapsExactly84ContinuousDaysIntoTwelveBySevenGrid() {
        val today = calendar(2026, Calendar.OCTOBER, 23, 12).timeInMillis
        val cells = WritingHeatmapModel.build(emptyList(), today)
        assertEquals(84, cells.size)
        assertEquals(84, cells.map { it.date }.distinct().size)

        val expectedStart = calendar(2026, Calendar.AUGUST, 1, 0)
        val expectedEnd = calendar(2026, Calendar.OCTOBER, 23, 0)
        assertEquals(format.format(expectedStart.time), cells.first().date)
        assertEquals(format.format(expectedEnd.time), cells.last().date)

        val cursor = expectedStart
        cells.forEach { cell ->
            assertEquals(format.format(cursor.time), cell.date)
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        assertEquals(12, WritingHeatmapModel.COLUMNS)
        assertEquals(7, WritingHeatmapModel.ROWS)
        assertEquals(cells[0].date, cells[0 * WritingHeatmapModel.COLUMNS + 0].date)
        assertEquals(cells[12].date, cells[1 * WritingHeatmapModel.COLUMNS + 0].date)
    }

    @Test
    fun bindsStatsAndFormatsAllDetailStates() {
        val today = calendar(2026, Calendar.OCTOBER, 23, 12).timeInMillis
        val cells = WritingHeatmapModel.build(
            listOf(
                WritingDailyStat("2026-10-21", 12),
                WritingDailyStat("2026-10-22", 0),
                WritingDailyStat("2026-10-23", -35),
            ),
            today,
        )
        val noRecord = cells.first { it.date == "2026-10-20" }
        val positive = cells.first { it.date == "2026-10-21" }
        val zero = cells.first { it.date == "2026-10-22" }
        val negative = cells.first { it.date == "2026-10-23" }
        assertEquals("无记录", WritingHeatmapModel.detailText(noRecord))
        assertEquals("净新增 12 字", WritingHeatmapModel.detailText(positive))
        assertEquals("0 字", WritingHeatmapModel.detailText(zero))
        assertEquals("减少 35 字", WritingHeatmapModel.detailText(negative))
        assertEquals(false, noRecord.recorded)
        assertTrue(zero.recorded)
        assertTrue(negative.netWords < 0)
        assertEquals("2026/08/01 – 2026/10/23", WritingHeatmapModel.rangeLabel(cells))
    }

    @Test
    fun duplicateStatsDatesAreAggregated() {
        val today = calendar(2026, Calendar.OCTOBER, 23, 12).timeInMillis
        val cells = WritingHeatmapModel.build(
            listOf(
                WritingDailyStat("2026-10-23", 5),
                WritingDailyStat("2026-10-23", 7),
            ),
            today,
        )
        assertEquals(12, cells.last().netWords)
        assertTrue(cells.last().recorded)
    }

    private fun calendar(year: Int, month: Int, day: Int, hour: Int): Calendar =
        Calendar.getInstance().apply {
            set(year, month, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
