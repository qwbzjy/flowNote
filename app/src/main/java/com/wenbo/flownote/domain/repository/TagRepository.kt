package com.wenbo.flownote.domain.repository

import com.wenbo.flownote.domain.model.TagWithCount
import kotlinx.coroutines.flow.Flow

interface TagRepository {

    fun observeTags(): Flow<List<TagWithCount>>

    /**
     * 重命名标签：同一事务内改登记表、并边界安全地改写受影响笔记正文。
     * 若 [newName] 已存在，语义为「合并」——正文与关联并入已存在标签，源标签删除。
     */
    suspend fun renameTag(tagId: Long, newName: String)

    /** 删除标签：删登记 + 删关联，并从受影响笔记正文中剥离 `#name`。 */
    suspend fun deleteTag(tagId: Long)
}
