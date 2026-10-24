package com.xtt.toolbox.domain.reading

/**
 * 阅读设置的可编辑参数。
 *
 * 当前只在阅读会话的 ViewModel 中保存，不写入数据库或全局设置；关闭应用后回到默认值。
 * 值在写入状态前会经过 [normalized]，避免字号/行距/边距组合导致不可读布局。
 */
data class ReaderSettings(
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    val lineHeightSp: Float = DEFAULT_LINE_HEIGHT_SP,
    val paragraphSpacingDp: Float = DEFAULT_PARAGRAPH_SPACING_DP,
    val horizontalPaddingDp: Float = DEFAULT_HORIZONTAL_PADDING_DP,
    val showCommentBubbles: Boolean = true,
    val showChapterCommentEntry: Boolean = true,
    val enableLongPressMenu: Boolean = true,
) {
    fun normalized(): ReaderSettings {
        val fontSize = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        val lineHeight = lineHeightSp
            .coerceIn(MIN_LINE_HEIGHT_SP, MAX_LINE_HEIGHT_SP)
            .coerceAtLeast(fontSize + MIN_EXTRA_LINE_HEIGHT_SP)
        return copy(
            fontSizeSp = fontSize,
            lineHeightSp = lineHeight,
            paragraphSpacingDp = paragraphSpacingDp.coerceIn(
                MIN_PARAGRAPH_SPACING_DP,
                MAX_PARAGRAPH_SPACING_DP,
            ),
            horizontalPaddingDp = horizontalPaddingDp.coerceIn(
                MIN_HORIZONTAL_PADDING_DP,
                MAX_HORIZONTAL_PADDING_DP,
            ),
        )
    }

    companion object {
        const val DEFAULT_FONT_SIZE_SP = 18f
        const val DEFAULT_LINE_HEIGHT_SP = 30f
        const val DEFAULT_PARAGRAPH_SPACING_DP = 2f
        const val DEFAULT_HORIZONTAL_PADDING_DP = 24f

        const val MIN_FONT_SIZE_SP = 16f
        const val MAX_FONT_SIZE_SP = 24f
        const val MIN_LINE_HEIGHT_SP = 24f
        const val MAX_LINE_HEIGHT_SP = 40f
        const val MIN_EXTRA_LINE_HEIGHT_SP = 4f
        const val MIN_PARAGRAPH_SPACING_DP = 0f
        const val MAX_PARAGRAPH_SPACING_DP = 16f
        const val MIN_HORIZONTAL_PADDING_DP = 12f
        const val MAX_HORIZONTAL_PADDING_DP = 40f

        val Default = ReaderSettings()
    }
}
