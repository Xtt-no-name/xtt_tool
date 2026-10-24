package com.xtt.toolbox.domain.content

import com.xtt.toolbox.data.model.ChapterDetectionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    private val parser = MarkdownParser()

    @Test
    fun plainTextUsesFilenameAndSingleFallbackChapter() {
        val parsed = parser.parse("只有普通文本。\n\n第二段。", "随手记.md")
        assertEquals("随手记", parsed.title)
        assertEquals(1, parsed.chapters.size)
        assertEquals("", parsed.chapters[0].title)
        assertEquals(ChapterDetectionSource.MARKDOWN_FALLBACK, parsed.chapters[0].detectionSource)
        assertTrue(parsed.text.contains("只有普通文本"))
        assertTrue(parsed.blocks.all { it.type == BookBlockType.PARAGRAPH })
    }

    @Test
    fun headingsBuildOrderedLevelsAndChapters() {
        val markdown = """
            # 第一章
            内容一
            ## 第一节
            内容二
            ### 细节
            内容三
            ## 第二节
            内容四
        """.trimIndent()
        val parsed = parser.parse(markdown, "headings.md")
        assertEquals(4, parsed.chapters.size)
        assertEquals(listOf("第一章", "第一节", "细节", "第二节"), parsed.chapters.map { it.title })
        assertEquals(listOf(0, 1, 2, 1), parsed.chapters.map { it.level })
        assertTrue(parsed.chapters[0].startOffset < parsed.chapters[1].startOffset)
        assertEquals(ChapterDetectionSource.MARKDOWN_HEADING, parsed.chapters[0].detectionSource)
    }

    @Test
    fun inlineStylesNestAndCodeDoesNotBecomeFormatting() {
        val parsed = parser.parse(
            "普通 **粗体** *斜体* ~~删除~~ **粗中的*斜体*结束** `行内 # 代码` [链接](https://example.com)",
            "inline.md",
        )
        val block = parsed.blocks.first()
        assertTrue(block.spans.any { it.bold })
        assertTrue(block.spans.any { it.italic })
        assertTrue(block.spans.any { it.bold && it.italic })
        assertTrue(block.spans.any { it.strike })
        assertTrue(block.spans.any { it.code })
        assertTrue(block.spans.any { it.link })
        assertTrue(parsed.text.contains("行内 # 代码"))
    }

    @Test
    fun fencedCodePreservesMarkersAndSpacing() {
        val parsed = parser.parse(
            """
            ```kotlin
            # 不是标题
            * 不是列表
              val x = 1
            ```
            之外
            """.trimIndent(),
            "code.md",
        )
        val code = parsed.blocks.first { it.type == BookBlockType.CODE }
        val codeText = parsed.text.substring(code.startOffset, code.endOffset)
        assertTrue("codeText=$codeText blocks=${parsed.blocks}", codeText.contains("# 不是标题"))
        assertTrue("codeText=$codeText", codeText.contains("* 不是列表"))
        assertTrue("codeText=$codeText", codeText.contains("  val x = 1"))
        assertEquals(1, parsed.chapters.size)
    }

    @Test
    fun listsQuotesRulesTablesAndImagesAreReadable() {
        val markdown = """
            # 结构
            - 一级
              - 二级
            1. 有序
            > 引用内容
            ---
            | A | B |
            |---|---|
            | 1 | 2 |
            ![图](images/a.png)
        """.trimIndent()
        val parsed = parser.parse(markdown, "structure.md")
        val listItems = parsed.blocks.filter { it.style?.listItem == true }
        assertEquals(3, listItems.size)
        assertEquals(listOf(0, 1, 0), listItems.map { it.style?.listDepth })
        assertEquals(listOf(0, 0, 1), listItems.map { it.style?.listNumber })
        assertTrue(parsed.blocks.any { it.type == BookBlockType.QUOTE })
        assertTrue(parsed.blocks.any { it.type == BookBlockType.RULE })
        val table = parsed.blocks.first { it.type == BookBlockType.TABLE }.table
        assertNotNull(table)
        assertEquals(listOf("A", "B"), table!!.header.map { it.text })
        assertEquals(listOf("1", "2"), table.rows.single().map { it.text })
        assertFalse(parsed.text.contains("| A | B |"))
        val image = parsed.blocks.first { it.type == BookBlockType.IMAGE }
        assertEquals("md-image-0", image.resourceId)
        assertEquals("images/a.png", image.style?.imageSourcePath)
        assertEquals(1, parsed.images.size)
        assertEquals("images/a.png", parsed.images.single().path)
    }

    @Test
    fun frontMatterMetadataIsStrippedAndMalformedYamlDoesNotFail() {
        val parsed = parser.parse(
            """
            ---
            title: Front Matter 标题
            author: 作者
            custom: value
            ---
            # 正文标题
            正文
            """.trimIndent(),
            "metadata.md",
        )
        assertEquals("Front Matter 标题", parsed.title)
        assertEquals("作者", parsed.author)
        assertFalse(parsed.text.contains("title:"))
        assertFalse(parsed.text.contains("custom:"))

        val malformed = parser.parse(
            """
            ---
            title: 可读标题
            broken yaml line
            ---
            # 正文
            content
            """.trimIndent(),
            "malformed.md",
        )
        assertEquals("可读标题", malformed.title)
        assertFalse(malformed.text.contains("broken yaml line"))
        assertTrue(malformed.warnings.any { it.contains("Front Matter") })
    }

    @Test
    fun tablesAreStructuredWithAlignmentAndInlineStyles() {
        val parsed = parser.parse(
            """
            | 左列 | 中间 | 右列 |
            |:---|:---:|---:|
            | **粗体** | *斜体* | `code` |
            | 很长的单元格文本会换行 |  | 尾 |
            |多一列|
            """.trimIndent(),
            "table.md",
        )
        val table = parsed.blocks.single { it.type == BookBlockType.TABLE }.table!!
        assertEquals(listOf("左列", "中间", "右列"), table.header.map { it.text })
        assertEquals(
            listOf(BookTextAlign.START, BookTextAlign.CENTER, BookTextAlign.END),
            table.alignments,
        )
        assertEquals(3, table.rows.size)
        assertTrue(table.rows[0][0].spans.any { it.bold })
        assertTrue(table.rows[0][1].spans.any { it.italic })
        assertTrue(table.rows[0][2].spans.any { it.code })
        assertEquals("", table.rows[1][1].text)
        assertEquals("", table.rows[2][1].text)
        assertFalse(parsed.text.contains("|:---|"))
    }

    @Test
    fun tableWithoutValidSeparatorFallsBackToParagraph() {
        val parsed = parser.parse(
            "| 普通 | 文字 |\n| 不是 | 分隔 |",
            "not-table.md",
        )
        assertTrue(parsed.blocks.none { it.type == BookBlockType.TABLE })
        assertTrue(parsed.text.contains("| 普通 | 文字 |"))
    }

    @Test
    fun taskListsAndNormalListsKeepNestingAndInlineStyles() {
        val parsed = parser.parse(
            """
            - [ ] 未完成 **粗体**
            - [x] 已完成
              - [X] 嵌套 `代码`
            - 普通列表
            """.trimIndent(),
            "tasks.md",
        )
        val tasks = parsed.blocks.filter { it.style?.taskChecked != null }
        assertEquals(listOf(false, true, true), tasks.map { it.style?.taskChecked })
        assertEquals(listOf(0, 0, 1), tasks.map { it.style?.listDepth })
        assertTrue(tasks[0].spans.any { it.bold })
        assertTrue(tasks[2].spans.any { it.code })
        assertEquals(1, parsed.blocks.count { it.style?.listItem == true && it.style?.taskChecked == null })
    }

    @Test
    fun footnotesLinkRepeatedReferencesAndDefinitions() {
        val parsed = parser.parse(
            """
            正文[^note] 再次[^note]，缺少[^missing]。
            [^note]: 脚注内容 **重点**
            [^note]: 重复定义
            """.trimIndent(),
            "footnotes.md",
        )
        assertTrue(parsed.text.contains("[脚注 1]"))
        assertTrue(parsed.text.contains("[脚注?]"))
        assertEquals(1, parsed.blocks.count { it.type == BookBlockType.FOOTNOTE })
        assertTrue(parsed.blocks.any {
            it.type == BookBlockType.FOOTNOTE &&
                parsed.text.substring(it.startOffset, it.endOffset).contains("重点")
        })
        assertTrue(parsed.warnings.any { it.contains("重复") })
        assertTrue(parsed.warnings.any { it.contains("缺少定义") })
        val referenceBlock = parsed.blocks.first()
        val reference = referenceBlock.spans.first { it.footnote }
        assertEquals("md-footnote-1", reference.targetBlockId)
        val definition = parsed.blocks.first { it.type == BookBlockType.FOOTNOTE }
        assertEquals("md-footnote-1", definition.blockId)
        assertEquals(listOf(referenceBlock.blockId), definition.footnoteRefIds)
    }

    @Test
    fun definitionListsAreStructuredAndOrdinaryParagraphsStayParagraphs() {
        val parsed = parser.parse(
            """
            术语
            : 术语的 **解释**
            : 第二行解释
            普通段落不会变成定义
            """.trimIndent(),
            "definitions.md",
        )
        assertEquals(BookBlockType.DEFINITION_TERM, parsed.blocks[0].type)
        assertEquals(BookBlockType.DEFINITION, parsed.blocks[1].type)
        assertEquals(BookBlockType.DEFINITION, parsed.blocks[2].type)
        assertTrue(parsed.blocks[1].spans.any { it.bold })
        assertTrue(parsed.blocks.any { it.type == BookBlockType.PARAGRAPH })
    }

    @Test
    fun tableDataRoundTripsThroughPersistenceCodec() {
        val table = BookTable(
            header = listOf(BookTableCell("A"), BookTableCell("B")),
            rows = listOf(
                listOf(BookTableCell("1", listOf(InlineSpan(0, 1, bold = true))), BookTableCell("")),
            ),
            alignments = listOf(BookTextAlign.CENTER, BookTextAlign.END),
        )
        val decoded = BookTableCodec.decode(BookTableCodec.encode(table))
        assertEquals(table, decoded)
    }

    @Test
    fun linkTargetsArePreservedAndFormattingDoesNotReplaceThem() {
        val parsed = parser.parse(
            "[访问 **网站**](https://example.com/a?x=1) 和 [危险](javascript:alert) 以及 [空]()",
            "links.md",
        )
        val block = parsed.blocks.first()
        val links = block.spans.filter { it.link }
        assertTrue(links.any { it.linkTarget == "https://example.com/a?x=1" })
        assertTrue(links.any { it.linkTarget == "https://example.com/a?x=1" && it.bold })
        assertTrue(links.any { it.linkTarget == "javascript:alert" })
        assertTrue(links.any { it.linkTarget == null })
        assertTrue(parsed.text.contains("访问"))
        assertTrue(parsed.text.contains("危险"))
    }

    @Test
    fun linksInTableCellsKeepTargets() {
        val parsed = parser.parse(
            """
            | 官网 | 说明 |
            |---|---|
            | [访问](https://example.com) 和 [危险](javascript:alert) | 普通 |
            """.trimIndent(),
            "table-link.md",
        )
        val cell = parsed.blocks.single { it.type == BookBlockType.TABLE }.table!!.rows.single()[0]
        assertEquals("访问 和 危险", cell.text)
        assertEquals(
            listOf("https://example.com", "javascript:alert"),
            cell.spans.filter { it.link }.map { it.linkTarget }.distinct(),
        )
    }

    @Test
    fun inlineImageSyntaxBecomesReadablePlaceholderWithoutRawUrl() {
        val parsed = parser.parse("说明：![缺失](images/missing.png) 结尾。", "inline-image.md")
        assertTrue(parsed.text.contains("[图片：缺失]"))
        assertFalse(parsed.text.contains("(images/missing.png)"))
    }

    @Test
    fun bomAndEscapesAreHandled() {
        val parsed = parser.parse("\uFEFF# 标题\n\\*不是斜体\\* 和 `代码`", "bom.md")
        assertEquals("标题", parsed.title)
        assertTrue(parsed.text.contains("*不是斜体*"))
        assertTrue(parsed.text.contains("代码"))
        assertTrue(parsed.blocks.any { it.spans.any { span -> span.code } })
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyMarkdownIsRejected() {
        parser.parse("  \n\n", "empty.md")
    }
}
