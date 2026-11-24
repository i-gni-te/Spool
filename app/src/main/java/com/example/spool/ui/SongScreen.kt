package com.example.spool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import com.example.spool.data.Prefs
import com.example.spool.domain.FolderGroup
import com.example.spool.domain.FolderIndex
import com.example.spool.domain.PlayScope
import com.example.spool.domain.SongSort
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
    val context = LocalContext.current
    val query by vm.query.collectAsState()
    val songs by vm.songs.collectAsState()
    val songsByName by vm.songsByName.collectAsState()
    val sort by vm.songSort.collectAsState()
    val fresh by vm.newSongs.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val total by vm.libraryCount.collectAsState()
    val message by vm.message.collectAsState()
    val playing by vm.playerState.collectAsState()
    val focus = LocalFocusManager.current

    var asFolders by remember { mutableStateOf(Prefs.songsAsFolders(context)) }
    var openFolderKey by rememberSaveable { mutableStateOf<String?>(null) }

    val groups = remember(songsByName) { FolderIndex.build(songsByName) }
    val openGroup = if (asFolders) groups.firstOrNull { it.key == openFolderKey } else null

    // Back leaves an open folder
    BackHandler(enabled = openGroup != null) { openFolderKey = null }

    // Letter headers only make sense in A–Z order
    val entries = remember(songs, sort) {
        if (sort == SongSort.AZ) buildEntries(songs) else songs.map { ListEntry.Track(it) }
    }

    // What's on screen decides what plays: a folder, the favorites, or the chosen sort
    val playSort = if (asFolders) SongSort.AZ else sort
    val playFolder = openGroup?.key

    fun shuffleAll() {
        vm.play(scope = PlayScope.SONGS, sort = playSort, folder = playFolder)
        focus.clearFocus()
    }

    fun playSong(song: MediaItemEntity) {
        vm.play(startUri = song.uri, sort = playSort, folder = playFolder)
        focus.clearFocus()
    }

    val shuffleCount = when {
        openGroup != null -> openGroup.songs.size
        asFolders -> songsByName.size
        else -> songs.size
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            if (openGroup != null) {
                FolderHeader(openGroup) { openFolderKey = null }
            } else {
                ScreenHeader(
                    title = "Songs",
                    count = "${if (asFolders) songsByName.size else songs.size} songs",
                    onReport = {
                        shareReport(
                            context,
                            songsByName.size,
                            (total - songsByName.size).coerceAtLeast(0)
                        )
                    }
                )
            }
        }
        item {
            SearchField(
                query = query,
                onQueryChange = vm::onQueryChange,
                placeholder = "Type an artist or song",
                modifier = Modifier.padding(top = 4.dp),
                onGo = { shuffleAll() }
            )
        }

        // All songs | Folders
        if (openGroup == null) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    listOf(false to "All songs", true to "Folders").forEach { (folders, label) ->
                        FilterChip(
                            selected = asFolders == folders,
                            onClick = {
                                asFolders = folders
                                openFolderKey = null
                                Prefs.setSongsAsFolders(context, folders)
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Ember.copy(alpha = 0.18f),
                                selectedLabelColor = Ember
                            )
                        )
                    }
                }
            }
        }

        // Sort chips (the folder view is always A–Z)
        if (openGroup == null && !asFolders) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SongSort.entries.toList(), key = { it.name }) { option ->
                        FilterChip(
                            selected = sort == option,
                            onClick = { vm.setSongSort(option) },
                            label = { Text(option.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Ember.copy(alpha = 0.18f),
                                selectedLabelColor = Ember
                            )
                        )
                    }
                }
            }
        }

        message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        when {
            // Inside a folder
            openGroup != null -> {
                items(openGroup.songs, key = { "f:" + it.uri }) { song ->
                    SongCard(song, playing.uri == song.uri, song.uri in favorites, vm) {
                        playSong(song)
                    }
                }
            }

            // The folder list
            asFolders -> {
                if (groups.isEmpty()) {
                    item {
                        Text(
                            if (total == 0) "No songs found yet" else "No songs match",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                        )
                    }
                }
                items(groups, key = { "g:" + it.key }) { group ->
                    FolderRow(group) { openFolderKey = group.key }
                }
            }

            // All songs
            else -> {
                // New downloads, newest first
                if (query.isBlank() && sort != SongSort.FAVORITES && fresh.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "New",
                                style = MaterialTheme.typography.titleLarge,
                                color = Ember,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.dismissNew() }) { Text("Clear") }
                        }
                    }
                    items(fresh, key = { "n:" + it.uri }) { song ->
                        SongCard(song, playing.uri == song.uri, song.uri in favorites, vm) {
                            playSong(song)
                        }
                    }
                }

                if (songs.isEmpty()) {
                    item {
                        Text(
                            when {
                                sort == SongSort.FAVORITES ->
                                    "No favorites yet. Tap the heart on a song."
                                total == 0 -> "No songs found yet"
                                else -> "No songs match"
                            },
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
                            favorite = entry.song.uri in favorites,
                            vm = vm
                        ) { playSong(entry.song) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderHeader(group: FolderGroup, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(Modifier.weight(1f)) {
            Text(
                group.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                (if (group.path.isNotEmpty()) "${group.path} · " else "") +
                        "${group.songs.size} songs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FolderRow(group: FolderGroup, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ember.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = Ember)
        }
        Column(Modifier.weight(1f)) {
            Text(
                group.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${group.songs.size} songs" +
                        (if (group.path.isNotEmpty()) " · ${group.path}" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SongCard(
    song: MediaItemEntity,
    current: Boolean,
    favorite: Boolean,
    vm: MusicViewModel,
    onClick: () -> Unit
) {
    var renaming by remember { mutableStateOf(false) }
    val needsName = remember(song.title) { MediaClassifier.looksRandom(song.title) }

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
            if (needsName) {
                Text(
                    "Needs a name · tap to rename",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ember,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { renaming = true }
                        .padding(vertical = 3.dp)
                )
            }
        }
        if (current) {
            Icon(Icons.Filled.GraphicEq, contentDescription = "Playing", tint = Ember)
        }
        IconButton(onClick = { vm.toggleFavorite(song.uri) }) {
            Icon(
                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                tint = if (favorite) Ember else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ItemMenuButton(item = song, vm = vm)
    }

    if (renaming) RenameDialog(song, vm) { renaming = false }
}