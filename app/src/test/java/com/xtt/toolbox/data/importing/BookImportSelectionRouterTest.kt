package com.xtt.toolbox.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookImportSelectionRouterTest {
    @Test
    fun emptySelectionIsIgnored() {
        assertEquals(BookImportSelection.None, BookImportSelectionRouter.route(emptyList()))
    }

    @Test
    fun oneDocumentUsesPreviewFlow() {
        val selection = BookImportSelectionRouter.route(listOf("content://one"))
        assertTrue(selection is BookImportSelection.Single)
        assertEquals("content://one", (selection as BookImportSelection.Single).uri)
    }

    @Test
    fun multipleDocumentsUseBatchFlow() {
        val selection = BookImportSelectionRouter.route(
            listOf("content://one", "content://two", "content://three"),
        )
        assertTrue(selection is BookImportSelection.Batch)
        assertEquals(
            listOf("content://one", "content://two", "content://three"),
            (selection as BookImportSelection.Batch).uris,
        )
    }
}
