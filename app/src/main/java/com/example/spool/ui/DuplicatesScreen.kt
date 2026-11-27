package com.example.spool.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spool.data.MediaItemEntity
import com.example.spool.domain.DuplicateGroup
import com.example.spool.domain.DuplicateRules
import com.example.spool.ui.theme.Ember
import com.example.spool.ui.theme.Ink
import java.text.DateFormat
import java.util.Date

@Composable
fun DuplicatesScreen(vm: MusicViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.duplicates.collectAsState()
    val leave = {
        vm.resetDuplicates()
        onBack()
    }
    BackHandler { leave() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = leave) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Duplicates", style = MaterialTheme.typography.headlineMedium)
        }

        when (val s = state) {
            DuplicateState.Idle -> IdleView(onScan = vm::scanDuplicates)
            is DuplicateState.Scanning -> ScanningView(s.done, s.total)
            is DuplicateState.Done -> ResultView(s.groups, vm)
        }
    }
}

@Composable
private fun IdleView(onScan: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.ContentCopy,
            contentDescription = null,
            tint = Ember,
            modifier = Modifier.size(56.dp)
        )
        Text(
            "Find duplicate files",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            "Cue looks for songs and videos stored more than once, such as the same file in " +
                    "Downloads and in WhatsApp. It compares size, length and the file's content. " +
                    "Nothing leaves your phone, and nothing is deleted until you choose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        Button(
            onClick = onScan,
            colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Ink)
        ) { Text("Scan for duplicates") }
    }
}

@Composable
private fun ScanningView(done: Int, total: Int) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Looking for duplicates…", style = MaterialTheme.typography.titleLarge)
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                color = Ember,
                trackColor = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
            Text(
                "Comparing $done of $total files",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LinearProgressIndicator(
                color = Ember,
                trackColor = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
            Text(
                "Checking file sizes…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultView(groups: List<DuplicateGroup>, vm: MusicViewModel) {
    val context = LocalContext.current

    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Text(
                "No duplicates found. Your library is tidy.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Start with the best copy of each group kept, and the others ticked for deletion
    val selected = remember(groups) {
        mutableStateMapOf<String, Boolean>().also { map ->
            groups.forEach { group ->
                val keep = DuplicateRules.suggestKeep(group.copies)
                group.copies.forEach { map[it.uri] = it.uri != keep.uri }
            }
        }
    }

    fun toggle(group: DuplicateGroup, copy: MediaItemEntity) {
        val ticked = selected[copy.uri] == true
        if (!ticked) {
            val stillKept = group.copies.count { it.uri != copy.uri && selected[it.uri] != true }
            if (stillKept == 0) {
                Toast.makeText(context, "Keep at least one copy", Toast.LENGTH_SHORT).show()
                return
            }
        }
        selected[copy.uri] = !ticked
    }

    val chosen = groups.flatMap { g -> g.copies.filter { selected[it.uri] == true } }
    val freed = groups.sumOf { g -> g.copies.count { selected[it.uri] == true } * g.sizeBytes }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "${groups.size} sets of duplicates · up to " +
                            "${formatBytes(groups.sumOf { it.wastedBytes })} can be freed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(groups, key = { it.copies.first().uri }) { group ->
                GroupCard(group, selected) { copy -> toggle(group, copy) }
            }
        }

        // Delete bar
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { vm.trashDuplicates(chosen) },
                enabled = chosen.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Ink
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete ${chosen.size} selected · ${formatBytes(freed)}")
            }
            Text(
                "They go to the trash, and you can restore them for about 30 days.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: DuplicateGroup,
    selected: Map<String, Boolean>,
    onToggle: (MediaItemEntity) -> Unit
) {
    val first = group.copies.first()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            first.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${group.copies.size} copies · ${formatBytes(group.sizeBytes)} each",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        group.copies.forEach { copy ->
            val ticked = selected[copy.uri] == true
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggle(copy) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(
                    checked = ticked,
                    onCheckedChange = { onToggle(copy) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.error,
                        checkmarkColor = Ink
                    )
                )
                MediaThumb(
                    uri = copy.uri,
                    seed = copy.title,
                    isVideo = copy.isVideo,
                    modifier = Modifier.size(40.dp),
                    corner = 8.dp,
                    sizePx = 120
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        copy.folder.trimEnd('/').ifBlank { "Unknown folder" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (copy.addedAt > 0) {
                            "Added " + DateFormat.getDateInstance().format(Date(copy.addedAt))
                        } else {
                            "Date unknown"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (ticked) "Delete" else "Keep",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ticked) MaterialTheme.colorScheme.error else Ember
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%d KB".format(bytes / 1024)
}