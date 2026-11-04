package com.example.spool.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val hasVideo: Boolean = false
)

class PlayerConnection(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** The connected player, for attaching a video view. Null until the service connects. */
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    init {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            controller?.addListener(listener)
            _player.value = controller
            publish()
        }, mainExecutor)
    }

    private fun publish() {
        val c = controller ?: return
        _state.value = PlayerState(
            title = c.mediaMetadata.title?.toString().orEmpty(),
            artist = c.mediaMetadata.artist?.toString().orEmpty(),
            isPlaying = c.playWhenReady && c.playbackState != Player.STATE_ENDED,
            hasVideo = c.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
        )
    }

    /** Result: number of matches, 0 for none, -1 if the player isn't connected yet. */
    fun playQuery(query: String, onResult: (Int) -> Unit) {
        val c = controller
        if (c == null) {
            onResult(-1)
            return
        }
        val args = Bundle().apply { putString(PlaybackService.KEY_QUERY, query) }
        val result = c.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_PLAY_QUERY, Bundle.EMPTY),
            args
        )
        result.addListener({
            val count = runCatching { result.get().extras.getInt(PlaybackService.KEY_COUNT) }
                .getOrDefault(-1)
            onResult(count)
        }, mainExecutor)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.playWhenReady) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }
}