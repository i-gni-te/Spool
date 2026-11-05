package com.example.spool.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spool.R
import com.example.spool.domain.ArtistEntry
import com.example.spool.ui.theme.SpoolGradient

private fun LazyGridScope.fullWidth(content: @Composable LazyGridItemScope.() -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }, content = content)
}

@Composable
fun HomeScreen(
    vm: MusicViewModel,
    onSeeAllSongs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query by vm.query.collectAsState()
    val songs by vm.songs.collectAsState()
    val videos by vm.videos.collectAsState()
    val total by vm.libraryCount.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val message by vm.message.collectAsState()
    val artists by vm.artists.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val focus = LocalFocusManager.current

    val typing = query.isNotBlank()
    val shown = if (typing) suggestions else artists.take(30)
    val ready = songs.size + videos.size

    fun playNow() {
        vm.play()
        focus.clearFocus()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact header
        fullWidth {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogoMark(markSize = 30.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.app_name).lowercase(),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (scanning) "syncing…" else if (total > 0) "$total in library" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Search: type, press Enter, it plays
        fullWidth {
            Column(Modifier.padding(horizontal = 4.dp)) {
                SearchField(
                    query = query,
                    onQueryChange = vm::onQueryChange,
                    placeholder = "Type an artist to play…",
                    onGo = { playNow() }
                )
            }
        }

        // While typing: one big action, plus a link to the full list
        if (typing) {
            fullWidth {
                Column(
                    Modifier.padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (ready > 0) {
                        PlayPill(
                            text = "Shuffle “${query.trim()}” · $ready",
                            onClick = { playNow() }
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${songs.size} songs · ${videos.size} videos",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (songs.isNotEmpty()) {
                                TextButton(onClick = onSeeAllSongs) { Text("See all songs") }
                            }
                        }
                    } else {
                        Text(
                            "Nothing matches “${query.trim()}”",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        message?.let { text ->
            fullWidth { Text(text, color = MaterialTheme.colorScheme.error) }
        }

        // Section header
        if (!typing || shown.isNotEmpty()) {
            fullWidth {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (typing) "Artists" else "Top artists",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (!typing && total > 0) {
                        TextButton(onClick = { playNow() }) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("Shuffle all")
                        }
                    }
                }
            }
        }

        // The artist grid: tap an avatar and that artist shuffles
        items(shown, key = { it.key }) { entry ->
            ArtistTile(entry) {
                vm.playArtist(entry)
                focus.clearFocus()
            }
        }

        if (!typing && shown.isEmpty()) {
            fullWidth {
                Text(
                    when {
                        total == 0 && scanning -> "Looking through your phone…"
                        total == 0 -> "No songs or videos found yet"
                        else -> "No artists yet"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtistTile(entry: ArtistEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(2.dp, SpoolGradient, CircleShape)
                .padding(5.dp)
        ) {
            MediaThumb(
                uri = entry.sampleUri,
                seed = entry.name,
                isVideo = false,
                modifier = Modifier.fillMaxSize(),
                corner = 200.dp,
                sizePx = 300
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            entry.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            "${entry.total} ${if (entry.total == 1) "track" else "tracks"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}