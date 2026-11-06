package com.wenbo.flownote.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.wenbo.flownote.data.db.entity.MemoEntity
import com.wenbo.flownote.data.db.entity.MemoTagCrossRef
import com.wenbo.flownote.data.db.entity.TagEntity

/** memo + 其标签（经由 crossref 派生索引解析）。 */
data class MemoWithTags(
    @Embedded val memo: MemoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MemoTagCrossRef::class,
            parentColumn = "memoId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)
