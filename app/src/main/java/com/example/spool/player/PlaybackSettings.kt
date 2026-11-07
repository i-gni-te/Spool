package com.example.spool.player

import kotlinx.coroutines.flow.MutableStateFlow

/** How the queue moves on: play in order and repeat, repeat one item, or shuffle. */
enum class LoopMode {
    ALL, ONE, SHUFFLE;

    fun next(): LoopMode = entries[(ordinal + 1) % entries.size]
}

/** Shared by the service and the UI. They run in the same process, so a plain object works. */
object PlaybackSettings {
    val loop = MutableStateFlow(LoopMode.ALL)
}