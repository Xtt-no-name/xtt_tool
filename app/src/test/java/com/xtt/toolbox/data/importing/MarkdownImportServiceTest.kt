package com.xtt.toolbox.data.importing

import com.xtt.toolbox.data.content.FileBookContentStore
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.data.repository.InMemoryBookRepository
import com.xtt.toolbox.domain.importing.TxtDocument
import com.xtt.toolbox.domain.importing.TxtDocumentSource
import com.xtt.toolbox.domain.importing.TxtImportException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class MarkdownImportServiceTest {
    private lateinit var root: File
    private lateinit var contentStore: FileBookContentStore
    private lateinit var repository: InMemoryBookRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("xtt-markdown-import").toFile()
        contentStore = FileBookContentStore(root)
        repository = InMemoryBookRepository()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun preparesAndCommitsMarkdownDocument() = runBlocking {
        val service = MarkdownImportService(
            source = MemorySource(
                """
                ---
                title: 元数据标题
                author: 作者
                ---
                # 第一章
                **粗体** 内容
                ```
                # 代码内不是标题
                ```
                """.trimIndent().toByteArray(),
            ),
            contentStore = contentStore,
            repository = repository,
            resourceReader = EmptyReader,
        )

        val preview = service.prepare("content://md", null)
        assertEquals(BookFormat.MARKDOWN, preview.format)
        assertEquals("元数据标题", preview.defaultTitle)
        assertEquals("作者", preview.author)
        assertEquals(1, preview.chapters.size)
        assertNotNull(preview.parsedMarkdown)

        val book = service.commit(preview, "最终书名").getOrThrow()
        assertEquals(BookFormat.MARKDOWN, book.format)
        assertEquals("最终书名", book.title)
        assertEquals("作者", book.author)
        assertTrue(contentStore.exists(book.contentLocation!!))
        val document = repository.loadDocument(book.bookId)
        assertNotNull(document)
        assertEquals(1, document!!.chapters.size)
        assertTrue(document.blocks.any { it.type.name == "CODE" })
        assertEquals(1, repository.books.value.size)
    }

    @Test
    fun rejectsEmptyOrInvalidUtf8Markdown() = runBlocking {
        val empty = MarkdownImportService(
            source = MemorySource(ByteArray(0)),
            contentStore = contentStore,
            repository = repository,
            resourceReader = EmptyReader,
        )
        assertTrue(runCatching { empty.prepare("content://empty", null) }.exceptionOrNull() is TxtImportException)

        val invalid = MarkdownImportService(
            source = MemorySource(byteArrayOf(0xC3.toByte(), 0x28)),
            contentStore = contentStore,
            repository = repository,
            resourceReader = EmptyReader,
        )
        assertTrue(runCatching { invalid.prepare("content://bad", null) }.exceptionOrNull() is TxtImportException)
    }

    @Test
    fun databaseFailureRemovesBookDirectory() = runBlocking {
        val failing = object : BookRepository by repository {
            override suspend fun insertDocument(
                book: com.xtt.toolbox.data.model.Book,
                document: com.xtt.toolbox.domain.content.BookDocument,
            ): Result<Unit> = Result.failure(IOException("模拟 DB 失败"))
        }
        val service = MarkdownImportService(
            source = MemorySource("# 标题\n正文".toByteArray()),
            contentStore = contentStore,
            repository = failing,
            resourceReader = EmptyReader,
        )
        val preview = service.prepare("content://md", null)
        val result = service.commit(preview, "书")
        assertTrue(result.isFailure)
        assertTrue(repository.books.value.isEmpty())
        assertEquals(0, File(root, "books").listFiles()?.size ?: 0)
    }

    @Test
    fun importsAuthorizedImagesWithDuplicateReuseAndCleanupBoundary() = runBlocking {
        val reader = MapReader(
            mapOf(
                "images/cover.png" to PNG,
                "images/中文 图.png" to PNG,
            ),
        )
        val service = MarkdownImportService(
            source = MemorySource(
                "# 图\n![封面](images/cover.png)\n![重复](images/cover.png)\n![中文](images/中文%20图.png)".toByteArray(),
            ),
            contentStore = contentStore,
            repository = repository,
            resourceReader = reader,
        )
        val preview = service.prepareWithResources("content://md", null, "content://tree")
        assertNotNull(preview.markdownImageStats)
        assertEquals(3, preview.markdownImageStats?.total)
        assertEquals(3, preview.markdownImageStats?.accessible)
        assertEquals(0, preview.markdownImageStats?.missing)

        val book = service.commit(preview, "图片书").getOrThrow()
        val document = repository.loadDocument(book.bookId)!!
        assertEquals(2, document.resources.size)
        val imageBlocks = document.blocks.filter { it.type.name == "IMAGE" }
        assertEquals(3, imageBlocks.size)
        assertEquals(imageBlocks[0].resourceId, imageBlocks[1].resourceId)
        assertTrue(imageBlocks.all { it.resourceId != null })
        document.resources.forEach { resource ->
            assertTrue(contentStore.resolveBookResource(book.bookId, resource.relativePath)?.isFile == true)
        }
    }

    @Test
    fun missingOrUnsupportedImagesStayAsPlaceholders() = runBlocking {
        val reader = MapReader(mapOf("images/bad.bin" to byteArrayOf(1, 2, 3)))
        val service = MarkdownImportService(
            source = MemorySource(
                "# 图\n![缺失](images/missing.png)\n![坏图](images/bad.bin)".toByteArray(),
            ),
            contentStore = contentStore,
            repository = repository,
            resourceReader = reader,
        )
        val preview = service.prepareWithResources("content://md", null, "content://tree")
        assertEquals(1, preview.markdownImageStats?.accessible)
        assertEquals(1, preview.markdownImageStats?.missing)
        val book = service.commit(preview, "占位书").getOrThrow()
        val document = repository.loadDocument(book.bookId)!!
        assertEquals(0, document.resources.size)
        assertTrue(document.blocks.filter { it.type.name == "IMAGE" }.all { it.resourceId == null })
    }

    @Test
    fun rejectsUnsupportedExtensionBeforeCreatingAnything() = runBlocking {
        val service = MarkdownImportService(
            source = MemorySource("# 标题\n正文".toByteArray(), "book.docx"),
            contentStore = contentStore,
            repository = repository,
            resourceReader = EmptyReader,
        )
        val error = runCatching { service.prepare("content://bad", null) }.exceptionOrNull()
        assertTrue(error is TxtImportException)
        assertTrue(repository.books.value.isEmpty())
        assertTrue(File(root, "books").listFiles().isNullOrEmpty())
    }

    private class MemorySource(
        val bytes: ByteArray,
        val name: String = "测试.md",
    ) : TxtDocumentSource {
        override suspend fun read(uri: String): TxtDocument = TxtDocument(
            displayName = name,
            sizeBytes = bytes.size.toLong(),
            bytes = bytes,
        )
    }

    private object EmptyReader : MarkdownResourceReader {
        override fun probe(directoryUri: String, relativePath: String): MarkdownResourceInfo? = null
        override fun read(directoryUri: String, relativePath: String): ByteArray? = null
    }

    private class MapReader(
        private val files: Map<String, ByteArray>,
    ) : MarkdownResourceReader {
        override fun probe(directoryUri: String, relativePath: String): MarkdownResourceInfo? =
            files[relativePath]?.let {
                MarkdownResourceInfo(relativePath, it.size.toLong(), "image/png")
            }

        override fun read(directoryUri: String, relativePath: String): ByteArray? =
            files[relativePath]
    }

    private companion object {
        val PNG: ByteArray = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
    }
}
