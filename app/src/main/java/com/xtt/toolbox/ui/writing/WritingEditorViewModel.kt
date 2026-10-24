package com.xtt.toolbox.ui.writing

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xtt.toolbox.data.AppContainerProvider
import com.xtt.toolbox.data.importing.ImportedWritingImage
import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.ExtractedResource
import com.xtt.toolbox.domain.writing.WritingChapterDraft
import com.xtt.toolbox.domain.writing.WritingChapterEditor
import com.xtt.toolbox.domain.writing.WritingChapterImage
import com.xtt.toolbox.domain.writing.WritingDocument
import com.xtt.toolbox.domain.writing.WritingParagraph
import com.xtt.toolbox.domain.writing.WritingPunctuation
import com.xtt.toolbox.domain.writing.WritingSaveResult
import com.xtt.toolbox.domain.writing.WritingSearch
import com.xtt.toolbox.domain.writing.WritingSmartFormat
import com.xtt.toolbox.domain.writing.WritingTextStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID

enum class EditorSaveStatus { SAVED, SAVING, FAILED }

data class WritingPreviewRequest(
    val bookId: String,
    val startOffset: Int,
)

data class WritingEditorUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val bookId: String? = null,
    val chapterId: String? = null,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val draft: WritingChapterDraft? = null,
    val allChapters: List<Pair<String, String>> = emptyList(),
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val wordCount: Int = 0,
    val saveStatus: EditorSaveStatus = EditorSaveStatus.SAVED,
    val message: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showFormatBar: Boolean = true,
    val showQuickBar: Boolean = true,
    val autoPairEnabled: Boolean = true,
    val punctuation: List<String> = emptyList(),
    val settingsMessage: String? = null,
    val imagePaths: Map<String, String> = emptyMap(),
    val lastInsertedImageId: String? = null,
    val findQuery: String = "",
    val replaceQuery: String = "",
    val findMatches: List<IntRange> = emptyList(),
    val findIndex: Int = 0,
    val previewRequest: WritingPreviewRequest? = null,
)

class WritingEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainerProvider.get(application)
    private val catalog = container.writingCatalogService
    private val documentService = container.writingDocumentService
    private val historyRepository = container.writingHistoryRepository
    private val imageImportService = container.writingImageImportService
    private val contentStore = container.contentStore
    private val preferences = application.getSharedPreferences(
        EDITOR_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val settingsStore = container.appSettingsStore

    private val _uiState = MutableStateFlow(
        WritingEditorUiState(
            showFormatBar = preferences.getBoolean(KEY_SHOW_FORMAT_BAR, true),
            showQuickBar = preferences.getBoolean(KEY_SHOW_QUICK_BAR, true),
            autoPairEnabled = preferences.getBoolean(KEY_AUTO_PAIR, true),
            punctuation = loadPunctuation(),
        ),
    )
    val uiState: StateFlow<WritingEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.excludePunctuation.collect { exclude ->
                _uiState.update { state ->
                    state.copy(
                        wordCount = state.draft?.let {
                            WritingChapterEditor.wordCount(it, exclude)
                        } ?: state.wordCount,
                    )
                }
            }
        }
    }

    private var baseDocument: WritingDocument? = null
    private var debounceJob: Job? = null
    private val saveMutex = Mutex()
    private var editGeneration = 0L
    private var history = mutableListOf<HistoryEntry>()
    private var historyIndex = -1
    private val autoCloseOffsets = mutableSetOf<Int>()
    private val pendingImages = mutableMapOf<String, ImportedWritingImage>()

    private data class HistoryEntry(
        val draft: WritingChapterDraft,
        val selectionStart: Int,
        val selectionEnd: Int,
        val autoCloseOffsets: Set<Int>,
    )

    fun open(bookId: String, chapterId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    visible = true,
                    loading = true,
                    bookId = bookId,
                    chapterId = chapterId,
                    message = null,
                )
            }
            val detail = catalog.load(bookId).getOrElse { error ->
                _uiState.update {
                    it.copy(loading = false, message = error.message ?: "作品加载失败")
                }
                return@launch
            }
            val draft = WritingChapterEditor.load(detail.document, chapterId)
            if (draft == null) {
                _uiState.update { it.copy(loading = false, message = "章节不存在") }
                return@launch
            }
            baseDocument = detail.document
            pendingImages.clear()
            val chapter = detail.document.document.chapters.first { it.chapterId == chapterId }
            val initialDraft = if (!draft.text.contains('\n')) {
                WritingChapterEditor.updateText(draft, draft.text + "\n")
            } else {
                draft
            }
            val imagePaths = resolveImagePaths(detail.document, initialDraft)
            val initialSelection = WritingChapterEditor.initialBodyOffset(initialDraft)
            history = mutableListOf()
            historyIndex = -1
            autoCloseOffsets.clear()
            pushHistory(initialDraft, initialSelection, initialSelection)
            _uiState.update {
                it.copy(
                    visible = true,
                    loading = false,
                    bookId = bookId,
                    chapterId = chapterId,
                    bookTitle = detail.book.title,
                    chapterTitle = chapter.title,
                    draft = initialDraft,
                    allChapters = detail.document.document.chapters.map {
                        it.chapterId to it.title
                    },
                    selectionStart = initialSelection,
                    selectionEnd = initialSelection,
                    wordCount = WritingChapterEditor.wordCount(
                        initialDraft,
                        settingsStore.excludePunctuation.value,
                    ),
                    saveStatus = EditorSaveStatus.SAVED,
                    canUndo = false,
                    canRedo = false,
                    message = null,
                    findQuery = "",
                    replaceQuery = "",
                    findMatches = emptyList(),
                    findIndex = 0,
                    imagePaths = imagePaths,
                    lastInsertedImageId = null,
                )
            }
        }
    }

    fun onTextChange(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        isComposing: Boolean = false,
    ) {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (!isComposing &&
            handleParagraphEnter(state, draft, text, selectionStart, selectionEnd)
        ) {
            return
        }
        if (!isComposing && state.autoPairEnabled &&
            handleAutoPair(state, draft, text, selectionStart, selectionEnd)
        ) {
            return
        }
        val updated = WritingChapterEditor.updateText(draft, text)
        if (updated.text == draft.text) {
            _uiState.update {
                it.copy(
                    selectionStart = selectionStart.coerceIn(0, updated.text.length),
                    selectionEnd = selectionEnd.coerceIn(0, updated.text.length),
                )
            }
            return
        }
        val adjustedOffsets = WritingPunctuation.adjustOffsets(draft.text, text, autoCloseOffsets)
        autoCloseOffsets.clear()
        autoCloseOffsets += adjustedOffsets
        applyDraft(updated, selectionStart, selectionEnd, adjustedOffsets)
    }

    private fun handleParagraphEnter(
        state: WritingEditorUiState,
        draft: WritingChapterDraft,
        newText: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): Boolean {
        if (selectionStart != selectionEnd) return false
        val oldText = draft.text
        val oldStart = state.selectionStart
        val oldEnd = state.selectionEnd
        if (newText.length != oldText.length - (oldEnd - oldStart) + 1 ||
            selectionStart != oldStart + 1
        ) {
            return false
        }
        val expected = oldText.substring(0, oldStart) + "\n" + oldText.substring(oldEnd)
        if (newText != expected) return false
        if (!isParagraphRange(draft, oldStart, oldEnd)) return false

        val result = WritingParagraph.replaceSelection(oldText, oldStart, oldEnd)
        val updated = WritingChapterEditor.updateText(draft, result.text)
        val offsets = WritingPunctuation.adjustOffsets(
            oldText,
            result.text,
            autoCloseOffsets,
        )
        autoCloseOffsets.clear()
        autoCloseOffsets += offsets
        applyDraft(updated, result.cursor, result.cursor, offsets)
        return true
    }

    private fun isParagraphRange(
        draft: WritingChapterDraft,
        start: Int,
        end: Int,
    ): Boolean {
        val startLine = draft.text.substring(0, start.coerceIn(0, draft.text.length))
            .count { it == '\n' }
        val endLine = draft.text.substring(0, end.coerceIn(0, draft.text.length))
            .count { it == '\n' }
        for (index in startLine..endLine) {
            if (draft.lineTypes.getOrNull(index) != BookBlockType.PARAGRAPH) return false
        }
        return true
    }

    private fun handleAutoPair(
        state: WritingEditorUiState,
        draft: WritingChapterDraft,
        newText: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): Boolean {
        val edit = WritingPunctuation.applyAutoPair(
            oldText = draft.text,
            oldSelectionStart = state.selectionStart,
            oldSelectionEnd = state.selectionEnd,
            newText = newText,
            newSelectionStart = selectionStart,
            newSelectionEnd = selectionEnd,
            autoCloseOffsets = autoCloseOffsets,
        ) ?: return false
        if (edit.text == draft.text) {
            _uiState.update {
                it.copy(selectionStart = edit.cursor, selectionEnd = edit.cursor)
            }
            return true
        }
        val updated = WritingChapterEditor.updateText(draft, edit.text)
        autoCloseOffsets.clear()
        autoCloseOffsets += edit.autoCloseOffsets
        applyDraft(updated, edit.cursor, edit.cursor, edit.autoCloseOffsets)
        return true
    }

    fun insertPunctuation(value: String) {
        if (value.isEmpty()) return
        val state = _uiState.value
        val draft = state.draft ?: return
        val start = state.selectionStart.coerceIn(0, draft.text.length)
        val end = state.selectionEnd.coerceIn(start, draft.text.length)
        val updated = WritingChapterEditor.replaceText(draft, start, end, value)
        val offsets = WritingPunctuation.adjustOffsetsForRange(
            oldStart = start,
            oldEnd = end,
            replacementLength = value.length,
            offsets = autoCloseOffsets,
        )
        autoCloseOffsets.clear()
        autoCloseOffsets += offsets
        applyDraft(updated, start + value.length, start + value.length, offsets)
    }

    fun importImage(uri: String) {
        val state = _uiState.value
        val bookId = state.bookId ?: return
        val draft = state.draft ?: return
        viewModelScope.launch {
            val imported = imageImportService.import(bookId, uri).getOrElse { error ->
                _uiState.update {
                    it.copy(message = error.message ?: "图片导入失败")
                }
                return@launch
            }
            val anchor = state.selectionStart.coerceIn(0, draft.text.length)
            val image = WritingChapterImage(
                anchor = anchor,
                blockId = "writing-image-block-${UUID.randomUUID()}",
                resourceId = imported.resource.resourceId,
                alt = null,
            )
            pendingImages[imported.resource.resourceId] = imported
            val placement = WritingParagraph.afterImage(draft.text, anchor)
            val updated = WritingChapterEditor.updateText(draft, placement.text).copy(
                images = draft.images + image,
            ).normalized()
            val cursor = placement.cursor
            _uiState.update {
                it.copy(
                    imagePaths = it.imagePaths + (
                        imported.resource.resourceId to imported.stagingFile.absolutePath
                        ),
                    lastInsertedImageId = image.blockId,
                )
            }
            applyDraft(updated, cursor, cursor)
        }
    }

    fun consumeInsertedImageFocus() {
        _uiState.update { it.copy(lastInsertedImageId = null) }
    }

    fun updateChapterTitle(title: String, selectionStart: Int, selectionEnd: Int) {
        val state = _uiState.value
        val draft = state.draft ?: return
        val firstLineEnd = draft.text.indexOf('\n')
        val firstIsHeading = draft.lineTypes.firstOrNull() == BookBlockType.HEADING
        val titleEnd = if (firstLineEnd >= 0) firstLineEnd else draft.text.length
        val updatedText = when {
            firstIsHeading -> draft.text.replaceRange(0, titleEnd, title)
            title.isNotBlank() -> "$title\n${draft.text}"
            else -> draft.text
        }
        var updated = WritingChapterEditor.updateText(draft, updatedText)
        if (title.isNotBlank() || firstIsHeading) {
            val types = updated.lineTypes.toMutableList()
            if (types.isNotEmpty()) {
                types[0] = BookBlockType.HEADING
            }
            updated = updated.copy(lineTypes = types)
        }
        val cursor = selectionStart.coerceIn(0, title.length)
        applyDraft(updated, cursor, selectionEnd.coerceIn(cursor, title.length))
        _uiState.update { it.copy(chapterTitle = title) }
    }

    fun deleteImage(blockId: String) {
        val state = _uiState.value
        val draft = state.draft ?: return
        val image = draft.images.firstOrNull { it.blockId == blockId } ?: return
        val updated = draft.copy(images = draft.images.filterNot { it.blockId == blockId }).normalized()
        val cursor = image.anchor.coerceIn(0, updated.text.length)
        applyDraft(updated, cursor, cursor)
    }

    private fun resolveImagePaths(
        document: WritingDocument,
        draft: WritingChapterDraft,
    ): Map<String, String> {
        val directory = document.book.contentLocation?.substringBeforeLast('/', "").orEmpty()
        val resources = document.document.resources.associateBy { it.resourceId }
        return draft.images.mapNotNull { image ->
            val resource = resources[image.resourceId] ?: return@mapNotNull null
            val location = if (directory.isBlank()) {
                resource.relativePath
            } else {
                "$directory/${resource.relativePath}"
            }
            contentStore.resolveResource(location)?.absolutePath?.let { path ->
                image.resourceId to path
            }
        }.toMap()
    }

    fun toggleBold() {
        applyInline(bold = true)
    }

    fun toggleItalic() {
        applyInline(italic = true)
    }

    fun setHeading() {
        applyLineType(BookBlockType.HEADING)
    }

    fun setQuote() {
        applyLineType(BookBlockType.QUOTE)
    }

    fun setParagraph() {
        applyLineType(BookBlockType.PARAGRAPH)
    }

    private fun applyInline(bold: Boolean = false, italic: Boolean = false) {
        val state = _uiState.value
        val draft = state.draft ?: return
        val updated = WritingChapterEditor.addInlineStyle(
            draft,
            state.selectionStart,
            state.selectionEnd,
            bold = bold,
            italic = italic,
        )
        applyDraft(updated, state.selectionStart, state.selectionEnd)
    }

    private fun applyLineType(type: BookBlockType) {
        val state = _uiState.value
        val draft = state.draft ?: return
        val updated = WritingChapterEditor.setLineType(
            draft,
            state.selectionStart,
            state.selectionEnd,
            type,
        )
        applyDraft(updated, state.selectionStart, state.selectionEnd)
    }

    fun undo() {
        if (historyIndex <= 0) return
        historyIndex--
        restoreHistory()
    }

    fun redo() {
        if (historyIndex >= history.lastIndex) return
        historyIndex++
        restoreHistory()
    }

    /** 对当前章节一次性排版；无变化时不进入历史栈、不触发保存。 */
    fun smartFormatChapter() {
        val state = _uiState.value
        val draft = state.draft ?: return
        val result = WritingSmartFormat.format(
            draft = draft,
            selectionStart = state.selectionStart,
            selectionEnd = state.selectionEnd,
        ) ?: return
        applyDraft(
            draft = result.draft,
            selectionStart = result.selectionStart,
            selectionEnd = result.selectionEnd,
        )
    }

    private fun restoreHistory() {
        val entry = history.getOrNull(historyIndex) ?: return
        autoCloseOffsets.clear()
        autoCloseOffsets += entry.autoCloseOffsets
        val matches = if (_uiState.value.findQuery.isNotEmpty()) {
            findRanges(entry.draft.text, _uiState.value.findQuery)
        } else {
            emptyList()
        }
        _uiState.update {
            it.copy(
                draft = entry.draft,
                selectionStart = entry.selectionStart,
                selectionEnd = entry.selectionEnd,
                wordCount = WritingChapterEditor.wordCount(
                    entry.draft,
                    settingsStore.excludePunctuation.value,
                ),
                canUndo = historyIndex > 0,
                canRedo = historyIndex < history.lastIndex,
                findMatches = matches,
                findIndex = if (matches.isEmpty()) 0 else it.findIndex.coerceIn(0, matches.lastIndex),
            )
        }
        editGeneration++
        scheduleSave()
    }

    fun updateFindQuery(query: String) {
        val text = _uiState.value.draft?.text.orEmpty()
        val matches = findRanges(text, query)
        val first = matches.firstOrNull()
        _uiState.update {
            it.copy(
                findQuery = query,
                findMatches = matches,
                findIndex = 0,
                selectionStart = first?.first ?: it.selectionStart,
                selectionEnd = first?.let { range -> range.last + 1 } ?: it.selectionEnd,
            )
        }
    }

    fun clearFind() {
        _uiState.update {
            it.copy(
                findQuery = "",
                replaceQuery = "",
                findMatches = emptyList(),
                findIndex = 0,
            )
        }
    }

    fun updateReplaceQuery(query: String) {
        _uiState.update { it.copy(replaceQuery = query) }
    }

    fun findNext() {
        _uiState.update { state ->
            if (state.findMatches.isEmpty()) {
                state
            } else {
                val next = (state.findIndex + 1) % state.findMatches.size
                val range = state.findMatches[next]
                state.copy(
                    findIndex = next,
                    selectionStart = range.first,
                    selectionEnd = range.last + 1,
                )
            }
        }
    }

    fun replaceCurrent() {
        val state = _uiState.value
        val draft = state.draft ?: return
        val range = state.findMatches.getOrNull(state.findIndex) ?: return
        val updated = WritingChapterEditor.replaceText(
            draft,
            range.first,
            range.last + 1,
            state.replaceQuery,
        )
        val offsets = WritingPunctuation.adjustOffsetsForRange(
            range.first,
            range.last + 1,
            state.replaceQuery.length,
            autoCloseOffsets,
        )
        autoCloseOffsets.clear()
        autoCloseOffsets += offsets
        applyDraft(
            updated,
            range.first + state.replaceQuery.length,
            range.first + state.replaceQuery.length,
            offsets,
        )
        val matches = findRanges(updated.text, state.findQuery)
        val first = matches.firstOrNull()
        _uiState.update {
            it.copy(
                findMatches = matches,
                findIndex = 0,
                selectionStart = first?.first ?: it.selectionStart,
                selectionEnd = first?.let { match -> match.last + 1 } ?: it.selectionEnd,
            )
        }
    }

    fun replaceAll() {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (state.findQuery.isEmpty() || state.findMatches.isEmpty()) return
        val updated = WritingChapterEditor.updateText(
            draft,
            WritingSearch.replaceAll(draft.text, state.findQuery, state.replaceQuery),
        )
        val offsets = WritingPunctuation.adjustOffsets(draft.text, updated.text, autoCloseOffsets)
        autoCloseOffsets.clear()
        autoCloseOffsets += offsets
        applyDraft(updated, 0, 0, offsets)
        val matches = findRanges(updated.text, state.findQuery)
        val first = matches.firstOrNull()
        _uiState.update {
            it.copy(
                findMatches = matches,
                findIndex = 0,
                selectionStart = first?.first ?: it.selectionStart,
                selectionEnd = first?.let { match -> match.last + 1 } ?: it.selectionEnd,
            )
        }
    }

    fun switchChapter(chapterId: String) {
        val bookId = _uiState.value.bookId ?: return
        viewModelScope.launch {
            val ok = flush()
            if (!ok) return@launch
            closeEditorInternal()
            open(bookId, chapterId)
        }
    }

    fun requestPreview() {
        viewModelScope.launch {
            if (!flush()) return@launch
            val document = baseDocument ?: return@launch
            val state = _uiState.value
            val chapterId = state.chapterId ?: return@launch
            val chapter = document.document.chapters.firstOrNull { it.chapterId == chapterId }
                ?: return@launch
            val offset = (chapter.startOffset + state.selectionStart)
                .coerceIn(0, document.text.length)
            _uiState.update {
                it.copy(previewRequest = WritingPreviewRequest(document.book.bookId, offset))
            }
        }
    }

    fun setShowFormatBar(visible: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_FORMAT_BAR, visible).apply()
        _uiState.update { it.copy(showFormatBar = visible) }
    }

    fun setShowQuickBar(visible: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_QUICK_BAR, visible).apply()
        _uiState.update { it.copy(showQuickBar = visible) }
    }

    fun setAutoPairEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_PAIR, enabled).apply()
        autoCloseOffsets.clear()
        _uiState.update { it.copy(autoPairEnabled = enabled) }
    }

    fun addPunctuation(value: String) {
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            _uiState.update { it.copy(settingsMessage = "标点不能为空") }
            return
        }
        val current = _uiState.value.punctuation
        if (current.contains(normalized)) {
            _uiState.update { it.copy(settingsMessage = "该标点已存在") }
            return
        }
        val updated = current + normalized
        savePunctuation(updated)
        _uiState.update { it.copy(punctuation = updated, settingsMessage = null) }
    }

    fun removePunctuation(index: Int) {
        val current = _uiState.value.punctuation
        if (index !in current.indices) return
        val updated = current.toMutableList().apply { removeAt(index) }
        savePunctuation(updated)
        _uiState.update { it.copy(punctuation = updated, settingsMessage = null) }
    }

    fun movePunctuation(index: Int, delta: Int) {
        val current = _uiState.value.punctuation
        val target = index + delta
        if (index !in current.indices || target !in current.indices) return
        val updated = current.toMutableList()
        val item = updated.removeAt(index)
        updated.add(target, item)
        savePunctuation(updated)
        _uiState.update { it.copy(punctuation = updated, settingsMessage = null) }
    }

    fun resetPunctuation() {
        val updated = WritingPunctuation.DEFAULT_PUNCTUATION.toList()
        savePunctuation(updated)
        _uiState.update { it.copy(punctuation = updated, settingsMessage = null) }
    }

    fun consumeSettingsMessage() {
        _uiState.update { it.copy(settingsMessage = null) }
    }

    private fun loadPunctuation(): List<String> {
        val raw = preferences.getString(KEY_PUNCTUATION, null)
            ?: return WritingPunctuation.DEFAULT_PUNCTUATION.toList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.getString(index) }
                .filter { it.isNotBlank() }
                .distinct()
        }.getOrDefault(WritingPunctuation.DEFAULT_PUNCTUATION.toList())
    }

    private fun savePunctuation(values: List<String>) {
        preferences.edit()
            .putString(KEY_PUNCTUATION, JSONArray(values).toString())
            .apply()
    }

    fun retrySave() {
        viewModelScope.launch { flush() }
    }

    fun consumePreviewRequest() {
        _uiState.update { it.copy(previewRequest = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** 保存并返回详情；失败时保持编辑器打开。 */
    fun close() {
        viewModelScope.launch {
            if (flush()) closeEditorInternal()
        }
    }

    private fun closeEditorInternal() {
        debounceJob?.cancel()
        debounceJob = null
        baseDocument = null
        pendingImages.values.forEach { imageImportService.discard(it) }
        pendingImages.clear()
        history.clear()
        historyIndex = -1
        _uiState.update {
            WritingEditorUiState(
                visible = false,
                bookId = it.bookId,
                chapterId = it.chapterId,
                showFormatBar = it.showFormatBar,
                showQuickBar = it.showQuickBar,
                autoPairEnabled = it.autoPairEnabled,
                punctuation = it.punctuation,
            )
        }
    }

    /** 等待当前草稿提交；失败返回 false，不清空草稿。 */
    suspend fun flush(): Boolean {
        debounceJob?.cancel()
        debounceJob = null
        return savePending()
    }

    private suspend fun savePending(): Boolean {
        var attempts = 0
        while (attempts++ < 4) {
            val state = _uiState.value
            val draft = state.draft ?: return true
            val generation = editGeneration
            if (!saveNow(draft, state)) return false
            if (generation == editGeneration) return true
        }
        return false
    }

    private fun scheduleSave() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            debounceJob = null
            savePending()
        }
    }

    private suspend fun saveNow(
        draft: WritingChapterDraft,
        state: WritingEditorUiState,
    ): Boolean {
        val base = baseDocument ?: return false
        val bookId = state.bookId ?: return false
        val beforeCount = WritingTextStats.totalCount(
            base,
            settingsStore.excludePunctuation.value,
        )
        _uiState.update { it.copy(saveStatus = EditorSaveStatus.SAVING) }
        val pendingSnapshot = pendingImages.toMap()
        val workingBase = if (pendingSnapshot.isEmpty()) {
            base
        } else {
            base.copy(
                document = base.document.copy(
                    resources = (base.document.resources + pendingSnapshot.values.map {
                        it.resource
                    }).distinctBy { it.resourceId },
                ),
            )
        }
        val updated = runCatching {
            WritingChapterEditor.toDocument(workingBase, draft)
        }.getOrElse { error ->
            _uiState.update {
                it.copy(
                    saveStatus = EditorSaveStatus.FAILED,
                    message = error.message ?: "内容格式无效，未保存",
                )
            }
            return false
        }
        val result = saveMutex.withLock {
            if (pendingSnapshot.isEmpty()) {
                documentService.saveChecked(
                    updated,
                    expectedContentHash = base.book.contentHash,
                )
            } else {
                documentService.saveCheckedWithResources(
                    document = updated,
                    expectedContentHash = base.book.contentHash,
                    newResources = pendingSnapshot.values.map {
                        ExtractedResource(it.resource, it.bytes)
                    },
                )
            }
        }
        return when (result) {
            is WritingSaveResult.Saved -> {
                baseDocument = result.document
                runCatching {
                    withContext(Dispatchers.IO) {
                        historyRepository.recordSave(
                            bookId = bookId,
                            beforeCount = beforeCount,
                            afterCount = WritingTextStats.totalCount(
                                result.document,
                                settingsStore.excludePunctuation.value,
                            ),
                        )
                    }
                }
                pendingSnapshot.keys.forEach { resourceId ->
                    pendingImages.remove(resourceId)?.let { imageImportService.discard(it) }
                }
                val currentDraft = _uiState.value.draft ?: draft
                val savedPaths = resolveImagePaths(result.document, currentDraft)
                val stagingPaths = pendingImages.mapNotNull { (id, image) ->
                    image.stagingFile.takeIf { it.isFile }?.let { id to it.absolutePath }
                }.toMap()
                val chapter = result.document.document.chapters.firstOrNull {
                    it.chapterId == draft.chapterId
                }
                _uiState.update {
                    it.copy(
                        bookTitle = result.document.book.title,
                        chapterTitle = chapter?.title ?: it.chapterTitle,
                        saveStatus = if (currentDraft == draft) {
                            EditorSaveStatus.SAVED
                        } else {
                            EditorSaveStatus.SAVING
                        },
                        canUndo = historyIndex > 0,
                        canRedo = historyIndex < history.lastIndex,
                        imagePaths = savedPaths + stagingPaths,
                    )
                }
                if (currentDraft != draft) scheduleSave()
                true
            }

            is WritingSaveResult.Conflict -> {
                _uiState.update {
                    it.copy(
                        saveStatus = EditorSaveStatus.FAILED,
                        message = "内容已被其他版本更新；当前草稿仍保留，请重试。",
                    )
                }
                false
            }

            is WritingSaveResult.Failed -> {
                _uiState.update {
                    it.copy(
                        saveStatus = EditorSaveStatus.FAILED,
                        message = result.cause?.message ?: "保存失败，请重试",
                    )
                }
                false
            }
        }
    }

    private fun applyDraft(
        draft: WritingChapterDraft,
        selectionStart: Int,
        selectionEnd: Int,
        autoOffsets: Set<Int>? = null,
    ) {
        val nextOffsets = autoOffsets ?: autoCloseOffsets.toSet()
        autoCloseOffsets.clear()
        autoCloseOffsets += nextOffsets
        pushHistory(draft, selectionStart, selectionEnd)
        val normalized = draft.normalized()
        val refreshedMatches = if (_uiState.value.findQuery.isNotEmpty()) {
            findRanges(normalized.text, _uiState.value.findQuery)
        } else {
            emptyList()
        }
        _uiState.update {
            it.copy(
                draft = normalized,
                selectionStart = selectionStart.coerceIn(0, normalized.text.length),
                selectionEnd = selectionEnd.coerceIn(0, normalized.text.length),
                wordCount = WritingChapterEditor.wordCount(
                    normalized,
                    settingsStore.excludePunctuation.value,
                ),
                canUndo = historyIndex > 0,
                canRedo = historyIndex < history.lastIndex,
                saveStatus = EditorSaveStatus.SAVING,
                message = null,
                findMatches = refreshedMatches,
                findIndex = if (refreshedMatches.isEmpty()) 0 else it.findIndex.coerceIn(
                    0,
                    refreshedMatches.lastIndex,
                ),
            )
        }
        editGeneration++
        scheduleSave()
    }

    private fun pushHistory(
        draft: WritingChapterDraft,
        selectionStart: Int,
        selectionEnd: Int,
    ) {
        if (historyIndex >= 0 && history.getOrNull(historyIndex)?.draft == draft) return
        if (historyIndex < history.lastIndex) {
            history = history.subList(0, historyIndex + 1).toMutableList()
        }
        history += HistoryEntry(
            draft = draft,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            autoCloseOffsets = autoCloseOffsets.toSet(),
        )
        if (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
        historyIndex = history.lastIndex
    }

    private fun findRanges(text: String, query: String): List<IntRange> =
        WritingSearch.matches(text, query)

    private companion object {
        const val AUTOSAVE_DELAY_MS = 700L
        const val MAX_HISTORY = 100
        const val EDITOR_PREFERENCES = "writing_editor"
        const val KEY_SHOW_FORMAT_BAR = "show_format_bar"
        const val KEY_SHOW_QUICK_BAR = "show_quick_bar"
        const val KEY_AUTO_PAIR = "auto_pair_punctuation"
        const val KEY_PUNCTUATION = "quick_punctuation"
    }
}
