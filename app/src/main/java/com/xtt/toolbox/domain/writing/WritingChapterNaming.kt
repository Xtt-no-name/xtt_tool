package com.xtt.toolbox.domain.writing

/**
 * 新建章节的默认命名。只依赖目标分卷内已有章节数量，不读取章节标题。
 */
object WritingChapterNaming {
    fun defaultTitle(existingChapterCount: Int): String {
        require(existingChapterCount >= 0) { "章节数量不能为负数" }
        return "第${chineseNumber(existingChapterCount + 1)}章"
    }

    /** 1..9999 的常用中文数字，支持十、百、千以及零的占位。 */
    fun chineseNumber(value: Int): String {
        require(value in 1..9999) { "章节序号超出支持范围" }
        val digits = value.toString().map { it - '0' }
        val numberChars = charArrayOf('零', '一', '二', '三', '四', '五', '六', '七', '八', '九')
        val units = arrayOf("", "十", "百", "千")
        val result = StringBuilder()
        var pendingZero = false
        digits.forEachIndexed { index, digit ->
            if (digit == 0) {
                if (result.isNotEmpty()) pendingZero = true
            } else {
                if (pendingZero) {
                    result.append(numberChars[0])
                    pendingZero = false
                }
                result.append(numberChars[digit])
                result.append(units[digits.lastIndex - index])
            }
        }
        if (result.startsWith("一十")) result.deleteCharAt(0)
        return result.toString()
    }
}
