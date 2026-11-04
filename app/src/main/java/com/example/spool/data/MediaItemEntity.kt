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
    val searchKey: String
)