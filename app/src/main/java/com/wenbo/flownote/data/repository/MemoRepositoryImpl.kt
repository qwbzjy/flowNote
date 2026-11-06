package com.wenbo.flownote.data.repository

import androidx.room.withTransaction
import com.wenbo.flownote.data.db.FlowNoteDatabase
import com.wenbo.flownote.data.db.MemoWithTags
import com.wenbo.flownote.data.db.entity.MemoEntity
import com.wenbo.flownote.data.files.ImageStore
import com.wenbo.flownote.domain.model.Memo
import com.wenbo.flownote.domain.model.MemoFilter
import com.wenbo.flownote.domain.repository.MemoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MemoRepositoryImpl(
    private val db: FlowNoteDatabase,
    private val imageStore: ImageStore,
) : MemoRepository {

    private val memoDao get() = db.memoDao()
    private val tagDao get() = db.tagDao()
    private val memoTagDao get() = db.memoTagDao()

    override fun observeMemos(filter: MemoFilter): Flow<List<Memo>> =
        when (filter) {
            MemoFilter.All -> memoDao.observeAll()
            is MemoFilter.ByTag -> memoTagDao.observeMemosForTag(filter.tagId)
        }.map { list -> list.map { it.toDomain() } }

    override fun observeMemo(id: Long): Flow<Memo?> =
        memoDao.observeById(id).map { it?.toDomain() }

    override fun observeTotalCount(): Flow<Int> = memoDao.observeTotalCount()

    override suspend fun getMemo(id: Long): Memo? = memoDao.getById(id)?.toDomain()

    override suspend fun search(query: String): List<Memo> {
        val escaped = escapeLike(query.trim())
        if (escaped.isEmpty()) return emptyList()
        return memoDao.search(escaped).map { it.toDomain() }
    }

    override suspend fun saveMemo(id: Long?, content: String, imageNames: List<String>): Long {
        val removedImages = mutableListOf<String>()
        val savedId = withContext(Dispatchers.IO) {
            db.withTransaction {
                val now = System.currentTimeMillis()
                val existing = id?.let { memoDao.getRawById(it) }
                val memoId: Long = if (existing == null) {
                    memoDao.insert(
                        MemoEntity(content = content, images = imageNames, createdAt = now, updatedAt = now)
                    )
                } else {
                    memoDao.update(existing.copy(content = content, images = imageNames, updatedAt = now))
                    existing.id
                }
                existing?.let { old ->
                    removedImages += old.images.filterNot { it in imageNames }
                }

                // 重建该笔记的标签关联（派生索引，正文是唯一事实源）
                rebuildTagLinks(tagDao, memoTagDao, memoId, content, now)
                // 正文变化可能使旧标签失去引用
                tagDao.deleteOrphans()
                memoId
            }
        }
        // 事务提交成功后清理磁盘上被移除的图片
        if (removedImages.isNotEmpty()) imageStore.deleteAll(removedImages)
        return savedId
    }

    override suspend fun deleteMemo(id: Long) {
        val images = withContext(Dispatchers.IO) {
            db.withTransaction {
                val entity = memoDao.getRawById(id) ?: return@withTransaction emptyList<String>()
                memoDao.deleteById(id) // crossref 级联删除
                tagDao.deleteOrphans()
                entity.images
            }
        }
        imageStore.deleteAll(images)
    }

    private fun MemoWithTags.toDomain() = Memo(
        id = memo.id,
        content = memo.content,
        images = memo.images,
        tags = tags.map { it.name },
        createdAt = memo.createdAt,
        updatedAt = memo.updatedAt,
    )

    private fun escapeLike(query: String): String =
        query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
