package com.example.spool.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Logo, screen title, a count, and an optional "report a problem" button. */
@Composable
fun ScreenHeader(
    title: String,
    count: String,
    modifier: Modifier = Modifier,
    onReport: (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogoMark(markSize = 28.dp)
        Spacer(Modifier.size(8.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.weight(1f))
        Text(
            count,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onReport != null) {
            IconButton(onClick = onReport) {
                Icon(
                    Icons.Filled.BugReport,
                    contentDescription = "Report a problem",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}