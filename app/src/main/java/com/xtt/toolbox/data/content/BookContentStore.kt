package com.xtt.toolbox.data.content

import com.xtt.toolbox.domain.content.ExtractedResource
import java.io.File

/** 正文文件写入结果；location 是相对于内容根目录的内部路径。 */
data class SavedContent(
    val location: String,
    val sizeBytes: Long,
    val sha256: String,
)

/**
 * 应用内部正文存储边界。
 *
 * 文件 I/O 是阻塞调用，调用方必须在 IO 调度器执行。实现不得覆盖原始导入文件。
 */
interface BookContentStore {
    fun save(bookId: String, text: String): SavedContent

    /** 保存正文和 EPUB 资源；资源路径必须位于该书籍专属目录内。 */
    fun saveDocument(
        bookId: String,
        text: String,
        resources: List<ExtractedResource>,
    ): SavedContent = save(bookId, text)

    /** 读取应用内部 UTF-8 正文；location 非法或文件缺失时抛出 IOException。 */
    fun readText(location: String): String

    /** 解析已保存资源；非法路径或文件不存在时返回 null。 */
    fun resolveResource(location: String): File? = null

    /** 解析某本书专属目录中的资源；不允许使用 bookId/相对路径逃逸书籍目录。 */
    fun resolveBookResource(bookId: String, relativePath: String): File? =
        resolveResource("books/$bookId/$relativePath")

    /** 在已有书籍目录内写入单个资源；用于 Markdown 阅读时补充图片。 */
    fun saveResource(bookId: String, relativePath: String, bytes: ByteArray): Boolean = false

    /** 删除已有书籍目录中的单个资源；只允许 bookId 专属目录内路径。 */
    fun deleteResource(bookId: String, relativePath: String): Boolean = false

    /**
     * 将编辑稿写入新的 revision 目录，不触碰当前有效正文。
     * 成功返回新内容位置（位于 books/<bookId>/revisions/<revisionId>）。
     */
    fun writeDocumentRevision(
        bookId: String,
        revisionId: String,
        text: String,
        resources: List<com.xtt.toolbox.domain.content.ExtractedResource>,
    ): SavedContent {
        throw UnsupportedOperationException("当前内容存储不支持 revision")
    }

    /** 删除未被当前书籍引用的 revision 目录。 */
    fun deleteRevision(bookId: String, revisionId: String): Boolean = false

    fun delete(location: String)

    /** 删除整本导入书籍的正文和资源目录；返回是否已确认没有残留。 */
    fun deleteBook(bookId: String): Boolean = true

    fun exists(location: String): Boolean
}
