package com.example.spool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** How far into a video the user got, so a movie or episode can be resumed later. */
@Entity(tableName = "watch_positions")
data class WatchPosition(
    @PrimaryKey val uri: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)