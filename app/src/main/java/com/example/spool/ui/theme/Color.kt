package com.example.spool.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** The palette in use. Changing it redraws every screen that reads one of the colours below. */
object Palette {
    var current by mutableStateOf(CuePalettes.CUE_DARK)
}

// The names the screens already use. They now follow the chosen style and mode.

/** Text and icons on top of the accent or the gradient. */
val Ink: Color get() = Palette.current.onAccent
val InkRaised: Color get() = Palette.current.surface
val InkHigh: Color get() = Palette.current.surfaceHigh
val Cream: Color get() = Palette.current.onBackground
val CreamDim: Color get() = Palette.current.onSurfaceDim
val Amber: Color get() = Palette.current.warm
val Ember: Color get() = Palette.current.accent
val Lavender: Color get() = Palette.current.cool
val Butter: Color get() = Palette.current.gold
val Hairline: Color get() = Palette.current.outline

val SpoolGradient: Brush get() = Brush.linearGradient(listOf(Amber, Ember, Lavender))