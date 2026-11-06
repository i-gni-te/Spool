package com.example.spool.player

import com.example.spool.domain.RandomQueue
import kotlinx.coroutines.flow.MutableStateFlow

/** The service publishes the queue here and the UI reads it. Same process, so a plain object works. */
object QueueInfo {
    val snapshot = MutableStateFlow<RandomQueue.Snapshot?>(null)
}