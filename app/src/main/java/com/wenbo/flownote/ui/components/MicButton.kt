package com.wenbo.flownote.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wenbo.flownote.ui.theme.FlomoAccent
import com.wenbo.flownote.ui.theme.FlomoTextSecondary

/**
 * 麦克风按钮：内置 RECORD_AUDIO 权限请求；听写中图标放大呼吸（脉动）。
 * engine 不可用时由调用方隐藏此按钮。
 */
@Composable
fun MicButton(
    isListening: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingStart by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingStart) onStart()
        pendingStart = false
    }

    val pulse = rememberInfiniteTransition(label = "micPulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "micScale",
    )

    IconButton(
        onClick = {
            when {
                isListening -> onStop()
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED -> onStart()

                else -> {
                    pendingStart = true
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = if (isListening) "停止听写" else "语音输入",
            tint = if (isListening) FlomoAccent else FlomoTextSecondary,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = if (isListening) scale else 1f
                    scaleY = if (isListening) scale else 1f
                },
        )
    }
}
