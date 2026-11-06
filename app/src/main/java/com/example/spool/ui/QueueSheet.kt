package com.example.spool.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spool.data.MediaItemEntity
import com.example.spool.domain.RandomQueue
import com.example.spool.ui.theme.Ember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    snapshot: RandomQueue.Snapshot?,
    onPlay: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        val now = snapshot?.current
        if (snapshot == null || now == null) {
            Text(
                "Nothing in the queue yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
            ) {
                item {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Text("Queue", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${snapshot.total} songs. Shuffle picks the next one at random. " +
                                    "Tap any song to play it now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item { SectionLabel("Now playing") }
                item { QueueRow(now, current = true, dim = false, onClick = onDismiss) }

                if (snapshot.remaining.isNotEmpty()) {
                    item { SectionLabel("Still to play · ${snapshot.remaining.size}") }
                    items(snapshot.remaining, key = { "r:" + it.uri }) { song ->
                        QueueRow(song, current = false, dim = false) {
                            onPlay(song.uri)
                            onDismiss()
                        }
                    }
                }

                if (snapshot.played.isNotEmpty()) {
                    item { SectionLabel("Already played · ${snapshot.played.size}") }
                    items(snapshot.played, key = { "p:" + it.uri }) { song ->
                        QueueRow(song, current = false, dim = true) {
                            onPlay(song.uri)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun QueueRow(
    song: MediaItemEntity,
    current: Boolean,
    dim: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .alpha(if (dim) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MediaThumb(
            uri = song.uri,
            seed = song.artist,
            isVideo = false,
            modifier = Modifier.size(44.dp),
            corner = 10.dp,
            sizePx = 160
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
        } else if (dim) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Played",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}