package com.xtt.toolbox.domain.content

import com.xtt.toolbox.data.model.ChapterDetectionSource

/** 解析器的章节草稿；正式 bookId/chapterId 在导入提交时分配/确定。 */
data class EpubChapterDraft(
    val title: String,
    val order: Int,
    val startOffset: Int,
    val endOffset: Int,
    val headingOffset: Int,
    val level: Int,
    val detectionSource: ChapterDetectionSource,
    val confidence: Float,
)

/** 图片资源的实际字节只在导入过程中存在，提交后仅保留内部路径和摘要。 */
data class ExtractedResource(
    val resource: BookResource,
    val bytes: ByteArray,
)

data class ParsedEpub(
    val title: String,
    val author: String?,
    val language: String?,
    val text: String,
    val chapters: List<EpubChapterDraft>,
    val blocks: List<BookBlock>,
    val resources: List<ExtractedResource>,
    val warnings: List<String>,
)
