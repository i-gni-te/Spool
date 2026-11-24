package com.example.spool.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.spool.MusicApp
import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import com.example.spool.data.PlayStat
import com.example.spool.data.Prefs
import com.example.spool.domain.MediaOrder
import com.example.spool.domain.PlayScope
import com.example.spool.domain.RandomQueue
import com.example.spool.domain.SongSort
import com.example.spool.player.LoopMode
import com.example.spool.player.PlaybackSettings
import com.example.spool.player.PlayerState
import com.example.spool.player.QueueInfo
import com.example.spool.player.SleepState
import com.example.spool.player.SleepTimer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.spool.data.ResumeVideo
import com.example.spool.domain.Series
import com.example.spool.domain.SeriesIndex
import android.net.Uri
import android.provider.OpenableColumns
import com.example.spool.data.SubtitleFile
import com.example.spool.domain.SubtitleFormats
import kotlinx.coroutines.withContext
import android.content.Context
import com.example.spool.data.AppSettings
import com.example.spool.data.CrashLogger
import com.example.spool.domain.LrcParser
import com.example.spool.domain.LyricsDoc
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/** A file change that needs the user's permission, shown by the system as a dialog. */
sealed interface FileRequest {
    val item: MediaItemEntity
}

data class TrashRequest(override val item: MediaItemEntity) : FileRequest
data class RenameRequest(override val item: MediaItemEntity, val newBaseName: String) : FileRequest

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val MAX_LYRICS_BYTES = 500_000

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext: Application = app
    private val musicApp = app as MusicApp
    private val repo = musicApp.repository
    private val player = musicApp.playerConnection

    private val _onboardingDone = MutableStateFlow(Prefs.onboardingDone(app))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Everything matching the text, shared by both pages. */
    private val found: StateFlow<List<MediaItemEntity>> =
        _query.flatMapLatest { repo.search(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val stats: StateFlow<Map<String, PlayStat>> =
        repo.playStats
            .map { list -> list.associateBy { it.uri } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _songSort = MutableStateFlow(Prefs.songSort(app))
    val songSort: StateFlow<SongSort> = _songSort.asStateFlow()

    /** The uris the user has marked with a heart. */
    val favorites: StateFlow<Set<String>> = repo.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val songs: StateFlow<List<MediaItemEntity>> =
        combine(found, _songSort, stats, favorites) { list, sort, st, favs ->
            list.filter {
                it.kind == MediaClassifier.SONG && (sort != SongSort.FAVORITES || it.uri in favs)
            }.sortedWith(sort.comparator(st))
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All songs A–Z, ignoring the sort chips. The Folders view groups these. */
    val songsByName: StateFlow<List<MediaItemEntity>> = found
        .map { list ->
            list.filter { it.kind == MediaClassifier.SONG }.sortedWith(MediaOrder.byName)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteVideos: StateFlow<List<MediaItemEntity>> =
        combine(found, favorites) { list, favs ->
            list.filter { it.isVideo && it.uri in favs }.sortedWith(MediaOrder.byName)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val videos: StateFlow<List<MediaItemEntity>> = found
        .map { list ->
            list.filter { it.kind == MediaClassifier.VIDEO }.sortedWith(MediaOrder.byName)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shorts: StateFlow<List<MediaItemEntity>> = found
        .map { list ->
            list.filter { it.kind == MediaClassifier.SHORT }.sortedWith(MediaOrder.byName)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val movies: StateFlow<List<MediaItemEntity>> = found
        .map { list ->
            list.filter { it.kind == MediaClassifier.MOVIE }.sortedWith(MediaOrder.byName)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** TV shows, one entry per show, with episodes in watching order. */
    val series: StateFlow<List<Series>> = found
        .map { list -> SeriesIndex.build(list.filter { it.kind == MediaClassifier.SHOW }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val continueWatching: StateFlow<List<ResumeVideo>> = repo.continueWatching(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    // Files that just arrived on the phone and haven't been played
    private val _newSince = MutableStateFlow(Prefs.newSince(app))

    private val _settings = MutableStateFlow(Prefs.settings(app))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val updated = change(_settings.value)
        _settings.value = updated
        Prefs.saveSettings(appContext, updated)
    }

    private fun fresh(
        list: List<MediaItemEntity>,
        st: Map<String, PlayStat>,
        since: Long,
        s: AppSettings,
        accept: (MediaItemEntity) -> Boolean
    ): List<MediaItemEntity> {
        if (!s.showNew) return emptyList()
        val floor = System.currentTimeMillis() - s.newDays * DAY_MS
        val cutoff = maxOf(since, floor)
        return list
            .filter { accept(it) && it.addedAt > cutoff && (st[it.uri]?.plays ?: 0) == 0 }
            .sortedByDescending { it.addedAt }
            .take(20)
    }

    val newSongs: StateFlow<List<MediaItemEntity>> =
        combine(found, stats, _newSince, _settings) { list, st, since, s ->
            fresh(list, st, since, s) { it.kind == MediaClassifier.SONG }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val newVideos: StateFlow<List<MediaItemEntity>> =
        combine(found, stats, _newSince, _settings) { list, st, since, s ->
            fresh(list, st, since, s) { it.isVideo }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val libraryCount: StateFlow<Int> = repo.count
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _openVideo = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openVideoEvents: SharedFlow<Unit> = _openVideo.asSharedFlow()

    // Shorts feed
    private val _feed = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val feed: StateFlow<List<MediaItemEntity>> = _feed.asStateFlow()

    private val _feedStart = MutableStateFlow("")
    val feedStart: StateFlow<String> = _feedStart.asStateFlow()

    private val _openShorts = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openShortsEvents: SharedFlow<Unit> = _openShorts.asSharedFlow()

    private val _shortsFill = MutableStateFlow(Prefs.shortsFill(app))
    val shortsFill: StateFlow<Boolean> = _shortsFill.asStateFlow()

    // File changes (delete, rename) that need a system permission dialog
    private val _fileRequests = MutableSharedFlow<FileRequest>(extraBufferCapacity = 1)
    val fileRequests: SharedFlow<FileRequest> = _fileRequests.asSharedFlow()

    val playerState: StateFlow<PlayerState> = player.state
    val videoPlayer: StateFlow<Player?> = player.player
    val loop: StateFlow<LoopMode> = PlaybackSettings.loop.asStateFlow()
    val queue: StateFlow<RandomQueue.Snapshot?> = QueueInfo.snapshot.asStateFlow()
    val sleep: StateFlow<SleepState> = SleepTimer.state.asStateFlow()
    /** The lyrics of whatever is playing, read and ready to show. */
    val currentLyrics: StateFlow<LyricsDoc?> = playerState
        .map { it.uri }
        .distinctUntilChanged()
        .flatMapLatest { uri -> if (uri.isEmpty()) flowOf(null) else repo.lyricsFor(uri) }
        .map { entry -> entry?.let { LrcParser.parse(it.text) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // New downloads show up while the app is open
    private var rescanJob: Job? = null
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!hasMediaAccess()) return
            rescanJob?.cancel()
            rescanJob = viewModelScope.launch {
                delay(1_500)
                rescan()
            }
        }
    }

    init {
        val resolver = appContext.contentResolver
        resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
    }

    override fun onCleared() {
        appContext.contentResolver.unregisterContentObserver(mediaObserver)
        super.onCleared()
    }

    private fun hasMediaAccess(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    fun finishOnboarding() {
        Prefs.setOnboardingDone(appContext)
        _onboardingDone.value = true
    }

    fun onQueryChange(text: String) {
        _query.value = text
        _message.value = null
    }

    fun setSongSort(sort: SongSort) {
        _songSort.value = sort
        Prefs.setSongSort(appContext, sort)
    }

    /** Clears the "New" sections. Files added after this moment show up as new again. */
    fun dismissNew() {
        val now = System.currentTimeMillis()
        _newSince.value = now
        Prefs.setNewSince(appContext, now)
    }

    fun rescan() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            try {
                repo.rescan()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("Spool", "Scan failed", e)
                _message.value = "Scan failed. Try reopening the app."
            } finally {
                _scanning.value = false
            }
        }
    }

    /**
     * A tapped item ([startUri]) plays in order, so shuffle switches off. The shuffle buttons
     * and Enter-to-play have no item and switch shuffle on. [sort] and [folder] are what the
     * user is looking at, so the queue matches the screen.
     */
    fun play(
        startUri: String? = null,
        scope: PlayScope = PlayScope.AUTO,
        sort: SongSort = SongSort.AZ,
        folder: String? = null
    ) {
        if (startUri == null) {
            setMode(LoopMode.SHUFFLE)
        } else if (PlaybackSettings.loop.value == LoopMode.SHUFFLE) {
            setMode(LoopMode.ALL)
        }
        player.playQuery(_query.value, startUri, scope, sort = sort, folder = folder) { count, startedVideo ->
            _message.value = when {
                count < 0 -> "Player is still starting, try again in a moment"
                count == 0 -> "Nothing to play here"
                else -> null
            }
            if (count > 0 && startedVideo) _openVideo.tryEmit(Unit)
        }
    }

    /** Adds or removes the heart. */
    fun toggleFavorite(uri: String) {
        if (uri.isEmpty()) return
        viewModelScope.launch { repo.setFavorite(uri, uri !in favorites.value) }
    }

    /** Launcher shortcuts: wait for the player service to connect, then act. */
    fun runShortcut(action: String) {
        viewModelScope.launch {
            player.player.first { it != null }
            when (action) {
                ShortcutActions.SHUFFLE_SONGS -> {
                    _query.value = ""
                    play(scope = PlayScope.SONGS)
                }
                ShortcutActions.SHUFFLE_VIDEOS -> {
                    _query.value = ""
                    play(scope = PlayScope.VIDEOS)
                }
                ShortcutActions.RESUME -> player.play()
            }
        }
    }

    // ----- Shorts feed -----

    /** Opens the feed on [startUri]; the rest follow in random order. */
    fun openShortsFeed(startUri: String) {
        val all = shorts.value
        val start = all.firstOrNull { it.uri == startUri } ?: return
        _feed.value = listOf(start) + all.filter { it.uri != startUri }.shuffled()
        _feedStart.value = startUri
        _openShorts.tryEmit(Unit)
    }

    fun openShortsFeedShuffled() {
        val list = shorts.value.shuffled()
        val first = list.firstOrNull() ?: return
        _feed.value = list
        _feedStart.value = first.uri
        _openShorts.tryEmit(Unit)
    }

    /** From the mini-player: back into the feed at the short that's loaded. */
    fun reopenShortsFeed() {
        val current = playerState.value.uri
        _feedStart.value = current
        if (_feed.value.none { it.uri == current }) _feed.value = shorts.value
        _openShorts.tryEmit(Unit)
    }

    fun playShort(uri: String) = player.playItem(uri)

    fun toggleShortsFill() {
        val next = !_shortsFill.value
        _shortsFill.value = next
        Prefs.setShortsFill(appContext, next)
    }

    // ----- Delete, rename, and moving videos between kinds -----

    fun delete(item: MediaItemEntity) {
        _fileRequests.tryEmit(TrashRequest(item))
    }

    fun rename(item: MediaItemEntity, newBaseName: String) {
        _fileRequests.tryEmit(RenameRequest(item, newBaseName))
    }

    suspend fun fileName(uri: String): String? = musicApp.fileEditor.displayName(uri)

    /** Moves a video between Shorts and Videos. Survives rescans. */
    fun setKind(item: MediaItemEntity, kind: Int) {
        viewModelScope.launch {
            repo.setKind(item.uri, kind)
            // A video moved out of Shorts leaves the open feed too
            if (kind != MediaClassifier.SHORT) {
                _feed.value = _feed.value.filter { it.uri != item.uri }
            }
        }
    }

    /** Called with the user's answer to the system permission dialog. */
    fun onFileRequestResult(request: FileRequest, granted: Boolean) {
        if (!granted) return
        viewModelScope.launch {
            when (request) {
                is TrashRequest -> {
                    // On approval the system has already moved the file to the trash
                    repo.remove(request.item.uri)
                    _feed.value = _feed.value.filter { it.uri != request.item.uri }
                    val now = playerState.value
                    if (now.uri == request.item.uri && !now.isShort) player.next()
                }

                is RenameRequest -> {
                    val ok = musicApp.fileEditor.rename(request.item.uri, request.newBaseName)
                    _message.value = if (ok) null else "Couldn't rename that file"
                    rescan()
                }
            }
        }
    }

    // ----- Playback mode, queue and sleep timer -----

    private fun setMode(mode: LoopMode) {
        PlaybackSettings.loop.value = mode
        Prefs.setLoopMode(appContext, mode)
    }

    fun cycleLoop() {
        val next = PlaybackSettings.loop.value.next()
        setMode(next)
        Toast.makeText(
            appContext,
            when (next) {
                LoopMode.ALL -> "Loop all"
                LoopMode.ONE -> "Loop one"
                LoopMode.SHUFFLE -> "Shuffle"
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    fun playNext(item: MediaItemEntity) = player.enqueue(item.uri, atFront = true)
    fun addToQueue(item: MediaItemEntity) = player.enqueue(item.uri, atFront = false)
    fun playQueueItem(uri: String) = player.playQueueItem(uri)

    fun setSleepMinutes(minutes: Int) = SleepTimer.startMinutes(minutes)
    fun setSleepEndOfTrack() = SleepTimer.startEndOfTrack()
    fun clearSleep() = SleepTimer.clear()

    fun pause() = player.pause()
    fun previous() = player.previous()
    fun next() = player.next()
    fun togglePlayPause() = player.togglePlayPause()

    /** The user picked a subtitle file for the video that's playing. */
    fun addSubtitle(sub: Uri) {
        val videoUri = playerState.value.uri
        if (videoUri.isEmpty()) return
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { displayNameOf(sub) }.orEmpty()
            val mime = SubtitleFormats.mimeFor(name)
            if (mime == null) {
                Toast.makeText(
                    appContext,
                    "That isn't a supported subtitle file (.srt, .vtt, .ass, .ttml)",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            repo.saveSubtitle(SubtitleFile(videoUri, sub.toString(), mime, name.substringBeforeLast('.')))
            player.refreshSubtitle(videoUri)
            Toast.makeText(appContext, "Subtitles added", Toast.LENGTH_SHORT).show()
        }
    }
    // ----- Lyrics -----

    /** The user picked an .lrc or .txt file for the song that's playing. */
    fun addLyricsFile(file: Uri) {
        val songUri = playerState.value.uri
        if (songUri.isEmpty()) return
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { readSmallText(file) }
            if (text.isNullOrBlank()) {
                Toast.makeText(
                    appContext,
                    "Couldn't read that file. Pick a small .lrc or .txt file.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                repo.saveLyrics(songUri, text)
                Toast.makeText(appContext, "Lyrics added", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveLyricsText(text: String) {
        val songUri = playerState.value.uri
        if (songUri.isEmpty() || text.isBlank()) return
        viewModelScope.launch { repo.saveLyrics(songUri, text) }
    }

    fun removeLyrics() {
        val songUri = playerState.value.uri
        if (songUri.isEmpty()) return
        viewModelScope.launch { repo.removeLyrics(songUri) }
    }

    /** Reads a text file, refusing anything big (a video picked by mistake, for example). */
    private fun readSmallText(uri: Uri): String? = runCatching {
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_LYRICS_BYTES) return@use null
                out.write(buffer, 0, read)
            }
            String(out.toByteArray(), Charsets.UTF_8)
        }
    }.getOrNull()

    // ----- Crash report from the last run -----

    private val _crashReport = MutableStateFlow<String?>(null)
    val crashReport: StateFlow<String?> = _crashReport.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { _crashReport.value = CrashLogger.read(appContext) }
    }

    fun sendCrashReport(context: Context) {
        val crash = _crashReport.value ?: return
        shareReport(
            context,
            songs.value.size,
            (libraryCount.value - songs.value.size).coerceAtLeast(0),
            crash
        )
        dismissCrashReport()
    }

    fun dismissCrashReport() {
        CrashLogger.clear(appContext)
        _crashReport.value = null
    }


    private fun displayNameOf(uri: Uri): String? =
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}