package com.xtt.toolbox.domain.importing

/**
 * 导入失败时携带的领域异常。
 *
 * [availableEncodings] 仅在编码无法确定/解码失败时提供，用于 UI 让用户选择后重试。
 */
class TxtImportException(
    message: String,
    val availableEncodings: List<String> = emptyList(),
    cause: Throwable? = null,
) : Exception(message, cause)
