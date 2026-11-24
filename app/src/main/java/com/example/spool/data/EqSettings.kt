package com.example.spool.data

/** The equalizer choices. Levels are in millibels (100 = 1 dB), bass strength is 0 to 1000. */
data class EqSettings(
    val enabled: Boolean = false,
    val bandLevels: List<Int> = emptyList(),
    val bassStrength: Int = 0
)