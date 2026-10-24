package com.xtt.toolbox.domain.importing

import com.xtt.toolbox.data.model.Book
import com.xtt.toolbox.data.model.BookFormat
import com.xtt.toolbox.domain.chapter.ChapterCandidate
import com.xtt.toolbox.domain.content.ParsedMarkdown
import com.xtt.toolbox.domain.content.ParsedEpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 原始 TXT 文档读取结果；bytes 只在导入过程内使用，不写入数据库。 */
data class TxtDocument(
    val displayName: String,
    val sizeBytes: Long,
    val bytes: ByteArray,
)

/** 可替换的 TXT 来源读取端口；Android 实现使用 SAF ContentResolver，测试使用内存假实现。 */
interface TxtDocumentSource {
    suspend fun read(uri: String): TxtDocument
}

/** Markdown 图片预览统计；accessible 表示已根据授权目录找到文件，最终格式在提交时校验。 */
data class MarkdownImageStats(
    val total: Int,
    val accessible: Int,
    val missing: Int,
)

/** 导入预览；text 为规范化后的完整正文，确认前不写入应用内部存储。 */
data class TxtImportPreview(
    val sourceUri: String,
    val sourceName: String,
    val defaultTitle: String,
    val originalSizeBytes: Long,
    val encoding: String,
    val encodingConfidence: EncodingConfidence,
    val hadBom: Boolean,
    val text: String,
    val chapters: List<ChapterCandidate>,
    val contentHash: String,
    val contentSizeBytes: Long,
    val duplicateBook: Book?,
    val warnings: List<String>,
    val availableEncodings: List<String>,
    val format: BookFormat = BookFormat.TXT,
    val author: String? = null,
    val parsedEpub: ParsedEpub? = null,
    val parsedMarkdown: ParsedMarkdown? = null,
    val resourceDirectoryUri: String? = null,
    val markdownImageStats: MarkdownImageStats? = null,
) {
    val chapterCount: Int
        get() = chapters.size
}
