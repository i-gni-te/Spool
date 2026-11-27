package com.example.spool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.spool.ui.theme.Ember
import kotlin.math.roundToInt

/** Asks first, shows progress, then reports what happened. */
@Composable
fun ConversionDialog(
    state: ConversionState,
    onConvert: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onShowSongs: () -> Unit
) {
    val container = MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(24.dp)

    when (state) {
        ConversionState.Idle -> Unit

        is ConversionState.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = container,
            shape = shape,
            title = { Text("Convert to audio?", style = MaterialTheme.typography.headlineMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Cue will save an audio-only copy of “${state.item.title}” in " +
                                "Music/Cue as an .m4a file. The video stays as it is.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state.item.durationMs < 30_000L) {
                        Text(
                            "It's under 30 seconds, so it won't show up in Songs, but the file " +
                                    "is still saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onConvert) { Text("Convert") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )

        is ConversionState.Running -> {
            KeepScreenOn()
            AlertDialog(
                onDismissRequest = {}, // only the Cancel button closes it
                containerColor = container,
                shape = shape,
                title = { Text("Converting…", style = MaterialTheme.typography.headlineMedium) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.progress <= 0.001f) {
                            LinearProgressIndicator(
                                color = Ember,
                                trackColor = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                color = Ember,
                                trackColor = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text(
                            if (state.progress <= 0.001f) "Starting…"
                            else "${(state.progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Keep Cue open until it finishes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
            )
        }

        is ConversionState.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = container,
            shape = shape,
            title = { Text("Audio saved", style = MaterialTheme.typography.headlineMedium) },
            text = {
                Text(
                    "Saved as ${state.fileName} in Music/Cue. It appears in Songs, under New.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = { TextButton(onClick = onShowSongs) { Text("Show in Songs") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )

        is ConversionState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = container,
            shape = shape,
            title = { Text("Couldn't convert", style = MaterialTheme.typography.headlineMedium) },
            text = { Text(state.reason, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
    }
}

/** The screen stays on while a conversion runs, so the phone doesn't pause it by sleeping. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}