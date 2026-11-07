package com.example.spool.player

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

data class SleepState(
    val endsAtMs: Long = 0L,        // elapsed-realtime time to stop at; 0 = no timer
    val endOfTrack: Boolean = false // stop when the current song finishes
) {
    val active: Boolean get() = endsAtMs > 0L || endOfTrack
}

/** Shared by the UI and the service (same process). */
object SleepTimer {
    val state = MutableStateFlow(SleepState())

    fun startMinutes(minutes: Int) {
        state.value = SleepState(endsAtMs = SystemClock.elapsedRealtime() + minutes * 60_000L)
    }

    fun startEndOfTrack() {
        state.value = SleepState(endOfTrack = true)
    }

    fun clear() {
        state.value = SleepState()
    }
}