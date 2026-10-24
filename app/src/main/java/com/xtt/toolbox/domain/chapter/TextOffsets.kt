package com.xtt.toolbox.domain.chapter

/**
 * 全项目统一的文本定位约定。
 *
 * 约定：
 * 1. 章节、书签、批注的偏移量全部基于 [normalizeNewlines] 返回的文本。
 * 2. 偏移单位是 Kotlin/JVM String 的 UTF-16 code unit（即 String 的索引），
 *    不是 UTF-8 字节偏移，也不是 Unicode 码点偏移。
 * 3. `startOffset` 包含，`endOffset` 不包含。
 * 4. 如果导入或保存时改变了换行符，必须先规范化，再计算和保存偏移量；
 *    阅读器、编辑器和批注模块必须使用同一份规范化文本。
 *
 * 当前阶段不实现完整解析器，但该约定从模型建立时就固定下来，避免以后混用偏移体系。
 */
object TextOffsets {
    /** 将 CRLF 和单独的 CR 统一为 LF；返回的新字符串才是偏移量的基准。 */
    fun normalizeNewlines(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')

    /** 判断偏移是否落在规范化文本的合法范围内（允许 offset == text.length）。 */
    fun isValidOffset(text: String, offset: Int): Boolean =
        offset in 0..text.length
}
