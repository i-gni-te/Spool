package com.example.spool.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.spool.domain.LyricsDoc
import com.example.spool.domain.RandomQueue
import com.example.spool.player.LoopMode
import com.example.spool.player.PlayerState
import com.example.spool.player.SleepState
import com.example.spool.ui.theme.Ember
import com.example.spool.ui.theme.Ink
import com.example.spool.ui.theme.artistPalette

@Composable
fun NowPlayingScreen(
    player: Player?,
    state: PlayerState,
    loop: LoopMode,
    queue: RandomQueue.Snapshot?,
    sleep: SleepState,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    lyrics: LyricsDoc?,
    onAddLyricsFile: (Uri) -> Unit,
    onSaveLyricsText: (String) -> Unit,
    onRemoveLyrics: () -> Unit,
    onCycleLoop: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPlayQueueItem: (String) -> Unit,
    onSleepMinutes: (Int) -> Unit,
    onSleepEndOfTrack: () -> Unit,
    onSleepOff: () -> Unit,
    onClose: () -> Unit
) {
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    val sleepLabel = rememberSleepLabel(sleep)

    // The lyrics file picker. A lyrics file has no standard type, so any file can be chosen.
    val lyricsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked -> if (picked != null) onAddLyricsFile(picked) }

    // The artist's two colours. They glow through the screen and fade over when the song changes.
    val seed = state.artist.ifBlank { state.title }
    val (colorA, colorB) = remember(seed) { artistPalette(seed) }
    val glowTop by animateColorAsState(colorA.copy(alpha = 0.40f), tween(800), label = "glowTop")
    val glowBottom by animateColorAsState(colorB.copy(alpha = 0.26f), tween(800), label = "glowBottom")
    val playBrush = remember(colorA, colorB) { Brush.linearGradient(listOf(colorA, colorB)) }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(glowTop, Color.Transparent),
                        endY = size.height * 0.65f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(glowBottom, Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.9f),
                        radius = size.width * 0.9f
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    "Now playing",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                        tint = if (favorite) Ember else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showSleep = true }) {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = "Sleep timer",
                        tint = if (sleep.active) Ember else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // The cover (with a shadow in the artist's colour), or the lyrics
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    LyricsPanel(
                        doc = lyrics,
                        player = player,
                        onAddFile = { lyricsPicker.launch(arrayOf("*/*")) },
                        onPaste = { showPaste = true },
                        onRemove = onRemoveLyrics,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    MediaThumb(
                        uri = state.uri,
                        seed = seed,
                        isVideo = false,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .shadow(
                                elevation = 28.dp,
                                shape = RoundedCornerShape(28.dp),
                                ambientColor = colorA,
                                spotColor = colorA
                            ),
                        corner = 28.dp,
                        sizePx = 720
                    )
                }
            }

            Text(
                state.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                state.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Pill(if (showLyrics) "Cover" else "Lyrics", active = showLyrics) {
                    showLyrics = !showLyrics
                }
                Pill("EQ", active = false) { showEq = true }
            }
            if (sleepLabel != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sleep: $sleepLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ember,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showSleep = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))

            if (player != null) {
                SeekBar(player = player, onTouch = {}, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))

            // Mode button on the left, queue list on the right
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LoopButton(mode = loop, onClick = onCycleLoop)
                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp))
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(playBrush)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                        tint = Ink
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp))
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = "Queue",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            snapshot = queue,
            onPlay = onPlayQueueItem,
            onDismiss = { showQueue = false }
        )
    }
    if (showSleep) {
        SleepDialog(
            active = sleep.active,
            onMinutes = onSleepMinutes,
            onEndOfTrack = onSleepEndOfTrack,
            onOff = onSleepOff,
            onDismiss = { showSleep = false }
        )
    }
    if (showPaste) {
        PasteLyricsDialog(
            onSave = onSaveLyricsText,
            onDismiss = { showPaste = false }
        )
    }
    if (showEq) EqualizerSheet { showEq = false }
}

@Composable
private fun Pill(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) Ember else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (active) Ember.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}