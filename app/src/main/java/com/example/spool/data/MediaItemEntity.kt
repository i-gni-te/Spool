package com.example.spool.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "media_items", indices = [Index("searchKey")])
data class MediaItemEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val isVideo: Boolean,
    val searchKey: String,
    val kind: Int = MediaClassifier.SONG,
    /** When the file arrived on the phone, in milliseconds. */
    val addedAt: Long = 0L,
    /** The folder the file sits in, e.g. "Movies/Action/". Used for movies and TV shows. */
    val folder: String = ""
)