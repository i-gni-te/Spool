package com.example.spool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.spool.ui.theme.Butter
import com.example.spool.ui.theme.Ember
import com.example.spool.ui.theme.Ink
import com.example.spool.ui.theme.Lavender

private val coverColors = listOf(
    Ember, Lavender, Butter,
    Color(0xFF5CE1C8), Color(0xFFFF6FA8), Color(0xFF6FA8FF)
)

/** Generated cover: the same seed (e.g. an artist) always gets the same gradient. */
@Composable
fun Cover(
    seed: String,
    modifier: Modifier = Modifier,
    isVideo: Boolean = false,
    corner: Dp = 14.dp
) {
    val hash = seed.lowercase().hashCode()
    val first = Math.floorMod(hash, coverColors.size)
    val second = (first + 1 + Math.floorMod(hash / 6, coverColors.size - 1)) % coverColors.size
    val initial = seed.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪"

    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(coverColors[first], coverColors[second]))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initial,
            style = MaterialTheme.typography.titleMedium,
            color = Ink.copy(alpha = 0.6f)
        )
        if (isVideo) {
            Icon(
                Icons.Filled.Videocam,
                contentDescription = "Video",
                tint = Ink.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(12.dp)
            )
        }
    }
}