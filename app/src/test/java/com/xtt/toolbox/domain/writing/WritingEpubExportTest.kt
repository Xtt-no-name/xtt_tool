package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class WritingEpubExportTest {
    @Test
    fun generatedEpubHasRequiredPackageFilesAndBody() {
        val document = WritingDocumentFactory.plainText(book(), "正文 & <特殊>", "")
        val bytes = WritingEpubExport.build(document, emptyMap())
        val entries = unzip(bytes)
        assertEquals("application/epub+zip", entries["mimetype"]?.toString(Charsets.US_ASCII))
        assertTrue(entries["META-INF/container.xml"]!!.toString(Charsets.UTF_8)
            .contains("OEBPS/content.opf"))
        assertTrue(entries["OEBPS/content.opf"]!!.toString(Charsets.UTF_8)
            .contains("dc:title"))
        assertTrue(entries["OEBPS/nav.xhtml"]!!.toString(Charsets.UTF_8)
            .contains("chapter-1.xhtml"))
        val chapter = entries["OEBPS/chapter-1.xhtml"]!!.toString(Charsets.UTF_8)
        assertTrue(chapter.contains("正文 &amp; &lt;特殊&gt;"))
    }

    @Test
    fun emptyTitleDoesNotWritePlaceholderIntoChapterBody() {
        val document = WritingDocumentFactory.plainText(book(), "正文", "")
        val chapter = unzip(WritingEpubExport.build(document, emptyMap()))[
            "OEBPS/chapter-1.xhtml"
        ]!!.toString(Charsets.UTF_8)
        assertFalse(chapter.contains("未命名章节"))
        assertFalse(chapter.contains("作品名"))
    }

    @Test
    fun imageIsEmbeddedAndNoInternalResourcePathAppears() {
        val base = WritingDocumentFactory.plainText(book(), "前文\n后文", "")
        val resource = BookResource(
            resourceId = "private-image-id",
            relativePath = "editor/images/private.png",
            mediaType = "image/png",
            sizeBytes = 4,
            sha256 = "test",
            kind = BookResourceKind.IMAGE,
        )
        val block = BookBlock(
            blockId = "image-block",
            order = 1,
            type = BookBlockType.IMAGE,
            startOffset = 2,
            endOffset = 2,
            resourceId = resource.resourceId,
        )
        val document = base.copy(
            document = base.document.copy(
                blocks = base.document.blocks + block,
                resources = listOf(resource),
            ),
        )
        val bytes = WritingEpubExport.build(
            document,
            mapOf(resource.resourceId to byteArrayOf(1, 2, 3, 4)),
        )
        val entries = unzip(bytes)
        assertTrue(entries.containsKey("OEBPS/images/image-1.png"))
        val chapter = entries["OEBPS/chapter-1.xhtml"]!!.toString(Charsets.UTF_8)
        assertTrue(chapter.contains("""<img src="images/image-1.png""""))
        assertFalse(chapter.contains("private-image-id"))
        assertFalse(chapter.contains("editor/images"))
    }

    @Test
    fun multiChapterDocumentKeepsChapterOrder() {
        var document = WritingDocumentEditor.blank(book())
        val firstId = document.document.chapters.single().chapterId
        var first = WritingChapterEditor.load(document, firstId)!!
        first = WritingChapterEditor.updateText(first, "第一章\n甲")
        document = WritingChapterEditor.toDocument(document, first)
        document = WritingDocumentEditor.addChapter(document, "第二章")
        val secondId = document.document.chapters.maxByOrNull { it.order }!!.chapterId
        var second = WritingChapterEditor.load(document, secondId)!!
        second = WritingChapterEditor.updateText(second, "第二章\n乙")
        document = WritingChapterEditor.toDocument(document, second)
        val entries = unzip(WritingEpubExport.build(document, emptyMap()))
        val nav = entries["OEBPS/nav.xhtml"]!!.toString(Charsets.UTF_8)
        assertTrue(nav.indexOf("第一章") < nav.indexOf("第二章"))
        assertTrue(entries.containsKey("OEBPS/chapter-1.xhtml"))
        assertTrue(entries.containsKey("OEBPS/chapter-2.xhtml"))
    }

    @Test
    fun generatedEpubCanBeOpenedByProjectEpubParser() {
        val document = WritingDocumentFactory.plainText(book(), "可重新打开的正文", "")
        val file = java.io.File.createTempFile("generated-writing-", ".epub")
        try {
            file.writeBytes(WritingEpubExport.build(document, emptyMap()))
            val parsed = com.xtt.toolbox.domain.content.EpubParser().parse(file, "generated.epub")
            assertTrue(parsed.text.contains("可重新打开的正文"))
            assertTrue(parsed.chapters.isNotEmpty())
        } finally {
            file.delete()
        }
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes())
                zip.closeEntry()
            }
        }
    }

    private fun book(): Book = Book(
        bookId = UUID.randomUUID().toString(),
        title = "导出作品",
        author = "作者",
        language = "zh",
        shelfType = ShelfType.WRITING,
        sourceType = SourceType.CREATED,
        format = BookFormat.DRAFT,
        createdAt = 1,
        updatedAt = 1,
    )
}
