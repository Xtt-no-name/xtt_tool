package com.xtt.toolbox.data.repository

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookVolume
import com.xtt.toolbox.data.model.ReadingPosition
import com.xtt.toolbox.domain.content.BookDocument
import kotlinx.coroutines.flow.StateFlow

/**
 * 书籍元数据仓库。
 *
 * 所有读写操作都应为 suspend，调用方负责切换到 IO 调度器；实现内部也需要保证
 * 数据库操作不阻塞主线程。当前实现为 SQLite 元数据 + 应用内部正文文件。
 */
interface BookRepository {
    val books: StateFlow<List<Book>>

    /** 从持久化存储重新加载全部书籍；失败时保留当前缓存并抛出异常。 */
    suspend fun reload()

    suspend fun getBook(bookId: String): Book?

    /** 按规范化正文的 SHA-256 查找疑似重复书籍；不按文件名判断重复。 */
    suspend fun findByContentHash(contentHash: String): Book?

    /** 新增书籍；bookId 已存在或数据库写入失败时返回失败，不更新书架缓存。 */
    suspend fun insert(book: Book): Result<Unit>

    /** 原子新增书籍和统一内容模型（EPUB 章节/块/资源索引）。 */
    suspend fun insertDocument(book: Book, document: BookDocument): Result<Unit> =
        insert(book)

    /** 读取 EPUB 统一内容模型；TXT 或没有内容块时返回 null。 */
    suspend fun loadDocument(bookId: String): BookDocument? = null

    /** 读取写作作品的分卷；不创建“未分卷”虚拟记录。 */
    suspend fun loadVolumes(bookId: String): List<BookVolume> = emptyList()

    suspend fun insertVolume(volume: BookVolume): Result<Unit> =
        Result.failure(UnsupportedOperationException("当前仓库不支持分卷"))

    suspend fun renameVolume(volumeId: String, title: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("当前仓库不支持分卷"))

    suspend fun deleteVolume(volumeId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("当前仓库不支持分卷"))

    /** 只替换指定书籍的文档索引；用于 Markdown 图片补充，不改书籍元数据。 */
    suspend fun replaceDocument(bookId: String, document: BookDocument): Result<Unit> =
        Result.failure(UnsupportedOperationException("当前仓库不支持替换文档"))

    /**
     * 原子提交一本已有书籍的新元数据和文档索引。
     * 实现必须在同一数据库事务中更新 books 与 chapters/blocks/resources。
     */
    suspend fun commitDocument(book: Book, document: BookDocument): Result<Unit> =
        Result.failure(UnsupportedOperationException("当前仓库不支持编辑稿提交"))

    /**
     * 内容哈希仍等于 [expectedContentHash] 时才提交；返回 false 表示版本冲突。
     * 默认实现无法做条件更新，只能委托普通提交并返回 true，供测试/轻量仓库使用。
     */
    suspend fun commitDocumentIfUnchanged(
        book: Book,
        document: BookDocument,
        expectedContentHash: String?,
    ): Result<Boolean> = commitDocument(book, document).map { true }

    /** 按 bookId 新增或更新；保留 bookId。 */
    suspend fun upsert(book: Book)

    /**
     * 只更新某本书的阅读位置和可选最近打开时间，不触碰正文或其他书籍。
     *
     * 数据库写入成功后才会更新 [books] 缓存；失败返回 failure，调用方不得假报保存成功。
     */
    suspend fun updateReadingPosition(
        bookId: String,
        position: ReadingPosition,
        lastOpenedAt: Long? = null,
    ): Result<Unit>

    /** 只移除书架中的元数据，不执行任何文件删除。 */
    suspend fun removeFromShelf(bookId: String)
}
