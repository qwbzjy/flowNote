package com.wenbo.flownote.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.wenbo.flownote.feature.edit.EditScreen
import com.wenbo.flownote.feature.home.HomeScreen
import com.wenbo.flownote.feature.search.SearchScreen
import com.wenbo.flownote.feature.tags.TagManagerScreen

/** 单 Activity + NavHost。标签筛选不产生路由（Home 的 UI state），避免返回栈被污染。 */
@Composable
fun FlowNoteApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenMemo = { id -> navController.navigate(EditRoute(id)) },
                onOpenSearch = { navController.navigate(SearchRoute) },
                onOpenTagManager = { navController.navigate(TagManagerRoute) },
            )
        }
        composable<EditRoute> { entry ->
            val route = entry.toRoute<EditRoute>()
            EditScreen(
                memoId = route.memoId,
                onClosed = { navController.popBackStack() },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onOpenMemo = { id -> navController.navigate(EditRoute(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<TagManagerRoute> {
            TagManagerScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
