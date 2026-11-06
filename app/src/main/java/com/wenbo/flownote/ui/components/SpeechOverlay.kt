package com.wenbo.flownote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenbo.flownote.speech.SpeechState
import com.wenbo.flownote.ui.theme.FlomoError
import com.wenbo.flownote.ui.theme.FlomoTextSecondary

/**
 * 听写状态条：Listening 时灰字实时预览 + 停止按钮；Error 时红色提示（引擎 2s 后自动回 Idle）。
 */
@Composable
fun SpeechOverlay(state: SpeechState, onStop: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        is SpeechState.Listening -> Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = state.partial.ifEmpty { "正在聆听，请说话…" },
                modifier = Modifier.weight(1f),
                color = FlomoTextSecondary,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                maxLines = 2,
            )
            TextButton(onClick = onStop) {
                Text("停止", color = FlomoTextSecondary, fontSize = 13.sp)
            }
        }

        is SpeechState.Error -> Text(
            text = state.message,
            modifier = modifier.fillMaxWidth(),
            color = FlomoError,
            fontSize = 13.sp,
        )

        SpeechState.Idle -> Unit
    }
}
