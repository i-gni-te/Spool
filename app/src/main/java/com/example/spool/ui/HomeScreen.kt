package com.example.spool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.spool.R
import com.example.spool.ui.theme.SpoolGradient

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: MusicViewModel, modifier: Modifier = Modifier) {
    val query by vm.query.collectAsState()
    val songs by vm.songs.collectAsState()
    val videos by vm.videos.collectAsState()
    val total by vm.libraryCount.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val message by vm.message.collectAsState()
    val artists by vm.topArtists.collectAsState()
    val focus = LocalFocusManager.current

    val ready = songs.size + videos.size
    fun playNow() {
        vm.play()
        focus.clearFocus()
    }

    val summary = when {
        total == 0 && scanning -> "Looking through your phone…"
        total == 0 -> "No songs or videos found yet"
        ready == 0 -> "Nothing matches “${query.trim()}”"
        else -> "${songs.size} songs · ${videos.size} videos ready"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Wordmark
        // Wordmark
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

        // Hero line
        item {
            Text(
                buildAnnotatedString {
                    append("What are we\n")
                    withStyle(SpanStyle(brush = SpoolGradient)) { append("playing") }
                    append(" today?")
                },
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        // Type, press Enter, it plays
        item {
            SearchField(
                query = query,
                onQueryChange = vm::onQueryChange,
                placeholder = "Artist, song, anything…",
                onGo = { playNow() }
            )
        }

        item {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            PlayPill(
                text = if (query.isBlank()) "Shuffle everything" else "Shuffle “${query.trim()}”",
                onClick = { playNow() },
                enabled = ready > 0
            )
        }

        message?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        // Quick picks: one tap plays an artist
        if (query.isBlank() && artists.isNotEmpty()) {
            item { Text("Quick picks", style = MaterialTheme.typography.titleLarge) }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    artists.forEach { a ->
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    vm.playArtist(a.name)
                                    focus.clearFocus()
                                }
                                .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Cover(seed = a.name, modifier = Modifier.size(28.dp), corner = 14.dp)
                            Text(a.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${a.total}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}