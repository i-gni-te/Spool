package com.example.spool.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SpoolColors = darkColorScheme(
    primary = Ember,
    onPrimary = Ink,
    secondary = Lavender,
    onSecondary = Ink,
    tertiary = Butter,
    onTertiary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = InkRaised,
    onSurface = Cream,
    surfaceVariant = InkHigh,
    onSurfaceVariant = CreamDim,
    outline = Hairline,
    error = Color(0xFFFF8A80)
)

private val SpoolShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun SpoolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpoolColors,
        typography = SpoolTypography,
        shapes = SpoolShapes,
        content = content
    )
}

/** App backdrop: deep ink with two slow-drifting colour glows. */
@Composable
fun Modifier.spoolBackground(): Modifier {
    val drift by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(12_000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "drift"
    )
    return this
        .background(Ink)
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Ember.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * (0.55f + 0.4f * drift), 0f),
                    radius = size.width * 0.95f
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Lavender.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width * (0.40f - 0.3f * drift), size.height * 0.40f),
                    radius = size.width * 0.85f
                )
            )
        }
}