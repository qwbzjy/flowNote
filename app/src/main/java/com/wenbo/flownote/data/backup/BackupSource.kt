package com.wenbo.flownote.data.backup

import androidx.room.withTransaction
import com.wenbo.flownote.data.db.FlowNoteDatabase
import com.wenbo.flownote.data.db.entity.MemoEntity
import com.wenbo.flownote.data.repository.rebuildTagLinks

/** 备份读写的 DB 缝隙，JVM 测试用 fake 替换。 */
interface BackupSource {
    suspend fun readAll(): List<MemoEntity>

    /** 单事务覆盖恢复：清空 → 保 id 批量插入 → 按正文重建标签索引 → 清孤儿标签。 */
    suspend fun restoreAll(memos: List<MemoEntity>)
}

class RoomBackupSource(private val db: FlowNoteDatabase) : BackupSource {

    override suspend fun readAll(): List<MemoEntity> = db.memoDao().getAll()

    override suspend fun restoreAll(memos: List<MemoEntity>) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            db.memoDao().deleteAll()
            // FK 级联已覆盖 crossref，显式清一次作为保险
            db.memoTagDao().clearAll()
            db.memoDao().insertAll(memos)
            memos.forEach { memo ->
                rebuildTagLinks(db.tagDao(), db.memoTagDao(), memo.id, memo.content, now)
            }
            // 旧库残留的无引用标签清尾（同名旧标签已被 rebuildTagLinks 复用）
            db.tagDao().deleteOrphans()
        }
    }
}
