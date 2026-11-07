package com.example.spool.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.spool.domain.PlayScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(
    val title: String = "",
    val artist: String = "",
    val uri: String = "",
    val isPlaying: Boolean = false,
    val hasVideo: Boolean = false,
    val isShort: Boolean = false
)

class PlayerConnection(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** The connected player, for the video surface and the seek bar. Null until connected. */
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
        val extras = c.mediaMetadata.extras
        _state.value = PlayerState(
            title = c.mediaMetadata.title?.toString().orEmpty(),
            artist = c.mediaMetadata.artist?.toString().orEmpty(),
            uri = c.currentMediaItem?.mediaId.orEmpty().removeSuffix("#next"),
            isPlaying = c.playWhenReady && c.playbackState != Player.STATE_ENDED,
            hasVideo = extras?.getBoolean(PlaybackService.KEY_IS_VIDEO, false) == true,
            isShort = extras?.getBoolean(PlaybackService.KEY_IS_SHORT, false) == true
        )
    }

    /** Callback: items queued (0 = none, -1 = not connected yet) and whether the first is a video. */
    /** Callback: items queued (0 = none, -1 = not connected yet) and whether the first is a video. */
    fun playQuery(
        query: String,
        startUri: String?,
        scope: PlayScope,
        artistKey: String? = null,
        onResult: (count: Int, startedVideo: Boolean) -> Unit
    ) {
        val c = controller
        if (c == null) {
            onResult(-1, false)
            return
        }
        val args = Bundle().apply {
            putString(PlaybackService.KEY_QUERY, query)
            if (startUri != null) putString(PlaybackService.KEY_START_URI, startUri)
            putString(PlaybackService.KEY_SCOPE, scope.name)
            if (artistKey != null) putString(PlaybackService.KEY_ARTIST, artistKey)
        }
        val result = c.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_PLAY_QUERY, Bundle.EMPTY),
            args
        )
        result.addListener({
            val extras = runCatching { result.get().extras }.getOrNull()
            onResult(
                extras?.getInt(PlaybackService.KEY_COUNT, -1) ?: -1,
                extras?.getBoolean(PlaybackService.KEY_STARTED_VIDEO, false) == true
            )
        }, mainExecutor)
    }

    /** Plays a single item by itself (the shorts feed). */
    fun playItem(uri: String) {
        val c = controller ?: return
        val args = Bundle().apply { putString(PlaybackService.KEY_START_URI, uri) }
        c.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_PLAY_ITEM, Bundle.EMPTY),
            args
        )
    }

    fun pause() {
        controller?.pause()
    }
    /** Plays a song picked from the queue list. */
    fun playQueueItem(uri: String) {
        val c = controller ?: return
        val args = Bundle().apply { putString(PlaybackService.KEY_START_URI, uri) }
        c.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_PLAY_QUEUE_ITEM, Bundle.EMPTY),
            args
        )
    }
    /** "Play next" (atFront = true) or "Add to queue" for a song. */
    fun enqueue(uri: String, atFront: Boolean) {
        val c = controller ?: return
        val args = Bundle().apply {
            putString(PlaybackService.KEY_START_URI, uri)
            putBoolean(PlaybackService.KEY_AT_FRONT, atFront)
        }
        c.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_ENQUEUE, Bundle.EMPTY),
            args
        )
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