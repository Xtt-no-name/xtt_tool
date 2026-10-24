package com.xtt.toolbox.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookImportFormatGateTest {
    @Test
    fun acceptsOnlySupportedExtensionsCaseInsensitive() {
        assertEquals(BookImportFormat.TXT, BookImportFormatGate.fromFileName("book.txt"))
        assertEquals(BookImportFormat.TXT, BookImportFormatGate.fromFileName("BOOK.TXT"))
        assertEquals(BookImportFormat.EPUB, BookImportFormatGate.fromFileName("book.epub"))
        assertEquals(BookImportFormat.EPUB, BookImportFormatGate.fromFileName("BOOK.EPUB"))
        assertEquals(BookImportFormat.MARKDOWN, BookImportFormatGate.fromFileName("book.md"))
        assertEquals(BookImportFormat.MARKDOWN, BookImportFormatGate.fromFileName("book.MARKDOWN"))
        assertTrue(BookImportFormatGate.isAllowed("folder/书名.Md"))
    }

    @Test
    fun rejectsUnsupportedOrMissingExtensions() {
        listOf("book.pdf", "book.docx", "image.png", "book", "book.", ".md", "").forEach {
            assertNull(it, BookImportFormatGate.fromFileName(it))
            assertFalse(BookImportFormatGate.isAllowed(it))
        }
    }

    @Test
    fun requiresFormatForEntryPoint() {
        BookImportFormatGate.require("BOOK.TXT", BookImportFormat.TXT)
        BookImportFormatGate.require("book.MARKDOWN", BookImportFormat.MARKDOWN)
        val missing = runCatching {
            BookImportFormatGate.require("book.pdf", BookImportFormat.TXT)
        }.exceptionOrNull()
        assertTrue(missing is TxtImportException)
        assertTrue(missing?.message?.contains("仅支持") == true)
    }
}
