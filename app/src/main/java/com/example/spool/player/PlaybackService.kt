package com.example.spool.player

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.spool.MusicApp
import com.example.spool.data.MediaItemEntity
import com.example.spool.domain.RandomQueue
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var queue: RandomQueue? = null
    private var consecutiveErrors = 0

    override fun onCreate() {
        super.onCreate()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones unplug
            .build()

        // Pause (instead of advancing) when a song ends, so we choose the next one
        exoPlayer.pauseAtEndOfMediaItems = true

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) consecutiveErrors = 0
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                ) {
                    playNext()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Skip unplayable files, but give up if some fail in a row
                if (++consecutiveErrors < MAX_CONSECUTIVE_ERRORS) playNext()
            }
        })

        val player = QueuePlayer(exoPlayer, onNext = ::playNext, onPrevious = ::playPrevious)
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    // Swiped away: keep playing if music is playing, otherwise shut down
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun playNext() {
        queue?.next()?.let(::play)
    }

    private fun playPrevious() {
        val previous = queue?.previous()
        if (previous != null) play(previous) else exoPlayer.seekTo(0L)
    }

    private fun play(item: MediaItemEntity) {
        val current = item.toMediaItem()
        // Second item is a placeholder so ExoPlayer reports "next" as available.
        // It never plays: we pause at the end of the first item and pick the next song ourselves.
        val placeholder = current.buildUpon().setMediaId(item.uri + "#next").build()
        exoPlayer.setMediaItems(listOf(current, placeholder))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    /** Builds a new shuffled queue from a search and starts playing. Returns the match count. */
    private suspend fun startQuery(query: String, startUri: String?): Int {
        val repo = (application as MusicApp).repository
        val results = repo.searchOnce(query)
        if (results.isEmpty()) return 0
        val newQueue = RandomQueue(results)
        queue = newQueue
        consecutiveErrors = 0
        val first = startUri?.let { uri -> results.firstOrNull { it.uri == uri } }
        if (first != null) play(newQueue.startWith(first)) else playNext()
        return results.size
    }

    private fun MediaItemEntity.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(uri)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album.takeIf { it.isNotEmpty() })
                    .build()
            )
            .build()

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(PLAY_QUERY_COMMAND)
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != ACTION_PLAY_QUERY) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            val future = SettableFuture.create<SessionResult>()
            scope.launch {
                val count = startQuery(args.getString(KEY_QUERY).orEmpty(), args.getString(KEY_START_URI))
                future.set(
                    SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply { putInt(KEY_COUNT, count) }
                    )
                )
            }
            return future
        }
    }

    companion object {

        const val KEY_START_URI = "start_uri"
        const val ACTION_PLAY_QUERY = "com.example.spool.PLAY_QUERY"
        const val KEY_QUERY = "query"
        const val KEY_COUNT = "count"
        private const val MAX_CONSECUTIVE_ERRORS = 5
        private val PLAY_QUERY_COMMAND = SessionCommand(ACTION_PLAY_QUERY, Bundle.EMPTY)
    }
}