package com.wenbo.flownote.feature.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.rememberAsyncImagePainter
import com.wenbo.flownote.core.tag.parseTags
import com.wenbo.flownote.di.LocalAppContainer
import com.wenbo.flownote.speech.SpeechState
import com.wenbo.flownote.ui.components.ConfirmDialog
import com.wenbo.flownote.ui.components.HashtagVisualTransformation
import com.wenbo.flownote.ui.components.MicButton
import com.wenbo.flownote.ui.components.SpeechOverlay
import com.wenbo.flownote.ui.components.TagPill
import com.wenbo.flownote.ui.theme.FlomoAccent
import com.wenbo.flownote.ui.theme.FlomoTextPrimary
import com.wenbo.flownote.ui.theme.FlomoTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    memoId: Long,
    onClosed: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: EditViewModel = viewModel { EditViewModel(container, memoId) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val speechState by vm.speechState.collectAsStateWithLifecycle()
    val speechAvailable by vm.speechAvailable.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9),
    ) { uris -> vm.onImagesPicked(uris) }

    LaunchedEffect(vm) { vm.closed.collect { onClosed() } }
    LaunchedEffect(vm) { vm.events.collect { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(vm) { vm.speechFinal.collect { vm.insertSpeechText(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "新建笔记" else "编辑笔记") },
                navigationIcon = {
                    IconButton(onClick = onClosed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = FlomoTextSecondary)
                        }
                    }
                    TextButton(onClick = vm::save, enabled = !state.isLoading) {
                        Text("保存", color = FlomoAccent)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(16.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    BasicTextField(
                        value = state.content,
                        onValueChange = vm::updateContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = FlomoTextPrimary),
                        visualTransformation = HashtagVisualTransformation,
                        decorationBox = { inner ->
                            Box {
                                if (state.content.text.isEmpty()) {
                                    Text("记录点什么...", color = FlomoTextSecondary, fontSize = 15.sp, lineHeight = 24.sp)
                                }
                                inner()
                            }
                        },
                    )

                    if (speechState != SpeechState.Idle) {
                        Spacer(Modifier.size(6.dp))
                        SpeechOverlay(state = speechState, onStop = vm::stopSpeech)
                    }

                    if (state.images.isNotEmpty() || state.isImportingImage) {
                        Spacer(Modifier.size(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.images.forEach { name ->
                                EditImageThumb(name = name, onRemove = { vm.removeImage(name) })
                            }
                            if (state.isImportingImage) {
                                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.size(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (speechAvailable) {
                            MicButton(isListening = speechState is SpeechState.Listening, onStart = vm::startSpeech, onStop = vm::stopSpeech)
                        }
                        IconButton(onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "添加图片", tint = FlomoTextSecondary)
                        }
                    }
                }
            }

            val tags = remember(state.content.text) { parseTags(state.content.text) }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "标签",
                        color = FlomoTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { TagPill(name = it) }
                    }
                }
            }

            if (!state.isNew && !state.isLoading) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "点击「保存」生效；删除请用右上角删除按钮",
                    color = FlomoTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "删除笔记",
            text = "删除后无法恢复，确定删除这条笔记吗？",
            confirmLabel = "删除",
            onConfirm = vm::delete,
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun EditImageThumb(name: String, onRemove: () -> Unit) {
    val container = LocalAppContainer.current
    Box(modifier = Modifier.size(64.dp)) {
        androidx.compose.foundation.Image(
            painter = rememberAsyncImagePainter(container.imageStore.fileFor(name)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .background(Color.LightGray, RoundedCornerShape(8.dp)),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.TopEnd),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移除图片",
                tint = Color.White,
                modifier = Modifier
                    .size(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
            )
        }
    }
}
