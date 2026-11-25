package com.xtt.toolbox.data.repository

import com.xtt.toolbox.data.local.BookDatabaseHelper
import com.xtt.toolbox.data.model.ShelfGroup
import com.xtt.toolbox.data.model.ShelfType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.UUID

class SqliteShelfGroupRepository(
    private val database: BookDatabaseHelper,
    private val books: BookRepository,
) : ShelfGroupRepository {
    private val _groups = MutableStateFlow<List<ShelfGroup>>(emptyList())
    override val groups: StateFlow<List<ShelfGroup>> = _groups.asStateFlow()

    override suspend fun reload() = withContext(Dispatchers.IO) {
        _groups.value = database.readShelfGroups()
    }

    override suspend fun create(
        shelfType: ShelfType,
        name: String,
    ): Result<ShelfGroup> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeName(name)
            require(normalized.isNotEmpty()) { "分组名称不能为空" }
            require(
                _groups.value.none {
                    it.shelfType == shelfType && it.name.equals(normalized, ignoreCase = true)
                },
            ) { "该书架已存在同名分组" }
            val now = System.currentTimeMillis()
            val group = ShelfGroup(
                groupId = UUID.randomUUID().toString(),
                shelfType = shelfType,
                name = normalized,
                isHidden = false,
                createdAt = now,
                updatedAt = now,
            )
            database.insertShelfGroup(group)
            _groups.update { current -> current + group }
            group
        }
    }

    override suspend fun rename(groupId: String, name: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalized = normalizeName(name)
                require(normalized.isNotEmpty()) { "分组名称不能为空" }
                val group = _groups.value.firstOrNull { it.groupId == groupId }
                    ?: error("分组不存在")
                require(
                    _groups.value.none {
                        it.groupId != groupId &&
                            it.shelfType == group.shelfType &&
                            it.name.equals(normalized, ignoreCase = true)
                    },
                ) { "该书架已存在同名分组" }
                val now = System.currentTimeMillis()
                if (!database.renameShelfGroup(groupId, normalized, now)) {
                    error("分组不存在")
                }
                _groups.update { current ->
                    current.map {
                        if (it.groupId == groupId) {
                            it.copy(name = normalized, updatedAt = now)
                        } else {
                            it
                        }
                    }
                }
            }
        }

    override suspend fun delete(groupId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!database.deleteShelfGroupAndUngroupBooks(groupId)) {
                    error("分组不存在")
                }
                _groups.update { current -> current.filterNot { it.groupId == groupId } }
                books.reload()
            }
        }

    override suspend fun setHidden(groupId: String, hidden: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                if (!database.setShelfGroupHidden(groupId, hidden, now)) {
                    error("分组不存在")
                }
                _groups.update { current ->
                    current.map {
                        if (it.groupId == groupId) {
                            it.copy(isHidden = hidden, updatedAt = now)
                        } else {
                            it
                        }
                    }
                }
            }
        }

    private fun normalizeName(name: String): String = name.trim()
}

class InMemoryShelfGroupRepository(
    initialGroups: List<ShelfGroup> = emptyList(),
) : ShelfGroupRepository {
    private val _groups = MutableStateFlow(initialGroups)
    override val groups: StateFlow<List<ShelfGroup>> = _groups.asStateFlow()

    override suspend fun reload() = Unit

    override suspend fun create(
        shelfType: ShelfType,
        name: String,
    ): Result<ShelfGroup> {
        val normalized = name.trim()
        if (normalized.isEmpty()) return Result.failure(IllegalArgumentException("分组名称不能为空"))
        if (_groups.value.any {
                it.shelfType == shelfType && it.name.equals(normalized, ignoreCase = true)
            }
        ) {
            return Result.failure(IllegalStateException("该书架已存在同名分组"))
        }
        val now = System.currentTimeMillis()
        val group = ShelfGroup(
            groupId = UUID.randomUUID().toString(),
            shelfType = shelfType,
            name = normalized,
            createdAt = now,
            updatedAt = now,
        )
        _groups.update { it + group }
        return Result.success(group)
    }

    override suspend fun rename(groupId: String, name: String): Result<Unit> {
        val normalized = name.trim()
        if (normalized.isEmpty()) return Result.failure(IllegalArgumentException("分组名称不能为空"))
        val group = _groups.value.firstOrNull { it.groupId == groupId }
            ?: return Result.failure(NoSuchElementException("分组不存在"))
        if (_groups.value.any {
                it.groupId != groupId &&
                    it.shelfType == group.shelfType &&
                    it.name.equals(normalized, ignoreCase = true)
            }
        ) {
            return Result.failure(IllegalStateException("该书架已存在同名分组"))
        }
        _groups.update { current ->
            current.map {
                if (it.groupId == groupId) it.copy(name = normalized) else it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun delete(groupId: String): Result<Unit> {
        if (_groups.value.none { it.groupId == groupId }) {
            return Result.failure(NoSuchElementException("分组不存在"))
        }
        _groups.update { current -> current.filterNot { it.groupId == groupId } }
        return Result.success(Unit)
    }

    override suspend fun setHidden(groupId: String, hidden: Boolean): Result<Unit> {
        if (_groups.value.none { it.groupId == groupId }) {
            return Result.failure(NoSuchElementException("分组不存在"))
        }
        _groups.update { current ->
            current.map { if (it.groupId == groupId) it.copy(isHidden = hidden) else it }
        }
        return Result.success(Unit)
    }
}
