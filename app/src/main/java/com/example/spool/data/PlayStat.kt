package com.example.spool.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/** How often and when a file was last listened to. Kept apart so rescans never erase it. */
@Entity(tableName = "play_stats")
data class PlayStat(
    @PrimaryKey val uri: String,
    val plays: Int,
    val lastPlayedAt: Long
)

data class PlayedTrack(
    @Embedded val item: MediaItemEntity,
    val plays: Int
)