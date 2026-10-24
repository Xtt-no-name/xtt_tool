package com.xtt.toolbox.data.content

import com.xtt.toolbox.domain.content.ExtractedResource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * 文件系统正文存储。
 *
 * 正式位置：`<root>/books/<bookId>/content.txt`，始终以 UTF-8 保存规范化后的正文。
 * 写入时先写同目录临时文件、校验长度，再重命名到正式位置；失败时清理临时文件。
 * 原始用户文件不在本类中读取或修改。
 */
class FileBookContentStore(
    private val root: File,
) : BookContentStore {
    private val booksRoot: File = File(root, "books")

    override fun save(bookId: String, text: String): SavedContent {
        cleanupTemporaryFiles()
        require(bookId.isNotBlank()) { "bookId 不能为空" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        val hash = ContentHash.sha256(bytes)

        if (!booksRoot.exists() && !booksRoot.mkdirs()) {
            throw IOException("无法创建内部书籍目录")
        }

        val targetDir = File(booksRoot, bookId)
        if (targetDir.exists()) {
            throw IOException("书籍正文目录已存在，拒绝覆盖")
        }

        val temp = File(booksRoot, ".tmp-${UUID.randomUUID()}.txt")
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(bytes)
                stream.flush()
                stream.fd.sync()
            }
            if (temp.length() != bytes.size.toLong()) {
                throw IOException("正文临时文件写入不完整")
            }
            if (!targetDir.mkdirs()) {
                throw IOException("无法创建书籍正文目录")
            }
            val target = File(targetDir, CONTENT_FILE_NAME)
            if (!temp.renameTo(target)) {
                throw IOException("无法将正文移动到正式位置")
            }
            if (target.length() != bytes.size.toLong()) {
                target.delete()
                throw IOException("正文文件校验失败")
            }
            return SavedContent(
                location = "books/$bookId/$CONTENT_FILE_NAME",
                sizeBytes = target.length(),
                sha256 = hash,
            )
        } catch (error: Throwable) {
            temp.delete()
            deleteEmptyDirectory(targetDir)
            throw error
        }
    }

    override fun saveDocument(
        bookId: String,
        text: String,
        resources: List<ExtractedResource>,
    ): SavedContent {
        cleanupTemporaryFiles()
        require(bookId.isNotBlank()) { "bookId 不能为空" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        val hash = ContentHash.sha256(bytes)
        if (!booksRoot.exists() && !booksRoot.mkdirs()) {
            throw IOException("无法创建内部书籍目录")
        }
        val targetDir = File(booksRoot, bookId)
        if (targetDir.exists()) {
            throw IOException("书籍正文目录已存在，拒绝覆盖")
        }
        val tempDir = File(booksRoot, ".tmp-doc-${UUID.randomUUID()}")
        try {
            if (!tempDir.mkdirs()) throw IOException("无法创建 EPUB 临时目录")
            File(tempDir, CONTENT_FILE_NAME).writeBytes(bytes)
            resources.forEach { extracted ->
                val resource = extracted.resource
                val target = safeChild(tempDir, resource.relativePath)
                    ?: throw IOException("EPUB 资源路径非法")
                target.parentFile?.mkdirs()
                target.writeBytes(extracted.bytes)
                if (target.length() != extracted.bytes.size.toLong()) {
                    throw IOException("EPUB 资源写入不完整")
                }
            }
            if (!tempDir.renameTo(targetDir)) {
                throw IOException("无法将 EPUB 内容移动到正式位置")
            }
            val contentTarget = File(targetDir, CONTENT_FILE_NAME)
            if (contentTarget.length() != bytes.size.toLong()) {
                throw IOException("EPUB 正文文件校验失败")
            }
            return SavedContent(
                location = "books/$bookId/$CONTENT_FILE_NAME",
                sizeBytes = contentTarget.length(),
                sha256 = hash,
            )
        } catch (error: Throwable) {
            tempDir.deleteRecursively()
            targetDir.deleteRecursively()
            throw error
        }
    }

    override fun resolveResource(location: String): File? {
        val file = resolve(location) ?: return null
        return file.takeIf { it.isFile }
    }

    override fun resolveBookResource(bookId: String, relativePath: String): File? {
        if (bookId.isBlank()) return null
        val bookDir = safeChild(booksRoot, bookId) ?: return null
        return safeChild(bookDir, relativePath)?.takeIf { it.isFile }
    }

    override fun saveResource(bookId: String, relativePath: String, bytes: ByteArray): Boolean {
        if (bookId.isBlank()) return false
        val bookDir = safeChild(booksRoot, bookId) ?: return false
        val target = safeChild(bookDir, relativePath) ?: return false
        return runCatching {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, ".tmp-res-${UUID.randomUUID()}")
            try {
                temp.writeBytes(bytes)
                if (temp.length() != bytes.size.toLong()) throw IOException("资源写入不完整")
                if (!temp.renameTo(target)) throw IOException("资源移动失败")
                true
            } finally {
                if (temp.exists()) temp.delete()
            }
        }.getOrDefault(false)
    }

    override fun deleteResource(bookId: String, relativePath: String): Boolean {
        if (bookId.isBlank()) return false
        val bookDir = safeChild(booksRoot, bookId) ?: return false
        val target = safeChild(bookDir, relativePath) ?: return false
        val deleted = !target.exists() || target.delete()
        deleteEmptyDirectory(target.parentFile)
        return deleted
    }

    override fun writeDocumentRevision(
        bookId: String,
        revisionId: String,
        text: String,
        resources: List<ExtractedResource>,
    ): SavedContent {
        require(bookId.isNotBlank() && revisionId.isNotBlank())
        val bookDir = safeChild(booksRoot, bookId)
            ?: throw IOException("书籍目录非法")
        val revisionsDir = safeChild(bookDir, "revisions")
            ?: throw IOException("revision 目录非法")
        val targetDir = safeChild(revisionsDir, revisionId)
            ?: throw IOException("revision ID 非法")
        if (targetDir.exists()) throw IOException("revision 已存在，拒绝覆盖")
        if (!bookDir.exists() && !bookDir.mkdirs()) throw IOException("无法创建书籍目录")
        if (!revisionsDir.exists() && !revisionsDir.mkdirs()) {
            throw IOException("无法创建 revision 目录")
        }
        val tempDir = File(revisionsDir, ".tmp-$revisionId")
        try {
            if (!tempDir.mkdirs()) throw IOException("无法创建 revision 临时目录")
            val bytes = text.toByteArray(Charsets.UTF_8)
            File(tempDir, CONTENT_FILE_NAME).writeBytes(bytes)
            resources.forEach { extracted ->
                val resource = extracted.resource
                val target = safeChild(tempDir, resource.relativePath)
                    ?: throw IOException("revision 资源路径非法")
                target.parentFile?.mkdirs()
                target.writeBytes(extracted.bytes)
                if (target.length() != extracted.bytes.size.toLong()) {
                    throw IOException("revision 资源写入不完整")
                }
            }
            if (!tempDir.renameTo(targetDir)) throw IOException("revision 移动失败")
            return SavedContent(
                location = "books/$bookId/revisions/$revisionId/$CONTENT_FILE_NAME",
                sizeBytes = bytes.size.toLong(),
                sha256 = ContentHash.sha256(bytes),
            )
        } catch (error: Throwable) {
            tempDir.deleteRecursively()
            targetDir.deleteRecursively()
            throw error
        }
    }

    override fun deleteRevision(bookId: String, revisionId: String): Boolean {
        if (bookId.isBlank() || revisionId.isBlank()) return false
        val bookDir = safeChild(booksRoot, bookId) ?: return false
        val revisionsDir = safeChild(bookDir, "revisions") ?: return false
        val target = safeChild(revisionsDir, revisionId) ?: return false
        val deleted = !target.exists() || target.deleteRecursively()
        deleteEmptyDirectory(revisionsDir)
        return deleted
    }

    override fun deleteBook(bookId: String): Boolean {
        if (bookId.isBlank()) return false
        val dir = safeChild(booksRoot, bookId) ?: return false
        if (!dir.exists()) return true
        return dir.deleteRecursively()
    }

    override fun readText(location: String): String {
        val file = resolve(location)
            ?: throw IOException("正文位置无效")
        if (!file.isFile) {
            throw IOException("正文文件不存在")
        }
        return file.readText(Charsets.UTF_8)
    }

    override fun delete(location: String) {
        val file = resolve(location) ?: return
        file.delete()
        deleteEmptyDirectory(file.parentFile)
    }

    override fun exists(location: String): Boolean = resolve(location)?.isFile == true

    /** 清理上次进程中断可能留下的临时文件；不会删除正式书籍目录。 */
    fun cleanupTemporaryFiles() {
        val files = booksRoot.listFiles() ?: return
        files.filter { it.isFile && it.name.startsWith(".tmp-") }.forEach { it.delete() }
    }

    /** 删除没有对应数据库书籍的正文目录；只能在书籍元数据成功重载后调用。 */
    fun cleanupOrphans(knownLocations: Set<String>) {
        val dirs = booksRoot.listFiles()?.filter { it.isDirectory } ?: return
        val now = System.currentTimeMillis()
        dirs.forEach { dir ->
            // 导入中的 .tmp-doc-* 目录可能仍在写入；只在明显过期后清理。
            if (dir.name.startsWith(TEMP_PREFIX) &&
                now - dir.lastModified() < STALE_TEMP_AGE_MS
            ) {
                return@forEach
            }
            val bookPrefix = "books/${dir.name}/"
            val knownForBook = knownLocations.filter { it.startsWith(bookPrefix) }
            if (knownForBook.isEmpty()) {
                runCatching { dir.deleteRecursively() }
                return@forEach
            }
            val legacyLocation = "books/${dir.name}/$CONTENT_FILE_NAME"
            if (legacyLocation !in knownForBook) {
                // 当前版本位于 revision 时，旧版根目录正文不再被数据库引用。
                runCatching { File(dir, CONTENT_FILE_NAME).delete() }
            }
            val revisions = File(dir, "revisions")
            if (revisions.isDirectory) {
                val knownRevisions = knownForBook
                    .filter { it.startsWith("$bookPrefix" + "revisions/") }
                    .map { it.removePrefix("$bookPrefix" + "revisions/").substringBefore('/') }
                    .toSet()
                revisions.listFiles()?.forEach { revision ->
                    if (revision.name.startsWith(TEMP_PREFIX)) {
                        if (now - revision.lastModified() >= STALE_TEMP_AGE_MS) {
                            runCatching { revision.deleteRecursively() }
                        }
                    } else if (revision.name !in knownRevisions) {
                        runCatching { revision.deleteRecursively() }
                    }
                }
                deleteEmptyDirectory(revisions)
            }
        }
    }

    private fun resolve(location: String): File? {
        if (location.isBlank()) return null
        return runCatching {
            val rootPath = root.canonicalFile
            val file = File(root, location).canonicalFile
            val insideRoot = file.path.startsWith(rootPath.path + File.separator)
            if (insideRoot) file else null
        }.getOrNull()
    }

    private fun safeChild(parent: File, relativePath: String): File? {
        if (relativePath.isBlank() || relativePath.startsWith("/") ||
            relativePath.startsWith("\\") || relativePath.contains('\\')
        ) {
            return null
        }
        return runCatching {
            val parentPath = parent.canonicalFile
            val child = File(parent, relativePath).canonicalFile
            if (child.path.startsWith(parentPath.path + File.separator)) child else null
        }.getOrNull()
    }

    private fun deleteEmptyDirectory(directory: File?) {
        if (directory == null) return
        val children = directory.list()
        if (children != null && children.isEmpty()) {
            directory.delete()
        }
    }

    private companion object {
        const val CONTENT_FILE_NAME = "content.txt"
        const val TEMP_PREFIX = ".tmp-"
        const val STALE_TEMP_AGE_MS = 60L * 60L * 1000L
    }
}
