package com.wenbo.flownote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// v1 仅浅色主题（flomo 同款纸感），不做半吊子暗色。
private val LightColors = lightColorScheme(
    primary = FlomoAccent,
    onPrimary = FlomoSurface,
    background = FlomoBackground,
    onBackground = FlomoTextPrimary,
    surface = FlomoSurface,
    onSurface = FlomoTextPrimary,
    onSurfaceVariant = FlomoTextSecondary,
    outlineVariant = FlomoDivider,
    error = FlomoError,
)

@Composable
fun FlowNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = FlowNoteTypography,
        shapes = FlowNoteShapes,
        content = content,
    )
}
