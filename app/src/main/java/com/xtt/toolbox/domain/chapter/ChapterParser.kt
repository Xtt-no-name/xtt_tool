package com.xtt.toolbox.domain.chapter

import com.xtt.toolbox.data.model.ChapterDetectionSource

/**
 * 章节解析器边界。
 *
 * 输入必须是 [TextOffsets.normalizeNewlines] 处理后的文本；解析器不得修改正文，
 * 也不得直接生成 [com.xtt.toolbox.data.model.Chapter.chapterId]。章节 ID 的分配、
 * 旧章节匹配和批注迁移由后续的章节持久化服务负责。
 *
 * 解析结果允许为空：找不到标题不是导入失败，阅读器必须退化为连续阅读。
 *
 * 未来首版应重点识别“独立成行”的标题，降低正文误判：
 * - 第一章 / 第二章 / 第001章 / 第十二回
 * - 序章 / 楔子 / 引子
 * - 第一卷 / 卷一
 * - Chapter 1 / CHAPTER 1
 *
 * 输出偏移量遵循 [TextOffsets]：UTF-16 code unit，基于规范化文本，start 包含、end 不包含。
 */
interface ChapterParser {
    fun parse(text: String): ChapterParseResult
}

/**
 * 解析器识别出的章节候选，尚未分配稳定 chapterId。
 *
 * [startOffset] 从标题行开始，[headingOffset] 指向标题行首字符；
 * [endOffset] 指向下一章标题行首或正文末尾。
 */
data class ChapterCandidate(
    val title: String,
    val order: Int,
    val startOffset: Int,
    val endOffset: Int,
    val headingOffset: Int,
    val detectionSource: ChapterDetectionSource,
    val confidence: Float,
    val level: Int = 0,
    /** 已持久化章节的稳定 ID；TXT 临时解析章节为 null。 */
    val chapterId: String? = null,
) {
    init {
        // 未识别到标题的兜底章节允许空标题，调用方不得用作品名填充。
        require(order >= 0) { "章节顺序不能为负数" }
        require(startOffset >= 0) { "startOffset 不能为负数" }
        require(endOffset >= startOffset) { "endOffset 不能早于 startOffset" }
        require(headingOffset in startOffset..endOffset) { "headingOffset 必须位于章节范围内" }
        require(confidence in 0f..1f) { "confidence 必须在 0f..1f 之间" }
        require(level >= 0) { "章节层级不能为负数" }
    }
}

/**
 * [candidates] 为空表示没有识别到章节，调用方应使用连续阅读，而不是拒绝导入。
 * [warnings] 用于记录可疑标题或解析告警，不影响连续阅读。
 */
data class ChapterParseResult(
    val candidates: List<ChapterCandidate>,
    val warnings: List<String> = emptyList(),
) {
    val hasChapters: Boolean
        get() = candidates.isNotEmpty()
}
