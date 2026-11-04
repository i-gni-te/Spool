package com.example.spool

import android.app.Application
import com.example.spool.data.AppDatabase
import com.example.spool.data.MediaRepository
import com.example.spool.data.MediaScanner

class MusicApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { MediaRepository(database.mediaDao(), MediaScanner(this)) }
    val playerConnection by lazy { com.example.spool.player.PlayerConnection(this) }
}