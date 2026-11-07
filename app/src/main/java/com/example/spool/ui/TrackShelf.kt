package com.example.spool.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spool.data.MediaItemEntity

data class ShelfTrack(val item: MediaItemEntity, val note: String)

/** A titled row of tracks that scrolls sideways. Tapping a card plays it. */
@Composable
fun TrackShelf(
    title: String,
    tracks: List<ShelfTrack>,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(tracks, key = { it.item.uri }) { track ->
                TrackCard(track) { onPlay(track.item.uri) }
            }
        }
    }
}

@Composable
private fun TrackCard(track: ShelfTrack, onClick: () -> Unit) {
    Column(
        Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        MediaThumb(
            uri = track.item.uri,
            seed = track.item.artist,
            isVideo = track.item.isVideo,
            modifier = Modifier.size(120.dp),
            corner = 16.dp,
            sizePx = 360
        )
        Spacer(Modifier.height(6.dp))
        Text(
            track.item.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            track.note,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}