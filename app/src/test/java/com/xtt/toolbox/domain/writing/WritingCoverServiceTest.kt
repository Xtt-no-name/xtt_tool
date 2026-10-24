package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.content.FileBookContentStore
import com.xtt.toolbox.data.repository.InMemoryBookRepository
import com.xtt.toolbox.domain.content.BookResourceKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class WritingCoverServiceTest {
    private lateinit var root: File
    private lateinit var store: FileBookContentStore
    private lateinit var repository: InMemoryBookRepository
    private lateinit var service: WritingCoverService

    @Before
    fun setUp() {
        root = Files.createTempDirectory("xtt-writing-cover").toFile()
        store = FileBookContentStore(root)
        repository = InMemoryBookRepository()
        val documentService = WritingDocumentService(repository, store)
        service = WritingCoverService(
            WritingCatalogService(repository, documentService),
            documentService,
        )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun coverIsCopiedToRevisionAndSurvivesReload() = runBlocking {
        val created = WritingCatalogService(
            repository,
            WritingDocumentService(repository, store),
        ).createBlank("封面作品").getOrThrow()
        val bookId = created.book.bookId
        val bytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )

        val withCover = service.setCover(bookId, bytes).getOrThrow()
        val cover = withCover.document.document.resources.single {
            it.kind == BookResourceKind.COVER
        }
        val contentDir = withCover.document.book.contentLocation!!.substringBeforeLast('/')
        assertTrue(store.resolveResource("$contentDir/${cover.relativePath}")?.isFile == true)

        val reloaded = WritingCatalogService(
            repository,
            WritingDocumentService(repository, store),
        ).load(bookId).getOrThrow()
        assertEquals(cover.resourceId, reloaded.document.document.resources.single {
            it.kind == BookResourceKind.COVER
        }.resourceId)
    }
}
