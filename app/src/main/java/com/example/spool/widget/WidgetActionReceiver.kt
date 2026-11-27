package com.example.spool.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.spool.data.Prefs
import com.example.spool.domain.PlayScope
import com.example.spool.domain.SongSort
import com.example.spool.player.LoopMode
import com.example.spool.player.PlaybackService
import com.example.spool.player.PlaybackSettings

/** Handles the widget's buttons by talking to the player, which starts if it isn't running. */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext
        val pending = goAsync()

        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            if (controller != null) {
                runCatching {
                    when (action) {
                        WidgetActions.PLAY_PAUSE ->
                            // With nothing loaded there is nothing to resume: shuffle instead
                            if (controller.currentMediaItem == null) shuffleSongs(app, controller)
                            else if (controller.playWhenReady) controller.pause()
                            else controller.play()

                        WidgetActions.NEXT -> controller.seekToNext()
                        WidgetActions.SHUFFLE -> shuffleSongs(app, controller)
                    }
                }
            }
            // Give the command time to reach the player before letting go
            Handler(Looper.getMainLooper()).postDelayed({
                controller?.release()
                pending.finish()
            }, 1500)
        }, ContextCompat.getMainExecutor(app))
    }

    private fun shuffleSongs(context: Context, controller: MediaController) {
        PlaybackSettings.loop.value = LoopMode.SHUFFLE
        Prefs.setLoopMode(context, LoopMode.SHUFFLE)
        val args = Bundle().apply {
            putString(PlaybackService.KEY_QUERY, "")
            putString(PlaybackService.KEY_SCOPE, PlayScope.SONGS.name)
            putString(PlaybackService.KEY_SORT, SongSort.AZ.name)
        }
        controller.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_PLAY_QUERY, Bundle.EMPTY),
            args
        )
    }
}