package com.example.spool.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import com.example.spool.ui.theme.Ember
import androidx.compose.material.icons.filled.Audiotrack

/**
 * A ⋮ button. Everything: Favorite, Share, Rename, Delete (to the trash). Songs: Play next,
 * Add to queue. Videos: Move to… (music video, movie, TV show, short).
 */
@Composable
fun ItemMenuButton(
    item: MediaItemEntity,
    vm: MusicViewModel,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val favorites by vm.favorites.collectAsState()
    val isFavorite = item.uri in favorites

    var expanded by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }

    Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = if (compact) {
                Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)
            } else {
                Modifier
            }
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "More options",
                tint = tint,
                modifier = if (compact) Modifier.size(18.dp) else Modifier
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            MenuRow(
                text = if (isFavorite) "Remove from favorites" else "Add to favorites",
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
            ) {
                expanded = false
                vm.toggleFavorite(item.uri)
            }

            if (!item.isVideo) {
                MenuRow("Play next", Icons.Filled.SkipNext) {
                    expanded = false
                    vm.playNext(item)
                }
                MenuRow("Add to queue", Icons.Filled.Add) {
                    expanded = false
                    vm.addToQueue(item)
                }
            } else {
                MenuRow("Move to…", Icons.Filled.SwapHoriz) {
                    expanded = false
                    moving = true
                }
                MenuRow("Convert to audio", Icons.Filled.Audiotrack) {
                    expanded = false
                    vm.askConvert(item)
                }
            }

            MenuRow("Share", Icons.Filled.Share) {
                expanded = false
                shareFile(context, item)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline
            )

            MenuRow("Rename", Icons.Filled.Edit) {
                expanded = false
                renaming = true
            }
            MenuRow(
                text = "Delete",
                icon = Icons.Filled.Delete,
                textColor = MaterialTheme.colorScheme.error,
                iconColor = MaterialTheme.colorScheme.error
            ) {
                expanded = false
                vm.delete(item)
            }
        }
    }

    if (renaming) RenameDialog(item, vm) { renaming = false }
    if (moving) {
        MoveDialog(
            item = item,
            onPick = { kind ->
                vm.setKind(item, kind)
                moving = false
            },
            onDismiss = { moving = false }
        )
    }
}

@Composable
private fun MenuRow(
    text: String,
    icon: ImageVector,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = Ember,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = textColor) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = iconColor) },
        onClick = onClick
    )
}

/** Fix a wrong guess: choose what a video really is. The choice is kept across rescans. */
@Composable
private fun MoveDialog(item: MediaItemEntity, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        MediaClassifier.VIDEO to "Music videos",
        MediaClassifier.MOVIE to "Movies",
        MediaClassifier.SHOW to "TV shows",
        MediaClassifier.SHORT to "Shorts"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Move to", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column {
                options.forEach { (kind, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(kind) }
                            .padding(horizontal = 4.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            color = if (kind == item.kind) Ember else Color.Unspecified
                        )
                        if (kind == item.kind) {
                            Icon(Icons.Filled.Check, contentDescription = "Current", tint = Ember)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RenameDialog(item: MediaItemEntity, vm: MusicViewModel, onDismiss: () -> Unit) {
    // The real file name, loaded from the phone
    val fileName by produceState<String?>(null, item.uri) { value = vm.fileName(item.uri) }
    val extension = fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
    val baseName = fileName?.substringBeforeLast('.') ?: ""
    var text by remember(fileName) { mutableStateOf(baseName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Rename", style = MaterialTheme.typography.headlineMedium) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                enabled = fileName != null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                label = { Text("File name") },
                suffix = { if (extension != null) Text(".$extension") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ember,
                    focusedLabelColor = Ember,
                    cursorColor = Ember
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = fileName != null && text.isNotBlank() && text != baseName,
                onClick = {
                    vm.rename(item, text)
                    onDismiss()
                }
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}