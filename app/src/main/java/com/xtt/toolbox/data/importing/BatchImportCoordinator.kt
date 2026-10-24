package com.xtt.toolbox.data.importing

import com.xtt.toolbox.domain.importing.TxtImportPreview
import kotlinx.coroutines.CancellationException

/** 批量导入的完成统计；重复文件单独计数，不算成功。 */
data class BatchImportSummary(
    val total: Int,
    val success: Int,
    val failed: Int,
    val skippedDuplicates: Int,
) {
    init {
        require(total >= 0) { "总文件数不能为负数" }
        require(success >= 0 && failed >= 0 && skippedDuplicates >= 0) { "统计不能为负数" }
        require(success + failed + skippedDuplicates == total) { "批量导入统计必须与总数一致" }
    }
}

data class BatchImportProgress(
    val completed: Int,
    val total: Int,
    val currentName: String?,
)

/**
 * 顺序处理一批 URI：单个文件失败不会中断后续文件。
 * [prepare] 负责读取/解析并返回预览；[commit] 负责落盘和数据库提交。
 */
class BatchImportCoordinator(
    private val prepare: suspend (uri: String) -> TxtImportPreview,
    private val commit: suspend (preview: TxtImportPreview, title: String) -> Boolean,
    private val onProgress: (BatchImportProgress) -> Unit = {},
) {
    suspend fun run(uris: List<String>): BatchImportSummary {
        var success = 0
        var failed = 0
        var skipped = 0
        uris.forEachIndexed { index, uri ->
            var currentName: String? = null
            try {
                val preview = prepare(uri)
                currentName = preview.sourceName
                if (preview.duplicateBook != null) {
                    skipped++
                } else if (commit(preview, preview.defaultTitle)) {
                    success++
                } else {
                    failed++
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failed++
            }
            onProgress(
                BatchImportProgress(
                    completed = index + 1,
                    total = uris.size,
                    currentName = currentName,
                ),
            )
        }
        return BatchImportSummary(
            total = uris.size,
            success = success,
            failed = failed,
            skippedDuplicates = skipped,
        )
    }
}
