package com.xtt.toolbox.domain.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownImagePathResolverTest {
    @Test
    fun resolvesSubdirectoriesAndParentSegmentsInsideSelectedRoot() {
        assertEquals("images/cover.png", MarkdownImagePathResolver.resolve("", "images/cover.png"))
        assertEquals("book/images/cover.png", MarkdownImagePathResolver.resolve("book", "images/cover.png"))
        assertEquals("assets/picture.jpg", MarkdownImagePathResolver.resolve("book", "../assets/picture.jpg"))
        assertEquals("book/中文 图.png", MarkdownImagePathResolver.resolve("book", "images/../中文%20图.png"))
    }

    @Test
    fun rejectsEscapesAndUnsafePaths() {
        assertNull(MarkdownImagePathResolver.resolve("", "../assets/picture.jpg"))
        assertNull(MarkdownImagePathResolver.resolve("book", "../../outside.jpg"))
        assertNull(MarkdownImagePathResolver.resolve("", "/sdcard/a.png"))
        assertNull(MarkdownImagePathResolver.resolve("", "images\\a.png"))
        assertNull(MarkdownImagePathResolver.resolve("", "https://example.com/a.png"))
        assertNull(MarkdownImagePathResolver.resolve("", "%2e%2e/outside.png"))
    }
}
