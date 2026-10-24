package com.xtt.toolbox.ui

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import com.xtt.toolbox.R
import com.xtt.toolbox.data.AppContainerProvider
import com.xtt.toolbox.data.backup.BackupInfo
import com.xtt.toolbox.data.importing.BookImportSelection
import com.xtt.toolbox.data.importing.BookImportSelectionRouter
import com.xtt.toolbox.data.model.ShelfType
import com.xtt.toolbox.data.profile.AvatarImageState
import com.xtt.toolbox.data.profile.AvatarLoader
import com.xtt.toolbox.domain.content.BookResourceKind
import com.xtt.toolbox.domain.writing.WritingDailyStat
import com.xtt.toolbox.domain.writing.WritingEpubExport
import com.xtt.toolbox.domain.writing.WritingTxtExport
import com.xtt.toolbox.ui.components.AppBottomBar
import com.xtt.toolbox.ui.components.AppActionMenu
import com.xtt.toolbox.ui.components.AppActionMenuItem
import com.xtt.toolbox.ui.components.AppIconButton
import com.xtt.toolbox.ui.components.AppSnackbarHost
import com.xtt.toolbox.ui.components.AppTopBar
import com.xtt.toolbox.ui.components.BookIcon
import com.xtt.toolbox.ui.components.ImportIcon
import com.xtt.toolbox.ui.components.PlusIcon
import com.xtt.toolbox.ui.components.ProfileTopBar
import com.xtt.toolbox.ui.components.SearchIcon
import com.xtt.toolbox.ui.components.SettingsTopBar
import com.xtt.toolbox.ui.components.SimpleTopBar
import com.xtt.toolbox.ui.importing.BatchImportDialog
import com.xtt.toolbox.ui.importing.TxtImportScreen
import com.xtt.toolbox.ui.importing.TxtImportViewModel
import com.xtt.toolbox.ui.profile.AvatarCropDialog
import com.xtt.toolbox.ui.profile.ProfileScreen
import com.xtt.toolbox.ui.profile.ProfileViewModel
import com.xtt.toolbox.ui.reading.ExternalLinkLauncher
import com.xtt.toolbox.ui.reading.ReaderScreen
import com.xtt.toolbox.ui.reading.ReaderViewModel
import com.xtt.toolbox.ui.reading.ReadingSearchScreen
import com.xtt.toolbox.ui.reading.ReadingSearchTopBar
import com.xtt.toolbox.ui.reading.ReadingShelfScreen
import com.xtt.toolbox.ui.settings.SettingsScreen
import com.xtt.toolbox.ui.toolbox.ToolboxScreen
import com.xtt.toolbox.ui.writing.NewWritingDialog
import com.xtt.toolbox.ui.writing.ReadingBookPickerDialog
import com.xtt.toolbox.ui.writing.WritingDetailScreen
import com.xtt.toolbox.ui.writing.WritingDetailTopBar
import com.xtt.toolbox.ui.writing.WritingEditorCallbacks
import com.xtt.toolbox.ui.writing.WritingEditorScreen
import com.xtt.toolbox.ui.writing.WritingEditorViewModel
import com.xtt.toolbox.ui.writing.WritingShelfScreen
import com.xtt.toolbox.ui.writing.WritingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val container = remember(context) { AppContainerProvider.get(context) }
    val repository = container.repository
    val books by repository.books.collectAsState()
    var coverPaths by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val bookIds = books.map { it.bookId }

    val importViewModel: TxtImportViewModel = remember(activity) {
        val host = requireNotNull(activity) { "TXT 导入需要 Activity 上下文" }
        ViewModelProvider(host)[TxtImportViewModel::class.java]
    }
    val importState by importViewModel.uiState.collectAsState()
    val batchImportState by importViewModel.batchState.collectAsState()

    val readerViewModel: ReaderViewModel = remember(activity) {
        val host = requireNotNull(activity) { "TXT 阅读需要 Activity 上下文" }
        ViewModelProvider(host)[ReaderViewModel::class.java]
    }
    val readerState by readerViewModel.uiState.collectAsState()

    val writingViewModel: WritingViewModel = remember(activity) {
        val host = requireNotNull(activity) { "写作书架需要 Activity 上下文" }
        ViewModelProvider(host)[WritingViewModel::class.java]
    }
    val writingState by writingViewModel.uiState.collectAsState()
    val writingEditorViewModel: WritingEditorViewModel = remember(activity) {
        val host = requireNotNull(activity) { "写作编辑器需要 Activity 上下文" }
        ViewModelProvider(host)[WritingEditorViewModel::class.java]
    }
    val writingEditorState by writingEditorViewModel.uiState.collectAsState()
    val profileViewModel: ProfileViewModel = remember(activity) {
        val host = requireNotNull(activity) { "个人资料需要 Activity 上下文" }
        ViewModelProvider(host)[ProfileViewModel::class.java]
    }
    val profileState by profileViewModel.uiState.collectAsState()
    val avatarPath = profileState.avatarPath
    val avatarVersion = profileState.avatarVersion
    var avatarState by remember(avatarPath, avatarVersion) {
        mutableStateOf(
            if (avatarPath == null) AvatarImageState.None else AvatarImageState.Loading,
        )
    }
    LaunchedEffect(avatarPath, avatarVersion) {
        avatarState = if (avatarPath == null) {
            AvatarImageState.None
        } else {
            val bitmap = withContext(Dispatchers.IO) { AvatarLoader.load(avatarPath) }
            if (bitmap == null) AvatarImageState.Failed else AvatarImageState.Ready(bitmap)
        }
    }
    var showWritingAdd by rememberSaveable { mutableStateOf(false) }
    var showNewWriting by rememberSaveable { mutableStateOf(false) }
    var showReadingPicker by rememberSaveable { mutableStateOf(false) }

    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.READING.name) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showReadingSearch by rememberSaveable { mutableStateOf(false) }
    var readingSearchQuery by rememberSaveable { mutableStateOf("") }
    val themeMode by container.appSettingsStore.themeMode.collectAsState()
    val readerSettings by container.appSettingsStore.readerSettings.collectAsState()
    val searchHistoryEnabled by container.appSettingsStore.searchHistoryEnabled.collectAsState()
    val excludePunctuation by container.appSettingsStore.excludePunctuation.collectAsState()
    val searchHistory by container.searchHistoryStore.history.collectAsState()
    LaunchedEffect(searchHistoryEnabled) {
        container.searchHistoryStore.setEnabled(searchHistoryEnabled)
    }
    val selectedTab = MainTab.entries.firstOrNull { it.name == selectedTabName } ?: MainTab.READING
    val tabStateHolder = rememberSaveableStateHolder()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, writingEditorState.visible) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && writingEditorState.visible) {
                scope.launch { writingEditorViewModel.flush() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var writingHistory by remember { mutableStateOf<List<WritingDailyStat>>(emptyList()) }
    var pendingRestore by remember { mutableStateOf<BackupInfo?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    LaunchedEffect(showProfile) {
        if (showProfile) {
            writingHistory = withContext(Dispatchers.IO) {
                container.writingHistoryRepository.loadLastYear()
            }
        }
    }
    val writeExport: (android.net.Uri?, String) -> Unit = { uri, label ->
        val bytes = exportBytes
        exportBytes = null
        if (uri != null && bytes != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(bytes)
                        } ?: error("无法打开目标文件")
                    }.isSuccess
                }
                showMessage(
                    if (success) "$label 导出成功" else "$label 导出失败，请检查目标位置",
                )
            }
        }
    }
    val txtExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> writeExport(uri, "TXT") }
    val epubExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/epub+zip"),
    ) { uri -> writeExport(uri, "EPUB") }

    val importMessage = stringResource(R.string.import_in_development)
    val newDocumentMessage = stringResource(R.string.new_document_in_development)
    val libraryLoadFailed = stringResource(R.string.library_load_failed)
    val bookMimeTypes = arrayOf(
        "text/plain",
        "text/*",
        "text/markdown",
        "text/x-markdown",
        "application/x-markdown",
        "application/epub+zip",
        "application/zip",
        "application/octet-stream",
    )

    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        when (val selection = BookImportSelectionRouter.route(uris.map { it.toString() })) {
            BookImportSelection.None -> Unit
            is BookImportSelection.Single -> importViewModel.prepare(android.net.Uri.parse(selection.uri))
            is BookImportSelection.Batch -> importViewModel.startBatchImport(
                selection.uris.map { android.net.Uri.parse(it) },
            )
        }
    }

    val imageDirectoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            importViewModel.attachImageDirectory(uri)
        }
    }

    val readerImageDirectoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            readerViewModel.attachMarkdownImageDirectory(uri)
        }
    }

    val writingImportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            showWritingAdd = false
            writingViewModel.importExternal(uri)
        }
    }

    val writingCoverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) writingViewModel.setCover(uri)
    }
    val writingImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch { writingEditorViewModel.importImage(uri.toString()) }
        }
    }
    val profileAvatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) profileViewModel.updateAvatar(uri.toString())
    }
    val backupCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                backupBusy = true
                val result = container.backupService.createBackup(uri)
                backupBusy = false
                result.fold(
                    onSuccess = { showMessage("备份成功：${it.bookCount} 个作品") },
                    onFailure = { showMessage("备份失败：${it.message ?: "未知错误"}") },
                )
            }
        }
    }
    val backupOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                backupBusy = true
                val result = container.backupService.inspect(uri)
                backupBusy = false
                result.fold(
                    onSuccess = {
                        pendingRestore = it
                        pendingRestoreUri = uri
                    },
                    onFailure = { showMessage("备份校验失败：${it.message ?: "格式不正确"}") },
                )
            }
        }
    }
    val openTxtPicker = {
        // 一个入口同时支持单本和多本；由选中数量决定后续流程。
        importFilePicker.launch(bookMimeTypes)
    }

    LaunchedEffect(repository) {
        val loadResult = runCatching { repository.reload() }
        if (loadResult.isSuccess) {
            runCatching { container.cleanupOrphanContent() }
        } else {
            showMessage(libraryLoadFailed)
        }
    }

    LaunchedEffect(books) {
        coverPaths = runCatching { container.loadCoverPaths() }.getOrDefault(emptyMap())
    }

    val importedBook = importState.importedBook
    LaunchedEffect(importedBook?.bookId) {
        if (importedBook != null) {
            selectedTabName = MainTab.READING.name
            showProfile = false
            showMessage(context.getString(R.string.import_success, importedBook.title))
            importViewModel.consumeImported()
        }
    }

    val readerSaveError = readerState.saveError
    LaunchedEffect(readerSaveError) {
        if (readerSaveError != null) {
            showMessage(context.getString(R.string.reader_save_failed, readerSaveError))
            readerViewModel.consumeSaveError()
        }
    }
    val readerFeedback = readerState.feedbackMessage
    LaunchedEffect(readerFeedback) {
        if (readerFeedback != null) {
            showMessage(readerFeedback)
            readerViewModel.consumeFeedback()
        }
    }

    val writingMessage = writingState.message
    LaunchedEffect(writingMessage) {
        if (writingMessage != null) {
            showMessage(writingMessage)
            writingViewModel.consumeMessage()
        }
    }

    val editorMessage = writingEditorState.message
    LaunchedEffect(editorMessage) {
        if (editorMessage != null) {
            showMessage(editorMessage)
            writingEditorViewModel.consumeMessage()
        }
    }

    val profileMessage = profileState.message
    LaunchedEffect(profileMessage) {
        if (profileMessage != null) {
            showMessage(profileMessage)
            profileViewModel.consumeMessage()
        }
    }

    LaunchedEffect(writingState.pendingChapterId) {
        val chapterId = writingState.pendingChapterId ?: return@LaunchedEffect
        val bookId = writingState.detail?.book?.bookId ?: return@LaunchedEffect
        writingViewModel.consumePendingChapter()
        writingEditorViewModel.open(bookId, chapterId)
    }

    LaunchedEffect(writingEditorState.previewRequest) {
        val request = writingEditorState.previewRequest ?: return@LaunchedEffect
        writingEditorViewModel.consumePreviewRequest()
        readerViewModel.openPreview(request.bookId, request.startOffset)
    }

    LaunchedEffect(writingEditorState.visible) {
        if (!writingEditorState.visible) {
            writingEditorState.bookId?.let { writingViewModel.open(it) }
        }
    }

    BackHandler(enabled = readerState.isVisible) {
        if (readerState.activePanel != null) {
            readerViewModel.closePanel()
        } else {
            readerViewModel.close()
        }
    }
    BackHandler(enabled = !readerState.isVisible && importState.isVisible) {
        importViewModel.cancel()
    }
    BackHandler(enabled = !readerState.isVisible && !importState.isVisible && showReadingSearch) {
        showReadingSearch = false
        readingSearchQuery = ""
    }
    BackHandler(enabled = !readerState.isVisible && !importState.isVisible && showProfile) {
        if (showSettings) {
            showSettings = false
        } else {
            showProfile = false
        }
    }
    BackHandler(
        enabled = !readerState.isVisible && writingEditorState.visible,
    ) {
        writingEditorViewModel.close()
    }
    BackHandler(
        enabled = !readerState.isVisible && !writingEditorState.visible &&
            writingState.detail != null,
    ) {
        writingViewModel.closeDetail()
    }

    Scaffold(
        topBar = {
            when {
                readerState.isVisible -> Unit

                writingEditorState.visible -> Unit

                writingState.detail != null -> WritingDetailTopBar(
                    title = writingState.detail!!.book.title,
                    onBack = writingViewModel::closeDetail,
                    onExportTxt = {
                        val detail = writingState.detail
                        if (detail != null) {
                            exportBytes = WritingTxtExport.build(detail.document)
                                .toByteArray(Charsets.UTF_8)
                            txtExportLauncher.launch(
                                WritingTxtExport.suggestedFileName(detail.book.title),
                            )
                        }
                    },
                    onExportEpub = {
                        val detail = writingState.detail
                        if (detail != null) {
                            scope.launch {
                                val bytes = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val base = detail.book.contentLocation
                                            ?.substringBeforeLast('/', "")
                                            .orEmpty()
                                        val resources = detail.document.document.resources
                                            .filter { it.kind == BookResourceKind.IMAGE }
                                            .mapNotNull { resource ->
                                                val path = if (base.isBlank()) {
                                                    resource.relativePath
                                                } else {
                                                    "$base/${resource.relativePath}"
                                                }
                                                container.contentStore
                                                    .resolveResource(path)
                                                    ?.takeIf { it.isFile }
                                                    ?.readBytes()
                                                    ?.let { resource.resourceId to it }
                                            }
                                            .toMap()
                                        WritingEpubExport.build(detail.document, resources)
                                    }
                                }
                                bytes.fold(
                                    onSuccess = {
                                        exportBytes = it
                                        epubExportLauncher.launch(
                                            WritingEpubExport.suggestedFileName(detail.book.title),
                                        )
                                    },
                                    onFailure = {
                                        showMessage("EPUB 导出准备失败：${it.message ?: "未知错误"}")
                                    },
                                )
                            }
                        }
                    },
                )

                importState.isVisible -> SimpleTopBar(
                    title = stringResource(R.string.import_book),
                    onBack = { importViewModel.cancel() },
                )

                showReadingSearch -> ReadingSearchTopBar(
                    query = readingSearchQuery,
                    onQueryChange = { readingSearchQuery = it },
                    onSubmit = { container.searchHistoryStore.record(readingSearchQuery) },
                    onBack = {
                        showReadingSearch = false
                        readingSearchQuery = ""
                    },
                )

                showProfile && showSettings -> SettingsTopBar(onBack = { showSettings = false })
                showProfile -> ProfileTopBar(onBack = { showProfile = false })

                else -> AppTopBar(
                    title = stringResource(selectedTab.labelRes),
                    subtitle = stringResource(selectedTab.subtitleRes),
                    avatarState = avatarState,
                    onAvatarClick = { showProfile = true },
                    actions = {
                        when (selectedTab) {
                            MainTab.READING -> {
                                AppIconButton(
                                    contentDescription = stringResource(R.string.search),
                                    onClick = {
                                        readingSearchQuery = ""
                                        showReadingSearch = true
                                    },
                                ) {
                                    SearchIcon()
                                }
                            }

                            MainTab.WRITING -> {
                                Box {
                                    AppIconButton(
                                        contentDescription = stringResource(R.string.new_document),
                                        onClick = { showWritingAdd = true },
                                    ) {
                                        PlusIcon()
                                    }
                                    AppActionMenu(
                                        expanded = showWritingAdd,
                                        onDismissRequest = { showWritingAdd = false },
                                    ) {
                                        AppActionMenuItem(
                                            text = "从外部导入",
                                            icon = { ImportIcon(Modifier.size(18.dp)) },
                                            onClick = {
                                                showWritingAdd = false
                                                writingImportPicker.launch(
                                                    arrayOf(
                                                        "text/plain",
                                                        "application/epub+zip",
                                                        "application/zip",
                                                        "application/octet-stream",
                                                    ),
                                                )
                                            },
                                        )
                                        AppActionMenuItem(
                                            text = "从阅读书架导入",
                                            icon = { BookIcon(Modifier.size(18.dp)) },
                                            onClick = {
                                                showWritingAdd = false
                                                showReadingPicker = true
                                            },
                                        )
                                        AppActionMenuItem(
                                            text = "新建作品",
                                            icon = { PlusIcon(Modifier.size(18.dp)) },
                                            onClick = {
                                                showWritingAdd = false
                                                showNewWriting = true
                                            },
                                        )
                                    }
                                }
                            }

                            MainTab.TOOLBOX -> Unit
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!showProfile && !showReadingSearch && !importState.isVisible &&
                !readerState.isVisible && !writingEditorState.visible &&
                writingState.detail == null
            ) {
                AppBottomBar(
                    selected = selectedTab,
                    onSelect = { tab -> selectedTabName = tab.name },
                )
            }
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when {
            readerState.isVisible -> ReaderScreen(
                state = readerState,
                onRetry = readerViewModel::retry,
                onBack = readerViewModel::close,
                onToggleControls = readerViewModel::toggleControls,
                onOpenPanel = readerViewModel::openPanel,
                onClosePanel = readerViewModel::closePanel,
                onSettingsChange = readerViewModel::updateSettings,
                onAddBookmark = readerViewModel::addBookmark,
                onAddPassageComment = readerViewModel::addPassageComment,
                onAddChapterComment = readerViewModel::addChapterComment,
                onOpenLink = { url ->
                    if (!ExternalLinkLauncher.open(context, url)) {
                        showMessage(context.getString(R.string.link_open_failed))
                    }
                },
                onPickMarkdownImageDirectory = {
                    readerImageDirectoryPicker.launch(null)
                },
                onVisiblePositionChanged = readerViewModel::onVisiblePositionChanged,
                modifier = contentModifier,
            )

            importState.isVisible -> TxtImportScreen(
                state = importState,
                onConfirm = importViewModel::confirm,
                onRetryEncoding = importViewModel::retryWithEncoding,
                onPickImageDirectory = { imageDirectoryPicker.launch(null) },
                onCancel = importViewModel::cancel,
                modifier = contentModifier,
            )

            showReadingSearch -> ReadingSearchScreen(
                books = books.filter { it.shelfType == ShelfType.READING },
                coverPaths = coverPaths,
                query = readingSearchQuery,
                history = searchHistory,
                historyEnabled = searchHistoryEnabled,
                onOpenBook = { book ->
                    showReadingSearch = false
                    readingSearchQuery = ""
                    readerViewModel.open(book.bookId)
                },
                onHistoryClick = { term ->
                    readingSearchQuery = term
                    container.searchHistoryStore.record(term)
                },
                onClearHistory = { container.searchHistoryStore.clear() },
                modifier = contentModifier,
            )

            showProfile && showSettings -> SettingsScreen(
                themeMode = themeMode,
                onThemeModeChange = container.appSettingsStore::setThemeMode,
                readerSettings = readerSettings,
                onReaderSettingsChange = container.appSettingsStore::setReaderSettings,
                searchHistoryEnabled = searchHistoryEnabled,
                onSearchHistoryEnabledChange = { enabled ->
                    container.appSettingsStore.setSearchHistoryEnabled(enabled)
                    container.searchHistoryStore.setEnabled(enabled)
                },
                modifier = contentModifier,
            )
            showProfile -> ProfileScreen(
                onShowMessage = showMessage,
                writingHistory = writingHistory,
                profileState = profileState,
                avatarState = avatarState,
                onPickAvatar = {
                    profileAvatarPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onClearAvatar = profileViewModel::clearAvatar,
                onSaveNickname = profileViewModel::updateNickname,
                onBackup = {
                    backupCreateLauncher.launch("xtt-backup.zip")
                },
                onRestore = {
                    backupOpenLauncher.launch(
                        arrayOf("application/zip", "application/octet-stream"),
                    )
                },
                onOpenSettings = { showSettings = true },
                modifier = contentModifier,
            )

            else -> tabStateHolder.SaveableStateProvider(selectedTab.name) {
                when (selectedTab) {
                    MainTab.READING -> ReadingShelfScreen(
                        books = books.filter { it.shelfType == ShelfType.READING },
                        onImport = openTxtPicker,
                        importInProgress = batchImportState.isRunning,
                        onOpenBook = { book ->
                            showProfile = false
                            readerViewModel.open(book.bookId)
                        },
                        coverPaths = coverPaths,
                        onDeleteBook = { book ->
                            scope.launch {
                                val result = container.bookDeletionService.delete(book.bookId)
                                result.fold(
                                    onSuccess = { outcome ->
                                        if (outcome.fileCleanupSucceeded) {
                                            showMessage(
                                                context.getString(
                                                    R.string.delete_book_success,
                                                    book.title,
                                                ),
                                            )
                                        } else {
                                            showMessage(
                                                context.getString(
                                                    R.string.delete_book_cleanup_pending,
                                                    book.title,
                                                ),
                                            )
                                            runCatching { container.cleanupOrphanContent() }
                                        }
                                    },
                                    onFailure = {
                                        showMessage(
                                            context.getString(
                                                R.string.delete_book_failed,
                                                book.title,
                                            ),
                                        )
                                    },
                                )
                            }
                        },
                        modifier = contentModifier,
                    )

                    MainTab.WRITING -> {
                        val detail = writingState.detail
                        if (writingEditorState.visible) {
                            WritingEditorScreen(
                                state = writingEditorState,
                                callbacks = WritingEditorCallbacks(
                                    onTextChange = writingEditorViewModel::onTextChange,
                                    onTitleChange = writingEditorViewModel::updateChapterTitle,
                                    onBack = writingEditorViewModel::close,
                                    onPreview = writingEditorViewModel::requestPreview,
                                    onPickImage = {
                                        writingImagePicker.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                                            ),
                                        )
                                    },
                                    onRetrySave = writingEditorViewModel::retrySave,
                                    onShowFormatBarChange = writingEditorViewModel::setShowFormatBar,
                                    onShowQuickBarChange = writingEditorViewModel::setShowQuickBar,
                                    onAutoPairChange = writingEditorViewModel::setAutoPairEnabled,
                                    excludePunctuation = excludePunctuation,
                                    onExcludePunctuationChange =
                                        container.appSettingsStore::setExcludePunctuation,
                                    onInsertPunctuation = writingEditorViewModel::insertPunctuation,
                                    onDeleteImage = writingEditorViewModel::deleteImage,
                                    onConsumeInsertedImageFocus =
                                        writingEditorViewModel::consumeInsertedImageFocus,
                                    onAddPunctuation = writingEditorViewModel::addPunctuation,
                                    onRemovePunctuation = writingEditorViewModel::removePunctuation,
                                    onMovePunctuation = writingEditorViewModel::movePunctuation,
                                    onResetPunctuation = writingEditorViewModel::resetPunctuation,
                                    onUndo = writingEditorViewModel::undo,
                                    onRedo = writingEditorViewModel::redo,
                                    onBold = writingEditorViewModel::toggleBold,
                                    onItalic = writingEditorViewModel::toggleItalic,
                                    onHeading = writingEditorViewModel::setHeading,
                                    onQuote = writingEditorViewModel::setQuote,
                                    onParagraph = writingEditorViewModel::setParagraph,
                                    onSmartFormat = { writingEditorViewModel.smartFormatChapter() },
                                    onFindQuery = writingEditorViewModel::updateFindQuery,
                                    onReplaceQuery = writingEditorViewModel::updateReplaceQuery,
                                    onFindNext = writingEditorViewModel::findNext,
                                    onReplaceCurrent = writingEditorViewModel::replaceCurrent,
                                    onReplaceAll = writingEditorViewModel::replaceAll,
                                    onClearFind = writingEditorViewModel::clearFind,
                                ),
                                modifier = contentModifier,
                            )
                        } else if (detail != null) {
                            WritingDetailScreen(
                                detail = detail,
                                coverPath = coverPaths[detail.book.bookId],
                                onPickCover = {
                                    writingCoverPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                                onOpenChapter = { chapter ->
                                    writingEditorViewModel.open(
                                        detail.book.bookId,
                                        chapter.chapterId,
                                    )
                                },
                                onCreateVolume = writingViewModel::createVolume,
                                onRenameVolume = writingViewModel::renameVolume,
                                onDeleteVolume = writingViewModel::deleteVolume,
                                onCreateChapter = writingViewModel::addChapter,
                                onRenameChapter = writingViewModel::renameChapter,
                                onDeleteChapter = writingViewModel::deleteChapter,
                                onMoveChapter = writingViewModel::moveChapter,
                                onMoveChapterUp = writingViewModel::moveChapterUp,
                                onMoveChapterDown = writingViewModel::moveChapterDown,
                                excludePunctuation = excludePunctuation,
                                modifier = contentModifier,
                            )
                        } else {
                            WritingShelfScreen(
                                books = books.filter { it.shelfType == ShelfType.WRITING },
                                coverPaths = coverPaths,
                                onOpenBook = { book -> writingViewModel.open(book.bookId) },
                                onDeleteBook = { book ->
                                    scope.launch {
                                        container.bookDeletionService.delete(book.bookId).fold(
                                            onSuccess = { outcome ->
                                                showMessage(
                                                    if (outcome.fileCleanupSucceeded) {
                                                        context.getString(
                                                            R.string.delete_book_success,
                                                            book.title,
                                                        )
                                                    } else {
                                                        context.getString(
                                                            R.string.delete_book_cleanup_pending,
                                                            book.title,
                                                        )
                                                    },
                                                )
                                                if (!outcome.fileCleanupSucceeded) {
                                                    runCatching { container.cleanupOrphanContent() }
                                                }
                                            },
                                            onFailure = {
                                                showMessage(
                                                    context.getString(
                                                        R.string.delete_book_failed,
                                                        book.title,
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                },
                                modifier = contentModifier,
                            )
                        }
                    }

                    MainTab.TOOLBOX -> ToolboxScreen(
                        onShowMessage = showMessage,
                        modifier = contentModifier,
                    )
                }
            }
        }
        BatchImportDialog(
            state = batchImportState,
            onDismiss = importViewModel::dismissBatchImport,
        )

        if (showNewWriting) {
            NewWritingDialog(
                onDismiss = { showNewWriting = false },
                onConfirm = { title ->
                    showNewWriting = false
                    writingViewModel.createBlank(title)
                },
            )
        }
        if (showReadingPicker) {
            ReadingBookPickerDialog(
                books = books,
                onDismiss = { showReadingPicker = false },
                onMove = { book ->
                    showReadingPicker = false
                    writingViewModel.moveFromReading(book.bookId)
                },
                onCopy = { book ->
                    showReadingPicker = false
                    writingViewModel.copyFromReading(book.bookId)
                },
            )
        }
    }

    profileState.cropBitmap?.let { bitmap ->
        AvatarCropDialog(
            bitmap = bitmap,
            busy = profileState.busy,
            onCancel = profileViewModel::cancelAvatarCrop,
            onConfirm = profileViewModel::confirmAvatarCrop,
        )
    }

    pendingRestore?.let { info ->        AlertDialog(
            onDismissRequest = {
                if (!backupBusy) {
                    pendingRestore = null
                    pendingRestoreUri = null
                }
            },
            title = { Text("恢复本地备份") },
            text = {
                Text(
                    "将恢复 ${info.bookCount} 个作品及备份中的正文、图片、统计和个人资料。" +
                        "恢复会替换当前数据；校验或恢复失败时会尝试回滚。是否继续？",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !backupBusy,
                    onClick = {
                        val uri = pendingRestoreUri ?: return@TextButton
                        pendingRestore = null
                        pendingRestoreUri = null
                        scope.launch {
                            backupBusy = true
                            val result = container.backupService.restore(uri)
                            backupBusy = false
                            result.fold(
                                onSuccess = {
                                    profileViewModel.reload()
                                    showMessage("恢复完成：${it.bookCount} 个作品；如界面未刷新，请重启应用")
                                },
                                onFailure = {
                                    showMessage("恢复失败：${it.message ?: "未知错误"}；现有数据已尽量保留")
                                },
                            )
                        }
                    },
                ) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(
                    enabled = !backupBusy,
                    onClick = {
                        pendingRestore = null
                        pendingRestoreUri = null
                    },
                ) { Text("取消") }
            },
        )
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
