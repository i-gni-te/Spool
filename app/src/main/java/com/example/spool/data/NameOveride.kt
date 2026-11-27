package com.example.spool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The name the user gave a file. Cue shows it whatever the tags inside the file say. */
@Entity(tableName = "name_overrides")
data class NameOverride(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String?
)