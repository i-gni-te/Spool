package com.example.spool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The lyrics the user added for a song, stored as text so they never depend on a file. */
@Entity(tableName = "lyrics")
data class LyricsEntry(
    @PrimaryKey val uri: String,
    val text: String,
    val updatedAt: Long
)