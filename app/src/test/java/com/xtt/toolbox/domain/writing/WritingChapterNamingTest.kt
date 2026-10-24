package com.xtt.toolbox.domain.writing

import org.junit.Assert.assertEquals
import org.junit.Test

class WritingChapterNamingTest {
    @Test
    fun titlesFollowTargetVolumeChapterCount() {
        assertEquals("第一章", WritingChapterNaming.defaultTitle(0))
        assertEquals("第二章", WritingChapterNaming.defaultTitle(1))
        assertEquals("第三章", WritingChapterNaming.defaultTitle(2))
        assertEquals("第四章", WritingChapterNaming.defaultTitle(3))
        assertEquals("第六章", WritingChapterNaming.defaultTitle(5))
    }

    @Test
    fun chineseNumberHandlesTensAndHundredsWithoutMisformatting() {
        assertEquals("十", WritingChapterNaming.chineseNumber(10))
        assertEquals("十一", WritingChapterNaming.chineseNumber(11))
        assertEquals("十九", WritingChapterNaming.chineseNumber(19))
        assertEquals("二十", WritingChapterNaming.chineseNumber(20))
        assertEquals("二十一", WritingChapterNaming.chineseNumber(21))
        assertEquals("九十九", WritingChapterNaming.chineseNumber(99))
        assertEquals("一百", WritingChapterNaming.chineseNumber(100))
        assertEquals("一百零一", WritingChapterNaming.chineseNumber(101))
        assertEquals("一百一十", WritingChapterNaming.chineseNumber(110))
        assertEquals("一千零一", WritingChapterNaming.chineseNumber(1001))
    }

    @Test
    fun defaultTitlesAtTwoAndThreeDigits() {
        assertEquals("第十章", WritingChapterNaming.defaultTitle(9))
        assertEquals("第十一章", WritingChapterNaming.defaultTitle(10))
        assertEquals("第二十一章", WritingChapterNaming.defaultTitle(20))
        assertEquals("第一百章", WritingChapterNaming.defaultTitle(99))
        assertEquals("第一百零一章", WritingChapterNaming.defaultTitle(100))
    }
}
