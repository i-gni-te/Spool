package com.example.spool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The user's own decision about a video. Kept across rescans. */
@Entity(tableName = "kind_overrides")
data class KindOverride(
    @PrimaryKey val uri: String,
    val kind: Int
)