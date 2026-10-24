package com.xtt.toolbox.domain.content

import com.xtt.toolbox.data.model.ChapterDetectionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {
    private val parser = EpubParser()

    @Test
    fun parsesSpineOrderNavigationAndInlineImage() {
        val file = createEpub(
            entries = baseEntries(
                chapterOneExtra = """<p>第一段 <img src="images/pixel.png" alt="插图"/> 后续文字。</p>""",
                includeNav = true,
            ),
        )
        try {
            val parsed = parser.parse(file, "sample.epub")

            assertEquals("测试书名", parsed.title)
            assertEquals("测试作者", parsed.author)
            assertEquals(2, parsed.chapters.size)
            assertEquals("第一章", parsed.chapters[0].title)
            assertEquals("第二章", parsed.chapters[1].title)
            assertEquals(ChapterDetectionSource.EPUB_NAV, parsed.chapters[0].detectionSource)
            assertTrue(parsed.text.indexOf("第一段") < parsed.text.indexOf("第二章正文"))
            assertEquals(1, parsed.resources.size)
            val imageBlock = parsed.blocks.first { it.type == BookBlockType.IMAGE }
            assertNotNull(imageBlock.resourceId)
            assertTrue(imageBlock.startOffset >= 0)
            assertTrue(parsed.resources.single().bytes.isNotEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun missingImageKeepsPlaceholderBlockAndWarning() {
        val file = createEpub(
            entries = baseEntries(
                chapterOneExtra = """<p>文字 <img src="images/missing.png" alt="缺失插图"/> 结尾。</p>""",
                includeNav = true,
            ),
        )
        try {
            val parsed = parser.parse(file, "sample.epub")
            val placeholder = parsed.blocks.first { it.type == BookBlockType.IMAGE }
            assertNull(placeholder.resourceId)
            assertEquals("缺失插图", placeholder.alt)
            assertTrue(parsed.warnings.any { it.contains("图片缺失") })
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsZipPathTraversal() {
        val file = File.createTempFile("epub-invalid-", ".epub")
        try {
            ZipOutputStream(FileOutputStream(file)).use { zip ->
                zip.putNextEntry(ZipEntry("../outside.txt"))
                zip.write("bad".toByteArray())
                zip.closeEntry()
            }
            val error = runCatching { parser.parse(file, "bad.epub") }.exceptionOrNull()
            assertTrue(error is EpubParseException)
        } finally {
            file.delete()
        }
    }

    @Test
    fun fallsBackToNcxWhenNavMissing() {
        val entries = baseEntries(chapterOneExtra = "<p>第一章正文。</p>", includeNav = false)
                .toMutableMap()
        entries["OEBPS/toc.ncx"] = ncx().toByteArray()
        entries["OEBPS/content.opf"] = opf(withNcx = true, includeNav = false).toByteArray()
        val file = createEpub(entries)
        try {
            val parsed = parser.parse(file, "ncx.epub")
            assertEquals("第一章 NCX", parsed.chapters[0].title)
        } finally {
            file.delete()
        }
    }

    @Test
    fun missingContainerAndCorruptOpfAreRejected() {
        val noContainer = createEpubEntries(
            listOf("mimetype" to "application/epub+zip".toByteArray()),
        )
        try {
            assertTrue(runCatching { parser.parse(noContainer, "bad.epub") }.exceptionOrNull() is EpubParseException)
        } finally {
            noContainer.delete()
        }

        val corrupt = baseEntries("<p>正文</p>", includeNav = false).toMutableMap()
        corrupt["OEBPS/content.opf"] = "<package".toByteArray()
        val corruptFile = createEpub(corrupt)
        try {
            assertTrue(runCatching { parser.parse(corruptFile, "bad.epub") }.exceptionOrNull() is EpubParseException)
        } finally {
            corruptFile.delete()
        }
    }

    @Test
    fun emptySpineIsRejected() {
        val entries = baseEntries("<p>正文</p>", includeNav = false).toMutableMap()
        entries["OEBPS/content.opf"] = """
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>空</dc:title></metadata>
              <manifest><item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine/>
            </package>
        """.trimIndent().toByteArray()
        val file = createEpub(entries)
        try {
            assertTrue(runCatching { parser.parse(file, "empty.epub") }.exceptionOrNull() is EpubParseException)
        } finally {
            file.delete()
        }

        val missingRef = entries.toMutableMap()
        missingRef["OEBPS/content.opf"] = """
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>缺失引用</dc:title></metadata>
              <manifest><item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="does-not-exist"/></spine>
            </package>
        """.trimIndent().toByteArray()
        val missingFile = createEpub(missingRef)
        try {
            assertTrue(
                runCatching { parser.parse(missingFile, "missing-ref.epub") }
                    .exceptionOrNull() is EpubParseException,
            )
        } finally {
            missingFile.delete()
        }
    }

    @Test
    fun rejectsEncodedTraversalEntries() {
        val encoded = createEpubEntries(
            listOf("%2e%2e/outside.txt" to "x".toByteArray()),
        )
        try {
            assertTrue(runCatching { parser.parse(encoded, "escape.epub") }.exceptionOrNull() is EpubParseException)
        } finally {
            encoded.delete()
        }

        val backslash = createEpubEntries(
            listOf("OEBPS\\outside.txt" to "x".toByteArray()),
        )
        try {
            assertTrue(runCatching { parser.parse(backslash, "backslash.epub") }.exceptionOrNull() is EpubParseException)
        } finally {
            backslash.delete()
        }

        val absolute = createEpubEntries(
            listOf("/outside.txt" to "x".toByteArray()),
        )
        try {
            assertTrue(runCatching { parser.parse(absolute, "absolute.epub") }.exceptionOrNull() is EpubParseException)
        } finally {
            absolute.delete()
        }
    }

    @Test
    fun externalResourcesAndScriptsAreIgnored() {
        val file = createEpub(
            baseEntries(
                chapterOneExtra = """
                    <script>alert('x')</script>
                    <p>脚本不应进入正文。<img src="https://example.com/a.png" alt="远程图片"/></p>
                """.trimIndent(),
                includeNav = true,
            ),
        )
        try {
            val parsed = parser.parse(file, "unsafe.epub")
            assertFalse(parsed.text.contains("alert"))
            assertTrue(parsed.text.contains("脚本不应进入正文"))
            assertTrue(parsed.blocks.any { it.type == BookBlockType.IMAGE && it.resourceId == null })
            assertTrue(parsed.resources.none { it.resource.relativePath.contains("example.com") })
        } finally {
            file.delete()
        }
    }

    @Test
    fun corruptImageBecomesPlaceholderAndBrokenNavFallsBack() {
        val entries = baseEntries(
            chapterOneExtra = """<p><img src="images/pixel.png" alt="坏图"/></p>""",
            includeNav = true,
        ).toMutableMap()
        entries["OEBPS/images/pixel.png"] = byteArrayOf(1, 2, 3)
        entries["OEBPS/nav.xhtml"] = "<not-valid".toByteArray()
        val file = createEpub(entries)
        try {
            val parsed = parser.parse(file, "broken-image.epub")
            assertTrue(parsed.warnings.any { it.contains("不支持的图片格式") })
            assertEquals("第一章", parsed.chapters[0].title)
            assertTrue(parsed.blocks.any { it.type == BookBlockType.IMAGE && it.resourceId == null })
        } finally {
            file.delete()
        }
    }

    @Test
    fun parsesInlineStylesCssListsAndImageLayout() {
        val file = createEpub(
            baseEntries(
                chapterOneExtra = """
                    <style>
                      .center { text-align: center; text-indent: 2em; margin-top: 1em; }
                      .accent { font-weight: bold; text-decoration: underline; }
                      body p { text-align: right; }
                    </style>
                    <p>普通<strong>粗体</strong>和<em>斜体<strong>嵌套</strong></em><u>下划线</u><s>删除线</s><a href="https://example.com">链接</a></p>
                    <p class="center">居中段落</p>
                    <ul><li>列表一</li><li class="accent">列表二</li></ul>
                    <p><img src="images/pixel.png" alt="样式图" style="width:50%;max-width:80%;height:200px"/></p>
                    <p style="font-size:999px;margin:-5px">极端样式降级</p>
                """.trimIndent(),
                includeNav = true,
            ),
        )
        try {
            val parsed = parser.parse(file, "styles.epub")
            val styled = parsed.blocks.first { it.type == BookBlockType.PARAGRAPH && it.spans.isNotEmpty() }
            assertTrue(styled.spans.any { it.bold })
            assertTrue(styled.spans.any { it.italic })
            assertTrue(styled.spans.any { it.bold && it.italic })
            assertTrue(styled.spans.any { it.underline })
            assertTrue(styled.spans.any { it.strike })
            assertTrue(styled.spans.any { it.link })

            val center = parsed.blocks.first { it.type == BookBlockType.PARAGRAPH && it.style?.textAlign != null }
            assertEquals(BookTextAlign.CENTER, center.style?.textAlign)
            assertEquals(2f, center.style?.textIndentEm)
            assertEquals(1f, center.style?.marginTopEm)

            val listItems = parsed.blocks.filter { it.type == BookBlockType.PARAGRAPH && it.style?.listItem == true }
            assertEquals(2, listItems.size)
            assertEquals("列表一", parsed.text.substring(listItems[0].startOffset, listItems[0].endOffset).trim())
            assertTrue(listItems[1].spans.any { it.bold && it.underline })

            val image = parsed.blocks.first { it.type == BookBlockType.IMAGE }
            assertEquals(0.5f, image.style?.imageWidthFraction)
            assertEquals(0.8f, image.style?.imageMaxWidthFraction)
            assertEquals(200f, image.style?.imageHeightDp)

            val extreme = parsed.blocks.first {
                parsed.text.substring(it.startOffset, it.endOffset).contains("极端样式降级")
            }
            assertNull(extreme.style)
            assertEquals(null, extreme.style?.lineHeightMultiplier)
        } finally {
            file.delete()
        }
    }

    private fun baseEntries(
        chapterOneExtra: String,
        includeNav: Boolean,
    ): MutableMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        entries["META-INF/container.xml"] = container().toByteArray()
        entries["OEBPS/content.opf"] = opf(withNcx = false, includeNav = includeNav).toByteArray()
        entries["OEBPS/chapter1.xhtml"] = """
            <html xmlns="http://www.w3.org/1999/xhtml"><body>
            <h1>第一章</h1>
            <p>第一章正文。</p>
            $chapterOneExtra
            <p>末尾。</p>
            </body></html>
        """.trimIndent().toByteArray()
        entries["OEBPS/chapter2.xhtml"] = """
            <html xmlns="http://www.w3.org/1999/xhtml"><body>
            <h1>第二章</h1><p>第二章正文。</p>
            </body></html>
        """.trimIndent().toByteArray()
        entries["OEBPS/images/pixel.png"] = PNG_1X1
        if (includeNav) {
            entries["OEBPS/nav.xhtml"] = nav().toByteArray()
        }
        return entries
    }

    private fun createEpub(entries: Map<String, ByteArray>): File =
        createEpubEntries(entries.entries.map { it.key to it.value })

    private fun createEpubEntries(entries: List<Pair<String, ByteArray>>): File {
        val file = File.createTempFile("epub-test-", ".epub")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun container(): String = """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent()

    private fun opf(withNcx: Boolean, includeNav: Boolean = true): String {
        val navItem = if (includeNav) {
            """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>"""
        } else {
            ""
        }
        val ncxItem = if (withNcx) {
            """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>"""
        } else {
            ""
        }
        val spineToc = if (withNcx) """ toc="ncx"""" else ""
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>测试书名</dc:title>
                <dc:creator>测试作者</dc:creator>
                <dc:language>zh-CN</dc:language>
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                <item id="img" href="images/pixel.png" media-type="image/png"/>
                $navItem
                $ncxItem
              </manifest>
              <spine$spineToc>
                <itemref idref="c1"/><itemref idref="c2"/>
              </spine>
            </package>
        """.trimIndent()
    }

    private fun nav(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
          <body><nav epub:type="toc"><ol>
            <li><a href="chapter1.xhtml">第一章</a></li>
            <li><a href="chapter2.xhtml">第二章</a></li>
          </ol></nav></body>
        </html>
    """.trimIndent()

    private fun ncx(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
          <navMap>
            <navPoint id="n1"><navLabel><text>第一章 NCX</text></navLabel><content src="chapter1.xhtml"/></navPoint>
            <navPoint id="n2"><navLabel><text>第二章 NCX</text></navLabel><content src="chapter2.xhtml"/></navPoint>
          </navMap>
        </ncx>
    """.trimIndent()

    private companion object {
        val PNG_1X1: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
    }
}
