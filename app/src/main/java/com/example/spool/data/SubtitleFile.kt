package com.example.spool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A subtitle file the user picked for a video. Remembered so it loads every time. */
@Entity(tableName = "subtitle_files")
data class SubtitleFile(
    @PrimaryKey val videoUri: String,
    val subtitleUri: String,
    val mimeType: String,
    val label: String
)