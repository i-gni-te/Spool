package com.example.spool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SpoolColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0B1A33),
    secondary = Color(0xFFB4C5E8),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF232733),
    onSurfaceVariant = Color(0xFFA3A9B8),
    outline = Color(0xFF3A4052),
    error = Color(0xFFFF8A80)
)

@Composable
fun SpoolTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SpoolColors, content = content)
}