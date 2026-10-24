package com.xtt.toolbox.ui.writing

/**
 * 编辑器回调集合。
 *
 * Compose 编译器不支持在单个 @Composable 上安全生成过多的 lambda 参数；
 * 把回调收拢为普通 data class，避免 WritingEditorScreen 生成损坏的字节码。
 */
data class WritingEditorCallbacks(
    val onTextChange: (String, Int, Int, Boolean) -> Unit,
    val onTitleChange: (String, Int, Int) -> Unit,
    val onBack: () -> Unit,
    val onPreview: () -> Unit,
    val onPickImage: () -> Unit,
    val onRetrySave: () -> Unit,
    val onShowFormatBarChange: (Boolean) -> Unit,
    val onShowQuickBarChange: (Boolean) -> Unit,
    val onAutoPairChange: (Boolean) -> Unit,
    val excludePunctuation: Boolean,
    val onExcludePunctuationChange: (Boolean) -> Unit,
    val onInsertPunctuation: (String) -> Unit,
    val onDeleteImage: (String) -> Unit,
    val onConsumeInsertedImageFocus: () -> Unit,
    val onAddPunctuation: (String) -> Unit,
    val onRemovePunctuation: (Int) -> Unit,
    val onMovePunctuation: (Int, Int) -> Unit,
    val onResetPunctuation: () -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onBold: () -> Unit,
    val onItalic: () -> Unit,
    val onHeading: () -> Unit,
    val onQuote: () -> Unit,
    val onParagraph: () -> Unit,
    val onSmartFormat: () -> Unit,
    val onFindQuery: (String) -> Unit,
    val onReplaceQuery: (String) -> Unit,
    val onFindNext: () -> Unit,
    val onReplaceCurrent: () -> Unit,
    val onReplaceAll: () -> Unit,
    val onClearFind: () -> Unit,
)
