package com.xtt.toolbox.domain.importing

import com.xtt.toolbox.data.model.Book

/** 统一导入入口：TXT、Markdown 和 EPUB 共用现有预览/确认 UI 与书架流程。 */
interface BookImportService {
    suspend fun prepare(uri: String, forcedEncoding: String? = null): TxtImportPreview

    /** Markdown 等格式可在用户选择 SAF 资源目录后重新准备预览；其他格式默认忽略。 */
    suspend fun prepareWithResources(
        uri: String,
        forcedEncoding: String?,
        resourceDirectoryUri: String?,
    ): TxtImportPreview = prepare(uri, forcedEncoding)

    suspend fun commit(preview: TxtImportPreview, title: String): Result<Book>
}
