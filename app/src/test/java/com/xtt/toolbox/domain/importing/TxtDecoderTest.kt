package com.xtt.toolbox.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class TxtDecoderTest {
    private val decoder = TxtDecoder()

    @Test
    fun decodesPlainUtf8() {
        val text = "第一章 开始\n这是正文。"
        val decoded = decoder.decode(text.toByteArray(Charsets.UTF_8))

        assertEquals(text, decoded.text)
        assertEquals("UTF-8", decoded.charsetName)
        assertFalse(decoded.hadBom)
    }

    @Test
    fun stripsUtf8Bom() {
        val text = "带 BOM 的中文"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(Charsets.UTF_8)
        val decoded = decoder.decode(bytes)

        assertEquals(text, decoded.text)
        assertEquals("UTF-8", decoded.charsetName)
        assertTrue(decoded.hadBom)
    }

    @Test
    fun decodesUtf16LeWithBom() {
        val text = "第一章 开始"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            text.toByteArray(Charset.forName("UTF-16LE"))
        val decoded = decoder.decode(bytes)

        assertEquals(text, decoded.text)
        assertEquals("UTF-16LE", decoded.charsetName)
        assertTrue(decoded.hadBom)
    }

    @Test
    fun decodesUtf16BeWithBom() {
        val text = "第一章 开始"
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            text.toByteArray(Charset.forName("UTF-16BE"))
        val decoded = decoder.decode(bytes)

        assertEquals(text, decoded.text)
        assertEquals("UTF-16BE", decoded.charsetName)
        assertTrue(decoded.hadBom)
    }

    @Test
    fun decodesGb18030WhenPresent() {
        val gb = Charset.isSupported("GB18030")
        org.junit.Assume.assumeTrue("当前 JVM 不支持 GB18030", gb)
        val text = "中文测试，这是一本长篇小说。"
        val decoded = decoder.decode(text.toByteArray(Charset.forName("GB18030")))

        assertEquals(text, decoded.text)
    }

    @Test
    fun rejectsInvalidForcedUtf8() {
        val error = assertThrows(TxtDecodingException::class.java) {
            decoder.decode(byteArrayOf(0xC3.toByte(), 0x28), forcedEncoding = "UTF-8")
        }
        assertTrue(error.availableEncodings.isNotEmpty())
    }

    @Test
    fun rejectsEmptyFile() {
        assertThrows(TxtDecodingException::class.java) {
            decoder.decode(ByteArray(0))
        }
    }
}
