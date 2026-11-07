package com.example.spool.ui.theme

import androidx.compose.ui.graphics.Color

private val palette = listOf(
    Ember, Lavender, Butter,
    Color(0xFF5CE1C8), Color(0xFFFF6FA8), Color(0xFF6FA8FF)
)

/** The same seed (an artist) always gets the same pair of colours. */
fun artistPalette(seed: String): Pair<Color, Color> {
    val hash = seed.lowercase().hashCode()
    val first = Math.floorMod(hash, palette.size)
    val second = (first + 1 + Math.floorMod(hash / 6, palette.size - 1)) % palette.size
    return palette[first] to palette[second]
}