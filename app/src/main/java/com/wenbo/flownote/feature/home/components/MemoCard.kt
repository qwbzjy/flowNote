package com.wenbo.flownote.feature.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenbo.flownote.core.time.TimeFormatter
import com.wenbo.flownote.domain.model.Memo
import com.wenbo.flownote.ui.components.MemoContentText
import com.wenbo.flownote.ui.components.MemoImageGrid
import com.wenbo.flownote.ui.theme.FlomoSurface
import com.wenbo.flownote.ui.theme.FlomoTextSecondary

/** flomo 卡片：正文（#标签 绿色）+ 图片网格 + 时间戳。点击进入编辑，长按删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoCard(
    memo: Memo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = FlomoSurface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(14.dp),
        ) {
            MemoContentText(content = memo.content)
            if (memo.images.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                MemoImageGrid(images = memo.images)
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = TimeFormatter.format(memo.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = FlomoTextSecondary,
            )
        }
    }
}
