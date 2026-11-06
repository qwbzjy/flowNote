package com.wenbo.flownote.domain.model

/** 笔记列表筛选：全部，或按标签。标签筛选是 Home 的 UI state，不产生路由。 */
sealed interface MemoFilter {
    data object All : MemoFilter
    data class ByTag(val tagId: Long, val name: String) : MemoFilter
}
