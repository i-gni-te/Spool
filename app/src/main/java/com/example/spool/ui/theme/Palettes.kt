package com.example.spool.ui.theme

import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.spool.data.ThemeStyle

/** Every colour the app uses, for one look in one mode. */
data class CuePalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val onBackground: Color,
    val onSurfaceDim: Color,
    /** Text, icons, borders and switches. Always readable on the background. */
    val accent: Color,
    /** The two outer colours of the brand gradient. The accent is the middle one. */
    val warm: Color,
    val cool: Color,
    val gold: Color,
    /** Text and icons placed on the accent or on the gradient. */
    val onAccent: Color,
    val error: Color,
    /** The colours generated covers pick from. */
    val covers: List<Color>
)

object CuePalettes {

    // ----- Cue: sunset coral and violet -----

    val CUE_DARK = CuePalette(
        isDark = true,
        background = Color(0xFF0A0910),
        surface = Color(0xFF14121C),
        surfaceHigh = Color(0xFF1F1B2B),
        outline = Color(0xFF332D45),
        onBackground = Color(0xFFF8F2EA),
        onSurfaceDim = Color(0xFF9F98B2),
        accent = Color(0xFFFF5C7A),
        warm = Color(0xFFFF9A3D),
        cool = Color(0xFF9B6BFF),
        gold = Color(0xFFFFC857),
        onAccent = Color(0xFF0A0910),
        error = Color(0xFFFF8A80),
        covers = listOf(
            Color(0xFFFF5C7A), Color(0xFF9B6BFF), Color(0xFFFFC857),
            Color(0xFF5CE1C8), Color(0xFFFF6FA8), Color(0xFF6FA8FF)
        )
    )

    val CUE_LIGHT = CuePalette(
        isDark = false,
        background = Color(0xFFFBF7F2),
        surface = Color(0xFFFFFFFF),
        surfaceHigh = Color(0xFFF2EBE3),
        outline = Color(0xFFE3D9CC),
        onBackground = Color(0xFF1B1822),
        onSurfaceDim = Color(0xFF6B6478),
        accent = Color(0xFFC8294D),
        warm = Color(0xFFE8650F),
        cool = Color(0xFF7C4DDB),
        gold = Color(0xFFB7791F),
        onAccent = Color(0xFFFFFFFF),
        error = Color(0xFFB3261E),
        covers = listOf(
            Color(0xFFC8294D), Color(0xFF7C4DDB), Color(0xFFB7791F),
            Color(0xFF0F8F7A), Color(0xFFC2417F), Color(0xFF2F6FD6)
        )
    )

    // ----- Ocean: calm blues -----

    val OCEAN_DARK = CuePalette(
        isDark = true,
        background = Color(0xFF0B1220),
        surface = Color(0xFF111B2E),
        surfaceHigh = Color(0xFF1A2842),
        outline = Color(0xFF2A3A57),
        onBackground = Color(0xFFE8EEF9),
        onSurfaceDim = Color(0xFF9AA9C4),
        accent = Color(0xFF6EA8FF),
        warm = Color(0xFF7DD3FC),
        cool = Color(0xFFA5B4FC),
        gold = Color(0xFFFDE68A),
        onAccent = Color(0xFF0B1220),
        error = Color(0xFFFF8A80),
        covers = listOf(
            Color(0xFF6EA8FF), Color(0xFF7DD3FC), Color(0xFFA5B4FC),
            Color(0xFF5EEAD4), Color(0xFF93C5FD), Color(0xFFC4B5FD)
        )
    )

    val OCEAN_LIGHT = CuePalette(
        isDark = false,
        background = Color(0xFFF4F7FC),
        surface = Color(0xFFFFFFFF),
        surfaceHigh = Color(0xFFE8EEF8),
        outline = Color(0xFFD5DEEC),
        onBackground = Color(0xFF0F1B2D),
        onSurfaceDim = Color(0xFF55637D),
        accent = Color(0xFF1D5FD6),
        warm = Color(0xFF0284C7),
        cool = Color(0xFF4F46E5),
        gold = Color(0xFF0E7490),
        onAccent = Color(0xFFFFFFFF),
        error = Color(0xFFB3261E),
        covers = listOf(
            Color(0xFF1D5FD6), Color(0xFF0284C7), Color(0xFF4F46E5),
            Color(0xFF0E7490), Color(0xFF3B82F6), Color(0xFF6D28D9)
        )
    )

    // ----- Midnight: true black with muted accents -----

    val MIDNIGHT_DARK = CuePalette(
        isDark = true,
        background = Color(0xFF000000),
        surface = Color(0xFF121212),
        surfaceHigh = Color(0xFF1E1E1E),
        outline = Color(0xFF2E2E2E),
        onBackground = Color(0xFFECECEC),
        onSurfaceDim = Color(0xFF9A9A9A),
        accent = Color(0xFF7FD1C4),
        warm = Color(0xFFA8E0B5),
        cool = Color(0xFFB7A6E8),
        gold = Color(0xFFE8C7A0),
        onAccent = Color(0xFF000000),
        error = Color(0xFFF2B8B5),
        covers = listOf(
            Color(0xFF7FD1C4), Color(0xFFA8E0B5), Color(0xFFB7A6E8),
            Color(0xFFE8C7A0), Color(0xFF9DB7E8), Color(0xFFD6A3BF)
        )
    )

    val MIDNIGHT_LIGHT = CuePalette(
        isDark = false,
        background = Color(0xFFFAFAFA),
        surface = Color(0xFFFFFFFF),
        surfaceHigh = Color(0xFFEFEFEF),
        outline = Color(0xFFDADADA),
        onBackground = Color(0xFF121212),
        onSurfaceDim = Color(0xFF5E5E5E),
        accent = Color(0xFF00796B),
        warm = Color(0xFF2E8B57),
        cool = Color(0xFF5E5BB5),
        gold = Color(0xFF8D6E1F),
        onAccent = Color(0xFFFFFFFF),
        error = Color(0xFFB3261E),
        covers = listOf(
            Color(0xFF00796B), Color(0xFF2E8B57), Color(0xFF5E5BB5),
            Color(0xFF8D6E1F), Color(0xFF3F6FA8), Color(0xFF9C4F7A)
        )
    )

    /** The palette for a style and mode. Material You reads the phone's wallpaper colours. */
    fun forStyle(style: ThemeStyle, dark: Boolean, context: Context): CuePalette =
        when (style) {
            ThemeStyle.CUE -> if (dark) CUE_DARK else CUE_LIGHT
            ThemeStyle.OCEAN -> if (dark) OCEAN_DARK else OCEAN_LIGHT
            ThemeStyle.MIDNIGHT -> if (dark) MIDNIGHT_DARK else MIDNIGHT_LIGHT
            ThemeStyle.DYNAMIC -> dynamic(context, dark)
        }

    private fun dynamic(context: Context, dark: Boolean): CuePalette {
        val s = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return CuePalette(
            isDark = dark,
            background = s.background,
            surface = s.surfaceContainer,
            surfaceHigh = s.surfaceContainerHigh,
            outline = s.outlineVariant,
            onBackground = s.onBackground,
            onSurfaceDim = s.onSurfaceVariant,
            accent = s.primary,
            warm = s.tertiary,
            cool = s.secondary,
            gold = s.tertiary,
            onAccent = s.onPrimary,
            error = s.error,
            covers = listOf(
                s.primary, s.secondary, s.tertiary, s.inversePrimary,
                lerp(s.primary, s.tertiary, 0.5f), lerp(s.secondary, s.primary, 0.5f)
            )
        )
    }
}