package com.xtt.toolbox.data.repository

import com.xtt.toolbox.data.model.ShelfGroup
import com.xtt.toolbox.data.model.ShelfType
import kotlinx.coroutines.flow.StateFlow

/**
 * 书架分组仓库。
 *
 * 分组与作品分属两个持久化对象；删除分组只解除作品归属，不删除作品正文。
 */
interface ShelfGroupRepository {
    val groups: StateFlow<List<ShelfGroup>>

    suspend fun reload()

    suspend fun create(shelfType: ShelfType, name: String): Result<ShelfGroup>

    suspend fun rename(groupId: String, name: String): Result<Unit>

    /** 删除分组并把组内作品恢复为未分组；不删除任何作品。 */
    suspend fun delete(groupId: String): Result<Unit>

    suspend fun setHidden(groupId: String, hidden: Boolean): Result<Unit>
}
