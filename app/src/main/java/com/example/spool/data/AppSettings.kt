package com.example.spool.data

/** Where "Report a problem" sends the report. */
enum class ReportVia { GITHUB, SHARE }

/** The look of the app. Used by the theme (coming next); stored here so settings stay in one place. */
enum class ThemeStyle { CUE, OCEAN, MIDNIGHT, DYNAMIC }
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Everything the Settings page can change, in one place. */
data class AppSettings(
    val skipSeconds: Int = 10,
    val doubleTapSkip: Boolean = true,
    val swipeGestures: Boolean = false,
    val keepAudioOnExit: Boolean = false,
    val resumeWhereLeftOff: Boolean = true,
    val gapless: Boolean = true,
    val fadeSeconds: Int = 0,
    val autoHideMini: Boolean = true,
    val showNew: Boolean = true,
    val newDays: Int = 7,
    val animatedBackground: Boolean = true,
    val reportVia: ReportVia = ReportVia.GITHUB,
    val themeStyle: ThemeStyle = ThemeStyle.CUE,
    val themeMode: ThemeMode = ThemeMode.DARK
) {
    companion object {
        val SKIP_CHOICES = listOf(5, 10, 30)
        val NEW_DAY_CHOICES = listOf(3, 7, 14, 30)
        val FADE_CHOICES = listOf(0, 2, 4, 6, 8, 12)
    }
}