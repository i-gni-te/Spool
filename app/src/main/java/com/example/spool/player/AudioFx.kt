package com.example.spool.player

import android.content.Context
import com.example.spool.data.EqSettings
import com.example.spool.data.Prefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** What this phone's equalizer offers. Filled in by the player once it has started. */
data class EqInfo(
    val bandHz: List<Int>,
    val minLevel: Int,
    val maxLevel: Int,
    val presets: List<String>,
    val bassSupported: Boolean
)

/** Shared by the service (which applies the sound effects) and the equalizer screen. */
object AudioFx {
    val settings = MutableStateFlow(EqSettings())
    val info = MutableStateFlow<EqInfo?>(null)
    val unavailable = MutableStateFlow(false)
    val presetRequests = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    fun load(context: Context) {
        settings.value = Prefs.eqSettings(context)
    }

    fun update(context: Context, change: (EqSettings) -> EqSettings) {
        val updated = change(settings.value)
        settings.value = updated
        Prefs.saveEqSettings(context, updated)
    }
}