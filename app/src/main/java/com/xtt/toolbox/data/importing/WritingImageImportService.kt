package com.xtt.toolbox.data.importing

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import com.xtt.toolbox.domain.content.ImageFormatDetector
import com.xtt.toolbox.domain.writing.WritingImageCropGeometry
import com.xtt.toolbox.domain.writing.WritingImageCropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
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
 * 写作图片裁剪的原始/预览分离数据：
 * - source：最多 4096px 的已纠正方向源图，最终裁剪只读取它；
 * - preview：最多 1600px 的显示副本，不参与最终像素计算。
 */
data class WritingImageCropSource(
    val source: Bitmap,
    val preview: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    fun recycle() {
        if (!source.isRecycled) source.recycle()
        if (preview !== source && !preview.isRecycled) preview.recycle()
    }
}

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

    /**
     * 加载编辑图片裁剪源。preview 仅用于显示，source 用于最终像素映射；
     * 两者必须在取消/确认后统一回收。
     */
    suspend fun loadCropSource(uri: String): Result<WritingImageCropSource> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = readBytes(uri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw IOException("图片无法解码")
                }
                var sample = 1
                while (bounds.outWidth / sample > MAX_SOURCE_EDGE ||
                    bounds.outHeight / sample > MAX_SOURCE_EDGE
                ) {
                    sample *= 2
                }
                val decoded = BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                ) ?: throw IOException("图片无法解码")
                val orientation = runCatching {
                    ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                val source = applyOrientation(decoded, orientation)
                val preview = makePreview(source)
                WritingImageCropSource(
                    source = source,
                    preview = preview,
                    sourceWidth = source.width,
                    sourceHeight = source.height,
                )
            }
        }

    /** 从源图按裁剪框生成最终图片字节；preview 从不参与该函数。 */
    fun cropToJpegBytes(
        source: WritingImageCropSource,
        rect: WritingImageCropRect,
        maxOutputEdge: Int = MAX_CROP_OUTPUT_EDGE,
    ): ByteArray {
        val clamped = WritingImageCropGeometry.clamp(
            rect,
            source.sourceWidth,
            source.sourceHeight,
        )
        if (clamped.width <= 0 || clamped.height <= 0) {
            throw IOException("裁剪区域无效")
        }
        val scale = minOf(
            1f,
            maxOutputEdge.toFloat() / maxOf(clamped.width, clamped.height),
        )
        val outputWidth = (clamped.width * scale).toInt().coerceAtLeast(1)
        val outputHeight = (clamped.height * scale).toInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.drawBitmap(
                source.source,
                Rect(clamped.left, clamped.top, clamped.right, clamped.bottom),
                Rect(0, 0, outputWidth, outputHeight),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            return java.io.ByteArrayOutputStream().use { stream ->
                if (!output.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                    throw IOException("图片编码失败")
                }
                stream.toByteArray()
            }
        } finally {
            if (!output.isRecycled) output.recycle()
        }
    }

    private fun makePreview(source: Bitmap): Bitmap {
        val maxEdge = maxOf(source.width, source.height)
        if (maxEdge <= PREVIEW_EDGE) return source
        val scale = PREVIEW_EDGE.toFloat() / maxEdge
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /**
     * 只解码一份供裁剪界面使用的中等尺寸位图，不创建 staged 资源。
     * 与头像裁剪相同：限制解码尺寸、应用 EXIF 方向，取消时不产生图片文件。
     */
    suspend fun loadEditableImage(uri: String): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = readBytes(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("图片无法解码")
            }
            var sample = 1
            while (bounds.outWidth / sample > MAX_EDITABLE_EDGE ||
                bounds.outHeight / sample > MAX_EDITABLE_EDGE
            ) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: throw IOException("图片无法解码")
            val orientation = runCatching {
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            applyOrientation(decoded, orientation)
        }
    }

    /** 保存裁剪后的 JPEG/PNG 字节，复用与相册导入相同的内部资源约束。 */
    suspend fun importBytes(
        bookId: String,
        bytes: ByteArray,
    ): Result<ImportedWritingImage> = withContext(Dispatchers.IO) {
        runCatching { saveStaged(bookId, bytes) }
    }

    private fun readBytes(uri: String): ByteArray {
        val parsed = Uri.parse(uri)
        return contentResolver.openInputStream(parsed)?.use { input ->
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
            buffer.toByteArray().also {
                if (it.isEmpty()) throw IOException("所选图片内容为空")
                if (ImageFormatDetector.detect(it) == null) {
                    throw IOException("不支持的图片格式（仅支持 JPEG、PNG、WebP 等常见格式）")
                }
            }
        } ?: throw IOException("无法读取所选图片")
    }

    private fun saveStaged(bookId: String, bytes: ByteArray): ImportedWritingImage {
        if (bytes.isEmpty()) throw IOException("图片内容为空")
        val format = ImageFormatDetector.detect(bytes)
            ?: throw IOException("不支持的图片格式（仅支持 JPEG、PNG、WebP 等常见格式）")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("图片无法解码")
        }

        val stagingDir = File(context.filesDir, "writing-image-staging/$bookId")
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
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return ImportedWritingImage(
            resource = BookResource(
                resourceId = id,
                relativePath = relativePath,
                mediaType = format.mediaType,
                sizeBytes = bytes.size.toLong(),
                sha256 = sha256,
                kind = BookResourceKind.IMAGE,
            ),
            bytes = bytes,
            stagingFile = target,
        )
    }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return source
        }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) source.recycle()
        return rotated
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
        const val MAX_EDITABLE_EDGE = 1600
        const val MAX_SOURCE_EDGE = 4096
        const val PREVIEW_EDGE = 1600
        const val MAX_CROP_OUTPUT_EDGE = 2048
    }
}
