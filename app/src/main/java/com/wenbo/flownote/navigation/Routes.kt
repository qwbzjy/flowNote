package com.wenbo.flownote.navigation

import kotlinx.serialization.Serializable

/** Type-safe 导航路由。Edit 的 memoId == -1L 表示新建。 */
@Serializable
data object HomeRoute

@Serializable
data class EditRoute(val memoId: Long = -1L)

@Serializable
data object SearchRoute

@Serializable
data object TagManagerRoute
