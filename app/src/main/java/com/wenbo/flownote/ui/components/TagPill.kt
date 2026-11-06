package com.wenbo.flownote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenbo.flownote.ui.theme.FlomoTagBg
import com.wenbo.flownote.ui.theme.FlomoTagGreen

/** 绿底绿字的标签 pill（如「#灵感」）。 */
@Composable
fun TagPill(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "#$name",
        modifier = modifier
            .background(FlomoTagBg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = FlomoTagGreen,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
}
