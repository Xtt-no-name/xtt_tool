package com.xtt.toolbox.domain.chapter

import com.xtt.toolbox.domain.importing.TxtDecoder

import java.io.File

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 本地真实小说回归集。
 *
 * 仓库不包含小说正文；设置 XTT_CORPUS_DIR（或本机 E:\Xtt_tool\book）才运行。
 * 断言只记录人工核对后的章节数量和关键边界，不把当前实现的输出当作唯一正确结果。
 */
class RealNovelCorpusRegressionTest {
    private val corpus = File(System.getenv("XTT_CORPUS_DIR") ?: "E:\\Xtt_tool\\book")

    @Test
    fun iceFireCook() {
        val parsed = parse("冰火魔厨.txt")
        assertEquals(519, parsed.candidates.size)
        assertEquals("引子", parsed.candidates.first().title)
        assertEquals("尾声", parsed.candidates.last().title)
        assertCompleteAndOrdered(parsed)
        assertTrue(parsed.candidates.all { it.confidence >= 0.55f })
    }

    @Test
    fun palaceGarden() {
        val parsed = parse("后宫·大观园记.txt")
        assertEquals(102, parsed.candidates.size)
        assertEquals("第一回", parsed.candidates.first().title)
        assertTrue(parsed.candidates.last().title.startsWith("第一百零五回"))
        assertCompleteAndOrdered(parsed)
    }

    @Test
    fun greatWei() {
        val parsed = parse("大魏芳华.txt")
        assertEquals(1010, parsed.candidates.size)
        assertEquals(10, parsed.candidates.count { it.level == 1 })
        assertEquals("第一卷", parsed.candidates.first().title)
        assertEquals("第九百九十九章辞旧迎新", parsed.candidates.last().title)
        assertCompleteAndOrdered(parsed)
    }

    private fun parse(name: String): ChapterParseResult {
        val file = File(corpus, name)
        assumeTrue("本地真实小说语料不存在：${file.absolutePath}", file.isFile)
        val decoded = TxtDecoder().decode(file.readBytes())
        val text = TextOffsets.normalizeNewlines(decoded.text)
        val parsed = RegexChapterParser().parse(text)
        assertTrue(parsed.diagnostics != null)
        assertTrue(parsed.diagnostics!!.durationMs < 10_000)
        return parsed
    }

    private fun assertCompleteAndOrdered(parsed: ChapterParseResult) {
        val chapters = parsed.candidates
        assertFalse(chapters.isEmpty())
        chapters.zipWithNext().forEach { (left, right) ->
            assertEquals(left.endOffset, right.startOffset)
            assertTrue(left.startOffset < left.endOffset)
        }
        assertTrue(chapters.last().endOffset > chapters.last().startOffset)
        assertTrue(chapters.none { it.title.isBlank() })
    }
}
