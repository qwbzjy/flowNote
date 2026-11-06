package com.wenbo.flownote.domain.model

/** 领域层笔记模型：tags 为解析出的标签名（不含 #）。 */
data class Memo(
    val id: Long,
    val content: String,
    val images: List<String>,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)
