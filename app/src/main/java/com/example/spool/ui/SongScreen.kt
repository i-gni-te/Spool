package com.example.spool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spool.data.MediaItemEntity
import com.example.spool.domain.PlayScope
import com.example.spool.ui.theme.Ember

private sealed interface ListEntry {
    data class Letter(val letter: String) : ListEntry
    data class Track(val song: MediaItemEntity) : ListEntry
}

/** Inserts an A–Z letter header whenever the artist's first letter changes. */
private fun buildEntries(songs: List<MediaItemEntity>): List<ListEntry> {
    val entries = ArrayList<ListEntry>(songs.size + 32)
    var lastLetter = ""
    for (song in songs) {
        val letter = song.artist.trim().firstOrNull()
            ?.takeIf { it.isLetter() }?.uppercase() ?: "#"
        if (letter != lastLetter) {
            entries += ListEntry.Letter(letter)
            lastLetter = letter
        }
        entries += ListEntry.Track(song)
    }
    return entries
}

@Composable
fun SongsScreen(vm: MusicViewModel, modifier: Modifier = Modifier) {
    val query by vm.query.collectAsState()
    val songs by vm.songs.collectAsState()
    val total by vm.libraryCount.collectAsState()
    val message by vm.message.collectAsState()
    val playing by vm.playerState.collectAsState()
    val focus = LocalFocusManager.current
    val entries = remember(songs) { buildEntries(songs) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { ScreenHeader("Songs", "${songs.size} songs") }
        item {
            SearchField(
                query = query,
                onQueryChange = vm::onQueryChange,
                placeholder = "Filter songs",
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item {
            PlayPill(
                text = "Shuffle ${songs.size} songs",
                onClick = {
                    vm.play(scope = PlayScope.SONGS)
                    focus.clearFocus()
                },
                enabled = songs.isNotEmpty(),
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        if (songs.isEmpty()) {
            item {
                Text(
                    if (total == 0) "No songs found yet" else "No songs match",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                )
            }
        }

        items(
            entries,
            key = {
                when (it) {
                    is ListEntry.Letter -> "h:" + it.letter
                    is ListEntry.Track -> "s:" + it.song.uri
                }
            }
        ) { entry ->
            when (entry) {
                is ListEntry.Letter -> Text(
                    entry.letter,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ember,
                    modifier = Modifier.padding(start = 4.dp, top = 14.dp)
                )

                is ListEntry.Track -> SongCard(
                    song = entry.song,
                    current = playing.uri == entry.song.uri,
                    vm = vm
                ) {
                    vm.play(startUri = entry.song.uri)
                    focus.clearFocus()
                }
            }
        }
    }
}

@Composable
private fun SongCard(
    song: MediaItemEntity,
    current: Boolean,
    vm: MusicViewModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (current) Ember.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MediaThumb(
            uri = song.uri,
            seed = song.artist,
            isVideo = false,
            modifier = Modifier.size(52.dp),
            corner = 12.dp,
            sizePx = 200
        )
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (current) Ember else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (current) {
            Icon(Icons.Filled.GraphicEq, contentDescription = "Playing", tint = Ember)
        }
        ItemMenuButton(item = song, vm = vm)
    }
}