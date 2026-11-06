package com.wenbo.flownote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wenbo.flownote.di.LocalAppContainer

/**
 * 卡片内图片网格：1 张大图；2–3 张等宽方形；更多时显示前 3 张 + 「+N」。
 * Coil 加载本地文件（文件名 → filesDir/images）。
 */
@Composable
fun MemoImageGrid(images: List<String>, modifier: Modifier = Modifier) {
    if (images.isEmpty()) return
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val shown = images.take(3)
    val overflow = images.size - shown.size

    when (shown.size) {
        1 -> {
            AsyncImage(
                model = container.imageStore.fileFor(shown[0]),
                contentDescription = null,
                modifier = modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        else -> {
            Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEachIndexed { index, name ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        AsyncImage(
                            model = container.imageStore.fileFor(name),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        if (overflow > 0 && index == shown.lastIndex) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.Black.copy(alpha = 0.45f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "+$overflow",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
