package com.xtt.toolbox.domain.importing

/** 文件内容的最低限度安全检查，避免仅凭扩展名把明显二进制文件当 TXT。 */
object BookContentValidator {
    fun requirePlainText(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) return
        if (startsWith(bytes, 0xFF, 0xFE) || startsWith(bytes, 0xFE, 0xFF)) return

        if (bytes.any { it == 0.toByte() }) {
            throw TxtImportException("文件内容不是可读取的文本，可能是二进制文件")
        }
        val binaryMagic = listOf(
            byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte(), '-'.code.toByte()),
            byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()),
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
            byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte()),
            byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03.toByte(), 0x04.toByte()),
            byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte()),
        )
        if (binaryMagic.any { startsWith(bytes, *it.map { value -> value.toInt() and 0xFF }.toIntArray()) }) {
            throw TxtImportException("文件内容不是可读取的文本，可能不是 TXT 文件")
        }
    }

    private fun startsWith(bytes: ByteArray, vararg prefix: Int): Boolean {
        if (bytes.size < prefix.size) return false
        return prefix.indices.all { (bytes[it].toInt() and 0xFF) == (prefix[it] and 0xFF) }
    }
}
