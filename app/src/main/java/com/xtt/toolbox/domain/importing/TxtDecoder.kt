package com.xtt.toolbox.domain.importing

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class EncodingConfidence {
    HIGH,
    MEDIUM,
}

data class DecodedText(
    val text: String,
    val charsetName: String,
    val hadBom: Boolean,
    val confidence: EncodingConfidence,
    val warnings: List<String>,
)

class TxtDecodingException(
    message: String,
    val availableEncodings: List<String>,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * TXT 编码识别与严格解码。
 *
 * 优先检查 BOM。没有 BOM 时先严格尝试 UTF-8，再尝试 GB18030/GBK；两种都成功且结果不同时
 * 仍默认 UTF-8，但降低置信度并提示用户可切换编码。解码失败绝不返回带替换字符的乱码。
 */
class TxtDecoder {
    private val gbCharset: Charset? = when {
        Charset.isSupported("GB18030") -> Charset.forName("GB18030")
        Charset.isSupported("GBK") -> Charset.forName("GBK")
        else -> null
    }

    val supportedEncodings: List<String> = buildList {
        add("UTF-8")
        gbCharset?.let { add(it.name()) }
        add("UTF-16LE")
        add("UTF-16BE")
    }

    fun decode(bytes: ByteArray, forcedEncoding: String? = null): DecodedText {
        if (bytes.isEmpty()) {
            throw TxtDecodingException("文件为空，没有可导入的文本", supportedEncodings)
        }

        if (forcedEncoding != null) {
            val charset = runCatching { Charset.forName(forcedEncoding) }.getOrNull()
                ?: throw TxtDecodingException("不支持的编码：$forcedEncoding", supportedEncodings)
            val (payload, hadBom) = stripBomFor(bytes, charset)
            val text = decodeStrict(payload, charset)
                ?: throw TxtDecodingException(
                    "使用 $forcedEncoding 解码失败，请选择其他编码",
                    supportedEncodings,
                )
            return DecodedText(
                text = text,
                charsetName = charset.name(),
                hadBom = hadBom,
                confidence = EncodingConfidence.HIGH,
                warnings = emptyList(),
            )
        }

        val bom = detectBom(bytes)
        if (bom != null) {
            val text = decodeStrict(bytes.copyOfRange(bom.length, bytes.size), bom.charset)
                ?: throw TxtDecodingException(
                    "检测到 ${bom.charset.name()} BOM，但正文解码失败",
                    supportedEncodings,
                )
            return DecodedText(
                text = text,
                charsetName = bom.charset.name(),
                hadBom = true,
                confidence = EncodingConfidence.HIGH,
                warnings = emptyList(),
            )
        }

        val utf8 = decodeStrict(bytes, Charsets.UTF_8)
        val gb = gbCharset?.let { decodeStrict(bytes, it) }
        val gbName = gbCharset?.name() ?: "GB18030"

        if (utf8 != null && gb != null) {
            if (utf8 == gb) {
                return DecodedText(
                    text = utf8,
                    charsetName = "UTF-8",
                    hadBom = false,
                    confidence = EncodingConfidence.HIGH,
                    warnings = emptyList(),
                )
            }
            return DecodedText(
                text = utf8,
                charsetName = "UTF-8",
                hadBom = false,
                confidence = EncodingConfidence.MEDIUM,
                warnings = listOf("文件没有 BOM，UTF-8 与 $gbName 都可解码；当前按 UTF-8 读取，请预览确认。"),
            )
        }

        if (utf8 != null) {
            return DecodedText(
                text = utf8,
                charsetName = "UTF-8",
                hadBom = false,
                confidence = EncodingConfidence.MEDIUM,
                warnings = listOf("文件没有 BOM，按 UTF-8 读取；如出现乱码可切换编码重试。"),
            )
        }

        if (gb != null) {
            return DecodedText(
                text = gb,
                charsetName = gbName,
                hadBom = false,
                confidence = EncodingConfidence.MEDIUM,
                warnings = listOf("UTF-8 解码失败，当前按 $gbName 读取，请预览确认。"),
            )
        }

        throw TxtDecodingException(
            "无法可靠识别文件编码，请选择编码后重试",
            supportedEncodings,
        )
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun detectBom(bytes: ByteArray): Bom? = when {
        bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte() -> Bom(Charsets.UTF_8, 3)

        bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte() -> Bom(Charset.forName("UTF-16LE"), 2)

        bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte() -> Bom(Charset.forName("UTF-16BE"), 2)

        else -> null
    }

    private fun stripBomFor(bytes: ByteArray, charset: Charset): Pair<ByteArray, Boolean> = when (charset.name()) {
        "UTF-8" -> if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size) to true
        } else {
            bytes to false
        }

        "UTF-16LE" -> if (
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
        ) {
            bytes.copyOfRange(2, bytes.size) to true
        } else {
            bytes to false
        }

        "UTF-16BE" -> if (
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
        ) {
            bytes.copyOfRange(2, bytes.size) to true
        } else {
            bytes to false
        }

        else -> bytes to false
    }

    private data class Bom(val charset: Charset, val length: Int)
}

/** 便于测试和 UI 复用的大小限制；普通长篇小说通常远小于 64 MiB。 */
const val MAX_TXT_BYTES: Long = 64L * 1024L * 1024L
