package com.xtt.toolbox.data.model

/**
 * 章节识别来源。
 *
 * 章节 ID 由章节持久化/匹配流程分配，解析器不直接生成 [Chapter.chapterId]。
 */
enum class ChapterDetectionSource {
    /** 通过标题规则识别。 */
    REGEX,

    /** 用户手动指定。 */
    MANUAL,

    /** 没有识别到标题时，为连续阅读建立的兜底章节。 */
    FALLBACK,

    /** EPUB3 导航或 EPUB2 NCX 目录提供的章节。 */
    EPUB_NAV,

    /** EPUB 没有目录条目时，从 XHTML 标题降级识别的章节。 */
    EPUB_HEADING,

    /** EPUB 单个 spine 文档的兜底章节。 */
    EPUB_FALLBACK,

    /** Markdown 标题提供的章节。 */
    MARKDOWN_HEADING,

    /** Markdown 没有标题时的单章兜底。 */
    MARKDOWN_FALLBACK,
}

/**
 * 已持久化的章节。
 *
 * 文本定位约定（必须与阅读器、编辑器、批注模块一致）：
 * - 所有偏移量都基于 [com.xtt.toolbox.domain.chapter.TextOffsets.normalizeNewlines]
 *   处理后的文本；
 * - 偏移单位是 Kotlin/JVM String 的 UTF-16 code unit，不是 UTF-8 字节或 Unicode 码点；
 * - [startOffset] 包含，[endOffset] 不包含；
 * - [headingOffset] 指向标题行第一个 UTF-16 code unit；
 * - 找不到标题时允许只有一个兜底章节，阅读器仍必须支持连续阅读。
 *
 * [chapterId] 必须稳定；刷新目录时不得通过删除全部章节并重新生成 ID 来丢失书签/批注关联。
 */
data class Chapter(
    val chapterId: String,
    val bookId: String,
    val title: String,
    val order: Int,
    val startOffset: Int,
    val endOffset: Int,
    val headingOffset: Int,
    val detectionSource: ChapterDetectionSource,
    val confidence: Float,
    val level: Int = 0,
    /** 所属分卷；null 表示未分卷。未分卷不创建虚拟分卷记录。 */
    val volumeId: String? = null,
) {
    init {
        require(chapterId.isNotBlank()) { "chapterId 不能为空" }
        require(bookId.isNotBlank()) { "bookId 不能为空" }
        // 未识别到标题时允许空章节名；UI 负责显示临时提示，不把提示写入此字段。
        require(order >= 0) { "章节顺序不能为负数" }
        require(startOffset >= 0) { "startOffset 不能为负数" }
        require(endOffset >= startOffset) { "endOffset 不能早于 startOffset" }
        require(headingOffset in startOffset..endOffset) { "headingOffset 必须位于章节范围内" }
        require(confidence in 0f..1f) { "confidence 必须在 0f..1f 之间" }
        require(level >= 0) { "章节层级不能为负数" }
    }
}
