package com.wenbo.flownote.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "memos", indices = [Index("createdAt")])
data class MemoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    /** 图片以相对文件名存储（UUID），不存绝对路径。 */
    val images: List<String>,
    /** 置顶笔记排在列表最前。 */
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
