package com.xtt.toolbox.data.importing

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.model.SourceType
import com.xtt.toolbox.domain.importing.EncodingConfidence
import com.xtt.toolbox.domain.importing.TxtImportPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BatchImportCoordinatorTest {
    @Test
    fun allFilesSucceedAndReportProgress() = runBlocking {
        val progress = mutableListOf<BatchImportProgress>()
        val coordinator = BatchImportCoordinator(
            prepare = { uri -> preview(uri) },
            commit = { _, _ -> true },
            onProgress = { progress += it },
        )
        val summary = coordinator.run(listOf("a", "b", "c"))

        assertEquals(BatchImportSummary(3, 3, 0, 0), summary)
        assertEquals(listOf(1, 2, 3), progress.map { it.completed })
        assertEquals(3, progress.last().completed)
    }

    @Test
    fun oneFailureDoesNotStopFollowingFiles() = runBlocking {
        val attempted = mutableListOf<String>()
        val coordinator = BatchImportCoordinator(
            prepare = { uri ->
                attempted += uri
                if (uri == "broken") throw IllegalStateException("坏文件")
                preview(uri)
            },
            commit = { preview, _ ->
                if (preview.sourceName == "storage.txt") false else true
            },
        )
        val summary = coordinator.run(listOf("ok", "broken", "storage", "last"))

        assertEquals(BatchImportSummary(4, 2, 2, 0), summary)
        assertEquals(listOf("ok", "broken", "storage", "last"), attempted)
    }

    @Test
    fun duplicateFilesAreSkippedAndNotCommitted() = runBlocking {
        var commits = 0
        val coordinator = BatchImportCoordinator(
            prepare = { uri -> preview(uri, duplicate = uri == "dup") },
            commit = { _, _ -> commits++; true },
        )
        val summary = coordinator.run(listOf("dup", "new"))

        assertEquals(BatchImportSummary(2, 1, 0, 1), summary)
        assertEquals(1, commits)
    }

    @Test
    fun cancellationIsPropagated() {
        runBlocking {
            val coordinator = BatchImportCoordinator(
                prepare = { throw CancellationException("cancel") },
                commit = { _, _ -> true },
            )

            assertThrows(CancellationException::class.java) {
                runBlocking { coordinator.run(listOf("a")) }
            }
        }
    }

    private fun preview(name: String, duplicate: Boolean = false): TxtImportPreview =
        TxtImportPreview(
            sourceUri = "content://$name",
            sourceName = "$name.txt",
            defaultTitle = name,
            originalSizeBytes = 4,
            encoding = "UTF-8",
            encodingConfidence = EncodingConfidence.HIGH,
            hadBom = false,
            text = "正文",
            chapters = emptyList(),
            contentHash = "hash-$name",
            contentSizeBytes = 4,
            duplicateBook = if (duplicate) {
                Book(
                    bookId = "dup-$name",
                    title = name,
                    shelfType = ShelfType.READING,
                    sourceType = SourceType.IMPORTED,
                    createdAt = 1,
                    updatedAt = 1,
                )
            } else {
                null
            },
            warnings = emptyList(),
            availableEncodings = emptyList(),
        )
}
