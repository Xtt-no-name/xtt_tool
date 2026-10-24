package com.xtt.toolbox.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class LocalProfile(
    val nickname: String,
    val avatarFile: File?,
)

object ProfileRules {
    const val DEFAULT_NICKNAME = "xtt的工具箱"
    const val MAX_NICKNAME_LENGTH = 24

    fun normalizeNickname(raw: String): String? =
        raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NICKNAME_LENGTH }
}

/** 把方形裁剪窗口映射到原始位图；zoom/offset 由裁剪手势产生。 */
object AvatarCrop {
    const val OUTPUT_SIZE = 512

    data class Geometry(
        val left: Float,
        val top: Float,
        val side: Float,
    )

    fun geometry(
        sourceWidth: Int,
        sourceHeight: Int,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        viewportPx: Float,
    ): Geometry {
        require(sourceWidth > 0 && sourceHeight > 0)
        val viewport = viewportPx.coerceAtLeast(1f)
        val baseScale = maxOf(
            viewport / sourceWidth,
            viewport / sourceHeight,
        )
        val scale = baseScale * zoom.coerceIn(1f, 4f)
        val left = viewport / 2f + offsetX - sourceWidth * scale / 2f
        val top = viewport / 2f + offsetY - sourceHeight * scale / 2f
        val sourceLeft = (-left / scale).coerceIn(0f, sourceWidth - 1f)
        val sourceTop = (-top / scale).coerceIn(0f, sourceHeight - 1f)
        val side = (viewport / scale).coerceAtMost(
            minOf(sourceWidth - sourceLeft, sourceHeight - sourceTop),
        ).coerceAtLeast(1f)
        return Geometry(sourceLeft, sourceTop, side)
    }

    fun crop(
        source: Bitmap,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        viewportPx: Float,
        outputSize: Int = OUTPUT_SIZE,
    ): Bitmap {
        val g = geometry(
            sourceWidth = source.width,
            sourceHeight = source.height,
            zoom = zoom,
            offsetX = offsetX,
            offsetY = offsetY,
            viewportPx = viewportPx,
        )
        val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(
            source,
            Rect(
                g.left.toInt(),
                g.top.toInt(),
                (g.left + g.side).toInt(),
                (g.top + g.side).toInt(),
            ),
            Rect(0, 0, outputSize, outputSize),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return output
    }
}

/**
 * 本地个人资料：昵称存 SharedPreferences，头像复制到应用私有目录。
 * 不依赖系统 URI；头像文件丢失或损坏时由 UI 回退默认头像。
 */
class LocalProfileStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("local_profile", Context.MODE_PRIVATE)
    private val profileDir = File(context.filesDir, "profile").apply { mkdirs() }
    private val avatarFile = File(profileDir, "avatar.jpg")

    fun load(): LocalProfile = LocalProfile(
        nickname = preferences.getString(KEY_NICKNAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: ProfileRules.DEFAULT_NICKNAME,
        avatarFile = recoverAvatarIfNeeded(),
    )

    fun saveNickname(raw: String): Result<String> {
        val normalized = ProfileRules.normalizeNickname(raw)
            ?: return Result.failure(IllegalArgumentException("昵称不能为空且不能超过 24 个字符"))
        preferences.edit().putString(KEY_NICKNAME, normalized).apply()
        return Result.success(normalized)
    }

    /** 只解码一份供裁剪界面使用的中等尺寸副本，并应用 EXIF 方向。 */
    suspend fun loadEditableAvatar(uri: String): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(Uri.parse(uri))
                ?: error("无法读取图片，请重新选择")
            boundsStream.use { stream ->
                // inJustDecodeBounds 模式下 decodeStream 按设计返回 null，不能把它当作读取失败。
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("图片内容无效")
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) {
                sample *= 2
            }
            val decoded = resolver.openInputStream(Uri.parse(uri))?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                )
            } ?: error("图片无法解码，可能是不支持的格式")
            val orientation = runCatching {
                resolver.openInputStream(Uri.parse(uri))?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            val rotated = applyOrientation(decoded, orientation)
            if (rotated !== decoded) decoded.recycle()
            rotated
        }
    }

    suspend fun saveAvatarBitmap(bitmap: Bitmap): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (bitmap.width <= 0 || bitmap.height <= 0) error("头像尺寸无效")
            if (bitmap.width != bitmap.height) error("头像必须是正方形")
            val temp = File(profileDir, ".avatar-${System.currentTimeMillis()}.jpg")
            val oldBackup = File(profileDir, ".avatar-old.jpg")
            try {
                FileOutputStream(temp).use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                        error("头像写入失败")
                    }
                    output.flush()
                }
                if (temp.length() == 0L) error("头像写入不完整")
                oldBackup.delete()
                if (avatarFile.exists() && !avatarFile.renameTo(oldBackup)) {
                    error("旧头像暂时无法替换")
                }
                if (!temp.renameTo(avatarFile)) {
                    temp.copyTo(avatarFile, overwrite = true)
                    temp.delete()
                }
                oldBackup.delete()
            } catch (error: Throwable) {
                temp.delete()
                oldBackup.takeIf { it.isFile }?.let {
                    avatarFile.delete()
                    it.renameTo(avatarFile)
                }
                throw error
            }
            avatarFile
        }
    }

    private fun recoverAvatarIfNeeded(): File? {
        val oldBackup = File(profileDir, ".avatar-old.jpg")
        if (!avatarFile.isFile && oldBackup.isFile) {
            oldBackup.renameTo(avatarFile)
        }
        return avatarFile.takeIf { it.isFile }
    }

    fun clearAvatar(): Boolean = runCatching {
        avatarFile.delete() || !avatarFile.exists()
    }.getOrDefault(false)

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
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private companion object {
        const val KEY_NICKNAME = "nickname"
    }
}
