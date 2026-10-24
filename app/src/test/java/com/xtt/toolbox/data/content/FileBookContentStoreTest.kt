package com.xtt.toolbox.data.content

import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import com.xtt.toolbox.domain.content.ExtractedResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class FileBookContentStoreTest {
    private lateinit var root: File
    private lateinit var store: FileBookContentStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("xtt-content-test").toFile()
        store = FileBookContentStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun savesEpubResourceAndRejectsTraversal() {
        val resource = BookResource(
            resourceId = "res-1",
            relativePath = "epub/resources/res-1.png",
            mediaType = "image/png",
            sizeBytes = 3,
            sha256 = "abc",
            kind = BookResourceKind.IMAGE,
        )
        val saved = store.saveDocument(
            bookId = "epub-book",
            text = "正文",
            resources = listOf(ExtractedResource(resource, byteArrayOf(1, 2, 3))),
        )
        assertTrue(saved.location.endsWith("content.txt"))
        assertTrue(store.resolveResource("books/epub-book/epub/resources/res-1.png")?.isFile == true)

        val bad = resource.copy(
            resourceId = "bad",
            relativePath = "../outside.png",
        )
        assertThrows(IOException::class.java) {
            store.saveDocument(
                bookId = "bad-book",
                text = "正文",
                resources = listOf(ExtractedResource(bad, byteArrayOf(1))),
            )
        }
        assertFalse(File(root, "outside.png").exists())
    }

    @Test
    fun writesUtf8ContentAndMetadata() {
        val text = "第一行\n第二行"
        val saved = store.save("book-1", text)

        assertTrue(store.exists(saved.location))
        assertEquals(text.toByteArray(Charsets.UTF_8).size.toLong(), saved.sizeBytes)
        assertEquals(ContentHash.sha256(text.toByteArray(Charsets.UTF_8)), saved.sha256)
        assertEquals(
            text,
            File(root, saved.location).readText(Charsets.UTF_8),
        )
        assertEquals(text, store.readText(saved.location))
    }

    @Test
    fun cleanupOrphansKeepsCurrentWritingRevisionAndRemovesUnusedRevisions() {
        store.save("writing-1", "旧正文")
        val current = store.writeDocumentRevision(
            bookId = "writing-1",
            revisionId = "current",
            text = "当前正文",
            resources = emptyList(),
        )
        store.writeDocumentRevision(
            bookId = "writing-1",
            revisionId = "orphan",
            text = "孤儿正文",
            resources = emptyList(),
        )

        store.cleanupOrphans(setOf(current.location))

        assertTrue(store.exists(current.location))
        assertFalse(File(root, "books/writing-1/revisions/orphan").exists())
        assertTrue(File(root, "books/writing-1").isDirectory)
    }

    @Test
    fun readMissingOrInvalidPathFails() {
        assertThrows(IOException::class.java) {
            store.readText("books/missing/content.txt")
        }
        assertThrows(IOException::class.java) {
            store.readText("../outside.txt")
        }
    }

    @Test
    fun refusesToOverwriteExistingBookContent() {
        store.save("book-1", "第一次")
        assertThrows(IOException::class.java) {
            store.save("book-1", "第二次")
        }
        assertTrue(store.exists("books/book-1/content.txt"))
    }

    @Test
    fun deletesContentAndEmptyDirectory() {
        val saved = store.save("book-1", "内容")
        store.delete(saved.location)

        assertFalse(store.exists(saved.location))
        assertFalse(File(root, "books/book-1").exists())
    }

    @Test
    fun rejectsPathTraversalOnDelete() {
        val outside = File(root.parentFile, "outside.txt")
        outside.writeText("不要删除")
        store.delete("../outside.txt")

        assertTrue(outside.exists())
        outside.delete()
    }

    @Test
    fun removesContentDirectoriesWithoutMetadata() {
        val kept = store.save("book-1", "保留")
        val orphan = store.save("book-2", "孤儿")

        store.cleanupOrphans(setOf(kept.location))

        assertTrue(store.exists(kept.location))
        assertFalse(store.exists(orphan.location))
        assertFalse(File(root, "books/book-2").exists())
    }

    @Test
    fun orphanCleanupPreservesKnownResourcesAndActiveTempDirectories() {
        val kept = store.save("book-1", "保留")
        val resource = BookResource(
            resourceId = "cover-1",
            relativePath = "epub/resources/cover.png",
            mediaType = "image/png",
            sizeBytes = 1,
            sha256 = "cover",
            kind = BookResourceKind.COVER,
        )
        val epub = store.saveDocument(
            bookId = "epub-1",
            text = "EPUB",
            resources = listOf(ExtractedResource(resource, byteArrayOf(1))),
        )
        val orphan = store.save("orphan-book", "孤儿")
        val activeTemp = File(root, "books/.tmp-doc-active").apply {
            mkdirs()
            File(this, "content.txt").writeText("正在导入")
        }
        val staleTemp = File(root, "books/.tmp-doc-stale").apply {
            mkdirs()
            File(this, "content.txt").writeText("上次中断")
            setLastModified(System.currentTimeMillis() - 2L * 60L * 60L * 1000L)
        }

        store.cleanupOrphans(setOf(kept.location, epub.location))

        assertTrue(store.exists(kept.location))
        assertTrue(store.resolveBookResource("epub-1", resource.relativePath)?.isFile == true)
        assertFalse(store.exists(orphan.location))
        assertTrue(activeTemp.isDirectory)
        assertFalse(staleTemp.exists())
    }
}
