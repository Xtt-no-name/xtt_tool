package com.xtt.toolbox.data.importing

import android.net.Uri
import android.provider.DocumentsContract

/**
 * 根据 SAF tree 与 Markdown 文档的 documentId 推导文档所在目录相对于 tree 根的路径。
 * 仅在两个 URI 属于同一 provider 且 documentId 有 tree 前缀时返回子目录，否则以 tree 根为基准。
 * 该计算只使用 tree 已授予的元数据，不读取 tree 外文件。
 */
object SafMarkdownImageBaseResolver {
    fun baseDirectory(treeUri: String, documentUri: String?): String {
        if (documentUri.isNullOrBlank()) return ""
        return runCatching {
            val tree = Uri.parse(treeUri)
            val document = Uri.parse(documentUri)
            if (tree.authority != document.authority) return ""
            val treeId = DocumentsContract.getTreeDocumentId(tree)
            val documentId = DocumentsContract.getDocumentId(document)
            if (documentId == treeId) return ""
            if (!documentId.startsWith("$treeId/")) return ""
            documentId.removePrefix("$treeId/").substringBeforeLast('/', "")
        }.getOrDefault("")
    }
}
