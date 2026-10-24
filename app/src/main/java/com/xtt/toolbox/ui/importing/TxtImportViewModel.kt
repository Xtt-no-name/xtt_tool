package com.xtt.toolbox.ui.importing

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.xtt.toolbox.data.AppContainerProvider
import com.xtt.toolbox.data.importing.BatchImportCoordinator
import com.xtt.toolbox.data.importing.BatchImportSummary
import com.xtt.toolbox.data.importing.MarkdownImageDirectoryStore
import com.xtt.toolbox.data.importing.SharedPreferencesMarkdownImageDirectoryStore
import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.domain.importing.BookImportFormat
import com.xtt.toolbox.domain.importing.BookImportFormatGate
import com.xtt.toolbox.domain.importing.BookImportService
import com.xtt.toolbox.domain.importing.TxtImportException
import com.xtt.toolbox.domain.importing.TxtImportPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TxtImportUiState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val preview: TxtImportPreview? = null,
    val errorMessage: String? = null,
    val availableEncodings: List<String> = emptyList(),
    val importedBook: Book? = null,
)

/**
 * 导入会话状态由 ViewModel 持有，旋转屏幕时预览不会丢失。
 *
 * 读取/解码/写入都在 TxtImportService 的 IO 上执行；本类只负责串行化操作和状态。
 */
class TxtImportViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainerProvider.get(application)
    private val textService: BookImportService = container.importService
    private val epubService: BookImportService = container.epubImportService
    private val markdownService: BookImportService = container.markdownImportService
    private val contentResolver = application.contentResolver
    private val markdownImageDirectoryStore = SharedPreferencesMarkdownImageDirectoryStore(application)
    private val _uiState = MutableStateFlow(TxtImportUiState())
    val uiState: StateFlow<TxtImportUiState> = _uiState.asStateFlow()
    private val _batchState = MutableStateFlow(BatchImportUiState())
    val batchState: StateFlow<BatchImportUiState> = _batchState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJob: Job? = null
    private var batchJob: Job? = null
    private var currentUri: String? = null
    private var currentImageDirectoryUri: String? = null

    fun prepare(uri: Uri) {
        if (_uiState.value.isSaving) return
        currentUri = uri.toString()
        currentImageDirectoryUri = null
        load(uri.toString(), forcedEncoding = null, imageDirectoryUri = null)
    }

    fun attachImageDirectory(uri: Uri?) {
        if (_uiState.value.isSaving) return
        val source = currentUri ?: return
        if (uri == null) return
        currentImageDirectoryUri = uri.toString()
        load(source, forcedEncoding = null, imageDirectoryUri = uri.toString())
    }

    fun retryWithEncoding(encoding: String) {
        if (_uiState.value.isSaving) return
        val uri = currentUri ?: return
        load(uri, forcedEncoding = encoding, imageDirectoryUri = currentImageDirectoryUri)
    }

    /** 多选文件后直接逐个导入；单文件失败不阻断后续文件。 */
    fun startBatchImport(uris: List<Uri>) {
        if (uris.isEmpty() || batchJob?.isActive == true) return
        val targets = uris.map(Uri::toString).distinct()
        if (targets.isEmpty()) return
        _batchState.value = BatchImportUiState(
            isVisible = true,
            isRunning = true,
            total = targets.size,
        )
        batchJob = scope.launch {
            try {
                val coordinator = BatchImportCoordinator(
                    prepare = { uri -> serviceFor(uri).prepare(uri) },
                    commit = { preview, title ->
                        serviceFor(preview.sourceUri).commit(preview, title).isSuccess
                    },
                    onProgress = { progress ->
                        _batchState.update {
                            it.copy(
                                completed = progress.completed,
                                total = progress.total,
                                currentName = progress.currentName,
                            )
                        }
                    },
                )
                val summary = coordinator.run(targets)
                _batchState.update {
                    it.copy(
                        isRunning = false,
                        completed = summary.total,
                        currentName = null,
                        summary = summary,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                val total = _batchState.value.total
                _batchState.update {
                    it.copy(
                        isRunning = false,
                        completed = total,
                        currentName = null,
                        summary = BatchImportSummary(
                            total = total,
                            success = 0,
                            failed = total,
                            skippedDuplicates = 0,
                        ),
                    )
                }
            } finally {
                batchJob = null
            }
        }
    }

    fun dismissBatchImport() {
        if (!_batchState.value.isRunning) {
            _batchState.value = BatchImportUiState()
        }
    }

    fun confirm(title: String) {
        val preview = _uiState.value.preview ?: return
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        if (title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "书名不能为空") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        activeJob = scope.launch {
            val result = serviceFor(preview.sourceUri).commit(preview, title)
            result.fold(
                onSuccess = { book ->
                    if (preview.format == BookFormat.MARKDOWN) {
                        preview.resourceDirectoryUri?.let { directoryUri ->
                            markdownImageDirectoryStore.put(book.bookId, directoryUri)
                        }
                    }
                    currentUri = null
                    _uiState.value = TxtImportUiState(
                        isVisible = false,
                        importedBook = book,
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.userMessage(),
                        )
                    }
                },
            )
        }
    }

    fun cancel() {
        if (_uiState.value.isSaving) return
        activeJob?.cancel()
        activeJob = null
        currentUri = null
        currentImageDirectoryUri = null
        _uiState.value = TxtImportUiState()
    }

    fun consumeImported() {
        _uiState.update { it.copy(importedBook = null) }
    }

    private fun load(
        uri: String,
        forcedEncoding: String?,
        imageDirectoryUri: String?,
    ) {
        if (_uiState.value.isSaving) return
        activeJob?.cancel()
        _uiState.value = TxtImportUiState(
            isVisible = true,
            isLoading = true,
            availableEncodings = emptyList(),
        )
        val displayName = queryDisplayName(uri)
        if (!BookImportFormatGate.isAllowed(displayName)) {
            _uiState.value = TxtImportUiState(
                isVisible = true,
                errorMessage = BookImportFormatGate.rejectionMessage(displayName),
            )
            return
        }
        activeJob = scope.launch {
            try {
                val preview = serviceFor(uri).prepareWithResources(
                    uri = uri,
                    forcedEncoding = forcedEncoding,
                    resourceDirectoryUri = imageDirectoryUri,
                )
                _uiState.value = TxtImportUiState(
                    isVisible = true,
                    preview = preview,
                    availableEncodings = preview.availableEncodings,
                )
            } catch (error: TxtImportException) {
                _uiState.value = TxtImportUiState(
                    isVisible = true,
                    errorMessage = error.userMessage(),
                    availableEncodings = error.availableEncodings,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.value = TxtImportUiState(
                    isVisible = true,
                    errorMessage = error.userMessage(),
                )
            }
        }
    }

    private fun serviceFor(uri: String): BookImportService {
        return when (BookImportFormatGate.fromFileName(queryDisplayName(uri))) {
            BookImportFormat.EPUB -> epubService
            BookImportFormat.MARKDOWN -> markdownService
            else -> textService
        }
    }

    private fun queryDisplayName(uri: String): String? =
        contentResolver.query(
            Uri.parse(uri),
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "导入失败，请重试"

    override fun onCleared() {
        activeJob?.cancel()
        scope.cancel()
        super.onCleared()
    }
}
