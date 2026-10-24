package com.xtt.toolbox

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xtt.toolbox.data.importing.WritingImageImportService
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
}
