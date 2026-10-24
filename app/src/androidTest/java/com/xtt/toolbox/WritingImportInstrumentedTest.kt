package com.xtt.toolbox

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xtt.toolbox.data.content.FileBookContentStore
import com.xtt.toolbox.data.importing.AndroidTxtDocumentSource
import com.xtt.toolbox.data.importing.EpubImportService
import com.xtt.toolbox.data.importing.TxtImportService
import com.xtt.toolbox.data.importing.WritingImportService
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.repository.InMemoryBookRepository
import com.xtt.toolbox.domain.writing.WritingDocumentService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class WritingImportInstrumentedTest {
    private lateinit var context: Context
    private lateinit var root: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.cacheDir, "writing-import-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun importsTxtAsDraftWithInternalContent() = runBlocking {
        val file = File(root, "imported.txt").apply {
            writeText("第一行\n第二行", Charsets.UTF_8)
        }
        val service = service()
        val detail = service.import(Uri.fromFile(file).toString()).getOrThrow()

        assertEquals(BookFormat.DRAFT, detail.book.format)
        assertEquals("imported", detail.book.title)
        assertTrue(detail.document.text.contains("第二行"))
        assertTrue(detail.document.book.contentLocation!!.contains("/revisions/"))
    }

    @Test
    fun importsEpubAsDraftAndKeepsImageResource() = runBlocking {
        val file = File(root, "imported.epub")
        createEpub(file)
        val service = service()
        val detail = service.import(Uri.fromFile(file).toString()).getOrThrow()

        assertEquals(BookFormat.DRAFT, detail.book.format)
        assertTrue(detail.document.document.resources.isNotEmpty())
        assertTrue(detail.document.document.blocks.any { it.resourceId != null })
    }

    private fun service(): WritingImportService {
        val store = FileBookContentStore(File(root, "library"))
        val repository = InMemoryBookRepository()
        val resolver = context.contentResolver
        val txt = TxtImportService(
            source = AndroidTxtDocumentSource(resolver),
            contentStore = store,
            repository = repository,
        )
        val epub = EpubImportService(
            resolver = resolver,
            cacheDir = root,
            contentStore = store,
            repository = repository,
        )
        return WritingImportService(
            resolver = resolver,
            txtImportService = txt,
            epubImportService = epub,
            repository = repository,
            documentService = WritingDocumentService(repository, store),
        )
    }

    private fun createEpub(target: File) {
        ZipOutputStream(target.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            entry(
                "OEBPS/content.opf",
                """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>导入测试</dc:title><dc:creator>作者</dc:creator><dc:language>zh</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/><item id="img" href="images/pic.png" media-type="image/png"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            entry(
                "OEBPS/nav.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="chapter1.xhtml">第一章</a></li></ol></nav></body></html>""",
            )
            entry(
                "OEBPS/chapter1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>正文<img src="images/pic.png" alt="图"/></p></body></html>""",
            )
            zip.putNextEntry(ZipEntry("OEBPS/images/pic.png"))
            zip.write(
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                    0x00, 0x00, 0x00, 0x00,
                ),
            )
            zip.closeEntry()
        }
    }
}
