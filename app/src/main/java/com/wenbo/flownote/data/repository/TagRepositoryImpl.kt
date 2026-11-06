package com.wenbo.flownote.data.repository

import androidx.room.withTransaction
import com.wenbo.flownote.core.tag.parseTags
import com.wenbo.flownote.core.tag.replaceTagOccurrences
import com.wenbo.flownote.core.tag.stripTagOccurrences
import com.wenbo.flownote.data.db.FlowNoteDatabase
import com.wenbo.flownote.data.db.TagWithCountRow
import com.wenbo.flownote.data.db.entity.MemoTagCrossRef
import com.wenbo.flownote.data.db.entity.TagEntity
import com.wenbo.flownote.domain.model.TagWithCount
import com.wenbo.flownote.domain.repository.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TagRepositoryImpl(
    private val db: FlowNoteDatabase,
) : TagRepository {

    private val tagDao get() = db.tagDao()
    private val memoDao get() = db.memoDao()
    private val memoTagDao get() = db.memoTagDao()

    override fun observeTags(): Flow<List<TagWithCount>> =
        tagDao.observeTagsWithCount().map { rows -> rows.map { it.toDomain() } }

    override suspend fun renameTag(tagId: Long, newName: String) {
        val name = newName.trim().trimStart('#')
        if (name.isEmpty()) return
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val oldTag = tagDao.findById(tagId) ?: return@withTransaction
                if (oldTag.name == name) return@withTransaction

                val target = tagDao.findByName(name)?.takeIf { it.id != tagId }
                val affected = memoTagDao.memosForTag(tagId)

                // 1) 改登记表（无冲突时原行改名；重名则为合并，稍后删源标签）
                if (target == null) {
                    tagDao.update(oldTag.copy(name = name))
                }

                // 2) 边界安全改写受影响笔记正文
                val now = System.currentTimeMillis()
                for (memo in affected) {
                    val newContent = replaceTagOccurrences(memo.content, oldTag.name, name)
                    if (newContent != memo.content) {
                        memoDao.updateContent(memo.id, newContent, now)
                    }
                }

                // 3) 合并场景：删源标签（crossref 随 CASCADE 清掉）
                if (target != null) {
                    tagDao.deleteById(tagId)
                }

                // 4) 重建受影响笔记的关联索引
                for (memo in affected) {
                    reindexMemo(memo.id, now)
                }
            }
        }
    }

    override suspend fun deleteTag(tagId: Long) {
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val tag = tagDao.findById(tagId) ?: return@withTransaction
                val affected = memoTagDao.memosForTag(tagId)

                val now = System.currentTimeMillis()
                for (memo in affected) {
                    val newContent = stripTagOccurrences(memo.content, tag.name)
                    if (newContent != memo.content) {
                        memoDao.updateContent(memo.id, newContent, now)
                    }
                }
                tagDao.deleteById(tagId) // crossref 随 CASCADE 清掉

                for (memo in affected) {
                    reindexMemo(memo.id, now)
                }
            }
        }
    }

    /** 按当前正文重建一条笔记的标签关联（与 MemoRepositoryImpl 的保存路径同一套语义）。 */
    private suspend fun reindexMemo(memoId: Long, now: Long) {
        memoTagDao.clearForMemo(memoId)
        val content = memoDao.getRawById(memoId)?.content ?: return
        val links = parseTags(content).map { name ->
            val tag = tagDao.findByName(name) ?: run {
                val newId = tagDao.insert(TagEntity(name = name, createdAt = now))
                TagEntity(id = newId, name = name, createdAt = now)
            }
            MemoTagCrossRef(memoId = memoId, tagId = tag.id)
        }
        if (links.isNotEmpty()) memoTagDao.insertAll(links)
    }

    private fun TagWithCountRow.toDomain() = TagWithCount(id = id, name = name, memoCount = memoCount)
}
