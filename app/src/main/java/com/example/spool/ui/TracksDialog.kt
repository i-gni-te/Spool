package com.example.spool.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.example.spool.ui.theme.Ember
import java.util.Locale

private data class TrackOption(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val selected: Boolean
)

/** Subtitles (built-in tracks, Off, or a file you add) and audio tracks. */
@Composable
fun TracksDialog(player: Player, onAddSubtitleFile: () -> Unit, onDismiss: () -> Unit) {
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var refresh by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
                refresh++
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val subtitles = remember(tracks) { optionsFor(tracks, C.TRACK_TYPE_TEXT) }
    val audio = remember(tracks) { optionsFor(tracks, C.TRACK_TYPE_AUDIO) }
    val subtitlesOff = remember(tracks, refresh) {
        player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
                subtitles.none { it.selected }
    }

    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        refresh++
    }

    fun pick(option: TrackOption, type: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
            .build()
        refresh++
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Subtitles and audio", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel("Subtitles")
                TrackRow("Off", selected = subtitlesOff) { disableSubtitles() }
                subtitles.forEach { option ->
                    TrackRow(option.label, selected = option.selected && !subtitlesOff) {
                        pick(option, C.TRACK_TYPE_TEXT)
                    }
                }
                TrackRow("Add subtitle file…", selected = false, icon = true) {
                    onAddSubtitleFile()
                    onDismiss()
                }

                SectionLabel("Audio")
                if (audio.size < 2) {
                    Text(
                        "This video has only one audio track.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    audio.forEach { option ->
                        TrackRow(option.label, selected = option.selected) {
                            pick(option, C.TRACK_TYPE_AUDIO)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Ember,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun TrackRow(
    label: String,
    selected: Boolean,
    icon: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (selected) Ember else Color.Unspecified
        )
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Ember)
        if (icon) Icon(Icons.Filled.Add, contentDescription = null, tint = Ember)
    }
}

private fun optionsFor(tracks: Tracks, type: Int): List<TrackOption> {
    var number = 0
    val result = mutableListOf<TrackOption>()
    for (group in tracks.groups) {
        if (group.type != type) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            number++
            result += TrackOption(
                group = group,
                index = i,
                label = labelFor(group.getTrackFormat(i), type, number),
                selected = group.isTrackSelected(i)
            )
        }
    }
    return result
}

private fun labelFor(format: Format, type: Int, number: Int): String {
    val language = format.language
        ?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { Locale.forLanguageTag(it).displayLanguage }
        ?.takeIf { it.isNotBlank() }
    val base = format.label?.takeIf { it.isNotBlank() } ?: language ?: "Track $number"
    if (type != C.TRACK_TYPE_AUDIO) return base

    val layout = when (format.channelCount) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        Format.NO_VALUE -> ""
        else -> "${format.channelCount} ch"
    }
    return if (layout.isEmpty()) base else "$base · $layout"
}