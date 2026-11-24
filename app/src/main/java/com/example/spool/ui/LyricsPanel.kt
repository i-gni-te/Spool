package com.example.spool.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.spool.domain.LyricsDoc
import com.example.spool.ui.theme.Ember
import kotlinx.coroutines.delay

/** Shows the lyrics in place of the cover. With an .lrc file the current line follows the music. */
@Composable
fun LyricsPanel(
    doc: LyricsDoc?,
    player: Player?,
    onAddFile: () -> Unit,
    onPaste: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (doc == null || doc.lines.isEmpty()) {
        Column(
            modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No lyrics for this song yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Add a .lrc file for lyrics that follow the music, or paste the words.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAddFile) { Text("Add .lrc file") }
                OutlinedButton(onClick = onPaste) { Text("Paste lyrics") }
            }
        }
        return
    }

    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, doc.synced) {
        while (doc.synced) {
            position = player?.currentPosition ?: 0L
            delay(200)
        }
    }
    val current = doc.indexAt(position)
    val listState = rememberLazyListState()

    // Keep the current line about a third of the way down
    LaunchedEffect(current) {
        if (current >= 0) {
            listState.animateScrollToItem(current, -(listState.layoutInfo.viewportSize.height / 3))
        }
    }

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(doc.lines) { index, line ->
                    val active = doc.synced && index == current
                    Text(
                        text = line.text.ifBlank { if (doc.synced) "♪" else " " },
                        style = if (active) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.titleMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            active -> Ember
                            doc.synced -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (doc.synced) Modifier.clickable { player?.seekTo(line.timeMs) }
                                else Modifier
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onAddFile) { Text("Replace file") }
            TextButton(onClick = onPaste) { Text("Paste") }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
fun PasteLyricsDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Paste lyrics", style = MaterialTheme.typography.headlineMedium) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 6,
                maxLines = 12,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Paste the words here. Text with [00:12.00] times will follow the music.") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ember,
                    cursorColor = Ember
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    onSave(text.trim())
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}