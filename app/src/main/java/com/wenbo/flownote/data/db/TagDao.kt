package com.wenbo.flownote.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wenbo.flownote.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/** 标签 + 使用中的笔记数（LEFT JOIN 保证零引用标签也出现在列表里）。 */
data class TagWithCountRow(
    val id: Long,
    val name: String,
    val memoCount: Int,
)

@Dao
interface TagDao {

    @Query(
        """
        SELECT t.id AS id, t.name AS name, COUNT(x.memoId) AS memoCount
        FROM tags t LEFT JOIN memo_tag_cross_ref x ON x.tagId = t.id
        GROUP BY t.id ORDER BY t.name
        """
    )
    fun observeTagsWithCount(): Flow<List<TagWithCountRow>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun findById(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun findByName(name: String): TagEntity?

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 清理不再被任何笔记引用的孤儿标签（登记表跟随正文事实源）。 */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM memo_tag_cross_ref)")
    suspend fun deleteOrphans()
}
