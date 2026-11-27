package com.example.spool.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class CuePalettesTest {
    private val all = mapOf(
        "Cue dark" to CuePalettes.CUE_DARK,
        "Cue light" to CuePalettes.CUE_LIGHT,
        "Ocean dark" to CuePalettes.OCEAN_DARK,
        "Ocean light" to CuePalettes.OCEAN_LIGHT,
        "Midnight dark" to CuePalettes.MIDNIGHT_DARK,
        "Midnight light" to CuePalettes.MIDNIGHT_LIGHT
    )

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    private fun check(palette: String, what: String, ratio: Float, minimum: Float) {
        assertTrue("$palette: $what has contrast $ratio, needs $minimum", ratio >= minimum)
    }

    @Test
    fun `main text is easy to read on the background and on cards`() {
        all.forEach { (name, p) ->
            check(name, "text on background", contrast(p.onBackground, p.background), 7f)
            check(name, "text on cards", contrast(p.onBackground, p.surface), 7f)
        }
    }

    @Test
    fun `dimmer text is still readable`() {
        all.forEach { (name, p) ->
            check(name, "dim text on background", contrast(p.onSurfaceDim, p.background), 4.5f)
            check(name, "dim text on cards", contrast(p.onSurfaceDim, p.surface), 4.5f)
        }
    }

    @Test
    fun `the accent colour is readable on the background and on cards`() {
        all.forEach { (name, p) ->
            check(name, "accent on background", contrast(p.accent, p.background), 4.5f)
            check(name, "accent on cards", contrast(p.accent, p.surface), 4.5f)
        }
    }

    @Test
    fun `text on the accent and on the gradient is readable`() {
        all.forEach { (name, p) ->
            check(name, "text on accent", contrast(p.onAccent, p.accent), 4.5f)
            check(name, "text on gradient start", contrast(p.onAccent, p.warm), 3f)
            check(name, "text on gradient end", contrast(p.onAccent, p.cool), 3f)
        }
    }

    @Test
    fun `cover letters are readable on every cover colour`() {
        all.forEach { (name, p) ->
            p.covers.forEachIndexed { i, cover ->
                check(name, "letter on cover $i", contrast(p.onAccent, cover), 3f)
            }
        }
    }
}