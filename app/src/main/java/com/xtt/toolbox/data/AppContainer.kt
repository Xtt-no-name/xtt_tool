package com.xtt.toolbox.data

import android.content.Context
import com.xtt.toolbox.data.backup.BackupService
import com.xtt.toolbox.data.content.FileBookContentStore
import com.xtt.toolbox.data.importing.AndroidTxtDocumentSource
import com.xtt.toolbox.data.importing.EpubImportService
import com.xtt.toolbox.data.importing.MarkdownImageAttachmentService
import com.xtt.toolbox.data.importing.MarkdownImageDirectoryStore
import com.xtt.toolbox.data.importing.MarkdownImportService
import com.xtt.toolbox.data.importing.SafMarkdownResourceReader
import com.xtt.toolbox.data.importing.SharedPreferencesMarkdownImageDirectoryStore
import com.xtt.toolbox.data.importing.TxtImportService
import com.xtt.toolbox.data.importing.WritingImageImportService
import com.xtt.toolbox.data.importing.WritingImportService
import com.xtt.toolbox.data.local.BookDatabaseHelper
import com.xtt.toolbox.data.reading.ReaderService
import com.xtt.toolbox.data.repository.BookDeletionService
import com.xtt.toolbox.data.repository.BookRepository
import com.xtt.toolbox.data.repository.ReaderAnnotationRepository
import com.xtt.toolbox.data.profile.LocalProfileStore
import com.xtt.toolbox.data.search.SearchHistoryStore
import com.xtt.toolbox.data.settings.AppSettingsStore
import com.xtt.toolbox.data.repository.WritingHistoryRepository
import com.xtt.toolbox.data.repository.SqliteBookRepository
import com.xtt.toolbox.data.repository.SqliteReaderAnnotationRepository
import com.xtt.toolbox.domain.writing.WritingDocumentService
import com.xtt.toolbox.domain.writing.WritingCatalogService
import com.xtt.toolbox.domain.writing.WritingCoverService
import com.xtt.toolbox.domain.writing.WritingLibraryService
import java.io.File

/**
 * 应用级依赖容器。
 *
 * 仓库和内容存储必须在 UI 与导入 ViewModel 之间共享；使用 application context，
 * 避免 Activity 重建时创建第二套数据库/仓库实例。
 */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    val databaseHelper: BookDatabaseHelper = BookDatabaseHelper(applicationContext)
    private val database = databaseHelper

    val writingHistoryRepository = WritingHistoryRepository(database)
    val profileStore = LocalProfileStore(applicationContext)
    val appSettingsStore = AppSettingsStore(applicationContext)
    val searchHistoryStore = SearchHistoryStore(
        applicationContext,
        appSettingsStore.searchHistoryEnabled.value,
    )

    val contentStore: FileBookContentStore =
        FileBookContentStore(File(applicationContext.filesDir, "library"))

    val repository: BookRepository = SqliteBookRepository(database)

    val backupService = BackupService(
        applicationContext,
        database,
        repository,
        profileStore,
    )

    val annotationRepository: ReaderAnnotationRepository =
        SqliteReaderAnnotationRepository(database)

    val importService: TxtImportService = TxtImportService(
        source = AndroidTxtDocumentSource(applicationContext.contentResolver),
        contentStore = contentStore,
        repository = repository,
    )

    val epubImportService: EpubImportService = EpubImportService(
        resolver = applicationContext.contentResolver,
        cacheDir = applicationContext.cacheDir,
        contentStore = contentStore,
        repository = repository,
    )

    val markdownImportService: MarkdownImportService = MarkdownImportService(
        source = AndroidTxtDocumentSource(applicationContext.contentResolver),
        contentStore = contentStore,
        repository = repository,
        resourceReader = SafMarkdownResourceReader(applicationContext.contentResolver),
    )

    val markdownImageAttachmentService: MarkdownImageAttachmentService =
        MarkdownImageAttachmentService(
            repository = repository,
            contentStore = contentStore,
            resourceReader = SafMarkdownResourceReader(applicationContext.contentResolver),
            directoryStore = SharedPreferencesMarkdownImageDirectoryStore(applicationContext),
        )

    val readerService: ReaderService = ReaderService(
        repository = repository,
        contentStore = contentStore,
    )

    val writingDocumentService: WritingDocumentService = WritingDocumentService(
        repository = repository,
        contentStore = contentStore,
    )

    val writingCatalogService: WritingCatalogService = WritingCatalogService(
        repository = repository,
        documentService = writingDocumentService,
    )

    val writingLibraryService: WritingLibraryService = WritingLibraryService(
        repository = repository,
        contentStore = contentStore,
        documentService = writingDocumentService,
    )

    val writingCoverService: WritingCoverService = WritingCoverService(
        catalogService = writingCatalogService,
        documentService = writingDocumentService,
    )

    val writingImportService: WritingImportService = WritingImportService(
        resolver = applicationContext.contentResolver,
        txtImportService = importService,
        epubImportService = epubImportService,
        repository = repository,
        documentService = writingDocumentService,
    )

    val writingImageImportService = WritingImageImportService(
        context = applicationContext,
        contentResolver = applicationContext.contentResolver,
    )

    val bookDeletionService: BookDeletionService = BookDeletionService(
        repository = repository,
        contentStore = contentStore,
    )

    /**
     * 读取所有书籍的明确封面资源并解析为内部绝对路径。
     * 只使用数据库中的 COVER 资源；缺失/非法路径直接跳过，由书架回退占位封面。
     */
    suspend fun loadCoverPaths(): Map<String, String> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val booksById = repository.books.value.associateBy { it.bookId }
            database.readCoverResources().mapNotNull { (bookId, resource) ->
                val book = booksById[bookId] ?: return@mapNotNull null
                val contentDir = book.contentLocation?.substringBeforeLast('/', "")
                val location = if (contentDir.isNullOrBlank()) {
                    resource.relativePath
                } else {
                    "$contentDir/${resource.relativePath}"
                }
                val file = contentStore.resolveResource(location)
                    ?: contentStore.resolveBookResource(bookId, resource.relativePath)
                    ?: return@mapNotNull null
                bookId to file.absolutePath
            }.toMap()
        }

    /** 在书籍元数据成功重载后清理未关联数据库记录的正文目录。 */
    suspend fun cleanupOrphanContent() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        contentStore.cleanupOrphans(
            repository.books.value.mapNotNull { it.contentLocation }.toSet(),
        )
    }
}

object AppContainerProvider {
    @Volatile
    private var instance: AppContainer? = null

    fun get(context: Context): AppContainer =
        instance ?: synchronized(this) {
            instance ?: AppContainer(context.applicationContext).also { instance = it }
        }
}
