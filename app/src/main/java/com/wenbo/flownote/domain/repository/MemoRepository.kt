package com.wenbo.flownote.domain.repository

import com.wenbo.flownote.domain.model.Memo
import com.wenbo.flownote.domain.model.MemoFilter
import kotlinx.coroutines.flow.Flow

interface MemoRepository {

    fun observeMemos(filter: MemoFilter): Flow<List<Memo>>

    /** 全部笔记总数（侧边栏「全部笔记」角标）。 */
    fun observeTotalCount(): Flow<Int>

    fun observeMemo(id: Long): Flow<Memo?>

    suspend fun getMemo(id: Long): Memo?

    /** 关键词搜索正文；空查询返回空列表。 */
    suspend fun search(query: String): List<Memo>

    /**
     * 保存笔记并在同一事务内按正文重建标签关联。
     * 编辑路径（id != null）时，被移除的图片文件会一并清理。
     * @return 笔记 id
     */
    suspend fun saveMemo(id: Long?, content: String, imageNames: List<String>): Long

    /** 删除笔记（级联删标签关联）并删除其图片文件。 */
    suspend fun deleteMemo(id: Long)
}
