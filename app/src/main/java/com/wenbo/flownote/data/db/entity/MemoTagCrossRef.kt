package com.wenbo.flownote.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * memo ↔ tag 关联表：**派生索引**，每次保存笔记时由正文重新解析并整体重建，
 * 只加速查询，永不作为事实源。双向级联删除。
 */
@Entity(
    tableName = "memo_tag_cross_ref",
    primaryKeys = ["memoId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = MemoEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class MemoTagCrossRef(
    val memoId: Long,
    val tagId: Long,
)
