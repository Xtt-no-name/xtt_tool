package com.xtt.toolbox.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSearchRulesTest {
    @Test
    fun chineseSubstringMatches() {
        assertTrue(ReadingSearchRules.matches("星海拾遗", "星海"))
        assertFalse(ReadingSearchRules.matches("星海拾遗", "银河"))
    }

    @Test
    fun englishMatchingIgnoresCase() {
        assertTrue(ReadingSearchRules.matches("The Silent Sea", "silent"))
        assertTrue(ReadingSearchRules.matches("The Silent Sea", "SEA"))
    }

    @Test
    fun queryIsTrimmedAndBlankDoesNotMatch() {
        assertTrue(ReadingSearchRules.matches("星海拾遗", "  星海  "))
        assertFalse(ReadingSearchRules.matches("星海拾遗", "   "))
    }
}

class SearchHistoryRulesTest {
    @Test
    fun blankIsNotRecordedAndDisabledClears() {
        assertEquals(listOf("星海"), SearchHistoryRules.updated(listOf("星海"), "  ", true))
        assertTrue(SearchHistoryRules.updated(listOf("星海"), "银河", false).isEmpty())
    }

    @Test
    fun trimsAndMovesDuplicateToFrontIgnoringCase() {
        val result = SearchHistoryRules.updated(listOf("Sea", "星海"), "  sea  ", true)
        assertEquals(listOf("sea", "星海"), result)
    }

    @Test
    fun limitsToTenMostRecent() {
        val existing = (1..10).map { "书$it" }
        val result = SearchHistoryRules.updated(existing, "新书", true)
        assertEquals(10, result.size)
        assertEquals("新书", result.first())
        assertFalse(result.contains("书10"))
    }
}
