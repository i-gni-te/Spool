package com.example.spool

import android.app.Application
import com.example.spool.data.AppDatabase
import com.example.spool.data.CrashLogger
import com.example.spool.data.MediaFileEditor
import com.example.spool.data.MediaRepository
import com.example.spool.data.MediaScanner
import com.example.spool.data.Prefs
import com.example.spool.player.AudioFx
import com.example.spool.player.PlaybackSettings
import com.example.spool.player.PlayerConnection

class MusicApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { MediaRepository(database.mediaDao(), MediaScanner(this)) }
    val playerConnection by lazy { PlayerConnection(this) }
    val fileEditor by lazy { MediaFileEditor(this) }

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        AudioFx.load(this)
        PlaybackSettings.loop.value = Prefs.loopMode(this)
    }
}