package com.example.spool.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.spool.data.ThemeMode
import com.example.spool.data.ThemeStyle

private val SpoolShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

private fun CuePalette.toColorScheme(): ColorScheme =
    if (isDark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent,
            secondary = cool, onSecondary = onAccent,
            tertiary = gold, onTertiary = onAccent,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onBackground,
            surfaceVariant = surfaceHigh, onSurfaceVariant = onSurfaceDim,
            outline = outline, outlineVariant = outline,
            error = error, onError = onAccent,
            surfaceContainerLowest = background, surfaceContainerLow = surface,
            surfaceContainer = surface, surfaceContainerHigh = surfaceHigh,
            surfaceContainerHighest = surfaceHigh
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = onAccent,
            secondary = cool, onSecondary = onAccent,
            tertiary = gold, onTertiary = onAccent,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onBackground,
            surfaceVariant = surfaceHigh, onSurfaceVariant = onSurfaceDim,
            outline = outline, outlineVariant = outline,
            error = error, onError = onAccent,
            surfaceContainerLowest = background, surfaceContainerLow = surface,
            surfaceContainer = surface, surfaceContainerHigh = surfaceHigh,
            surfaceContainerHighest = surfaceHigh
        )
    }

@Composable
fun SpoolTheme(
    style: ThemeStyle = ThemeStyle.CUE,
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val palette = remember(style, dark, context) { CuePalettes.forStyle(style, dark, context) }

    // Set before anything inside is drawn, so every colour read in the app sees the same look
    Palette.current = palette

    MaterialTheme(
        colorScheme = palette.toColorScheme(),
        typography = SpoolTypography,
        shapes = SpoolShapes,
        content = content
    )
}

/** App backdrop: the background colour with two soft glows that drift slowly (or stay still). */
@Composable
fun Modifier.spoolBackground(animate: Boolean = true): Modifier {
    val drift: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(12_000, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "drift"
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }

    val palette = Palette.current
    val strength = if (palette.isDark) 1f else 0.55f // softer on light backgrounds
    val glowA = palette.accent.copy(alpha = 0.20f * strength)
    val glowB = palette.cool.copy(alpha = 0.15f * strength)

    return this
        .background(MaterialTheme.colorScheme.background)
        .drawBehind {
            val d = drift.value
            drawRect(
                Brush.radialGradient(
                    listOf(glowA, Color.Transparent),
                    center = Offset(size.width * (0.55f + 0.4f * d), 0f),
                    radius = size.width * 0.95f
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(glowB, Color.Transparent),
                    center = Offset(size.width * (0.40f - 0.3f * d), size.height * 0.40f),
                    radius = size.width * 0.85f
                )
            )
        }
}