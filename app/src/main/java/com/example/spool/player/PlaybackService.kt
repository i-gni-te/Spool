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
import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import com.example.spool.domain.PlayScope
import com.example.spool.domain.QueueFilter
import com.example.spool.domain.RandomQueue
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.spool.domain.ArtistParser

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var queue: RandomQueue? = null
    private var consecutiveErrors = 0

    /** True while the shorts feed drives playback: the current short simply loops. */
    private var feedMode = false

    private data class StartResult(val count: Int, val startedVideo: Boolean)

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

        // Pause (instead of advancing) when an item ends, so we decide what comes next
        exoPlayer.pauseAtEndOfMediaItems = true

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) consecutiveErrors = 0
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                ) {
                    onItemEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Skip unplayable files, but give up if several fail in a row
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

    /** An item finished on its own: loop it, move on, or stop at the end of the queue. */
    private fun onItemEnded() {
        if (feedMode) {
            exoPlayer.seekTo(0L)
            exoPlayer.play()
            return
        }
        when (PlaybackSettings.loop.value) {
            LoopMode.ONE -> {
                exoPlayer.seekTo(0L)
                exoPlayer.play()
            }
            LoopMode.ALL -> playNext()
            LoopMode.OFF ->
                if (queue?.hasNextInRound() == true) playNext() else exoPlayer.seekTo(0L)
        }
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
        // It never plays: we pause at the end of the first item and pick the next one ourselves.
        val placeholder = current.buildUpon().setMediaId(item.uri + "#next").build()
        exoPlayer.setMediaItems(listOf(current, placeholder))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    /** Builds a new shuffled queue from a search and starts playing. */
    private suspend fun startQuery(
        query: String,
        startUri: String?,
        playScope: PlayScope,
        artistKey: String?
    ): StartResult {
        feedMode = false
        val repo = (application as MusicApp).repository
        var results = repo.searchOnce(query)
        // Artist tiles play exactly that artist, not every title containing the name
        if (!artistKey.isNullOrEmpty()) {
            results = results.filter { artistKey in ArtistParser.keys(it.artist) }
        }
        val first = startUri?.let { uri -> results.firstOrNull { it.uri == uri } }
        val plan = QueueFilter.plan(results, playScope, first)
        if (plan.pool.isEmpty()) return StartResult(0, false)

        val newQueue = RandomQueue(plan.pool)
        queue = newQueue
        consecutiveErrors = 0
        val anchor = plan.anchor
        val started = if (anchor != null) newQueue.startWith(anchor) else newQueue.next()
        if (started != null) play(started)
        return StartResult(plan.pool.size, started?.isVideo == true)
    }

    /** Plays one item on its own (the shorts feed). It loops until the user swipes on. */
    private suspend fun playItem(uri: String) {
        if (uri.isEmpty()) return
        feedMode = true
        val current = exoPlayer.currentMediaItem?.mediaId?.removeSuffix("#next")
        if (current == uri) {
            exoPlayer.play()
            return
        }
        val item = (application as MusicApp).repository.byUri(uri) ?: return
        queue = null
        consecutiveErrors = 0
        play(item)
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
                    .setExtras(
                        Bundle().apply {
                            putBoolean(KEY_IS_VIDEO, isVideo)
                            putBoolean(KEY_IS_SHORT, kind == MediaClassifier.SHORT)
                        }
                    )
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
                .add(PLAY_ITEM_COMMAND)
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
            val future = SettableFuture.create<SessionResult>()
            when (customCommand.customAction) {
                ACTION_PLAY_QUERY -> scope.launch {
                    val result = startQuery(
                        query = args.getString(KEY_QUERY).orEmpty(),
                        startUri = args.getString(KEY_START_URI),
                        playScope = runCatching {
                            PlayScope.valueOf(args.getString(KEY_SCOPE).orEmpty())
                        }.getOrDefault(PlayScope.AUTO),
                        artistKey = args.getString(KEY_ARTIST)
                    )
                    future.set(
                        SessionResult(
                            SessionResult.RESULT_SUCCESS,
                            Bundle().apply {
                                putInt(KEY_COUNT, result.count)
                                putBoolean(KEY_STARTED_VIDEO, result.startedVideo)
                            }
                        )
                    )
                }

                ACTION_PLAY_ITEM -> scope.launch {
                    playItem(args.getString(KEY_START_URI).orEmpty())
                    future.set(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return future
        }
    }

    companion object {

        const val KEY_ARTIST = "artist_key"
        const val ACTION_PLAY_QUERY = "com.example.spool.PLAY_QUERY"
        const val ACTION_PLAY_ITEM = "com.example.spool.PLAY_ITEM"
        const val KEY_QUERY = "query"
        const val KEY_START_URI = "start_uri"
        const val KEY_SCOPE = "scope"
        const val KEY_COUNT = "count"
        const val KEY_STARTED_VIDEO = "started_video"
        const val KEY_IS_VIDEO = "is_video"
        const val KEY_IS_SHORT = "is_short"
        private const val MAX_CONSECUTIVE_ERRORS = 5
        private val PLAY_QUERY_COMMAND = SessionCommand(ACTION_PLAY_QUERY, Bundle.EMPTY)
        private val PLAY_ITEM_COMMAND = SessionCommand(ACTION_PLAY_ITEM, Bundle.EMPTY)
    }
}