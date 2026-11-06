package com.wenbo.flownote.data.repository

import com.wenbo.flownote.core.tag.parseTags
import com.wenbo.flownote.data.db.MemoTagDao
import com.wenbo.flownote.data.db.TagDao
import com.wenbo.flownote.data.db.entity.MemoTagCrossRef
import com.wenbo.flownote.data.db.entity.TagEntity

/**
 * 按正文重建一条笔记的标签关联（派生索引）。
 * MemoRepositoryImpl 的保存路径与 BackupSource 的恢复路径共用同一套语义。
 */
internal suspend fun rebuildTagLinks(
    tagDao: TagDao,
    memoTagDao: MemoTagDao,
    memoId: Long,
    content: String,
    now: Long,
) {
    memoTagDao.clearForMemo(memoId)
    val links = parseTags(content).map { name ->
        val tag = tagDao.findByName(name) ?: run {
            val newId = tagDao.insert(TagEntity(name = name, createdAt = now))
            TagEntity(id = newId, name = name, createdAt = now)
        }
        MemoTagCrossRef(memoId = memoId, tagId = tag.id)
    }
    if (links.isNotEmpty()) memoTagDao.insertAll(links)
}
