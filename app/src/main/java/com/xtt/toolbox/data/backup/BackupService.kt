package com.xtt.toolbox.data.backup

import android.content.Context
import android.net.Uri
import com.xtt.toolbox.data.local.BookDatabaseHelper
import com.xtt.toolbox.data.profile.LocalProfileStore
import com.xtt.toolbox.data.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupInfo(
    val createdAt: Long,
    val schemaVersion: Int,
    val bookCount: Int,
)

data class BackupResult(
    val bookCount: Int,
)

/**
 * 全量本地备份：数据库快照 + 内部正文/图片库 + 头像/昵称。
 * 恢复采用“校验后替换 + 失败回滚”，不会在未校验时清空现有数据。
 */
class BackupService(
    private val context: Context,
    private val helper: BookDatabaseHelper,
    private val repository: BookRepository,
    private val profileStore: LocalProfileStore,
) {
    private val libraryDir = File(context.filesDir, "library")
    private val profileDir = File(context.filesDir, "profile")
    private val databaseFile = helper.databaseFile()

    suspend fun createBackup(uri: Uri): Result<BackupInfo> = withContext(Dispatchers.IO) {
        runCatching {
            helper.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
            helper.closeForSnapshot()
            val staging = createStaging()
            try {
                databaseFile.takeIf { it.isFile }?.copyTo(File(staging, "database.db"))
                    ?: error("数据库文件不存在")
                listOf("-wal", "-shm").forEach { suffix ->
                    val side = File("${databaseFile.absolutePath}$suffix")
                    if (side.isFile) side.copyTo(File(staging, "database.db$suffix"))
                }
                copyDirectory(libraryDir, File(staging, "library"))
                copyDirectory(profileDir, File(staging, "profile"))
                val profile = profileStore.load()
                val manifest = JSONObject()
                    .put("version", BACKUP_VERSION)
                    .put("schemaVersion", helper.schemaVersion())
                    .put("createdAt", System.currentTimeMillis())
                    .put("bookCount", repository.books.value.size)
                    .put("nickname", profile.nickname)
                File(staging, "manifest.json").writeText(manifest.toString(), Charsets.UTF_8)
                writeZip(uri, staging)
                BackupInfo(
                    createdAt = manifest.getLong("createdAt"),
                    schemaVersion = manifest.getInt("schemaVersion"),
                    bookCount = manifest.getInt("bookCount"),
                )
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    suspend fun inspect(uri: Uri): Result<BackupInfo> = withContext(Dispatchers.IO) {
        runCatching {
            openZip(uri).use { zip ->
                var manifest: JSONObject? = null
                var hasDatabase = false
                while (true) {
                    val entry = zip.nextEntry ?: break
                    validateEntryName(entry.name)
                    if (entry.name == "manifest.json") {
                        manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    } else if (entry.name == "database.db") {
                        val header = ByteArray(16)
                        val count = zip.read(header)
                        hasDatabase = count == 16 &&
                            header.toString(Charsets.US_ASCII) == "SQLite format 3\u0000"
                    }
                }
                val data = manifest ?: error("备份缺少 manifest.json")
                if (data.getInt("version") != BACKUP_VERSION) error("不支持的备份版本")
                if (data.getInt("schemaVersion") != helper.schemaVersion()) error("备份数据库版本不兼容")
                if (!hasDatabase) error("备份数据库文件无效")
                BackupInfo(
                    createdAt = data.getLong("createdAt"),
                    schemaVersion = data.getInt("schemaVersion"),
                    bookCount = data.getInt("bookCount"),
                )
            }
        }
    }

    suspend fun restore(uri: Uri): Result<BackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = createStaging()
            val rollback = File(context.cacheDir, "backup-rollback-${UUID.randomUUID()}")
            try {
                extractZip(uri, staging)
                val manifest = JSONObject(
                    File(staging, "manifest.json").readText(Charsets.UTF_8),
                )
                if (manifest.getInt("version") != BACKUP_VERSION) error("不支持的备份版本")
                if (manifest.getInt("schemaVersion") != helper.schemaVersion()) {
                    error("备份数据库版本不兼容")
                }
                val restoredDb = File(staging, "database.db")
                if (!restoredDb.isFile) error("备份数据库文件缺失")
                val header = ByteArray(16)
                FileInputStream(restoredDb).use { it.read(header) }
                if (header.toString(Charsets.US_ASCII) != "SQLite format 3\u0000") {
                    error("备份数据库文件无效")
                }

                // 先保存当前状态，任何替换失败都能回滚。
                helper.closeForSnapshot()
                rollback.mkdirs()
                if (databaseFile.isFile) databaseFile.copyTo(File(rollback, "database.db"))
                listOf("-wal", "-shm").forEach { suffix ->
                    val side = File("${databaseFile.absolutePath}$suffix")
                    if (side.isFile) side.copyTo(File(rollback, "database.db$suffix"))
                }
                copyDirectory(libraryDir, File(rollback, "library"))
                copyDirectory(profileDir, File(rollback, "profile"))
                try {
                    replaceDatabase(restoredDb)
                    replaceDirectory(File(staging, "library"), libraryDir)
                    replaceDirectory(File(staging, "profile"), profileDir)
                    manifest.optString("nickname")
                        .takeIf { it.isNotBlank() }
                        ?.let { profileStore.saveNickname(it) }
                    helper.writableDatabase
                    repository.reload()
                    BackupResult(manifest.optInt("bookCount", 0))
                } catch (error: Throwable) {
                    runCatching { restoreRollback(rollback) }
                    throw error
                }
            } finally {
                staging.deleteRecursively()
                rollback.deleteRecursively()
            }
        }
    }

    private fun replaceDatabase(source: File) {
        val target = databaseFile
        target.parentFile?.mkdirs()
        File("${target.absolutePath}-wal").delete()
        File("${target.absolutePath}-shm").delete()
        target.delete()
        source.copyTo(target, overwrite = true)
    }

    private fun replaceDirectory(source: File, target: File) {
        if (target.exists() && !target.deleteRecursively()) error("无法替换 ${target.name}")
        if (source.isDirectory) {
            source.copyRecursively(target, overwrite = true)
        }
    }

    private suspend fun restoreRollback(rollback: File) {
        helper.closeForSnapshot()
        File(rollback, "database.db").takeIf { it.isFile }?.copyTo(databaseFile, overwrite = true)
        replaceDirectory(File(rollback, "library"), libraryDir)
        replaceDirectory(File(rollback, "profile"), profileDir)
        helper.writableDatabase
        repository.reload()
    }

    private fun createStaging(): File =
        File(context.cacheDir, "backup-staging-${UUID.randomUUID()}").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

    private fun openZip(uri: Uri): ZipInputStream =
        ZipInputStream(context.contentResolver.openInputStream(uri)
            ?: error("无法读取备份文件"))

    private fun writeZip(uri: Uri, source: File) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("无法创建备份文件")
        ZipOutputStream(output).use { zip ->
            source.walkTopDown().filter { it.isFile }.forEach { file ->
                val name = file.relativeTo(source).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun extractZip(uri: Uri, target: File) {
        openZip(uri).use { zip ->
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                validateEntryName(entry.name)
                if (++entries > MAX_ENTRIES) error("备份条目过多")
                val file = File(target, entry.name)
                val canonical = file.canonicalFile
                if (!canonical.path.startsWith(target.canonicalPath + File.separator)) {
                    error("备份包含非法路径")
                }
                if (entry.isDirectory) {
                    canonical.mkdirs()
                } else {
                    canonical.parentFile?.mkdirs()
                    FileOutputStream(canonical).use { output ->
                        copyLimited(zip, output)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun copyLimited(input: ZipInputStream, output: FileOutputStream) {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_FILE_BYTES) error("备份条目过大")
            output.write(buffer, 0, read)
        }
    }

    private fun validateEntryName(name: String) {
        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\") ||
            name.contains("..") || name.contains('\\')
        ) {
            error("备份包含非法路径")
        }
    }

    private fun copyDirectory(source: File, target: File) {
        if (!source.isDirectory) return
        source.copyRecursively(target, overwrite = true)
    }

    companion object {
        const val BACKUP_VERSION = 1
        private const val MAX_ENTRIES = 5000
        private const val MAX_FILE_BYTES = 250L * 1024 * 1024
    }
}
