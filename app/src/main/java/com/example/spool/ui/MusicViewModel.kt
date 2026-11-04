package com.example.spool.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spool.MusicApp
import com.example.spool.data.MediaItemEntity
import com.example.spool.player.PlayerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val musicApp = app as MusicApp
    private val repo = musicApp.repository
    private val player = musicApp.playerConnection

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<MediaItemEntity>> = _query
        .flatMapLatest { repo.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryCount: StateFlow<Int> = repo.count
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val playerState: StateFlow<PlayerState> = player.state

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

    fun playQuery() {
        val q = _query.value
        if (q.isBlank()) return
        player.playQuery(q) { count ->
            _message.value = when {
                count < 0 -> "Player is still starting, try again in a moment"
                count == 0 -> "No matches for \"$q\""
                else -> null
            }
        }
    }

    fun previous() = player.previous()
    fun next() = player.next()
    fun togglePlayPause() = player.togglePlayPause()
}