package com.xtt.toolbox.data.importing

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 使用 SAF 授权目录读取 Markdown 相对图片。
 *
 * 只通过 DocumentsContract 从用户选择的 tree 根逐级查询子文档；不把相对路径直接拼成
 * 文件路径，也不读取 tree 之外的文件。单个图片上限与 EPUB 一致（30 MiB）。
 */
class SafMarkdownResourceReader(
    private val resolver: ContentResolver,
) : MarkdownResourceReader {
    override fun probe(directoryUri: String, relativePath: String): MarkdownResourceInfo? {
        return find(directoryUri, relativePath)?.info
    }

    override fun read(directoryUri: String, relativePath: String): ByteArray? {
        val found = find(directoryUri, relativePath) ?: return null
        if (found.info.sizeBytes > MAX_IMAGE_BYTES) return null
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            Uri.parse(directoryUri),
            found.documentId,
        )
        return try {
            resolver.openInputStream(documentUri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_IMAGE_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private data class Found(
        val documentId: String,
        val info: MarkdownResourceInfo,
    )

    private fun find(directoryUri: String, relativePath: String): Found? {
        val normalized = MarkdownPaths.normalize(relativePath) ?: return null
        val treeUri = runCatching { Uri.parse(directoryUri) }.getOrNull() ?: return null
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        val parts = normalized.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        findFrom(treeUri, rootId, parts)?.let { return it }

        // 用户可能直接选择图片目录；若路径首段等于该目录名，允许去掉首段再查找。
        val rootName = queryDocumentInfo(treeUri, rootId)?.displayName
        if (parts.size > 1 && rootName == parts.first()) {
            return findFrom(treeUri, rootId, parts.drop(1))
        }
        return null
    }

    private fun findFrom(treeUri: Uri, rootId: String, parts: List<String>): Found? {
        var parent = rootId
        var info: MarkdownResourceInfo? = null
        for ((index, part) in parts.withIndex()) {
            val child = findChild(treeUri, parent, part) ?: return null
            parent = child.first
            info = child.second
            if (index < parts.lastIndex && child.second.mimeType != Document.MIME_TYPE_DIR) {
                return null
            }
        }
        return info?.let { Found(parent, it) }
    }

    private fun findChild(
        treeUri: Uri,
        parentId: String,
        displayName: String,
    ): Pair<String, MarkdownResourceInfo>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        return try {
            resolver.query(
                childrenUri,
                arrayOf(
                    Document.COLUMN_DOCUMENT_ID,
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_MIME_TYPE,
                    Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(Document.COLUMN_SIZE)
                var count = 0
                while (cursor.moveToNext() && count < MAX_CHILDREN) {
                    count++
                    if (nameIndex < 0 || cursor.getString(nameIndex) != displayName) continue
                    val id = if (idIndex >= 0) cursor.getString(idIndex) else null
                    if (id.isNullOrBlank()) continue
                    val info = MarkdownResourceInfo(
                        displayName = displayName,
                        sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            cursor.getLong(sizeIndex)
                        } else {
                            -1L
                        },
                        mimeType = if (mimeIndex >= 0 && !cursor.isNull(mimeIndex)) {
                            cursor.getString(mimeIndex)
                        } else {
                            null
                        },
                    )
                    return@use id to info
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDocumentInfo(treeUri: Uri, documentId: String): MarkdownResourceInfo? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return try {
            resolver.query(
                uri,
                arrayOf(
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_SIZE,
                    Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                MarkdownResourceInfo(
                    displayName = cursor.getString(0) ?: "",
                    sizeBytes = if (!cursor.isNull(1)) cursor.getLong(1) else -1L,
                    mimeType = if (!cursor.isNull(2)) cursor.getString(2) else null,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
        const val MAX_CHILDREN = 5_000
    }
}
