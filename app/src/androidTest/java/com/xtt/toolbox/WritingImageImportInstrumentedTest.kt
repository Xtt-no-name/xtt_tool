package com.xtt.toolbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xtt.toolbox.data.importing.WritingImageImportService
import com.xtt.toolbox.domain.writing.WritingImageCropGeometry
import com.xtt.toolbox.domain.writing.WritingImageCropRect
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WritingImageImportInstrumentedTest {
    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()

    @After
    fun cleanUp() {
        files.forEach { file -> runCatching { file.delete() } }
        files.clear()
    }

    @Test
    fun importsIntoPrivateFilesAndDiscardsStaging() = runBlocking {
        val source = File(context.cacheDir, "writing-image-source-${UUID.randomUUID()}.png")
        val bytes = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
            Base64.DEFAULT,
        )
        source.writeBytes(bytes)
        files += source
        val bookId = "image-test-${UUID.randomUUID()}"
        val service = WritingImageImportService(context, context.contentResolver)
        val imported = service.import(bookId, Uri.fromFile(source).toString()).getOrThrow()
        files += imported.stagingFile
        assertTrue(imported.stagingFile.isFile)
        assertTrue(
            imported.stagingFile.canonicalPath.startsWith(
                context.filesDir.canonicalPath + File.separator,
            ),
        )
        assertFalse(imported.stagingFile.canonicalPath.contains("/Pictures/"))
        assertFalse(imported.stagingFile.canonicalPath.contains("/DCIM/"))
        assertTrue(imported.resource.relativePath.startsWith("editor/images/"))
        assertEquals(bytes.size.toLong(), imported.resource.sizeBytes)
        assertTrue(bytes.contentEquals(imported.stagingFile.readBytes()))
        assertTrue(source.exists())
        assertTrue(bytes.contentEquals(source.readBytes()))
        service.discard(imported)
        assertFalse(imported.stagingFile.exists())
    }

    @Test
    fun invalidBytesFailWithoutReturningImage() = runBlocking {
        val source = File(context.cacheDir, "writing-image-invalid-${UUID.randomUUID()}.png")
        source.writeBytes(byteArrayOf(1, 2, 3, 4))
        files += source
        val service = WritingImageImportService(context, context.contentResolver)
        val result = service.import(
            "image-test-${UUID.randomUUID()}",
            Uri.fromFile(source).toString(),
        )
        assertTrue(result.isFailure)
        assertTrue(source.exists())
    }

    @Test
    fun cropUsesSourceNotPreviewAndHonorsSourceRectangle() = runBlocking {
        val sourceFile = File(context.cacheDir, "writing-crop-source-${UUID.randomUUID()}.png")
        val sourceBitmap = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888)
        sourceBitmap.eraseColor(Color.RED)
        java.io.FileOutputStream(sourceFile).use { output ->
            sourceBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        sourceBitmap.recycle()
        files += sourceFile

        val service = WritingImageImportService(context, context.contentResolver)
        val cropSource = service.loadCropSource(Uri.fromFile(sourceFile).toString()).getOrThrow()
        try {
            assertEquals(2000, cropSource.sourceWidth)
            assertEquals(1000, cropSource.sourceHeight)
            assertEquals(2000, cropSource.source.width)
            assertEquals(1600, cropSource.preview.width)
            assertEquals(800, cropSource.preview.height)
            assertTrue(cropSource.preview !== cropSource.source)

            val rect = WritingImageCropGeometry.clamp(
                WritingImageCropRect(100, 100, 500, 300),
                cropSource.sourceWidth,
                cropSource.sourceHeight,
            )
            val bytes = service.cropToJpegBytes(cropSource, rect)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            assertEquals(400, bounds.outWidth)
            assertEquals(200, bounds.outHeight)
        } finally {
            cropSource.recycle()
        }
    }

    @Test
    fun cropSourceAppliesExifOrientationBeforePreviewAndCrop() = runBlocking {
        val sourceFile = File(context.cacheDir, "writing-exif-source-${UUID.randomUUID()}.jpg")
        val sourceBitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        sourceBitmap.eraseColor(Color.BLUE)
        java.io.FileOutputStream(sourceFile).use { output ->
            sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        sourceBitmap.recycle()
        android.media.ExifInterface(sourceFile.absolutePath).apply {
            setAttribute(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_ROTATE_90.toString(),
            )
            saveAttributes()
        }
        files += sourceFile

        val service = WritingImageImportService(context, context.contentResolver)
        val cropSource = service.loadCropSource(Uri.fromFile(sourceFile).toString()).getOrThrow()
        try {
            assertEquals(200, cropSource.sourceWidth)
            assertEquals(400, cropSource.sourceHeight)
            assertEquals(200, cropSource.preview.width)
            assertEquals(400, cropSource.preview.height)
            val bytes = service.cropToJpegBytes(
                cropSource,
                WritingImageCropRect(0, 0, 200, 400),
            )
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            assertEquals(200, bounds.outWidth)
            assertEquals(400, bounds.outHeight)
        } finally {
            cropSource.recycle()
        }
    }
}
