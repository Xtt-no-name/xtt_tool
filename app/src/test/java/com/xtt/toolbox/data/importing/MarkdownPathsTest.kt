package com.xtt.toolbox.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownPathsTest {
    @Test
    fun normalizesRelativeNamesAndDecodesPercentEscapes() {
        assertEquals("images/cover.png", MarkdownPaths.normalize("images/cover.png"))
        assertEquals("images/中文 图.png", MarkdownPaths.normalize("images/%E4%B8%AD%E6%96%87%20%E5%9B%BE.png"))
        assertEquals("images/a+b.png", MarkdownPaths.normalize("images/a+b.png"))
    }

    @Test
    fun rejectsAbsoluteTraversalAndProtocols() {
        assertNull(MarkdownPaths.normalize("../outside.png"))
        assertNull(MarkdownPaths.normalize("images/../../outside.png"))
        assertNull(MarkdownPaths.normalize("/sdcard/image.png"))
        assertNull(MarkdownPaths.normalize("\\image.png"))
        assertNull(MarkdownPaths.normalize("https://example.com/a.png"))
        assertNull(MarkdownPaths.normalize("images/%2e%2e/outside.png"))
    }
}
