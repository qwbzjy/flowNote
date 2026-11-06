package com.wenbo.flownote.feature.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenbo.flownote.di.LocalAppContainer
import com.wenbo.flownote.domain.model.TagWithCount
import com.wenbo.flownote.ui.components.ConfirmDialog
import com.wenbo.flownote.ui.components.EmptyState
import com.wenbo.flownote.ui.theme.FlomoTagGreen
import com.wenbo.flownote.ui.theme.FlomoTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagerScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: TagManagerViewModel = viewModel { TagManagerViewModel(container) }
    val tags by vm.tags.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<TagWithCount?>(null) }
    var deleteTarget by remember { mutableStateOf<TagWithCount?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理标签") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (tags.isEmpty()) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                EmptyState(
                    title = "还没有标签",
                    subtitle = "在笔记正文里输入 #标签 即可创建",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(top = padding.calculateTopPadding())
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(tags, key = { it.id }) { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "#${tag.name}",
                            color = FlomoTagGreen,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${tag.memoCount} 条",
                            color = FlomoTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        IconButton(onClick = { renameTarget = tag }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "重命名", tint = FlomoTextSecondary)
                        }
                        IconButton(onClick = { deleteTarget = tag }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = FlomoTextSecondary)
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { tag ->
        RenameDialog(
            initialName = tag.name,
            onConfirm = { newName ->
                vm.rename(tag, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { tag ->
        ConfirmDialog(
            title = "删除标签",
            text = "将从 ${tag.memoCount} 条笔记的正文中移除 #${tag.name}，笔记本身不受影响。",
            confirmLabel = "删除",
            onConfirm = { vm.delete(tag) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun RenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名标签", style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("新名称（无需输入 #）") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.trim().trimStart('#').isNotEmpty(),
            ) {
                Text("确定", color = FlomoTagGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
