package com.xtt.toolbox.domain.content

/** 本地图片魔数校验；与 EPUB/Markdown 导入限制保持一致。 */
data class DetectedImageFormat(
    val mediaType: String,
    val extension: String,
)

object ImageFormatDetector {
    fun detect(bytes: ByteArray): DetectedImageFormat? {
        if (bytes.size < 12) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() ->
                DetectedImageFormat("image/jpeg", "jpg")

            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() ->
                DetectedImageFormat("image/png", "png")

            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() ->
                DetectedImageFormat("image/gif", "gif")

            bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) ->
                DetectedImageFormat("image/webp", "webp")

            else -> null
        }
    }
}
