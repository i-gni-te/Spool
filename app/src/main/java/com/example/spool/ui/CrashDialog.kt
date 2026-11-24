package com.example.spool.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun CrashDialog(onSend: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Cue crashed last time", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Text(
                "Sorry about that. Sending the report helps get it fixed. It contains your " +
                        "phone model, the app version and technical details of the error.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = { TextButton(onClick = onSend) { Text("Send report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}