package com.xtt.toolbox.data.importing

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import com.xtt.toolbox.domain.content.ImageFormatDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

data class ImportedWritingImage(
    val resource: BookResource,
    val bytes: ByteArray,
    val stagingFile: File,
)

/**
 * 将系统相册选择结果复制到应用私有文件的临时资源区。
 *
 * staging 文件位于 filesDir，不在公共 Pictures/DCIM，也不会注册到 MediaStore。
 * 只有后续 revision 保存成功，编辑器才会删除 staging；保存失败时保留以便重试。
 */
class WritingImageImportService(
    private val context: Context,
    private val contentResolver: ContentResolver,
) {
    suspend fun import(bookId: String, uri: String): Result<ImportedWritingImage> =
        withContext(Dispatchers.IO) {
            val stagingDir = File(context.filesDir, "writing-image-staging/$bookId")
            var stagingFile: File? = null
            try {
                val parsed = Uri.parse(uri)
                val bytes = contentResolver.openInputStream(parsed)?.use { input ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(64 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMAGE_BYTES) {
                            throw IOException("图片文件过大，最大支持 32 MB")
                        }
                        buffer.write(chunk, 0, count)
                    }
                    buffer.toByteArray()
                } ?: throw IOException("无法读取所选图片")

                if (bytes.isEmpty()) throw IOException("所选图片内容为空")
                val format = ImageFormatDetector.detect(bytes)
                    ?: throw IOException("不支持的图片格式（仅支持 JPEG、PNG、WebP 等常见格式）")
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    throw IOException("图片无法解码")
                }

                val id = "writing-image-${UUID.randomUUID()}"
                val relativePath = "editor/images/$id.${format.extension}"
                val target = File(stagingDir, "$id.${format.extension}")
                if (!stagingDir.exists() && !stagingDir.mkdirs()) {
                    throw IOException("无法创建应用内部图片暂存目录")
                }
                val temp = File(stagingDir, ".tmp-$id")
                try {
                    FileOutputStream(temp).use { stream ->
                        stream.write(bytes)
                        stream.flush()
                        stream.fd.sync()
                    }
                    if (temp.length() != bytes.size.toLong()) {
                        throw IOException("图片写入不完整")
                    }
                    if (!temp.renameTo(target)) {
                        throw IOException("图片暂存移动失败")
                    }
                } finally {
                    if (temp.exists()) temp.delete()
                }
                stagingFile = target
                val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                val resource = BookResource(
                    resourceId = id,
                    relativePath = relativePath,
                    mediaType = format.mediaType,
                    sizeBytes = bytes.size.toLong(),
                    sha256 = sha256,
                    kind = BookResourceKind.IMAGE,
                )
                Result.success(ImportedWritingImage(resource, bytes, target))
            } catch (error: Throwable) {
                stagingFile?.delete()
                Result.failure(error)
            }
        }

    fun discard(image: ImportedWritingImage) {
        runCatching { image.stagingFile.delete() }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
    }
}
