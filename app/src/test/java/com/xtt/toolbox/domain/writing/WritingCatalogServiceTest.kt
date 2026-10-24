package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.content.FileBookContentStore
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.data.repository.InMemoryBookRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class WritingCatalogServiceTest {
    private lateinit var root: File
    private lateinit var store: FileBookContentStore
    private lateinit var repository: InMemoryBookRepository
    private lateinit var catalog: WritingCatalogService

    @Before
    fun setUp() {
        root = Files.createTempDirectory("xtt-writing-catalog").toFile()
        store = FileBookContentStore(root)
        repository = InMemoryBookRepository()
        catalog = WritingCatalogService(
            repository = repository,
            documentService = WritingDocumentService(repository, store),
        )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun saveEditedChapterRoundTripsFormattingAndText() = runBlocking {
        val created = catalog.createBlank("编辑闭环").getOrThrow()
        val bookId = created.book.bookId
        val chapterId = created.document.document.chapters.single().chapterId
        var draft = WritingChapterEditor.updateText(
            WritingChapterEditor.load(created.document, chapterId)!!,
            "第一章\n正文",
        )
        draft = WritingChapterEditor.addInlineStyle(draft, 4, 6, bold = true)
        val saved = catalog.saveEditedChapter(bookId, draft).getOrThrow()
        val loaded = WritingChapterEditor.load(saved.document, chapterId)!!
        assertEquals("第一章\n正文", loaded.text)
        assertTrue(loaded.spans.any { it.bold })
        assertEquals(2, loaded.lineTypes.size)
    }

    @Test
    fun volumeAndChapterOperationsPersistVolumeAssignment() = runBlocking {
        val created = catalog.createBlank("目录作品").getOrThrow()
        val bookId = created.book.bookId
        val volume = catalog.createVolume(bookId, "第一卷").getOrThrow().volumes.single()
        val withChapter = catalog.addChapter(bookId, "第一章", volume.volumeId).getOrThrow()
        val chapter = withChapter.document.document.chapters.last()
        assertEquals(volume.volumeId, chapter.volumeId)

        val moved = catalog.moveChapter(bookId, chapter.chapterId, null).getOrThrow()
        assertEquals(null, moved.document.document.chapters.last().volumeId)

        val renamed = catalog.renameChapter(bookId, chapter.chapterId, "改名章节").getOrThrow()
        assertEquals("改名章节", renamed.document.document.chapters.last().title)

        val reordered = catalog.moveChapterUp(bookId, renamed.document.document.chapters.last().chapterId)
            .getOrThrow()
        assertTrue(reordered.document.document.chapters.none { it.volumeId == volume.volumeId })
    }

    @Test
    fun deletingVolumeKeepsChaptersAndMovesThemToUnassigned() = runBlocking {
        val created = catalog.createBlank("删卷作品").getOrThrow()
        val bookId = created.book.bookId
        val volume = catalog.createVolume(bookId, "待删除卷").getOrThrow().volumes.single()
        val withChapter = catalog.addChapter(bookId, "保留章节", volume.volumeId).getOrThrow()
        val chapterId = withChapter.document.document.chapters.last().chapterId

        val result = catalog.deleteVolume(volume.volumeId).getOrThrow()

        assertTrue(result.volumes.isEmpty())
        val chapter = result.document.document.chapters.first { it.chapterId == chapterId }
        assertEquals(null, chapter.volumeId)
        assertEquals("保留章节", chapter.title)
    }

    @Test
    fun chapterOrderOperationsKeepStableIds() = runBlocking {
        val created = catalog.createBlank("排序作品").getOrThrow()
        val bookId = created.book.bookId
        val firstId = created.document.document.chapters.single().chapterId
        val withSecond = catalog.addChapter(bookId, "第二章", null).getOrThrow()
        val secondId = withSecond.document.document.chapters.last().chapterId

        val movedUp = catalog.moveChapterUp(bookId, secondId).getOrThrow()
        assertEquals(listOf(secondId, firstId), movedUp.document.document.chapters.map { it.chapterId })
        val movedDown = catalog.moveChapterDown(bookId, secondId).getOrThrow()
        assertEquals(listOf(firstId, secondId), movedDown.document.document.chapters.map { it.chapterId })
    }

    @Test
    fun wordCountsExcludeChapterHeading() = runBlocking {
        val created = catalog.createBlank("字数作品").getOrThrow()
        val detail = catalog.addChapter(created.book.bookId, "标题不应计入", null).getOrThrow()
        val document = detail.document
        val chapter = document.document.chapters.last()
        assertEquals(0, WritingTextStats.chapterCount(document, chapter.chapterId))
        assertEquals(0, WritingTextStats.totalCount(document))
    }

    @Test
    fun createdDraftHasNoOriginAndEmptyVolumes() = runBlocking {
        val created = catalog.createBlank("新草稿").getOrThrow()
        assertEquals(BookFormat.DRAFT, created.book.format)
        assertEquals(null, created.book.originBookId)
        assertTrue(created.volumes.isEmpty())
    }

    @Test
    fun invalidVolumeFromAnotherBookIsRejected() = runBlocking {
        val created = catalog.createBlank("归属检查").getOrThrow()
        val result = catalog.addChapter(
            bookId = created.book.bookId,
            title = "非法章节",
            volumeId = UUID.randomUUID().toString(),
        )
        assertTrue(result.isFailure)
    }
}
