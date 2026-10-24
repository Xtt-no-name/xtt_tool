package com.xtt.toolbox.domain.writing

/** 某本地日期的写作净新增汇总；只包含真实记录过的日期。 */
data class WritingDailyStat(
    val date: String,
    val netWords: Int,
)
