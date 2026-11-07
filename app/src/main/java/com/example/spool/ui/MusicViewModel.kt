package com.example.spool.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.spool.MusicApp
import com.example.spool.data.ArtistCount
import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import com.example.spool.data.Prefs
import com.example.spool.domain.PlayScope
import com.example.spool.player.LoopMode
import com.example.spool.player.PlaybackSettings
import com.example.spool.player.PlayerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.spool.data.TextNormalizer
import com.example.spool.domain.ArtistEntry
import com.example.spool.domain.ArtistIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import com.example.spool.domain.RandomQueue
import com.example.spool.player.QueueInfo
import com.example.spool.data.PlayedTrack
import com.example.spool.player.SleepState
import com.example.spool.player.SleepTimer
import android.widget.Toast
import com.example.spool.domain.MediaOrder

/** A file change that needs the user's permission, shown by the system as a dialog. */
sealed interface FileRequest {
    val item: MediaItemEntity
}

data class TrashRequest(override val item: MediaItemEntity) : FileRequest
data class RenameRequest(override val item: MediaItemEntity, val newBaseName: String) : FileRequest

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

    /** Everything matching the text, shared by all three screens. */
    private val found: StateFlow<List<MediaItemEntity>> =
        _query.flatMapLatest { repo.search(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val songs: StateFlow<List<MediaItemEntity>> = found
        .map { list ->
            list.filter { it.kind == MediaClassifier.SONG }.sortedWith(MediaOrder.byName)
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
    val artists: StateFlow<List<ArtistEntry>> = repo.search("")
        .map { ArtistIndex.build(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Artists matching the text being typed, best matches first. */
    val suggestions: StateFlow<List<ArtistEntry>> = combine(artists, _query) { list, q ->
        val key = TextNormalizer.normalize(q)
        if (key.isEmpty()) {
            emptyList()
        } else {
            list.filter { key in it.key }
                .sortedByDescending { it.key.startsWith(key) }
                .take(9)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val libraryCount: StateFlow<Int> = repo.count
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val recentlyPlayed: StateFlow<List<MediaItemEntity>> = repo.recentlyPlayed(12)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mostPlayed: StateFlow<List<PlayedTrack>> = repo.mostPlayed(12)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _openVideo = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openVideoEvents: SharedFlow<Unit> = _openVideo.asSharedFlow()

    // Shorts feed
    private val _feed = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val feed: StateFlow<List<MediaItemEntity>> = _feed.asStateFlow()

    private val _shortsFill = MutableStateFlow(Prefs.shortsFill(app))
    val shortsFill: StateFlow<Boolean> = _shortsFill.asStateFlow()
    private val _feedStart = MutableStateFlow("")
    val feedStart: StateFlow<String> = _feedStart.asStateFlow()

    private val _openShorts = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openShortsEvents: SharedFlow<Unit> = _openShorts.asSharedFlow()

    // File changes (delete, rename) that need a system permission dialog
    private val _fileRequests = MutableSharedFlow<FileRequest>(extraBufferCapacity = 1)
    val fileRequests: SharedFlow<FileRequest> = _fileRequests.asSharedFlow()

    val playerState: StateFlow<PlayerState> = player.state
    val videoPlayer: StateFlow<Player?> = player.player
    val loop: StateFlow<LoopMode> = PlaybackSettings.loop.asStateFlow()

    val queue: StateFlow<RandomQueue.Snapshot?> = QueueInfo.snapshot.asStateFlow()
    val sleep: StateFlow<SleepState> = SleepTimer.state.asStateFlow()
    fun finishOnboarding() {
        Prefs.setOnboardingDone(appContext)
        _onboardingDone.value = true
    }

    fun onQueryChange(text: String) {
        _query.value = text
        _message.value = null
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
     * and Enter-to-play have no item and switch shuffle on.
     */
    fun play(startUri: String? = null, scope: PlayScope = PlayScope.AUTO) {
        if (startUri == null) {
            setMode(LoopMode.SHUFFLE)
        } else if (PlaybackSettings.loop.value == LoopMode.SHUFFLE) {
            setMode(LoopMode.ALL)
        }
        player.playQuery(_query.value, startUri, scope) { count, startedVideo ->
            _message.value = when {
                count < 0 -> "Player is still starting, try again in a moment"
                count == 0 -> "Nothing to play here"
                else -> null
            }
            if (count > 0 && startedVideo) _openVideo.tryEmit(Unit)
        }
    }

    /** Plays exactly this artist. The search text on screen stays as it is. */
    fun playArtist(entry: ArtistEntry) {
        setMode(LoopMode.SHUFFLE)
        player.playQuery(entry.name, null, PlayScope.AUTO, entry.key) { count, startedVideo ->
            _message.value = when {
                count < 0 -> "Player is still starting, try again in a moment"
                count == 0 -> "Nothing to play here"
                else -> null
            }
            if (count > 0 && startedVideo) _openVideo.tryEmit(Unit)
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

    // ----- Delete and rename -----

    fun delete(item: MediaItemEntity) {
        _fileRequests.tryEmit(TrashRequest(item))
    }

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

    fun rename(item: MediaItemEntity, newBaseName: String) {
        _fileRequests.tryEmit(RenameRequest(item, newBaseName))
    }

    suspend fun fileName(uri: String): String? = musicApp.fileEditor.displayName(uri)

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

    fun setSleepMinutes(minutes: Int) = SleepTimer.startMinutes(minutes)
    fun setSleepEndOfTrack() = SleepTimer.startEndOfTrack()
    fun clearSleep() = SleepTimer.clear()

    fun toggleShortsFill() {
        val next = !_shortsFill.value
        _shortsFill.value = next
        Prefs.setShortsFill(appContext, next)
    }

    fun playQueueItem(uri: String) = player.playQueueItem(uri)

    fun pause() = player.pause()
    fun previous() = player.previous()
    fun next() = player.next()
    fun togglePlayPause() = player.togglePlayPause()
}