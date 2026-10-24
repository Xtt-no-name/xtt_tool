package com.xtt.toolbox.domain.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookBlockStyleCodecTest {
    @Test
    fun roundTripsBlockStyleAndSpans() {
        val style = BookBlockStyle(
            textAlign = BookTextAlign.JUSTIFY,
            textIndentEm = 1.5f,
            marginTopEm = 0.5f,
            marginBottomEm = 1f,
            lineHeightMultiplier = 1.4f,
            listItem = true,
            taskChecked = true,
            imageWidthFraction = 0.7f,
            imageMaxWidthFraction = 0.9f,
            imageHeightDp = 240f,
            imageSourcePath = "images/封面 图.png",
        )
        val spans = listOf(
            InlineSpan(0, 3, bold = true, italic = true),
            InlineSpan(
                3,
                8,
                underline = true,
                strike = true,
                link = true,
                linkTarget = "https://example.com/a?x=1&y=2",
            ),
            InlineSpan(8, 12, footnote = true, targetBlockId = "md-footnote-1"),
        )

        assertEquals(style, BookBlockStyleCodec.decodeStyle(BookBlockStyleCodec.encodeStyle(style)))
        assertEquals(spans, BookBlockStyleCodec.decodeSpans(BookBlockStyleCodec.encodeSpans(spans)))
    }

    @Test
    fun malformedStyleAndSpansDegradeSafely() {
        assertNull(BookBlockStyleCodec.decodeStyle(null))
        assertNull(BookBlockStyleCodec.decodeStyle(""))
        assertTrue(BookBlockStyleCodec.decodeSpans("bad;1-0:x;2-3:q").isEmpty())

        val decoded = BookBlockStyleCodec.decodeStyle("a=CENTER;i=999;t=-2;w=4")
        assertEquals(BookTextAlign.CENTER, decoded?.textAlign)
        assertEquals(8f, decoded?.textIndentEm)
        assertEquals(0f, decoded?.marginTopEm)
        assertEquals(1f, decoded?.imageWidthFraction)
    }
}
