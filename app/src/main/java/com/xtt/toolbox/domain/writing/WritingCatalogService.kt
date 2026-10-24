package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookVolume
import com.xtt.toolbox.data.repository.BookRepository
import java.util.UUID

data class WritingDetail(
    val document: WritingDocument,
    val volumes: List<BookVolume>,
) {
    val book: Book get() = document.book
}

/**
 * 作品目录用例：章节/分卷操作只通过 WritingDocument 快照和 WritingDocumentService 保存。
 * UI 不直接操作章节表或 revision 文件。
 */
class WritingCatalogService(
    private val repository: BookRepository,
    private val documentService: WritingDocumentService,
) {
    suspend fun load(bookId: String): Result<WritingDetail> =
        documentService.load(bookId).map { document ->
            WritingDetail(document, repository.loadVolumes(bookId))
        }

    suspend fun createBlank(title: String): Result<WritingDetail> =
        documentService.createBlank(title).map { document ->
            WritingDetail(document, repository.loadVolumes(document.book.bookId))
        }

    suspend fun createVolume(bookId: String, title: String): Result<WritingDetail> {
        val normalized = title.trim()
        if (normalized.isEmpty()) return Result.failure(IllegalArgumentException("分卷名称不能为空"))
        val current = load(bookId).getOrElse { return Result.failure(it) }
        val order = (current.volumes.maxOfOrNull { it.order } ?: -1) + 1
        val volume = BookVolume(
            volumeId = UUID.randomUUID().toString(),
            bookId = bookId,
            title = normalized,
            order = order,
            createdAt = System.currentTimeMillis(),
        )
        repository.insertVolume(volume).getOrElse { return Result.failure(it) }
        return load(bookId)
    }

    suspend fun renameVolume(volumeId: String, title: String): Result<WritingDetail> {
        val normalized = title.trim()
        if (normalized.isEmpty()) return Result.failure(IllegalArgumentException("分卷名称不能为空"))
        val bookId = findBookIdForVolume(volumeId)
            ?: return Result.failure(IllegalArgumentException("分卷不存在"))
        repository.renameVolume(volumeId, normalized).getOrElse { return Result.failure(it) }
        return load(bookId)
    }

    suspend fun deleteVolume(volumeId: String): Result<WritingDetail> {
        val bookId = findBookIdForVolume(volumeId)
            ?: return Result.failure(IllegalArgumentException("分卷不存在"))
        val current = load(bookId).getOrElse { return Result.failure(it) }
        val updated = current.document.document.chapters.fold(current.document) { doc, chapter ->
            if (chapter.volumeId == volumeId) {
                WritingDocumentEditor.moveChapterToVolume(doc, chapter.chapterId, null)
            } else {
                doc
            }
        }
        val save = saveDocument(updated)
        save.getOrElse { return Result.failure(it) }
        repository.deleteVolume(volumeId).getOrElse { return Result.failure(it) }
        return load(bookId)
    }

    suspend fun addChapter(
        bookId: String,
        title: String,
        volumeId: String?,
    ): Result<WritingDetail> {
        val current = load(bookId).getOrElse { return Result.failure(it) }
        if (volumeId != null && current.volumes.none { it.volumeId == volumeId }) {
            return Result.failure(IllegalArgumentException("分卷不属于当前作品"))
        }
        val updated = try {
            WritingDocumentEditor.addChapter(
                current.document,
                title = title,
                volumeId = volumeId,
            )
        } catch (error: Throwable) {
            return Result.failure(error)
        }
        return saveAndReload(updated)
    }

    suspend fun renameChapter(
        bookId: String,
        chapterId: String,
        title: String,
    ): Result<WritingDetail> {
        val current = load(bookId).getOrElse { return Result.failure(it) }
        val updated = runCatching {
            WritingDocumentEditor.renameChapter(current.document, chapterId, title)
        }.getOrElse { return Result.failure(it) }
        return saveAndReload(updated)
    }

    suspend fun deleteChapter(bookId: String, chapterId: String): Result<WritingDetail> {
        val current = load(bookId).getOrElse { return Result.failure(it) }
        val updated = runCatching {
            WritingDocumentEditor.deleteChapter(current.document, chapterId)
        }.getOrElse { return Result.failure(it) }
        return saveAndReload(updated)
    }

    suspend fun moveChapter(
        bookId: String,
        chapterId: String,
        volumeId: String?,
    ): Result<WritingDetail> {
        val current = load(bookId).getOrElse { return Result.failure(it) }
        if (volumeId != null && current.volumes.none { it.volumeId == volumeId }) {
            return Result.failure(IllegalArgumentException("分卷不属于当前作品"))
        }
        val updated = runCatching {
            WritingDocumentEditor.moveChapterToVolume(current.document, chapterId, volumeId)
        }.getOrElse { return Result.failure(it) }
        return saveAndReload(updated)
    }

    suspend fun reorderChapters(bookId: String, orderedChapterIds: List<String>): Result<WritingDetail> {
        val current = load(bookId).getOrElse { return Result.failure(it) }
        val updated = runCatching {
            WritingDocumentEditor.reorderChapters(current.document, orderedChapterIds)
        }.getOrElse { return Result.failure(it) }
        return saveAndReload(updated)
    }

    suspend fun saveEditedChapter(
        bookId: String,
        draft: WritingChapterDraft,
    ): Result<WritingDetail> {
        val current = load(bookId).getOrElse { return Result.failure(it) }
        val updated = runCatching {
            WritingChapterEditor.toDocument(current.document, draft)
        }.getOrElse { return Result.failure(it) }
        return saveAndReload(updated)
    }

    suspend fun moveChapterUp(bookId: String, chapterId: String): Result<WritingDetail> {
        val current = load(bookId).getOrElse { return Result.failure(it) }
        val ids = current.document.document.chapters.map { it.chapterId }.toMutableList()
        val index = ids.indexOf(chapterId)
        if (index <= 0) return Result.success(current)
        ids[index] = ids[index - 1].also { ids[index - 1] = ids[index] }
        return reorderChapters(bookId, ids)
    }

    suspend fun moveChapterDown(bookId: String, chapterId: String): Result<WritingDetail> {
        val current = load(bookId).getOrElse { return Result.failure(it) }
        val ids = current.document.document.chapters.map { it.chapterId }.toMutableList()
        val index = ids.indexOf(chapterId)
        if (index < 0 || index >= ids.lastIndex) return Result.success(current)
        ids[index] = ids[index + 1].also { ids[index + 1] = ids[index] }
        return reorderChapters(bookId, ids)
    }

    suspend fun renameBook(bookId: String, title: String): Result<WritingDetail> {
        val current = load(bookId).getOrElse { return Result.failure(it) }
        val updated = runCatching {
            WritingDocumentEditor.renameBook(current.document, title)
        }.getOrElse { return Result.failure(it) }
        return saveAndReload(updated)
    }

    private suspend fun saveAndReload(document: WritingDocument): Result<WritingDetail> {
        val save = documentService.saveChecked(document)
        return when (save) {
            is WritingSaveResult.Saved -> load(save.document.book.bookId)
            is WritingSaveResult.Conflict -> Result.failure(
                WritingDocumentConflictException(save.message),
            )

            is WritingSaveResult.Failed -> Result.failure(
                save.cause ?: IllegalStateException(save.message),
            )
        }
    }

    private suspend fun saveDocument(document: WritingDocument): Result<Unit> {
        return when (val result = documentService.saveChecked(document)) {
            is WritingSaveResult.Saved -> Result.success(Unit)
            is WritingSaveResult.Conflict -> Result.failure(
                WritingDocumentConflictException(result.message),
            )

            is WritingSaveResult.Failed -> Result.failure(
                result.cause ?: IllegalStateException(result.message),
            )
        }
    }

    private suspend fun findBookIdForVolume(volumeId: String): String? {
        return repository.books.value.firstNotNullOfOrNull { book ->
            if (repository.loadVolumes(book.bookId).any { it.volumeId == volumeId }) {
                book.bookId
            } else {
                null
            }
        }
    }
}
