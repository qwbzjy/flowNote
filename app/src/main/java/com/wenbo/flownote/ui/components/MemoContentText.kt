package com.wenbo.flownote.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.wenbo.flownote.core.tag.tagRanges
import com.wenbo.flownote.ui.theme.FlomoTagGreen

/** 正文渲染：`#标签` 段落绿色高亮（flomo 同款）。 */
@Composable
fun MemoContentText(
    content: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val annotated = remember(content) { buildTaggedString(content) }
    Text(
        text = annotated,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun buildTaggedString(content: String) = buildAnnotatedString {
    append(content)
    for (range in tagRanges(content)) {
        addStyle(
            SpanStyle(color = FlomoTagGreen, fontWeight = FontWeight.Medium),
            start = range.first,
            end = range.last + 1,
        )
    }
}
