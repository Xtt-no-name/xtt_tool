package com.xtt.toolbox.domain.importing

/** 书籍导入允许的格式；扩展名识别不区分大小写。 */
enum class BookImportFormat {
    TXT,
    EPUB,
    MARKDOWN,
}

object BookImportFormatGate {
    private val allowedDescription = "仅支持 .txt、.epub、.md、.markdown 文件"

    fun fromFileName(fileName: String?): BookImportFormat? {
        val name = fileName?.substringAfterLast('/')?.substringAfterLast('\\') ?: return null
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return null
        return when (name.substring(dot + 1).lowercase()) {
            "txt" -> BookImportFormat.TXT
            "epub" -> BookImportFormat.EPUB
            "md", "markdown" -> BookImportFormat.MARKDOWN
            else -> null
        }
    }

    fun require(fileName: String?, expected: BookImportFormat) {
        val actual = fromFileName(fileName)
            ?: throw TxtImportException("$allowedDescription，当前文件没有受支持的扩展名")
        if (actual != expected) {
            throw TxtImportException("$allowedDescription，当前文件格式与导入入口不匹配")
        }
    }

    fun isAllowed(fileName: String?): Boolean = fromFileName(fileName) != null

    fun rejectionMessage(fileName: String?): String =
        if (fileName.isNullOrBlank()) {
            "$allowedDescription，当前文件没有可识别的文件名"
        } else {
            "$allowedDescription，当前文件扩展名不受支持"
        }
}
