package com.xtt.toolbox.domain.writing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingSearchTest {
    @Test
    fun findsAllLiteralMatches() {
        val ranges = WritingSearch.matches("aba aba", "aba")
        assertEquals(2, ranges.size)
        assertEquals(0 until 3, ranges[0])
        assertEquals(4 until 7, ranges[1])
    }

    @Test
    fun regexCharactersAreTreatedLiterally() {
        val text = "a[b] a.b"
        assertEquals(1, WritingSearch.matches(text, "[b]").size)
        assertEquals(1, WritingSearch.matches(text, "a.b").size)
        assertEquals(1, WritingSearch.matches(text, "[").size)
    }

    @Test
    fun emptyQueryHasNoMatches() {
        assertTrue(WritingSearch.matches("abc", "").isEmpty())
    }

    @Test
    fun missingQueryHasNoMatches() {
        assertTrue(WritingSearch.matches("abc", "z").isEmpty())
    }

    @Test
    fun replaceAllHandlesEmptyReplacementAndAdjacentMatches() {
        assertEquals("bbbb", WritingSearch.replaceAll("aaaa", "aa", "bb"))
        assertEquals("", WritingSearch.replaceAll("aaaa", "aa", ""))
        assertEquals("x", WritingSearch.replaceAll("aa", "aa", "x"))
    }

    @Test
    fun replacementContainingQueryDoesNotLoop() {
        assertEquals("aa", WritingSearch.replaceAll("a", "a", "aa"))
        assertEquals("ab ab", WritingSearch.replaceAll("ab ab", "ab", "ab"))
    }
}
