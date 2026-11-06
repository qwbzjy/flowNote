package com.wenbo.flownote.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenbo.flownote.di.LocalAppContainer
import com.wenbo.flownote.feature.home.components.MemoCard
import com.wenbo.flownote.ui.components.EmptyState
import com.wenbo.flownote.ui.theme.FlomoAccent
import com.wenbo.flownote.ui.theme.FlomoTextPrimary
import com.wenbo.flownote.ui.theme.FlomoTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenMemo: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BasicTextField(
                            value = state.query,
                            onValueChange = vm::onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = FlomoTextPrimary),
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (state.query.isEmpty()) {
                                        Text("搜索笔记内容", color = FlomoTextSecondary, fontSize = 15.sp)
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            if (state.searched) {
                Text(
                    "共 ${state.results.size} 条结果",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = FlomoTextSecondary,
                    fontSize = 12.sp,
                )
            }
            if (state.searched && state.results.isEmpty()) {
                EmptyState(
                    title = "没有找到相关笔记",
                    subtitle = "换个关键词试试",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.results, key = { it.id }) { memo ->
                        MemoCard(
                            memo = memo,
                            onClick = { onOpenMemo(memo.id) },
                            onLongClick = {},
                            onTogglePin = { vm.togglePin(memo) },
                        )
                    }
                }
            }
        }
    }
}
