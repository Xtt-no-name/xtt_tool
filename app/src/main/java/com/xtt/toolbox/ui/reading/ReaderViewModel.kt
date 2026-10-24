package com.xtt.toolbox.ui.reading

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.xtt.toolbox.data.AppContainerProvider
import com.xtt.toolbox.data.model.Bookmark
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.data.model.Comment
import com.xtt.toolbox.data.model.CommentType
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.repository.ReaderAnnotationRepository
import com.xtt.toolbox.data.repository.ReaderAnnotations
import com.xtt.toolbox.domain.chapter.ChapterCandidate
import com.xtt.toolbox.domain.chapter.RegexChapterParser
import com.xtt.toolbox.domain.reading.ReaderBlock
import com.xtt.toolbox.domain.reading.ReaderChapterTitle
import com.xtt.toolbox.domain.reading.ReaderDocument
import com.xtt.toolbox.domain.reading.ReaderPositionBuffer
import com.xtt.toolbox.domain.reading.ReaderPositionPolicy
import com.xtt.toolbox.domain.reading.ReaderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ReaderPanel {
    DIRECTORY,
    NOTES,
    SETTINGS,
}

data class ReaderUiState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val bookId: String? = null,
    val title: String = "",
    val text: String = "",
    val blocks: List<ReaderBlock> = emptyList(),
    val chapters: List<ChapterCandidate> = emptyList(),
    val initialBlockIndex: Int = 0,
    val currentOffset: Int = 0,
    val chapterOffset: Int = 0,
    val percent: Float = 0f,
    val chapterId: String? = null,
    val controlsVisible: Boolean = false,
    val activePanel: ReaderPanel? = null,
    val settings: ReaderSettings = ReaderSettings.Default,
    val bookFormat: BookFormat = BookFormat.TXT,
    val imageAttachmentBusy: Boolean = false,
    val contentHash: String? = null,
    val isPreview: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
    val saveError: String? = null,
) {
    /** 当前章节来自本次阅读的临时解析结果；无章节时用书名作为顶部标题。 */
    val currentChapterTitle: String?
        get() = ReaderChapterTitle.titleAt(chapters, chapterOffset)

    /** 仅统计段评；书签和章评不计入段尾气泡。 */
    val passageCommentCounts: Map<Int, Int>
        get() = comments.asSequence()
            .filter { it.commentType == CommentType.PASSAGE }
            .mapNotNull { it.anchorStart }
            .groupingBy { it }
            .eachCount()

    val chapterCommentCounts: Map<String, Int>
        get() = comments.asSequence()
            .filter { it.commentType == CommentType.CHAPTER }
            .groupingBy { it.chapterId }
            .eachCount()

    /** Markdown/DRAFT 不提供书签、段评、章评入口；TXT/EPUB 保持原能力。 */
    val supportsAnnotations: Boolean
        get() = !isPreview && bookFormat != BookFormat.MARKDOWN && bookFormat != BookFormat.DRAFT
}

/**
 * 阅读会话 ViewModel。
 *
 * 位置保存采用单 worker + 合并待保存偏移：滚动只更新最新偏移并发送一个信号，
 * worker debounce 后写 SQLite；退出时在同一个 Mutex 下 flush 最新位置。
 * 数据库写入由仓库切到 IO，主线程不做文件/数据库 I/O。
 */
class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainerProvider.get(application)
    private val readerService = container.readerService
    private val repository = container.repository
    private val annotationRepository: ReaderAnnotationRepository = container.annotationRepository
    private val imageAttachmentService = container.markdownImageAttachmentService
    private val settingsStore = container.appSettingsStore

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)
    private val saveMutex = Mutex()
    private val positionBuffer = ReaderPositionBuffer()
    private val chapterParser = RegexChapterParser()
    private var currentSettings = settingsStore.readerSettings.value

    private var loadJob: Job? = null
    private var saveWorker: Job? = null
    private var closing = false

    fun open(bookId: String) {
        openInternal(bookId, null, preview = false, initialOffsetOverride = null)
    }

    /** 写作预览：不读写阅读位置，不加载批注，可从编辑光标位置开始。 */
    fun openPreview(bookId: String, initialOffset: Int) {
        openInternal(
            bookId = bookId,
            feedbackOnSuccess = null,
            preview = true,
            initialOffsetOverride = initialOffset,
        )
    }

    private fun openInternal(
        bookId: String,
        feedbackOnSuccess: String?,
        preview: Boolean,
        initialOffsetOverride: Int?,
    ) {
        if (bookId.isBlank()) return
        currentSettings = settingsStore.readerSettings.value
        closing = false
        loadJob?.cancel()
        saveWorker?.cancel()
        saveWorker = null
        _uiState.value = ReaderUiState(
            isVisible = true,
            isLoading = true,
            bookId = bookId,
            controlsVisible = true,
            settings = currentSettings,
            isPreview = preview,
        )

        loadJob = scope.launch {
            try {
                val result = readerService.load(bookId)
                val loaded = result.getOrNull()
                if (loaded == null) {
                    _uiState.value = ReaderUiState(
                        isVisible = true,
                        bookId = bookId,
                        controlsVisible = true,
                        settings = currentSettings,
                        isPreview = preview,
                        errorMessage = result.exceptionOrNull()?.message
                            ?: "无法加载这本书",
                    )
                    return@launch
                }

                val document = if (loaded.document != null) {
                    ReaderDocument.fromContentBlocks(
                        text = loaded.text,
                        bookBlocks = loaded.document.blocks,
                        imagePaths = loaded.imagePaths,
                    )
                } else {
                    ReaderDocument.fromText(loaded.text)
                }
                val chapters = if (loaded.document != null) {
                    loaded.document.chapters.map { chapter ->
                        ChapterCandidate(
                            title = chapter.title,
                            order = chapter.order,
                            startOffset = chapter.startOffset,
                            endOffset = chapter.endOffset,
                            headingOffset = chapter.headingOffset,
                            detectionSource = chapter.detectionSource,
                            confidence = chapter.confidence,
                            level = chapter.level,
                            chapterId = chapter.chapterId,
                        )
                    }
                } else {
                    withContext(Dispatchers.Default) {
                        chapterParser.parse(document.text).candidates
                    }
                }
                val annotationsResult = if (preview || loaded.book.format == BookFormat.DRAFT) {
                    kotlin.Result.success(ReaderAnnotations())
                } else {
                    annotationRepository.load(bookId)
                }
                val annotations = annotationsResult.getOrElse { ReaderAnnotations() }
                val initialOffset = ReaderPositionPolicy.clampOffset(
                    textLength = document.length,
                    offset = initialOffsetOverride ?: loaded.initialOffset,
                )
                val initialPercent = ReaderPositionPolicy.percent(
                    textLength = document.length,
                    offset = initialOffset,
                )
                val initialBlockIndex = document.blockIndexForOffset(initialOffset)
                val chapterId = loaded.book.readingPosition?.chapterId
                positionBuffer.initialize(initialOffset)
                _uiState.value = ReaderUiState(
                    isVisible = true,
                    bookId = bookId,
                    title = loaded.book.title,
                    text = document.text,
                    blocks = document.blocks,
                    chapters = chapters,
                    initialBlockIndex = initialBlockIndex,
                    currentOffset = initialOffset,
                    chapterOffset = initialOffset,
                    percent = initialPercent,
                    chapterId = chapterId,
                    controlsVisible = document.text.isEmpty(),
                    settings = currentSettings,
                    bookFormat = loaded.book.format,
                    imageAttachmentBusy = false,
                    contentHash = loaded.book.contentHash,
                    isPreview = preview,
                    bookmarks = annotations.bookmarks,
                    comments = annotations.comments,
                    feedbackMessage = feedbackOnSuccess ?: annotationsResult.exceptionOrNull()?.let { error ->
                        "笔记加载失败：${error.message ?: "未知错误"}"
                    },
                )
                startSaveWorker()

                if (!preview && loaded.book.shelfType == ShelfType.READING) {
                    // 只记录阅读书架打开；写作预览/编辑不改变阅读书架最近打开顺序。
                    val openResult = repository.updateReadingPosition(
                        bookId = bookId,
                        position = ReadingPosition(
                            chapterId = chapterId,
                            offset = initialOffset,
                            percent = initialPercent,
                        ),
                        lastOpenedAt = System.currentTimeMillis(),
                    )
                    openResult.onFailure { error ->
                        _uiState.update {
                            it.copy(saveError = error.message ?: "最近打开时间保存失败")
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.value = ReaderUiState(
                    isVisible = true,
                    bookId = bookId,
                    controlsVisible = true,
                    settings = currentSettings,
                    isPreview = preview,
                    errorMessage = error.message ?: "无法加载这本书",
                )
            }
        }
    }

    fun retry() {
        val state = _uiState.value
        val bookId = state.bookId ?: return
        if (state.isPreview) {
            openPreview(bookId, state.currentOffset)
        } else {
            open(bookId)
        }
    }

    /** 点击正文切换控制栏；若面板已打开，则先关闭面板。 */
    fun toggleControls() {
        _uiState.update { it.toggleReaderControls() }
    }

    /** 打开互斥的阅读面板；再次点击同一按钮关闭。 */
    fun openPanel(panel: ReaderPanel) {
        if (panel == ReaderPanel.NOTES && !_uiState.value.supportsAnnotations) return
        _uiState.update { it.openReaderPanel(panel) }
    }

    fun closePanel() {
        _uiState.update { it.closeReaderPanel() }
    }

    /** Markdown 阅读时由用户选择图片目录；完成后重载当前书籍以刷新图片块。 */
    fun attachMarkdownImageDirectory(uri: Uri) {
        val snapshot = _uiState.value
        val bookId = snapshot.bookId ?: return
        if (snapshot.bookFormat != BookFormat.MARKDOWN || snapshot.imageAttachmentBusy) return
        _uiState.update {
            it.copy(
                imageAttachmentBusy = true,
                feedbackMessage = "正在处理图片资源…",
            )
        }
        scope.launch {
            val result = imageAttachmentService.attach(bookId, uri.toString())
            result.fold(
                onSuccess = { summary ->
                    openInternal(
                        bookId = bookId,
                        feedbackOnSuccess = "图片处理完成：引用 ${summary.referenced}，成功 ${summary.imported}，" +
                            "缺失 ${summary.missing}，不支持 ${summary.unsupported}",
                        preview = false,
                        initialOffsetOverride = null,
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            imageAttachmentBusy = false,
                            feedbackMessage = error.message ?: "图片目录处理失败",
                        )
                    }
                },
            )
        }
    }

    fun addBookmark(block: ReaderBlock) {
        val snapshot = _uiState.value
        if (!snapshot.supportsAnnotations) return
        if (!snapshot.isVisible || snapshot.isLoading || snapshot.bookId == null) return
        if (snapshot.bookmarks.any { it.position == block.startOffset }) {
            _uiState.update { it.copy(feedbackMessage = "该位置已有书签") }
            return
        }
        val chapter = ReaderChapterTitle.chapterAt(snapshot.chapters, block.startOffset)
        val now = System.currentTimeMillis()
        val bookmark = Bookmark(
            bookmarkId = UUID.randomUUID().toString(),
            bookId = snapshot.bookId,
            chapterId = chapter?.let(ReaderChapterTitle::keyOf),
            chapterTitle = chapter?.title,
            chapterStartOffset = chapter?.startOffset,
            position = block.startOffset,
            title = block.text.trim().replace('\n', ' ').take(60)
                .ifBlank { chapter?.title ?: snapshot.title },
            createdAt = now,
        )
        scope.launch {
            annotationRepository.addBookmark(bookmark).fold(
                onSuccess = {
                    _uiState.update {
                        if (it.bookId == bookmark.bookId) {
                            it.copy(
                                bookmarks = it.bookmarks + bookmark,
                                feedbackMessage = "已添加书签",
                            )
                        } else {
                            it
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(feedbackMessage = error.message ?: "书签保存失败")
                    }
                },
            )
        }
    }

    fun addPassageComment(block: ReaderBlock, content: String) {
        val snapshot = _uiState.value
        if (!snapshot.supportsAnnotations) return
        if (!snapshot.isVisible || snapshot.isLoading || snapshot.bookId == null) return
        val normalized = content.trim()
        if (normalized.isEmpty()) {
            _uiState.update { it.copy(feedbackMessage = "评论不能为空") }
            return
        }
        val chapter = ReaderChapterTitle.chapterAt(snapshot.chapters, block.startOffset)
        val now = System.currentTimeMillis()
        val comment = Comment(
            commentId = UUID.randomUUID().toString(),
            bookId = snapshot.bookId,
            chapterId = chapter?.let(ReaderChapterTitle::keyOf)
                ?: ReaderChapterTitle.UNASSIGNED_CHAPTER_KEY,
            chapterTitle = chapter?.title,
            chapterStartOffset = chapter?.startOffset,
            commentType = CommentType.PASSAGE,
            content = normalized,
            createdAt = now,
            updatedAt = now,
            anchorStart = block.startOffset,
            anchorEnd = block.endOffset,
            quotedText = block.text.trim(),
            contentRevision = snapshot.contentHash,
        )
        addComment(comment, "段评已保存")
    }

    fun addChapterComment(chapter: ChapterCandidate, content: String) {
        val snapshot = _uiState.value
        if (!snapshot.supportsAnnotations) return
        if (!snapshot.isVisible || snapshot.isLoading || snapshot.bookId == null) return
        val normalized = content.trim()
        if (normalized.isEmpty()) {
            _uiState.update { it.copy(feedbackMessage = "评论不能为空") }
            return
        }
        val now = System.currentTimeMillis()
        val comment = Comment(
            commentId = UUID.randomUUID().toString(),
            bookId = snapshot.bookId,
            chapterId = ReaderChapterTitle.keyOf(chapter),
            chapterTitle = chapter.title,
            chapterStartOffset = chapter.startOffset,
            commentType = CommentType.CHAPTER,
            content = normalized,
            createdAt = now,
            updatedAt = now,
            contentRevision = snapshot.contentHash,
        )
        addComment(comment, "章评已保存")
    }

    fun consumeFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private fun addComment(comment: Comment, successMessage: String) {
        scope.launch {
            annotationRepository.addComment(comment).fold(
                onSuccess = {
                    _uiState.update {
                        if (it.bookId == comment.bookId) {
                            it.copy(
                                comments = it.comments + comment,
                                feedbackMessage = successMessage,
                            )
                        } else {
                            it
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(feedbackMessage = error.message ?: "评论保存失败")
                    }
                },
            )
        }
    }

    fun updateSettings(settings: ReaderSettings) {
        currentSettings = settings.normalized()
        settingsStore.setReaderSettings(currentSettings)
        _uiState.update { it.copy(settings = currentSettings) }
    }

    /**
     * 由阅读器在首个可见块变化时调用；[atEnd] 为 true 时保存正文末尾。
     * 这里只合并位置，不直接写数据库。
     */
    fun onVisiblePositionChanged(offset: Int, atEnd: Boolean) {
        val snapshot = _uiState.value
        if (!snapshot.isVisible || snapshot.isLoading || snapshot.errorMessage != null) return
        val target = ReaderPositionPolicy.clampOffset(
            textLength = snapshot.text.length,
            offset = if (atEnd) snapshot.text.length else offset,
        )
        if (!positionBuffer.update(target)) return
        _uiState.update {
            it.copy(
                currentOffset = target,
                chapterOffset = offset,
                percent = ReaderPositionPolicy.percent(snapshot.text.length, target),
            )
        }
        saveSignal.trySend(Unit)
    }

    fun consumeSaveError() {
        _uiState.update { it.copy(saveError = null) }
    }

    fun close() {
        if (closing || !_uiState.value.isVisible) return
        closing = true
        loadJob?.cancel()
        loadJob = null
        val snapshot = _uiState.value

        scope.launch {
            if (!snapshot.isLoading && snapshot.errorMessage == null && !snapshot.isPreview) {
                persistLatest()
            }
            saveWorker?.cancel()
            saveWorker = null
            _uiState.update { it.copy(isVisible = false, isLoading = false, activePanel = null) }
            closing = false
        }
    }

    private fun startSaveWorker() {
        if (saveWorker?.isActive == true) return
        saveWorker = scope.launch {
            for (ignored in saveSignal) {
                delay(SAVE_DEBOUNCE_MS)
                persistLatest()
            }
        }
    }

    private suspend fun persistLatest() {
        val snapshot = _uiState.value
        if (snapshot.isPreview) return
        if (!snapshot.isVisible || snapshot.isLoading || snapshot.bookId == null) return
        val offset = positionBuffer.peek() ?: return
        val position = ReaderPositionPolicy.position(
            textLength = snapshot.text.length,
            offset = offset,
            chapterId = snapshot.chapterId,
        )
        val result = saveMutex.withLock {
            try {
                repository.updateReadingPosition(snapshot.bookId, position)
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
        if (result.isSuccess) {
            positionBuffer.markSaved(offset)
            _uiState.update { state ->
                if (state.bookId == snapshot.bookId) state.copy(saveError = null) else state
            }
        } else {
            _uiState.update { state ->
                if (state.bookId == snapshot.bookId) {
                    state.copy(saveError = result.exceptionOrNull()?.message ?: "阅读位置保存失败")
                } else {
                    state
                }
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        saveWorker?.cancel()
        scope.cancel()
        super.onCleared()
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 700L
    }
}

/** 阅读器控制栏和面板的纯状态转换，便于单元测试。 */
fun ReaderUiState.toggleReaderControls(): ReaderUiState =
    if (activePanel != null) {
        copy(activePanel = null)
    } else {
        copy(controlsVisible = !controlsVisible)
    }

fun ReaderUiState.openReaderPanel(panel: ReaderPanel): ReaderUiState =
    copy(
        controlsVisible = true,
        activePanel = if (activePanel == panel) null else panel,
    )

fun ReaderUiState.closeReaderPanel(): ReaderUiState =
    copy(activePanel = null)
