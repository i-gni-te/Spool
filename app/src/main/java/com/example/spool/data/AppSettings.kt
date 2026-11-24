package com.example.spool.data

/** Everything the Settings page can change, in one place. */
data class AppSettings(
    val skipSeconds: Int = 10,
    val doubleTapSkip: Boolean = true,
    val keepAudioOnExit: Boolean = false,
    val resumeWhereLeftOff: Boolean = true,
    val showNew: Boolean = true,
    val newDays: Int = 7,
    val animatedBackground: Boolean = true
) {
    companion object {
        val SKIP_CHOICES = listOf(5, 10, 30)
        val NEW_DAY_CHOICES = listOf(3, 7, 14, 30)
    }
}