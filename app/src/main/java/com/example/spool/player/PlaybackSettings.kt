package com.example.spool.player

import kotlinx.coroutines.flow.MutableStateFlow

enum class LoopMode {
    OFF, ALL, ONE;

    fun next(): LoopMode = entries[(ordinal + 1) % entries.size]
}

/** Shared by the service and the UI. They run in the same process, so a plain object works. */
object PlaybackSettings {
    val loop = MutableStateFlow(LoopMode.OFF)
}