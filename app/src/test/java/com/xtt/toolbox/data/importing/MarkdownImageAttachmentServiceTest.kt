package com.xtt.toolbox.data.importing

import com.xtt.toolbox.data.content.FileBookContentStore
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.InMemoryBookRepository
import com.xtt.toolbox.domain.content.BookBlock
import com.xtt.toolbox.domain.content.BookBlockStyle
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookDocument
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class MarkdownImageAttachmentServiceTest {
    private lateinit var root: File
    private lateinit var contentStore: FileBookContentStore
    private lateinit var repository: InMemoryBookRepository
    private lateinit var directories: FakeDirectoryStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("xtt-md-image-attach").toFile()
        contentStore = FileBookContentStore(root)
        repository = InMemoryBookRepository()
        directories = FakeDirectoryStore()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun attachesImagesAndReusesSameResourceOnRepeat() = runBlocking {
        val book = book("book-1")
        repository.insertDocument(book, document("images/cover.png"))
        val reader = FakeReader(mapOf("images/cover.png" to PNG))
        val service = service(reader)

        val first = service.attach("book-1", "content://tree").getOrThrow()
        assertEquals(1, first.referenced)
        assertEquals(1, first.imported)
        val loaded = repository.loadDocument("book-1")!!
        val resource = loaded.resources.single()
        assertNotNull(loaded.blocks.single().resourceId)
        assertTrue(contentStore.resolveBookResource("book-1", resource.relativePath)?.isFile == true)
        assertEquals("content://tree", directories.get("book-1"))

        val second = service.attach("book-1", "content://tree").getOrThrow()
        assertEquals(0, second.imported)
        assertEquals(1, repository.loadDocument("book-1")!!.resources.size)
    }

    @Test
    fun missingAndUnsupportedImagesDoNotBlockOtherImages() = runBlocking {
        val reader = FakeReader(
            mapOf(
                "images/good.png" to PNG,
                "images/bad.bin" to byteArrayOf(1, 2, 3),
            ),
        )
        val document = BookDocument(
            chapters = emptyList(),
            blocks = listOf(
                imageBlock("a", 0, "images/good.png"),
                imageBlock("b", 1, "images/bad.bin"),
                imageBlock("c", 2, "images/missing.png"),
            ),
            resources = emptyList(),
        )
        repository.insertDocument(book("book-2"), document)
        val result = service(reader).attach("book-2", "content://tree").getOrThrow()

        assertEquals(3, result.referenced)
        assertEquals(1, result.imported)
        assertEquals(1, result.missing)
        assertEquals(1, result.unsupported)
        val loaded = repository.loadDocument("book-2")!!
        assertEquals(1, loaded.resources.size)
        assertEquals(2, loaded.blocks.count { it.resourceId == null })
    }

    @Test
    fun changingDirectoryReplacesResourceAndDeletesOldFile() = runBlocking {
        repository.insertDocument(book("book-3"), document("images/cover.png"))
        val reader = FakeReader(mapOf("images/cover.png" to PNG))
        val service = service(reader)
        service.attach("book-3", "content://tree-a").getOrThrow()
        val oldPath = repository.loadDocument("book-3")!!.resources.single().relativePath
        assertTrue(contentStore.resolveBookResource("book-3", oldPath)?.isFile == true)

        reader.files = mapOf("images/cover.png" to OTHER_PNG)
        service.attach("book-3", "content://tree-b").getOrThrow()
        val newPath = repository.loadDocument("book-3")!!.resources.single().relativePath
        assertTrue(contentStore.resolveBookResource("book-3", oldPath) == null)
        assertTrue(contentStore.resolveBookResource("book-3", newPath)?.isFile == true)
    }

    private fun service(reader: FakeReader): MarkdownImageAttachmentService =
        MarkdownImageAttachmentService(repository, contentStore, reader, directories)

    private fun book(id: String): Book = Book(
        bookId = id,
        title = id,
        shelfType = ShelfType.READING,
        sourceType = SourceType.IMPORTED,
        format = BookFormat.MARKDOWN,
        contentLocation = "books/$id/content.txt",
        createdAt = 1,
        updatedAt = 1,
    )

    private fun document(path: String): BookDocument = BookDocument(
        chapters = emptyList(),
        blocks = listOf(imageBlock("image", 0, path)),
        resources = emptyList(),
    )

    private fun imageBlock(id: String, order: Int, path: String): BookBlock = BookBlock(
        blockId = id,
        order = order,
        type = BookBlockType.IMAGE,
        startOffset = 0,
        endOffset = 0,
        style = BookBlockStyle(imageSourcePath = path),
        alt = "图片",
    )

    private class FakeReader(var files: Map<String, ByteArray>) : MarkdownResourceReader {
        override fun probe(directoryUri: String, relativePath: String): MarkdownResourceInfo? =
            files[relativePath]?.let { MarkdownResourceInfo(relativePath, it.size.toLong(), "image/png") }

        override fun read(directoryUri: String, relativePath: String): ByteArray? = files[relativePath]
    }

    private class FakeDirectoryStore : MarkdownImageDirectoryStore {
        private val values = mutableMapOf<String, String>()
        override fun get(bookId: String): String? = values[bookId]
        override fun put(bookId: String, directoryUri: String) {
            values[bookId] = directoryUri
        }

        override fun remove(bookId: String) {
            values.remove(bookId)
        }
    }

    private companion object {
        val PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
        val OTHER_PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Zl1sAAAAASUVORK5CYII=",
        )
    }
}
