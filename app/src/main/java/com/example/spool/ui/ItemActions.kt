package com.example.spool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.spool.data.MediaItemEntity

/** A ⋮ button with Rename and Delete. Delete moves the file to the trash. */
@Composable
fun ItemMenuButton(
    item: MediaItemEntity,
    vm: MusicViewModel,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    compact: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    renaming = true
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    vm.delete(item)
                }
            )
        }
    }

    if (renaming) RenameDialog(item, vm) { renaming = false }
}

@Composable
private fun RenameDialog(item: MediaItemEntity, vm: MusicViewModel, onDismiss: () -> Unit) {
    // The real file name, loaded from the phone
    val fileName by produceState<String?>(null, item.uri) { value = vm.fileName(item.uri) }
    val extension = fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
    val baseName = fileName?.substringBeforeLast('.') ?: ""
    var text by remember(fileName) { mutableStateOf(baseName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                enabled = fileName != null,
                singleLine = true,
                label = { Text("File name") },
                suffix = { if (extension != null) Text(".$extension") }
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