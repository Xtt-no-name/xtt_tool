package com.xtt.toolbox.data.model

/**
 * 书架分组。
 *
 * 分组只属于一个书架；阅读书架和写作书架的分组通过 [shelfType] 隔离。
 * 删除分组不会删除作品，仓库会在同一事务中把组内作品恢复为未分组。
 */
data class ShelfGroup(
    val groupId: String,
    val shelfType: ShelfType,
    val name: String,
    val isHidden: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(groupId.isNotBlank()) { "groupId 不能为空" }
        require(name.isNotBlank()) { "分组名称不能为空" }
        require(createdAt >= 0) { "createdAt 不能为负数" }
        require(updatedAt >= createdAt) { "updatedAt 不能早于 createdAt" }
    }
}
