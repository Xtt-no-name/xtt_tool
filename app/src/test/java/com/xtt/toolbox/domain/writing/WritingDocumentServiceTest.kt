package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.content.FileBookContentStore
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.data.repository.InMemoryBookRepository
import com.xtt.toolbox.data.reading.ReaderService
import com.xtt.toolbox.domain.content.BookDocument
import com.xtt.toolbox.domain.content.ExtractedResource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WritingDocumentServiceTest {
    private lateinit var root: File
    private lateinit var store: FileBookContentStore
    private lateinit var repository: InMemoryBookRepository
    private lateinit var service: WritingDocumentService

    @Before
    fun setUp() {
        root = Files.createTempDirectory("xtt-writing").toFile()
        store = FileBookContentStore(root)
        repository = InMemoryBookRepository()
        service = WritingDocumentService(repository, store)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun createBlankPersistsBookChapterAndContent() = runBlocking {
        val created = service.createBlank("空白作品").getOrThrow()
        val loaded = service.load(created.book.bookId).getOrThrow()

        assertEquals(created.book.bookId, loaded.book.bookId)
        assertEquals("空白作品", loaded.book.title)
        assertEquals("第一章\n", loaded.text)
        assertEquals(1, loaded.document.chapters.size)
        assertEquals("第一章", loaded.document.chapters.single().title)
        assertNotNull(repository.getBook(created.book.bookId))
        assertTrue(store.exists(created.book.contentLocation!!))
    }

    @Test
    fun saveAndReloadKeepsChapterTextAndReaderMetadata() = runBlocking {
        var document = service.createBlank("连载").getOrThrow()
        document = WritingDocumentEditor.addChapter(document, "第二章")
        document = WritingDocumentEditor.renameChapter(document, document.document.chapters[0].chapterId, "开始")
        document = WritingDocumentEditor.addChapter(document, "第三章")
        document = WritingDocumentEditor.reorderChapters(
            document,
            listOf(
                document.document.chapters[2].chapterId,
                document.document.chapters[0].chapterId,
                document.document.chapters[1].chapterId,
            ),
        )

        val saved = service.save(document).getOrThrow()
        val loaded = service.load(saved.book.bookId).getOrThrow()
        assertEquals("第三章\n开始\n第二章\n", loaded.text)
        assertEquals(
            listOf("第三章", "开始", "第二章"),
            loaded.document.chapters.map { it.title },
        )
        val reader = ReaderService(repository, store).load(saved.book.bookId).getOrThrow()
        assertEquals("第三章", reader.document!!.chapters.first().title)
        assertEquals(loaded.text, reader.text)
    }

    @Test
    fun databaseCommitFailureLeavesOldRevisionReadable() = runBlocking {
        var document = service.createBlank("失败恢复").getOrThrow()
        document = WritingDocumentEditor.addChapter(document, "第二章")
        document = service.save(document).getOrThrow()
        val oldText = document.text
        val oldRevision = document.book.contentLocation!!
        val failingRepository = object : BookRepository by repository {
            override suspend fun commitDocument(book: com.xtt.toolbox.data.model.Book, document: BookDocument): Result<Unit> =
                Result.failure(IllegalStateException("模拟数据库失败"))

            override suspend fun commitDocumentIfUnchanged(
                book: com.xtt.toolbox.data.model.Book,
                document: BookDocument,
                expectedContentHash: String?,
            ): Result<Boolean> = Result.failure(IllegalStateException("模拟数据库失败"))
        }
        val failing = WritingDocumentService(failingRepository, store)
        val changed = WritingDocumentEditor.renameChapter(
            document,
            document.document.chapters.first().chapterId,
            "新标题",
        )
        val result = failing.save(changed)
        assertTrue(result.isFailure)

        val loaded = service.load(document.book.bookId).getOrThrow()
        assertEquals(oldText, loaded.text)
        assertTrue(store.exists(oldRevision))
        assertEquals(1, store.listRevisionIds(document.book.bookId).size)
    }

    @Test
    fun revisionWriteFailureLeavesOldRevisionReadable() = runBlocking {
        var document = service.createBlank("写入失败").getOrThrow()
        document = WritingDocumentEditor.addChapter(document, "第二章")
        document = service.save(document).getOrThrow()
        val oldText = document.text
        val oldRevision = document.book.contentLocation!!
        val failingStore = object : com.xtt.toolbox.data.content.BookContentStore by store {
            override fun writeDocumentRevision(
                bookId: String,
                revisionId: String,
                text: String,
                resources: List<ExtractedResource>,
            ): com.xtt.toolbox.data.content.SavedContent =
                throw java.io.IOException("模拟写入失败")
        }
        val failing = WritingDocumentService(repository, failingStore)
        val changed = WritingDocumentEditor.renameChapter(
            document,
            document.document.chapters.first().chapterId,
            "新标题",
        )
        assertTrue(failing.save(changed).isFailure)

        val loaded = service.load(document.book.bookId).getOrThrow()
        assertEquals(oldText, loaded.text)
        assertTrue(store.exists(oldRevision))
    }

    @Test
    fun repeatedSaveDoesNotDuplicateChaptersOrRevisions() = runBlocking {
        var document = service.createBlank("重复保存").getOrThrow()
        document = WritingDocumentEditor.addChapter(document, "第二章")
        val first = service.save(document).getOrThrow()
        val second = service.save(first).getOrThrow()
        val loaded = service.load(second.book.bookId).getOrThrow()

        assertEquals(2, loaded.document.chapters.size)
        assertEquals(1, store.listRevisionIds(second.book.bookId).size)
        assertEquals(second.book.contentLocation, loaded.book.contentLocation)
    }

    @Test
    fun saveCopiesImageResourceIntoNewRevision() = runBlocking {
        var document = service.createBlank("图片作品").getOrThrow()
        val oldDirectory = document.book.contentLocation!!.substringBeforeLast('/')
        val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val imageFile = File(root, "$oldDirectory/images/cover.png")
        imageFile.parentFile?.mkdirs()
        imageFile.writeBytes(imageBytes)
        val resource = com.xtt.toolbox.domain.content.BookResource(
            resourceId = "image-1",
            relativePath = "images/cover.png",
            mediaType = "image/png",
            sizeBytes = imageBytes.size.toLong(),
            sha256 = "hash",
            kind = com.xtt.toolbox.domain.content.BookResourceKind.IMAGE,
            alt = "封面",
        )
        val chapter = document.document.chapters.single()
        val image = com.xtt.toolbox.domain.content.BookBlock(
            blockId = "image-block",
            order = 0,
            type = com.xtt.toolbox.domain.content.BookBlockType.IMAGE,
            startOffset = chapter.startOffset,
            endOffset = chapter.startOffset,
            resourceId = resource.resourceId,
            alt = "封面",
        )
        document = document.copy(
            document = document.document.copy(
                blocks = (document.document.blocks + image)
                    .sortedWith(compareBy({ it.startOffset }, { it.type == com.xtt.toolbox.domain.content.BookBlockType.HEADING }))
                    .mapIndexed { index, block -> block.copy(order = index) },
                resources = listOf(resource),
            ),
        )

        val saved = service.save(document).getOrThrow()
        val newImage = File(
            root,
            saved.book.contentLocation!!.substringBeforeLast('/') + "/images/cover.png",
        )
        assertTrue(newImage.isFile)
        assertTrue(newImage.readBytes().contentEquals(imageBytes))
        val reader = ReaderService(repository, store).load(saved.book.bookId).getOrThrow()
        val readerImage = reader.imagePaths[resource.resourceId]
        assertNotNull(readerImage)
        assertTrue(readerImage!!.replace('\\', '/').endsWith("images/cover.png"))
    }

    @Test
    fun staleSnapshotIsRejectedInsteadOfOverwritingNewerSave() = runBlocking {
        var document = service.createBlank("冲突保护").getOrThrow()
        document = WritingDocumentEditor.addChapter(document, "第二章")
        val stale = document
        val current = service.save(document).getOrThrow()
        val currentText = current.text

        val result = service.saveChecked(stale)
        assertTrue(result is WritingSaveResult.Conflict)
        assertEquals(currentText, service.load(current.book.bookId).getOrThrow().text)
        assertEquals(1, store.listRevisionIds(current.book.bookId).size)

        val legacyResult = service.save(stale)
        assertTrue(legacyResult.isFailure)
        assertTrue(legacyResult.exceptionOrNull() is WritingDocumentConflictException)
    }

    @Test
    fun conditionalCommitConflictCleansNewRevision() = runBlocking {
        var document = service.createBlank("条件冲突").getOrThrow()
        document = WritingDocumentEditor.addChapter(document, "第二章")
        val racingRepository = object : BookRepository by repository {
            override suspend fun commitDocumentIfUnchanged(
                book: com.xtt.toolbox.data.model.Book,
                document: BookDocument,
                expectedContentHash: String?,
            ): Result<Boolean> = Result.success(false)
        }
        val racing = WritingDocumentService(racingRepository, store)
        val result = racing.saveChecked(document)

        assertTrue(result is WritingSaveResult.Conflict)
        assertEquals(1, store.listRevisionIds(document.book.bookId).size)
        assertEquals(
            "第一章\n",
            service.load(document.book.bookId).getOrThrow().text,
        )
    }

    @Test
    fun oldRevisionCleanupFailureDoesNotInvalidateNewVersion() = runBlocking {
        var document = service.createBlank("清理失败").getOrThrow()
        document = WritingDocumentEditor.addChapter(document, "第二章")
        val cleanupFailingStore = object : com.xtt.toolbox.data.content.BookContentStore by store {
            override fun deleteRevision(bookId: String, revisionId: String): Boolean =
                throw java.io.IOException("模拟旧 revision 清理失败")
        }
        val cleanupFailing = WritingDocumentService(repository, cleanupFailingStore)
        val saved = cleanupFailing.save(document).getOrThrow()

        assertEquals(document.text, service.load(saved.book.bookId).getOrThrow().text)
        assertTrue(store.exists(saved.book.contentLocation!!))
        assertTrue(store.listRevisionIds(saved.book.bookId).size >= 1)
    }

    @Test
    fun blankDraftHasDraftFormatAndNoOrigin() = runBlocking {
        val created = service.createBlank("草稿格式").getOrThrow()
        assertEquals(com.xtt.toolbox.data.model.BookFormat.DRAFT, created.book.format)
        assertEquals(null, created.book.originBookId)
    }

    @Test
    fun invalidDocumentIsRejectedWithoutReplacingOldRevision() = runBlocking {
        val created = service.createBlank("非法模型").getOrThrow()
        val oldText = created.text
        val invalid = created.copy(
            document = created.document.copy(chapters = emptyList()),
        )
        assertTrue(service.save(invalid).isFailure)
        assertEquals(oldText, service.load(created.book.bookId).getOrThrow().text)
    }

    private fun FileBookContentStore.resolveRevisionFiles(bookId: String): List<File> =
        File(root, "books/$bookId/revisions")
            .listFiles()
            ?.filter { it.isDirectory }
            .orEmpty()

    private fun FileBookContentStore.listRevisionIds(bookId: String): List<String> =
        resolveRevisionFiles(bookId).map { it.name }
}
