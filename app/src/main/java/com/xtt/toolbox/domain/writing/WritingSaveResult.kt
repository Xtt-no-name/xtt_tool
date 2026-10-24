package com.xtt.toolbox.domain.writing

/** 带版本语义的保存结果；Conflict 不代表保存成功。 */
sealed interface WritingSaveResult {
    data class Saved(val document: WritingDocument) : WritingSaveResult

    data class Failed(
        val message: String,
        val cause: Throwable? = null,
    ) : WritingSaveResult

    data class Conflict(
        val expectedContentHash: String?,
        val currentContentHash: String?,
        val message: String = "内容已被其他修改更新，请重新加载后再保存",
    ) : WritingSaveResult
}

class WritingDocumentConflictException(
    message: String,
) : IllegalStateException(message)
