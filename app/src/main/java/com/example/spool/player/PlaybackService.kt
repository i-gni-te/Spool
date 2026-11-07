package com.example.spool.player

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
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
import com.example.spool.data.Prefs
import com.example.spool.domain.ArtistParser
import com.example.spool.domain.PlayScope
import com.example.spool.domain.QueueFilter
import com.example.spool.domain.RandomQueue
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.session.DefaultMediaNotificationProvider
import com.example.spool.R

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var queue: RandomQueue? = null
    private var consecutiveErrors = 0

    /** True while the shorts feed drives playback: the current short simply loops. */
    private var feedMode = false

    // Counting plays: an item counts once it has really been listened to
    private var listenedMs = 0L
    private var listenedUri: String? = null
    private var countedUri: String? = null

    private val queueFile by lazy { File(filesDir, "resume_queue.json") }

    private data class StartResult(val count: Int, val startedVideo: Boolean)

    private fun wantsShuffle() = PlaybackSettings.loop.value == LoopMode.SHUFFLE

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
            .setWakeMode(C.WAKE_MODE_LOCAL)    // keep the phone awake while playing
            .build()

        // Pause (instead of advancing) when an item ends, so we decide what comes next
        exoPlayer.pauseAtEndOfMediaItems = true

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) consecutiveErrors = 0
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                        onItemEnded()
                    } else {
                        savePosition() // paused: remember where
                    }
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

        // Our own small icon in the notification
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().also {
                it.setSmallIcon(R.drawable.ic_notification)
            }
        )

        // Bring back what was playing last time, paused
        restoreLast()
        startBackgroundLoops()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    // Swiped away: keep playing if music is playing, otherwise shut down
    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePosition()
        scope.cancel()
        QueueInfo.snapshot.value = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun startBackgroundLoops() {
        // The mode button: switch the running queue between in-order and shuffle
        scope.launch {
            PlaybackSettings.loop.collect { mode ->
                val current = queue ?: return@collect
                current.shuffle = mode == LoopMode.SHUFFLE
                publishQueue()
            }
        }

        // Remember the position every 10 seconds while playing
        scope.launch {
            while (true) {
                delay(10_000)
                if (exoPlayer.isPlaying) savePosition()
            }
        }

        // Count plays
        scope.launch {
            while (true) {
                delay(1_000)
                trackListening()
            }
        }

        // Sleep timer: wait, fade out, pause. A new timer, or turning it off, cancels this.
        scope.launch {
            SleepTimer.state.collectLatest { sleep ->
                if (sleep.endsAtMs <= 0L) return@collectLatest
                while (true) {
                    val remaining = sleep.endsAtMs - SystemClock.elapsedRealtime()
                    if (remaining <= FADE_MS) break
                    delay(minOf(remaining - FADE_MS, 30_000L))
                }
                fadeOutAndPause()
                SleepTimer.clear()
            }
        }
    }

    private suspend fun fadeOutAndPause() {
        val steps = 20
        try {
            for (i in 1..steps) {
                exoPlayer.volume = 1f - i / steps.toFloat()
                delay(FADE_MS / steps)
            }
            exoPlayer.pause()
        } finally {
            exoPlayer.volume = 1f
        }
    }

    private fun trackListening() {
        val uri = exoPlayer.currentMediaItem?.mediaId?.removeSuffix("#next")
        if (uri != listenedUri) {
            listenedUri = uri
            listenedMs = 0L
        }
        if (uri == null || uri == countedUri || !exoPlayer.isPlaying) return
        // Shorts loop forever, so they don't count as plays
        if (exoPlayer.mediaMetadata.extras?.getBoolean(KEY_IS_SHORT, false) == true) return

        listenedMs += 1_000
        val duration = exoPlayer.duration
        val needed = if (duration > 0) minOf(LISTEN_MS, duration / 2) else LISTEN_MS
        if (listenedMs >= needed) {
            countedUri = uri
            scope.launch(Dispatchers.IO) {
                (application as MusicApp).repository.recordPlay(uri)
            }
        }
    }

    /** An item finished on its own: stop for the sleep timer, repeat it, or move on. */
    private fun onItemEnded() {
        countedUri = null
        listenedMs = 0L

        // Sleep timer set to "end of this song": stop here, with the next song ready
        if (SleepTimer.state.value.endOfTrack) {
            SleepTimer.clear()
            cueNext()
            return
        }
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
            LoopMode.ALL, LoopMode.SHUFFLE -> playNext()
        }
    }

    /** Lines up the next song without playing it. */
    private fun cueNext() {
        val item = queue?.next()
        if (item == null) {
            exoPlayer.seekTo(0L)
            return
        }
        load(item)
        exoPlayer.playWhenReady = false
        exoPlayer.prepare()
        publishQueue()
    }

    private fun playNext() {
        queue?.next()?.let(::play)
    }

    private fun playPrevious() {
        val previous = queue?.previous()
        if (previous != null) play(previous) else exoPlayer.seekTo(0L)
    }

    /** Loads an item without starting it. */
    private fun load(item: MediaItemEntity, positionMs: Long = 0L) {
        val current = item.toMediaItem()
        // Second item is a placeholder so ExoPlayer reports "next" as available.
        // It never plays: we pause at the end of the first item and pick the next one ourselves.
        val placeholder = current.buildUpon().setMediaId(item.uri + "#next").build()
        exoPlayer.setMediaItems(listOf(current, placeholder), 0, positionMs)
        attachArtwork(item)
    }

    /** Loads cover art off the main thread, then adds it to the playing item for the notification. */
    private fun attachArtwork(item: MediaItemEntity) {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                Artwork.forItem(applicationContext, item.uri, item.artist, item.title)
            }
            // The song may have changed while the art was loading
            val playing = exoPlayer.currentMediaItem ?: return@launch
            if (playing.mediaId != item.uri) return@launch
            val withArt = playing.buildUpon()
                .setMediaMetadata(
                    playing.mediaMetadata.buildUpon()
                        .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .build()
                )
                .build()
            exoPlayer.replaceMediaItem(exoPlayer.currentMediaItemIndex, withArt)
        }
    }

    private fun play(item: MediaItemEntity) {
        listenedMs = 0L
        countedUri = null
        load(item)
        exoPlayer.prepare()
        exoPlayer.play()
        publishQueue()
    }

    /** Tells the queue screen what changed and saves the queue for next time. */
    private fun publishQueue() {
        val current = queue
        QueueInfo.snapshot.value = current?.snapshot()
        if (current == null) return // shorts feed: keep the last music queue saved

        current.current?.let { Prefs.saveResume(this, it.uri, 0L) }
        val state = current.state()
        scope.launch(Dispatchers.IO) { ResumeStore.writeQueue(queueFile, state) }
    }

    private fun savePosition() {
        val uri = queue?.current?.uri ?: return
        if (exoPlayer.mediaItemCount == 0) return
        Prefs.saveResume(this, uri, exoPlayer.currentPosition)
    }

    /** Loads the last queue and the song that was playing, paused at its last position. */
    private fun restoreLast() {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { ResumeStore.readQueue(queueFile) }
                ?: return@launch
            // The user may already have started something while this was loading
            if (queue != null || exoPlayer.mediaItemCount > 0) return@launch

            val uris = (saved.all + saved.unplayed + saved.history + saved.upNext).distinct()
            val songs = (application as MusicApp).repository.byUris(uris)
            val restored = RandomQueue.restore(saved, songs) ?: return@launch
            val current = restored.current ?: return@launch
            if (queue != null || exoPlayer.mediaItemCount > 0) return@launch

            val resume = Prefs.loadResume(this@PlaybackService)
            val position = if (resume != null && resume.first == current.uri) resume.second else 0L

            restored.shuffle = wantsShuffle() // the button's current setting wins
            queue = restored
            feedMode = false
            load(current, position)
            exoPlayer.playWhenReady = false
            exoPlayer.prepare()
            QueueInfo.snapshot.value = restored.snapshot()
        }
    }

    /** The user picked a song from the queue list: play it now. */
    private fun playFromQueue(uri: String) {
        val current = queue ?: return
        val item = current.find(uri) ?: return
        feedMode = false
        play(current.startWith(item))
    }

    /** "Play next" and "Add to queue". With nothing playing, the song just starts. */
    private suspend fun enqueue(uri: String, atFront: Boolean) {
        if (uri.isEmpty()) return
        val item = (application as MusicApp).repository.byUri(uri) ?: return
        val current = queue
        val musicIsPlaying = current != null && !feedMode &&
                exoPlayer.mediaMetadata.extras?.getBoolean(KEY_IS_VIDEO, false) != true
        if (!musicIsPlaying) {
            startQuery(query = "", startUri = uri, playScope = PlayScope.SONGS, artistKey = null)
            return
        }
        current?.enqueue(item, atFront)
        publishQueue()
        Toast.makeText(
            this,
            if (atFront) "Playing next" else "Added to queue",
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Builds a new queue from a search and starts playing. */
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

        val newQueue = RandomQueue(plan.pool, shuffle = wantsShuffle())
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
                .add(PLAY_QUEUE_ITEM_COMMAND)
                .add(ENQUEUE_COMMAND)
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

                ACTION_PLAY_QUEUE_ITEM -> {
                    playFromQueue(args.getString(KEY_START_URI).orEmpty())
                    future.set(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                ACTION_ENQUEUE -> scope.launch {
                    enqueue(
                        uri = args.getString(KEY_START_URI).orEmpty(),
                        atFront = args.getBoolean(KEY_AT_FRONT, false)
                    )
                    future.set(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return future
        }
    }

    companion object {
        const val ACTION_PLAY_QUERY = "com.example.spool.PLAY_QUERY"
        const val ACTION_PLAY_ITEM = "com.example.spool.PLAY_ITEM"
        const val ACTION_PLAY_QUEUE_ITEM = "com.example.spool.PLAY_QUEUE_ITEM"
        const val ACTION_ENQUEUE = "com.example.spool.ENQUEUE"
        const val KEY_QUERY = "query"
        const val KEY_START_URI = "start_uri"
        const val KEY_SCOPE = "scope"
        const val KEY_ARTIST = "artist_key"
        const val KEY_AT_FRONT = "at_front"
        const val KEY_COUNT = "count"
        const val KEY_STARTED_VIDEO = "started_video"
        const val KEY_IS_VIDEO = "is_video"
        const val KEY_IS_SHORT = "is_short"
        private const val MAX_CONSECUTIVE_ERRORS = 5
        private const val FADE_MS = 10_000L
        private const val LISTEN_MS = 30_000L
        private val PLAY_QUERY_COMMAND = SessionCommand(ACTION_PLAY_QUERY, Bundle.EMPTY)
        private val PLAY_ITEM_COMMAND = SessionCommand(ACTION_PLAY_ITEM, Bundle.EMPTY)
        private val PLAY_QUEUE_ITEM_COMMAND = SessionCommand(ACTION_PLAY_QUEUE_ITEM, Bundle.EMPTY)
        private val ENQUEUE_COMMAND = SessionCommand(ACTION_ENQUEUE, Bundle.EMPTY)
    }
}