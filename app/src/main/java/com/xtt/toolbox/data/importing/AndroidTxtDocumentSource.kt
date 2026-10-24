package com.xtt.toolbox.data.importing

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.xtt.toolbox.domain.importing.MAX_TXT_BYTES
import com.xtt.toolbox.domain.importing.TxtDocument
import com.xtt.toolbox.domain.importing.TxtDocumentSource
import com.xtt.toolbox.domain.importing.TxtImportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 使用 SAF 返回的 content URI 读取 TXT。
 *
 * 不保存持久化 URI 权限：正文已复制到应用内部后，原始文件不需要继续可访问。
 * 读取失败、文件被删除、权限被撤销都会作为明确的导入错误返回。
 */
class AndroidTxtDocumentSource(
    private val resolver: ContentResolver,
) : TxtDocumentSource {
    override suspend fun read(uri: String): TxtDocument = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        val metadata = readMetadata(parsed)
        if (metadata.sizeBytes != null && metadata.sizeBytes > MAX_TXT_BYTES) {
            throw TxtImportException("文件超过 64 MB，暂不支持导入")
        }

        val bytes = try {
            resolver.openInputStream(parsed)?.use { stream ->
                readLimitedBytes(stream)
            } ?: throw TxtImportException("无法打开文件，请重新选择")
        } catch (error: TxtImportException) {
            throw error
        } catch (error: SecurityException) {
            throw TxtImportException("没有读取该文件的权限，请重新选择", cause = error)
        } catch (error: IOException) {
            throw TxtImportException("读取文件失败，请确认文件仍然存在", cause = error)
        } catch (error: IllegalArgumentException) {
            throw TxtImportException("文件地址无效，请重新选择", cause = error)
        }

        if (bytes.isEmpty()) {
            throw TxtImportException("文件为空，没有可导入的文本")
        }
        TxtDocument(
            displayName = metadata.displayName?.takeIf { it.isNotBlank() }
                ?: parsed.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "未命名",
            sizeBytes = metadata.sizeBytes ?: bytes.size.toLong(),
            bytes = bytes,
        )
    }

    private fun readMetadata(uri: Uri): Metadata {
        return try {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use Metadata(null, null)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex).takeIf { it >= 0L }
                } else {
                    null
                }
                Metadata(name, size)
            } ?: Metadata(null, null)
        } catch (_: Exception) {
            Metadata(null, null)
        }
    }

    private fun readLimitedBytes(stream: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_TXT_BYTES) {
                throw TxtImportException("文件超过 64 MB，暂不支持导入")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private data class Metadata(
        val displayName: String?,
        val sizeBytes: Long?,
    )
}
