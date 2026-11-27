package com.example.spool.player

import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.spool.MusicApp
import com.example.spool.R
import com.example.spool.data.EqSettings
import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import com.example.spool.data.Prefs
import com.example.spool.data.SubtitleFile
import com.example.spool.domain.ArtistParser
import com.example.spool.domain.FolderIndex
import com.example.spool.domain.PlayScope
import com.example.spool.domain.QueueFilter
import com.example.spool.domain.RandomQueue
import com.example.spool.domain.SeriesParser
import com.example.spool.domain.SongSort
import com.example.spool.widget.CueWidget
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

    // Subtitle files the user picked, keyed by video
    private val subtitleCache = HashMap<String, SubtitleFile>()

    // The equalizer and bass boost attached to the player's sound
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    // A damaged spot in a file: how often we've retried and where it happened
    private var errorUri: String? = null
    private var errorRetries = 0
    private var errorPosition = 0L

    // Gapless playback: the song waiting second in the player's list, ready to start with no pause
    private var preloaded: MediaItemEntity? = null
    private var loadingPreloadUri: String? = null
    private var preloadGeneration = 0
    private val badPreloads = HashSet<String>()

    private val queueFile by lazy { File(filesDir, "resume_queue.json") }

    private data class StartResult(val count: Int, val startedVideo: Boolean)

    private fun wantsShuffle() = PlaybackSettings.loop.value == LoopMode.SHUFFLE

    private fun toast(text: String) =
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    override fun onCreate() {
        super.onCreate()

        // Lets Android try a different decoder when the first one fails on a file
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        exoPlayer = ExoPlayer.Builder(this, renderers)
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

        // Pause (instead of advancing) when an item ends, so we decide what comes next.
        // Gapless playback turns this off while a song is waiting in the second spot.
        exoPlayer.pauseAtEndOfMediaItems = true

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) consecutiveErrors = 0
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                        saveWatchPosition(finished = true)
                        onItemEnded()
                    } else {
                        savePosition() // paused: remember where
                        saveWatchPosition()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Gapless: the song waiting second in line just started on its own
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && preloaded != null) {
                    scope.launch(Dispatchers.Main) { onGaplessAdvance() }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackError(error)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachEffects(audioSessionId)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_MEDIA_METADATA_CHANGED
                    )
                ) {
                    pushWidget()
                }
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

        loadSubtitles()
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
        releaseEffects()
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
        // Fade between songs, if the user chose a fade length
        scope.launch {
            while (true) {
                delay(150)
                applyFade()
            }
        }
        // Equalizer settings and presets chosen on the equalizer screen
        scope.launch { AudioFx.settings.collect { applyFx(it) } }
        scope.launch { AudioFx.presetRequests.collect { applyPreset(it) } }

        // The mode button: switch the running queue between in-order and shuffle
        scope.launch {
            PlaybackSettings.loop.collect { mode ->
                val current = queue ?: return@collect
                current.shuffle = mode == LoopMode.SHUFFLE
                publishQueue()
            }
        }

        // A sleep timer set to "end of this song" must stop at the end, so no song is lined up
        scope.launch { SleepTimer.state.collect { refreshPreload() } }

        // Remember the position every 10 seconds while playing
        scope.launch {
            while (true) {
                delay(10_000)
                if (exoPlayer.isPlaying) {
                    savePosition()
                    saveWatchPosition()
                }
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
        sleepFading = true
        try {
            for (i in 1..steps) {
                exoPlayer.volume = 1f - i / steps.toFloat()
                delay(FADE_MS / steps)
            }
            exoPlayer.pause()
        } finally {
            exoPlayer.volume = 1f
            sleepFading = false
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

    // ----- Errors -----

    /**
     * A mid-file problem (a damaged spot) must not throw the whole movie away. Retry in place,
     * then skip a little further ahead each time. Only give up and move on if the file won't
     * start, or the same stretch keeps failing.
     */
    private fun handlePlaybackError(error: PlaybackException) {
        val uri = exoPlayer.currentMediaItem?.mediaId?.removeSuffix("#next")
        val position = exoPlayer.currentPosition
        Log.e("Spool", "Playback error ${error.errorCodeName} at ${position}ms in $uri", error)

        // A problem with the song waiting second in line must not stop the one that's playing
        val waiting = preloaded
        if (waiting != null) {
            badPreloads += waiting.uri
            dropPreload()
            exoPlayer.prepare()
            exoPlayer.play()
            return
        }

        // A different file, or well past the last problem: start counting again
        if (uri != errorUri || position > errorPosition + 60_000L) {
            errorUri = uri
            errorRetries = 0
        }

        if (uri != null && position > 3_000L && errorRetries < ERROR_JUMPS.size) {
            val jump = ERROR_JUMPS[errorRetries]
            errorRetries++
            errorPosition = position
            exoPlayer.seekTo(position + jump)
            exoPlayer.prepare()
            exoPlayer.play()
            if (jump > 0) toast("Damaged part, skipped ahead ${jump / 1000}s")
            return
        }

        // The file won't play at all, or kept failing at the same spot
        val title = exoPlayer.mediaMetadata.title ?: "this file"
        if (++consecutiveErrors < MAX_CONSECUTIVE_ERRORS) {
            toast("Couldn't play \"$title\", skipping")
            playNext()
        }
    }

    // ----- Moving between items -----

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
            LoopMode.ALL, LoopMode.SHUFFLE -> queue?.next()?.let { play(it, fadeIn = true) }
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
        queue?.next()?.let { play(it) }
    }

    private fun playPrevious() {
        val previous = queue?.previous()
        if (previous != null) play(previous) else exoPlayer.seekTo(0L)
    }

    private fun placeholderFor(base: MediaItem, uri: String): MediaItem =
        base.buildUpon().setMediaId("$uri#next").build()

    /** Loads an item without starting it. */
    private fun load(item: MediaItemEntity, positionMs: Long = 0L) {
        // A fresh list: whatever was lined up second is gone
        preloadGeneration++
        preloaded = null
        loadingPreloadUri = null
        exoPlayer.pauseAtEndOfMediaItems = true

        val current = item.toMediaItem()
        // Second item is a placeholder so ExoPlayer reports "next" as available.
        // It never plays: we pause at the end of the first item and pick the next one ourselves.
        // (Gapless playback swaps it for the real next song when that is possible.)
        exoPlayer.setMediaItems(listOf(current, placeholderFor(current, item.uri)), 0, positionMs)
        attachArtwork(item)
    }

    private fun play(item: MediaItemEntity, fadeIn: Boolean = false) {
        listenedMs = 0L
        countedUri = null
        errorRetries = 0
        errorUri = null
        // After an automatic change the new song starts silent and fades in. Skips start at once.
        fadeInPending = fadeIn && !item.isVideo && fadeMs() > 0L
        if (!sleepFading) exoPlayer.volume = if (fadeInPending) 0f else 1f
        load(item)
        exoPlayer.prepare()
        exoPlayer.play()
        publishQueue()
        maybeResume(item)
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

    // ----- Gapless playback -----

    private fun gaplessWanted(current: MediaItemEntity): Boolean =
        Prefs.settings(this).gapless &&
                !feedMode &&
                !current.isVideo &&
                PlaybackSettings.loop.value != LoopMode.ONE &&
                !SleepTimer.state.value.endOfTrack

    /**
     * Keeps the second spot in the player's list right. For music in a normal queue it holds the
     * song that comes next, ready to play with no pause. Otherwise it holds a placeholder, and
     * the player stops at the end of each item so we can decide what happens.
     */
    private fun refreshPreload() {
        val q = queue
        val current = q?.current
        if (q == null || current == null || exoPlayer.mediaItemCount == 0 ||
            !gaplessWanted(current)
        ) {
            disablePreload()
            return
        }

        val next = q.peekNext()
        if (next == null || next.isVideo || next.uri in badPreloads) {
            disablePreload()
            return
        }
        if (preloaded?.uri == next.uri || loadingPreloadUri == next.uri) return // already on it
        if (!readable(next.uri)) {
            disablePreload()
            return
        }

        // Until the next song is really in place, stop at the end as usual
        exoPlayer.pauseAtEndOfMediaItems = true
        val generation = ++preloadGeneration
        val currentUri = current.uri
        loadingPreloadUri = next.uri

        scope.launch {
            val art = withContext(Dispatchers.IO) {
                Artwork.forItem(applicationContext, next.uri, next.artist, next.title)
            }
            // Only go ahead if nothing changed while the artwork loaded
            if (generation != preloadGeneration) return@launch
            loadingPreloadUri = null
            if (exoPlayer.mediaItemCount < 2) return@launch
            if (exoPlayer.currentMediaItem?.mediaId != currentUri) return@launch
            if (queue?.peekNext()?.uri != next.uri) return@launch

            exoPlayer.replaceMediaItem(1, next.toMediaItem(art))
            preloaded = next
            exoPlayer.pauseAtEndOfMediaItems = false
        }
    }

    /** Back to a placeholder in the second spot, and stop at the end of each item. */
    private fun disablePreload() {
        if (preloaded != null) {
            dropPreload()
        } else {
            preloadGeneration++
            loadingPreloadUri = null
            exoPlayer.pauseAtEndOfMediaItems = true
        }
    }

    private fun dropPreload() {
        val base = exoPlayer.currentMediaItem
        if (base != null && exoPlayer.mediaItemCount >= 2) {
            exoPlayer.replaceMediaItem(1, placeholderFor(base, base.mediaId.removeSuffix("#next")))
        }
        preloaded = null
        loadingPreloadUri = null
        preloadGeneration++
        exoPlayer.pauseAtEndOfMediaItems = true
    }

    /** The song that was waiting second has started by itself: bring the queue in line with it. */
    private fun onGaplessAdvance() {
        val q = queue
        val expected = preloaded
        preloaded = null
        loadingPreloadUri = null
        preloadGeneration++
        if (q == null || expected == null) return

        val committed = q.next()
        fadeInPending = fadeMs() > 0L
        countedUri = null
        listenedMs = 0L
        consecutiveErrors = 0
        val playing = exoPlayer.currentMediaItem

        if (committed == null || committed.uri != expected.uri || playing == null ||
            playing.mediaId != expected.uri
        ) {
            // The queue and the player disagree (rare): reload cleanly
            committed?.let { play(it) }
            return
        }

        // The finished song leaves the front of the list, and a placeholder takes the second spot
        if (exoPlayer.currentMediaItemIndex > 0) exoPlayer.removeMediaItem(0)
        exoPlayer.addMediaItem(placeholderFor(playing, expected.uri))
        exoPlayer.pauseAtEndOfMediaItems = true
        publishQueue() // saves the queue, and lines up the song after this one
    }

    // ----- Queue, saving and restoring -----

    /** Tells the queue screen what changed, saves the queue for next time, and lines up the next song. */
    private fun publishQueue() {
        refreshPreload()

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

    /** Remembers how far into a movie or long video we are, so it can be resumed later. */
    private fun saveWatchPosition(finished: Boolean = false) {
        val item = exoPlayer.currentMediaItem ?: return
        val extras = exoPlayer.mediaMetadata.extras
        if (extras == null ||
            !extras.getBoolean(KEY_IS_VIDEO, false) ||
            extras.getBoolean(KEY_IS_SHORT, false)
        ) return

        val uri = item.mediaId.removeSuffix("#next")
        val position = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        scope.launch(Dispatchers.IO) {
            val repo = (application as MusicApp).repository
            when {
                finished || (duration > 0 && position > duration * 0.95) ->
                    repo.clearWatchPosition(uri)
                position > 5_000 ->
                    repo.saveWatchPosition(uri, position, duration.coerceAtLeast(0L))
            }
        }
    }

    /** Movies and episodes continue where you stopped. Music videos start from the beginning. */
    private fun maybeResume(item: MediaItemEntity) {
        if (!Prefs.settings(this).resumeWhereLeftOff) return
        if (item.kind != MediaClassifier.MOVIE && item.kind != MediaClassifier.SHOW) return
        scope.launch {
            val saved = (application as MusicApp).repository.watchPosition(item.uri)
                ?: return@launch
            val unfinished = saved.durationMs <= 0 || saved.positionMs < saved.durationMs * 0.95
            val stillThisItem = exoPlayer.currentMediaItem?.mediaId == item.uri
            if (saved.positionMs > 5_000 && unfinished && stillThisItem &&
                exoPlayer.currentPosition < 3_000
            ) {
                exoPlayer.seekTo(saved.positionMs)
                toast("Resumed at ${clock(saved.positionMs)}")
            }
        }
    }

    private fun clock(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
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
            val sort = Prefs.songSort(this@PlaybackService)
            val stats = if (sort == SongSort.AZ) emptyMap()
            else (application as MusicApp).repository.statsMap()
            val restored = RandomQueue.restore(saved, songs, order = sort.comparator(stats))
                ?: return@launch
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
            refreshPreload()
        }
    }

    // ----- Commands from the app -----

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
        toast(if (atFront) "Playing next" else "Added to queue")
    }

    /** Builds a new queue from a search and starts playing. */
    private suspend fun startQuery(
        query: String,
        startUri: String?,
        playScope: PlayScope,
        artistKey: String?,
        sort: SongSort = SongSort.AZ,
        folder: String? = null
    ): StartResult {
        feedMode = false
        badPreloads.clear()
        val repo = (application as MusicApp).repository
        var results = repo.searchOnce(query)
        // Artist filters play exactly that artist, not every title containing the name
        if (!artistKey.isNullOrEmpty()) {
            results = results.filter { artistKey in ArtistParser.keys(it.artist) }
        }
        // Folder view: only that folder
        if (!folder.isNullOrEmpty()) {
            results = results.filter { FolderIndex.keyOf(it) == folder }
        }
        // Favorites view: only the hearted items
        if (sort == SongSort.FAVORITES) {
            val favorites = repo.favoriteUris()
            results = results.filter { it.uri in favorites }
        }

        val first = startUri?.let { uri -> results.firstOrNull { it.uri == uri } }
        val plan = QueueFilter.plan(results, playScope, first)
        var pool = plan.pool
        var order: Comparator<MediaItemEntity>? = null

        // An episode queues its whole show, in season and episode order
        if (first != null && first.kind == MediaClassifier.SHOW) {
            val key = SeriesParser.key(first)
            pool = repo.searchOnce("").filter {
                it.kind == MediaClassifier.SHOW && SeriesParser.key(it) == key
            }
            order = SeriesParser.orderFor(pool)
        }
        if (pool.isEmpty()) return StartResult(0, false)

        // In-order play follows the order the user is looking at
        val stats = if (sort == SongSort.AZ || sort == SongSort.FAVORITES) emptyMap()
        else repo.statsMap()
        val newQueue = RandomQueue(
            pool,
            shuffle = wantsShuffle(),
            order = order ?: sort.comparator(stats)
        )
        queue = newQueue
        consecutiveErrors = 0
        val anchor = plan.anchor
        val started = if (anchor != null) newQueue.startWith(anchor) else newQueue.next()
        if (started != null) play(started)
        return StartResult(pool.size, started?.isVideo == true)
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

    // ----- Subtitles -----

    private fun loadSubtitles() {
        scope.launch {
            (application as MusicApp).repository.allSubtitles()
                .forEach { subtitleCache[it.videoUri] = it }
        }
    }

    /** A subtitle was just added for [videoUri]: remember it, and apply it now if that video is playing. */
    private suspend fun refreshSubtitle(videoUri: String) {
        val saved = (application as MusicApp).repository.subtitleFor(videoUri) ?: return
        subtitleCache[videoUri] = saved

        val current = queue?.current ?: return
        val playingUri = exoPlayer.currentMediaItem?.mediaId?.removeSuffix("#next")
        if (current.uri != videoUri || playingUri != videoUri) return

        // Reload this video at the same spot, now with the subtitle file attached
        val wasPlaying = exoPlayer.playWhenReady
        val position = exoPlayer.currentPosition
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        load(current, position)
        exoPlayer.playWhenReady = wasPlaying
        exoPlayer.prepare()
    }

    /** A file that can no longer be opened (deleted, permission lost) must not stop playback. */
    private fun readable(uriString: String): Boolean =
        runCatching {
            contentResolver.openInputStream(Uri.parse(uriString))?.use { true } ?: false
        }.getOrDefault(false)

    // ----- Equalizer and bass boost -----

    private fun attachEffects(sessionId: Int) {
        releaseEffects()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
        AudioFx.unavailable.value = equalizer == null
        publishFxInfo()
        applyFx(AudioFx.settings.value)
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }

    /** Tells the equalizer screen how many bands this phone has, and their range and presets. */
    private fun publishFxInfo() {
        val eq = equalizer
        if (eq == null) {
            AudioFx.info.value = null
            return
        }
        val bands = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        AudioFx.info.value = EqInfo(
            bandHz = List(bands) { eq.getCenterFreq(it.toShort()) / 1000 },
            minLevel = range[0].toInt(),
            maxLevel = range[1].toInt(),
            presets = List(eq.numberOfPresets.toInt()) { eq.getPresetName(it.toShort()) },
            bassSupported = bassBoost?.strengthSupported == true
        )
        // First run: start flat
        val current = AudioFx.settings.value
        if (current.bandLevels.size != bands) {
            AudioFx.settings.value = current.copy(bandLevels = List(bands) { 0 })
        }
    }

    private fun applyFx(s: EqSettings) {
        runCatching {
            equalizer?.let { eq ->
                val info = AudioFx.info.value
                if (info != null && s.bandLevels.size == info.bandHz.size) {
                    s.bandLevels.forEachIndexed { i, level ->
                        eq.setBandLevel(
                            i.toShort(),
                            level.coerceIn(info.minLevel, info.maxLevel).toShort()
                        )
                    }
                }
                eq.setEnabled(s.enabled)
            }
            bassBoost?.let { bb ->
                if (bb.strengthSupported) bb.setStrength(s.bassStrength.coerceIn(0, 1000).toShort())
                bb.setEnabled(s.enabled && s.bassStrength > 0)
            }
        }
    }

    /** A preset from the phone's own list: apply it, then show the band levels it chose. */
    private fun applyPreset(index: Int) {
        val eq = equalizer ?: return
        runCatching {
            eq.usePreset(index.toShort())
            val info = AudioFx.info.value ?: return
            val levels = List(info.bandHz.size) { eq.getBandLevel(it.toShort()).toInt() }
            val updated = AudioFx.settings.value.copy(enabled = true, bandLevels = levels)
            AudioFx.settings.value = updated
            Prefs.saveEqSettings(this, updated)
        }
    }

    // ----- Home-screen widget -----

    private fun pushWidget() {
        val meta = exoPlayer.mediaMetadata
        CueWidget.update(
            context = this,
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            playing = exoPlayer.mediaItemCount > 0 &&
                    exoPlayer.playWhenReady &&
                    exoPlayer.playbackState != Player.STATE_ENDED,
            artwork = meta.artworkData
        )
    }

    // ----- Media items -----

    private fun MediaItemEntity.toMediaItem(artwork: ByteArray? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album.takeIf { it.isNotEmpty() })
            .setExtras(
                Bundle().apply {
                    putBoolean(KEY_IS_VIDEO, isVideo)
                    putBoolean(KEY_IS_SHORT, kind == MediaClassifier.SHORT)
                }
            )
        if (artwork != null) {
            metadata.setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        val builder = MediaItem.Builder()
            .setMediaId(uri)
            .setUri(uri)
            .setMediaMetadata(metadata.build())

        // A subtitle file the user picked for this video
        subtitleCache[uri]?.takeIf { readable(it.subtitleUri) }?.let { sub ->
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.subtitleUri))
                        .setMimeType(sub.mimeType)
                        .setLabel(sub.label)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
        }
        return builder.build()
    }

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
                .add(REFRESH_SUBTITLE_COMMAND)
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
                        artistKey = args.getString(KEY_ARTIST),
                        sort = runCatching {
                            SongSort.valueOf(args.getString(KEY_SORT).orEmpty())
                        }.getOrDefault(SongSort.AZ),
                        folder = args.getString(KEY_FOLDER)
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

                ACTION_REFRESH_SUBTITLE -> scope.launch {
                    refreshSubtitle(args.getString(KEY_START_URI).orEmpty())
                    future.set(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return future
        }
    }
    /** How long the fade at the end and start of songs lasts. 0 means no fade. */
    private fun fadeMs(): Long = Prefs.settings(this).fadeSeconds * 1000L

    /** Fades the end of a song and the start of the next one, when the user chose a fade length. */
    private fun applyFade() {
        if (sleepFading) return // the sleep timer is fading the sound itself

        val configured = fadeMs()
        val current = queue?.current
        val applies = configured > 0L && !feedMode && current != null && !current.isVideo
        if (!applies) {
            fadeInPending = false
            if (exoPlayer.volume != 1f) exoPlayer.volume = 1f
            return
        }
        if (!exoPlayer.isPlaying) return // paused or still loading: leave the volume as it is

        val duration = exoPlayer.duration
        if (duration <= 0L) return
        val position = exoPlayer.currentPosition
        val fade = minOf(configured, duration / 2)

        var factor = 1f
        // End of the song: fade out, when another song follows
        val followed = PlaybackSettings.loop.value != LoopMode.ONE &&
                !SleepTimer.state.value.endOfTrack &&
                queue?.peekNext() != null
        if (followed) {
            factor = minOf(factor, ((duration - position).toFloat() / fade).coerceIn(0f, 1f))
        }
        // Start of the song: fade in, after an automatic change
        if (fadeInPending) {
            if (position >= fade) fadeInPending = false
            else factor = minOf(factor, (position.toFloat() / fade).coerceIn(0f, 1f))
        }
        exoPlayer.volume = factor * factor // squared sounds more even to the ear
    }

    companion object {
        const val ACTION_PLAY_QUERY = "com.example.spool.PLAY_QUERY"
        const val ACTION_PLAY_ITEM = "com.example.spool.PLAY_ITEM"
        const val ACTION_PLAY_QUEUE_ITEM = "com.example.spool.PLAY_QUEUE_ITEM"
        const val ACTION_ENQUEUE = "com.example.spool.ENQUEUE"
        const val ACTION_REFRESH_SUBTITLE = "com.example.spool.REFRESH_SUBTITLE"
        const val KEY_QUERY = "query"
        // Fade between songs
        private var fadeInPending = false
        private var sleepFading = false
        const val KEY_START_URI = "start_uri"
        const val KEY_SCOPE = "scope"
        const val KEY_ARTIST = "artist_key"
        const val KEY_SORT = "sort"
        const val KEY_FOLDER = "folder"
        const val KEY_AT_FRONT = "at_front"
        const val KEY_COUNT = "count"
        const val KEY_STARTED_VIDEO = "started_video"
        const val KEY_IS_VIDEO = "is_video"
        const val KEY_IS_SHORT = "is_short"
        private const val MAX_CONSECUTIVE_ERRORS = 5
        private const val FADE_MS = 10_000L
        private const val LISTEN_MS = 30_000L

        // Where to restart after an error in the middle of a file: same spot, then further ahead
        private val ERROR_JUMPS = longArrayOf(0L, 5_000L, 15_000L, 30_000L)

        private val PLAY_QUERY_COMMAND = SessionCommand(ACTION_PLAY_QUERY, Bundle.EMPTY)
        private val PLAY_ITEM_COMMAND = SessionCommand(ACTION_PLAY_ITEM, Bundle.EMPTY)
        private val PLAY_QUEUE_ITEM_COMMAND = SessionCommand(ACTION_PLAY_QUEUE_ITEM, Bundle.EMPTY)
        private val ENQUEUE_COMMAND = SessionCommand(ACTION_ENQUEUE, Bundle.EMPTY)
        private val REFRESH_SUBTITLE_COMMAND = SessionCommand(ACTION_REFRESH_SUBTITLE, Bundle.EMPTY)
    }
}