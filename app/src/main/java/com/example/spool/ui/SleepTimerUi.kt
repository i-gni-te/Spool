package com.example.spool.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.spool.player.SleepState
import com.example.spool.ui.theme.Ember
import kotlinx.coroutines.delay

/** "12:34" while counting down, "after this song" for end-of-song, null when off. */
@Composable
fun rememberSleepLabel(sleep: SleepState): String? {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sleep.endsAtMs) {
        while (sleep.endsAtMs > 0L) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    return when {
        sleep.endOfTrack -> "after this song"
        sleep.endsAtMs > 0L -> formatDuration((sleep.endsAtMs - now).coerceAtLeast(0L))
        else -> null
    }
}

@Composable
fun SleepDialog(
    active: Boolean,
    onMinutes: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onOff: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                listOf(15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour")
                    .forEach { (minutes, label) ->
                        OptionRow(label) {
                            onMinutes(minutes)
                            onDismiss()
                        }
                    }
                OptionRow("End of this song") {
                    onEndOfTrack()
                    onDismiss()
                }
                if (active) {
                    OptionRow("Turn off timer", highlight = true) {
                        onOff()
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun OptionRow(label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (highlight) Ember else Color.Unspecified,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
}