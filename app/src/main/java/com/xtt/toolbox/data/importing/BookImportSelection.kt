package com.xtt.toolbox.data.importing

/** 统一导入入口根据最终选中数量决定单本预览或批量直导。 */
sealed interface BookImportSelection {
    data object None : BookImportSelection
    data class Single(val uri: String) : BookImportSelection
    data class Batch(val uris: List<String>) : BookImportSelection
}

object BookImportSelectionRouter {
    fun route(uris: List<String>): BookImportSelection = when (uris.size) {
        0 -> BookImportSelection.None
        1 -> BookImportSelection.Single(uris.first())
        else -> BookImportSelection.Batch(uris)
    }
}
