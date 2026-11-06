package com.wenbo.flownote.feature.home.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil3.compose.rememberAsyncImagePainter
import com.wenbo.flownote.di.LocalAppContainer
import com.wenbo.flownote.speech.SpeechState
import com.wenbo.flownote.ui.components.HashtagVisualTransformation
import com.wenbo.flownote.ui.components.MicButton
import com.wenbo.flownote.ui.components.SpeechOverlay
import com.wenbo.flownote.ui.theme.FlomoAccent
import com.wenbo.flownote.ui.theme.FlomoDivider
import com.wenbo.flownote.ui.theme.FlomoTextPrimary
import com.wenbo.flownote.ui.theme.FlomoTextSecondary

/**
 * flomo 式快速输入区：无边框输入框 + 图片缩略图行 + [图片][麦克风][发送]。
 */
@Composable
fun QuickComposer(
    draft: TextFieldValue,
    images: List<String>,
    isImporting: Boolean,
    speechState: SpeechState,
    speechAvailable: Boolean,
    onDraftChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onImagesPicked: (List<Uri>) -> Unit,
    onRemoveImage: (String) -> Unit,
    onStartSpeech: () -> Unit,
    onStopSpeech: () -> Unit,
) {
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9),
    ) { uris -> onImagesPicked(uris) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp, max = 132.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = FlomoTextPrimary),
                visualTransformation = HashtagVisualTransformation,
                decorationBox = { inner ->
                    Box {
                        if (draft.text.isEmpty()) {
                            Text(
                                "记录点什么...",
                                color = FlomoTextSecondary,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                            )
                        }
                        inner()
                    }
                },
            )

            if (images.isNotEmpty() || isImporting) {
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    images.forEach { name ->
                        ComposerImageThumb(name = name, onRemove = { onRemoveImage(name) })
                    }
                    if (isImporting) {
                        Box(
                            modifier = Modifier.size(56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }

            if (speechState != SpeechState.Idle) {
                Spacer(Modifier.size(6.dp))
                SpeechOverlay(state = speechState, onStop = onStopSpeech)
            }

            Spacer(Modifier.size(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (speechAvailable) {
                    MicButton(isListening = speechState is SpeechState.Listening, onStart = onStartSpeech, onStop = onStopSpeech)
                }
                IconButton(onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = "添加图片",
                        tint = FlomoTextSecondary,
                    )
                }
                Spacer(Modifier.weight(1f))
                val canSubmit = draft.text.isNotBlank() || images.isNotEmpty()
                IconButton(onClick = onSubmit, enabled = canSubmit) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "保存",
                        tint = if (canSubmit) FlomoAccent else FlomoDivider,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerImageThumb(name: String, onRemove: () -> Unit) {
    val container = LocalAppContainer.current
    Box(modifier = Modifier.size(56.dp)) {
        Image(
            painter = rememberAsyncImagePainter(container.imageStore.fileFor(name)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
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
