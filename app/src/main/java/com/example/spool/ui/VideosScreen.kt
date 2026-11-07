package com.example.spool.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spool.data.MediaItemEntity
import com.example.spool.data.MediaScanner
import com.example.spool.domain.PlayScope
import com.example.spool.ui.theme.Ember

@Composable
fun VideosScreen(vm: MusicViewModel, modifier: Modifier = Modifier) {
    val query by vm.query.collectAsState()
    val videos by vm.videos.collectAsState()
    val shorts by vm.shorts.collectAsState()
    val total by vm.libraryCount.collectAsState()
    val message by vm.message.collectAsState()
    val playing by vm.playerState.collectAsState()
    val focus = LocalFocusManager.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader("Videos", "${videos.size + shorts.size} videos") }
        item {
            SearchField(
                query = query,
                onQueryChange = vm::onQueryChange,
                placeholder = "Filter videos"
            )
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        if (videos.isEmpty() && shorts.isEmpty()) {
            item {
                Text(
                    if (total == 0) "No videos found yet" else "No videos match",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
        }

        // Shorts belt: tapping one opens the swipe feed
        if (shorts.isNotEmpty()) {
            item {
                SectionHeader("Shorts", shorts.size, "Shuffle shorts") {
                    vm.openShortsFeedShuffled()
                    focus.clearFocus()
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shorts, key = { it.uri }) { short ->
                        Box(Modifier.width(112.dp).aspectRatio(9f / 16f)) {
                            MediaThumb(
                                uri = short.uri,
                                seed = short.title,
                                isVideo = true,
                                modifier = Modifier.fillMaxSize(),
                                corner = 18.dp,
                                sizePx = 480,
                                badge = formatDuration(short.durationMs),
                                onClick = {
                                    vm.openShortsFeed(short.uri)
                                    focus.clearFocus()
                                }
                            )
                            ItemMenuButton(
                                item = short,
                                vm = vm,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                tint = Color.White,
                                compact = true
                            )
                        }
                    }
                }
            }
        }

        // Videos: big thumbnail cards
        if (videos.isNotEmpty()) {
            item {
                SectionHeader("Videos", videos.size, "Shuffle videos") {
                    vm.play(scope = PlayScope.VIDEOS)
                    focus.clearFocus()
                }
            }
            items(videos, key = { it.uri }) { video ->
                VideoCard(video, current = playing.uri == video.uri, vm = vm) {
                    vm.play(startUri = video.uri)
                    focus.clearFocus()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAction) {
            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(action)
        }
    }
}

@Composable
private fun VideoCard(
    video: MediaItemEntity,
    current: Boolean,
    vm: MusicViewModel,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    ) {
        MediaThumb(
            uri = video.uri,
            seed = video.title,
            isVideo = true,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            corner = 0.dp,
            sizePx = 640,
            badge = formatDuration(video.durationMs)
        )
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (current) Ember else Color.Unspecified,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.artist != MediaScanner.UNKNOWN_ARTIST) {
                    Text(
                        video.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ItemMenuButton(item = video, vm = vm)
        }
    }
}