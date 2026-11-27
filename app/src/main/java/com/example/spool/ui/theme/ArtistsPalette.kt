package com.example.spool.ui.theme

import androidx.compose.ui.graphics.Color

/** The same seed (an artist) always gets the same pair of colours, from the current style. */
fun artistPalette(seed: String): Pair<Color, Color> {
    val colors = Palette.current.covers
    val hash = seed.lowercase().hashCode()
    val first = Math.floorMod(hash, colors.size)
    val second = (first + 1 + Math.floorMod(hash / 6, colors.size - 1)) % colors.size
    return colors[first] to colors[second]
}