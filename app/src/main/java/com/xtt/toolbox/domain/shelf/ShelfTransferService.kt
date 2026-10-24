package com.xtt.toolbox.domain.shelf

import com.xtt.toolbox.data.model.Book

/**
 * 双书架转移边界。本阶段只预留接口和结果类型，不执行文件复制。
 *
 * 规则：
 * - 阅读 → 写作（[moveToWritingShelf]）：保留同一 bookId，把原始导入内容作为内部编辑稿的
 *   初始正文，保留 sourceUri 和原始文件；复制成功后才修改 shelfType，失败时归属不变。
 * - 写作 → 阅读：只改变 shelfType 和编辑权限，保留内部编辑稿，不覆盖原始导入文件。
 * - 两个方向都保留同一个 bookId、阅读位置和可兼容的批注关联。
 * - 独立可编辑副本是另一个身份，不能复用本接口的 move 语义；未来由单独服务生成新
 *   bookId 并记录 originBookId。本阶段不实现副本复制。
 */
interface ShelfTransferService {
    suspend fun moveToWritingShelf(bookId: String): ShelfTransferResult

    suspend fun moveToReadingShelf(bookId: String): ShelfTransferResult
}

sealed interface ShelfTransferResult {
    data class Success(val book: Book) : ShelfTransferResult

    data class Failure(
        val reason: String,
        val cause: Throwable? = null,
    ) : ShelfTransferResult
}
