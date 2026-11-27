package com.example.spool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.spool.data.ThemeMode
import com.example.spool.data.ThemeStyle
import com.example.spool.ui.theme.CuePalette
import com.example.spool.ui.theme.CuePalettes
import com.example.spool.ui.theme.Ember

private fun ThemeStyle.label() = when (this) {
    ThemeStyle.CUE -> "Cue"
    ThemeStyle.OCEAN -> "Ocean"
    ThemeStyle.MIDNIGHT -> "Midnight"
    ThemeStyle.DYNAMIC -> "Material You"
}

private fun ThemeStyle.hint() = when (this) {
    ThemeStyle.CUE -> "Sunset coral and violet"
    ThemeStyle.OCEAN -> "Calm, trustworthy blues"
    ThemeStyle.MIDNIGHT -> "True black with soft teal. Easy on OLED screens"
    ThemeStyle.DYNAMIC -> "Colours taken from your wallpaper"
}

/** Colour style cards with live previews, and the dark, light or system mode. */
@Composable
fun ThemePicker(
    style: ThemeStyle,
    mode: ThemeMode,
    onStyle: (ThemeStyle) -> Unit,
    onMode: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val previewDark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Colour style", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ThemeStyle.entries.toList(), key = { it.name }) { option ->
                val palette = remember(option, previewDark) {
                    CuePalettes.forStyle(option, previewDark, context)
                }
                StyleCard(option, palette, selected = option == style) { onStyle(option) }
            }
        }
        Text(
            style.hint(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "Mode",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 6.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                listOf(
                    ThemeMode.SYSTEM to "Follow system",
                    ThemeMode.DARK to "Dark",
                    ThemeMode.LIGHT to "Light"
                ),
                key = { it.first.name }
            ) { (value, label) ->
                FilterChip(
                    selected = mode == value,
                    onClick = { onMode(value) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Ember.copy(alpha = 0.18f),
                        selectedLabelColor = Ember
                    )
                )
            }
        }
    }
}

@Composable
private fun StyleCard(
    style: ThemeStyle,
    palette: CuePalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Ember else palette.outline,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // A card, the three brand colours, and the gradient bar
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surface)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(palette.warm, palette.accent, palette.cool).forEach { color ->
                        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(palette.warm, palette.accent, palette.cool)
                            )
                        )
                )
            }
        }
        Text(
            style.label(),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Ember else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}