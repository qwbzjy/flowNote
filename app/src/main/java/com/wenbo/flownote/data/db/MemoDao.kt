package com.wenbo.flownote.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wenbo.flownote.data.db.entity.MemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {

    @Transaction
    @Query("SELECT * FROM memos ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoWithTags>>

    @Transaction
    @Query("SELECT * FROM memos WHERE id = :id")
    fun observeById(id: Long): Flow<MemoWithTags?>

    @Transaction
    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getById(id: Long): MemoWithTags?

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getRawById(id: Long): MemoEntity?

    @Query("SELECT * FROM memos ORDER BY createdAt ASC")
    suspend fun getAll(): List<MemoEntity>

    @Query("DELETE FROM memos")
    suspend fun deleteAll()

    @Transaction
    @Query("SELECT * FROM memos WHERE content LIKE '%' || :escaped || '%' ESCAPE '\\' ORDER BY createdAt DESC")
    suspend fun search(escaped: String): List<MemoWithTags>

    @Query("SELECT COUNT(*) FROM memos")
    fun observeTotalCount(): Flow<Int>

    @Insert
    suspend fun insert(memo: MemoEntity): Long

    /** 备份恢复用：显式 id 批量插入，冲突即失败（恢复前表必为空）。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(memos: List<MemoEntity>)

    @Update
    suspend fun update(memo: MemoEntity)

    @Query("UPDATE memos SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, updatedAt: Long)

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT images FROM memos")
    suspend fun getAllImagesJson(): List<String>
}
