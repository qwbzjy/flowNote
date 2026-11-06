package com.wenbo.flownote.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.wenbo.flownote.data.db.entity.MemoEntity
import com.wenbo.flownote.data.db.entity.MemoTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoTagDao {

    @Query("DELETE FROM memo_tag_cross_ref WHERE memoId = :memoId")
    suspend fun clearForMemo(memoId: Long)

    @Query("DELETE FROM memo_tag_cross_ref")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(links: List<MemoTagCrossRef>)

    @Transaction
    @Query(
        """
        SELECT memos.* FROM memos
        JOIN memo_tag_cross_ref ON memos.id = memoId
        WHERE tagId = :tagId
        ORDER BY memos.pinned DESC, memos.createdAt DESC
        """
    )
    fun observeMemosForTag(tagId: Long): Flow<List<MemoWithTags>>

    @Query(
        """
        SELECT memos.* FROM memos
        JOIN memo_tag_cross_ref ON memos.id = memoId
        WHERE tagId = :tagId
        """
    )
    suspend fun memosForTag(tagId: Long): List<MemoEntity>

    @Query("SELECT memoId FROM memo_tag_cross_ref WHERE tagId = :tagId")
    suspend fun memoIdsForTag(tagId: Long): List<Long>
}
