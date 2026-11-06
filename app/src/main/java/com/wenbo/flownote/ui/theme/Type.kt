package com.wenbo.flownote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

val FlowNoteTypography = Typography(
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    // 卡片正文
    bodyMedium = Default.bodyMedium.copy(fontSize = 15.sp, lineHeight = 24.sp, color = FlomoTextPrimary),
    // 时间戳
    labelSmall = Default.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = FlomoTextSecondary),
)
