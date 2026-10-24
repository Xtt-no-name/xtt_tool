package com.xtt.toolbox.ui.writing

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xtt.toolbox.data.AppContainerProvider
import com.xtt.toolbox.domain.writing.WritingDetail
import com.xtt.toolbox.domain.writing.WritingDocumentConflictException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WritingUiState(
    val detail: WritingDetail? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val pendingChapterId: String? = null,
)

class WritingViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainerProvider.get(application)
    private val catalog = container.writingCatalogService
    private val library = container.writingLibraryService
    private val importService = container.writingImportService
    private val coverService = container.writingCoverService
    private val contentResolver = application.contentResolver

    private val _uiState = MutableStateFlow(WritingUiState())
    val uiState: StateFlow<WritingUiState> = _uiState.asStateFlow()

    fun open(bookId: String) {
        runOperation {
            val detail = catalog.load(bookId).getOrThrow()
            _uiState.update { it.copy(detail = detail, loading = false) }
        }
    }

    fun closeDetail() {
        _uiState.value = WritingUiState()
    }

    fun createBlank(title: String) {
        runOperation(busy = true) {
            val detail = catalog.createBlank(title).getOrThrow()
            _uiState.update { it.copy(detail = detail) }
        }
    }

    fun importExternal(uri: Uri) {
        runOperation(busy = true) {
            val detail = importService.import(uri.toString()).getOrThrow()
            _uiState.update {
                it.copy(detail = detail, message = "导入完成：${detail.book.title}")
            }
        }
    }

    fun moveFromReading(bookId: String) {
        runOperation(busy = true) {
            val detail = library.moveToWriting(bookId).getOrThrow()
            _uiState.update {
                it.copy(detail = detail, message = "已转入写作书架")
            }
        }
    }

    fun copyFromReading(bookId: String) {
        runOperation(busy = true) {
            val detail = library.copyToWriting(bookId).getOrThrow()
            _uiState.update {
                it.copy(detail = detail, message = "已创建可编辑副本")
            }
        }
    }

    fun createVolume(title: String) {
        withDetail { detail ->
            catalog.createVolume(detail.book.bookId, title)
        }
    }

    fun renameVolume(volumeId: String, title: String) {
        withDetail {
            catalog.renameVolume(volumeId, title)
        }
    }

    fun deleteVolume(volumeId: String) {
        withDetail {
            catalog.deleteVolume(volumeId)
        }
    }

    fun addChapter(title: String, volumeId: String?) {
        val detail = _uiState.value.detail ?: return
        runOperation(busy = true) {
            val updated = catalog.addChapter(detail.book.bookId, title, volumeId).getOrThrow()
            val newChapterId = updated.document.document.chapters.lastOrNull()?.chapterId
            _uiState.update {
                it.copy(detail = updated, pendingChapterId = newChapterId)
            }
        }
    }

    fun consumePendingChapter() {
        _uiState.update { it.copy(pendingChapterId = null) }
    }

    fun renameChapter(chapterId: String, title: String) {
        withDetail { detail ->
            catalog.renameChapter(detail.book.bookId, chapterId, title)
        }
    }

    fun deleteChapter(chapterId: String) {
        withDetail { detail ->
            catalog.deleteChapter(detail.book.bookId, chapterId)
        }
    }

    fun moveChapter(chapterId: String, volumeId: String?) {
        withDetail { detail ->
            catalog.moveChapter(detail.book.bookId, chapterId, volumeId)
        }
    }

    fun moveChapterUp(chapterId: String) {
        withDetail { detail ->
            catalog.moveChapterUp(detail.book.bookId, chapterId)
        }
    }

    fun moveChapterDown(chapterId: String) {
        withDetail { detail ->
            catalog.moveChapterDown(detail.book.bookId, chapterId)
        }
    }

    fun renameBook(title: String) {
        withDetail { detail ->
            catalog.renameBook(detail.book.bookId, title)
        }
    }

    fun setCover(uri: Uri) {
        val bookId = _uiState.value.detail?.book?.bookId ?: return
        runOperation(busy = true) {
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("无法读取封面图片")
            }
            val detail = coverService.setCover(bookId, bytes).getOrThrow()
            _uiState.update { it.copy(detail = detail, message = "封面已更新") }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun withDetail(operation: suspend (WritingDetail) -> Result<WritingDetail>) {
        val detail = _uiState.value.detail ?: return
        runOperation(busy = true) {
            val updated = operation(detail).getOrThrow()
            _uiState.update { it.copy(detail = updated) }
        }
    }

    private fun runOperation(
        busy: Boolean = false,
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = !busy && it.detail == null, busy = busy) }
            try {
                operation()
            } catch (error: Throwable) {
                val message = when {
                    error is WritingDocumentConflictException -> error.message
                    else -> error.message ?: "操作失败"
                }
                _uiState.update { it.copy(message = message) }
            } finally {
                _uiState.update { it.copy(loading = false, busy = false) }
            }
        }
    }
}
