package com.wenbo.flownote.domain.model

/** 标签及其关联的笔记数。 */
data class TagWithCount(
    val id: Long,
    val name: String,
    val memoCount: Int,
)
