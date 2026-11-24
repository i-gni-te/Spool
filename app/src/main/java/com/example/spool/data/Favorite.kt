package com.example.spool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A song or video the user marked with a heart. */
@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val uri: String,
    val addedAt: Long
)