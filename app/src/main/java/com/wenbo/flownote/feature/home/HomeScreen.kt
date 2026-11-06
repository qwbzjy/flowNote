package com.wenbo.flownote.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenbo.flownote.di.LocalAppContainer
import com.wenbo.flownote.domain.model.MemoFilter
import com.wenbo.flownote.feature.home.components.MemoCard
import com.wenbo.flownote.feature.home.components.QuickComposer
import com.wenbo.flownote.feature.home.components.TagDrawer
import com.wenbo.flownote.ui.components.ConfirmDialog
import com.wenbo.flownote.ui.components.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenMemo: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTagManager: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val speechState by vm.speechState.collectAsStateWithLifecycle()
    val speechAvailable by vm.speechAvailable.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var pendingImportConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> vm.onExportTarget(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> vm.onImportTarget(uri) }

    LaunchedEffect(vm) {
        vm.speechFinal.collect { vm.insertSpeechText(it) }
    }
    LaunchedEffect(vm) {
        vm.events.collect { snackbarHostState.showSnackbar(it) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            TagDrawer(
                tags = state.tags,
                totalCount = state.totalCount,
                currentFilter = state.filter,
                isExporting = state.isExporting,
                isImporting = state.isImporting,
                onSelectAll = {
                    vm.clearFilter()
                    scope.launch { drawerState.close() }
                },
                onSelectTag = { id, name ->
                    vm.selectTag(id, name)
                    scope.launch { drawerState.close() }
                },
                onManageTags = onOpenTagManager,
                onExportClick = { exportLauncher.launch(suggestedBackupName()) },
                onImportClick = { pendingImportConfirm = true },
            )
        },
    ) {
        Scaffold(
            topBar = {
                HomeTopBar(
                    filter = state.filter,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSearch = onOpenSearch,
                    onClearFilter = vm::clearFilter,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                Modifier
                    .padding(top = padding.calculateTopPadding())
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                    ),
            ) {
                QuickComposer(
                    draft = state.draft,
                    images = state.draftImages,
                    isImporting = state.isImportingImage,
                    speechState = speechState,
                    speechAvailable = speechAvailable,
                    onDraftChange = vm::updateDraft,
                    onSubmit = vm::submitDraft,
                    onImagesPicked = vm::onImagesPicked,
                    onRemoveImage = vm::removeDraftImage,
                    onStartSpeech = vm::startSpeech,
                    onStopSpeech = vm::stopSpeech,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.memos.isEmpty()) {
                    EmptyState(
                        title = if (state.filter is MemoFilter.All) "还没有笔记" else "该标签下还没有笔记",
                        subtitle = "在上方输入框记下第一条灵感吧",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.memos, key = { it.id }) { memo ->
                            MemoCard(
                                memo = memo,
                                onClick = { onOpenMemo(memo.id) },
                                onLongClick = { pendingDeleteId = memo.id },
                                onTogglePin = { vm.togglePin(memo) },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        ConfirmDialog(
            title = "删除笔记",
            text = "删除后无法恢复，确定删除这条笔记吗？",
            confirmLabel = "删除",
            onConfirm = { vm.deleteMemo(id) },
            onDismiss = { pendingDeleteId = null },
        )
    }

    if (pendingImportConfirm) {
        ConfirmDialog(
            title = "导入备份",
            text = "将从备份文件覆盖恢复全部笔记与图片，当前所有数据会先被清空，且无法撤销。确定继续吗？",
            confirmLabel = "选择文件并覆盖",
            onConfirm = {
                importLauncher.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream",
                    ),
                )
            },
            onDismiss = { pendingImportConfirm = false },
        )
    }
}

/** 导出文件建议名：flownote-backup-yyyyMMdd-HHmm.zip */
private fun suggestedBackupName(): String =
    "flownote-backup-" +
        java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) +
        ".zip"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    filter: MemoFilter,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onClearFilter: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = when (val f = filter) {
                    is MemoFilter.ByTag -> "#${f.name}"
                    MemoFilter.All -> "flowNote"
                },
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "打开抽屉")
            }
        },
        actions = {
            if (filter is MemoFilter.ByTag) {
                IconButton(onClick = onClearFilter) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "清除标签筛选",
                    )
                }
            }
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "搜索")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}
