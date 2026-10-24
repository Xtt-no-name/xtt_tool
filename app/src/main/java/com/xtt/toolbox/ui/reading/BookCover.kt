package com.xtt.toolbox.ui.reading

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.ui.components.BookIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书架封面。
 *
 * 只接受已经由数据层解析过的应用内部文件路径；解码限制缩略图尺寸并共享内存 LRU，
 * 避免滚动时反复解码原图。文件缺失、损坏或未提供封面时回退到项目原有占位图标。
 */
@Composable
fun BookCover(
    coverPath: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 26.dp,
) {
    val bitmap by produceState<Bitmap?>(
        initialValue = coverPath?.let { BookCoverCache.get(it) },
        key1 = coverPath,
    ) {
        value = if (coverPath == null) {
            null
        } else {
            withContext(Dispatchers.IO) { BookCoverCache.load(coverPath) }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            BookIcon(
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private object BookCoverCache {
    private const val MAX_DIMENSION = 320
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Synchronized
    fun get(path: String): Bitmap? = cache.get(path)

    fun load(path: String): Bitmap? {
        cache.get(path)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (
            bounds.outWidth / sample > MAX_DIMENSION ||
            bounds.outHeight / sample > MAX_DIMENSION
        ) {
            sample *= 2
        }
        val bitmap = runCatching {
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull() ?: return null
        cache.put(path, bitmap)
        return bitmap
    }
}
