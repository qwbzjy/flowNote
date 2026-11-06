package com.wenbo.flownote.ui.components

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.wenbo.flownote.core.tag.tagRanges
import com.wenbo.flownote.ui.theme.FlomoTagGreen

/** 输入框内 `#标签` 实时绿色高亮（不改文本本身，仅视觉变换）。 */
val HashtagVisualTransformation = VisualTransformation { text ->
    val annotated = buildAnnotatedString {
        append(text.text)
        for (range in tagRanges(text.text)) {
            addStyle(
                SpanStyle(color = FlomoTagGreen, fontWeight = FontWeight.Medium),
                start = range.first,
                end = range.last + 1,
            )
        }
    }
    TransformedText(annotated, OffsetMapping.Identity)
}
