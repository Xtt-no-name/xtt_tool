package com.xtt.toolbox.data.model

import java.util.UUID

/**
 * 书架归属。
 *
 * 与 [SourceType] 是两个独立概念：一本书可以从阅读书架转入写作书架，
 * 此时 [Book.shelfType] 改变，但 [Book.sourceType] 仍可能是 [SourceType.IMPORTED]。
 */
enum class ShelfType {
    READING,
    WRITING,
}

/**
 * 内容来源。
 *
 * - [IMPORTED]：来自外部文件，原始文件由 [Book.sourceUri] 引用。
 * - [CREATED]：在应用内创建。
 *
 * 来源是历史事实，书架是当前归属；从阅读书架转入写作书架不会把 IMPORTED 改写成 CREATED。
 */
enum class SourceType {
    IMPORTED,
    CREATED,
}

/** 当前已知的正文格式；DRAFT 表示应用内部可编辑文档，不是已生成文件。 */
enum class BookFormat {
    TXT,
    MARKDOWN,
    EPUB,

    /** 写作书架内部草稿；不是可导入文件格式，也不代表已有 EPUB 文件。 */
    DRAFT,
}

/**
 * 阅读位置。
 *
 * [offset] 使用 [com.xtt.toolbox.domain.chapter.TextOffsets] 约定的 UTF-16 code unit 偏移，
 * 基于已规范化换行符的正文计算，而不是 UTF-8 字节或 Unicode 码点。
 * [percent] 为 0f..1f 的阅读进度，仅用于展示。
 */
data class ReadingPosition(
    val chapterId: String? = null,
    val offset: Int = 0,
    val percent: Float? = null,
) {
    init {
        require(offset >= 0) { "阅读位置 offset 不能为负数" }
        require(percent == null || percent in 0f..1f) { "阅读进度必须在 0f..1f 之间" }
    }
}

/**
 * 书籍身份模型。
 *
 * 迁移书架时保留同一个 [bookId]；章节、书签和批注都通过 [bookId] 关联，
 * 不依赖书名或文件路径作为身份。
 *
 * 字段约定：
 * - [sourceUri]：原始外部文件 URI，可为空；阅读书架不得覆盖该文件。
 * - [contentLocation]：应用内部正文副本位置，可为空；导入书籍指向只读副本，写作书籍指向编辑稿。
 * - [originBookId]：独立可编辑副本的来源书籍；普通草稿和原书为空。
 * - [readingPosition]：最近阅读位置；偏移约定见 [ReadingPosition]。
 * - 时间字段均使用 epoch milliseconds。
 */
data class Book(
    val bookId: String,
    val title: String,
    val author: String? = null,
    val language: String? = null,
    val shelfType: ShelfType,
    val sourceType: SourceType,
    val sourceUri: String? = null,
    val contentLocation: String? = null,
    val sourceName: String? = null,
    val contentHash: String? = null,
    val contentSize: Long = 0L,
    val sourceEncoding: String? = null,
    val format: BookFormat = BookFormat.TXT,
    val readingPosition: ReadingPosition? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long? = null,
    /** 独立可编辑副本的来源书籍；普通草稿和原书为 null。 */
    val originBookId: String? = null,
) {
    init {
        require(bookId.isNotBlank()) { "bookId 不能为空" }
        require(title.isNotBlank()) { "书名不能为空" }
        require(createdAt >= 0) { "createdAt 不能为负数" }
        require(updatedAt >= createdAt) { "updatedAt 不能早于 createdAt" }
        require(lastOpenedAt == null || lastOpenedAt >= 0) { "lastOpenedAt 不能为负数" }
        require(contentSize >= 0) { "contentSize 不能为负数" }
    }

    /** 只有写作书架允许编辑正文；阅读书架始终是只读的。 */
    val isEditable: Boolean
        get() = shelfType == ShelfType.WRITING

    companion object {
        /**
         * 创建新书。书架归属与来源分开传入，避免把“转入写作”误写成“来源为创作”。
         */
        fun create(
            title: String,
            author: String? = null,
            language: String? = null,
            shelfType: ShelfType,
            sourceType: SourceType,
            sourceUri: String? = null,
            contentLocation: String? = null,
            sourceName: String? = null,
            contentHash: String? = null,
            contentSize: Long = 0L,
            sourceEncoding: String? = null,
            format: BookFormat = BookFormat.TXT,
            now: Long = System.currentTimeMillis(),
            originBookId: String? = null,
        ): Book {
            val normalizedTitle = title.trim()
            require(normalizedTitle.isNotEmpty()) { "书名不能为空" }
            return Book(
                bookId = UUID.randomUUID().toString(),
                title = normalizedTitle,
                author = author?.trim()?.takeIf { it.isNotEmpty() },
                language = language?.trim()?.takeIf { it.isNotEmpty() },
                shelfType = shelfType,
                sourceType = sourceType,
                sourceUri = sourceUri,
                contentLocation = contentLocation,
                sourceName = sourceName,
                contentHash = contentHash,
                contentSize = contentSize,
                sourceEncoding = sourceEncoding,
                format = format,
                readingPosition = null,
                createdAt = now,
                updatedAt = now,
                lastOpenedAt = null,
                originBookId = originBookId,
            )
        }
    }
}
