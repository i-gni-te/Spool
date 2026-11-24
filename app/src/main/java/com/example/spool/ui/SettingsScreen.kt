package com.example.spool.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.spool.data.AppSettings
import com.example.spool.data.Prefs
import com.example.spool.ui.theme.Ember
import com.example.spool.ui.theme.Ink

// Change this to your own repository's releases page if it differs
private const val RELEASES_URL = "https://github.com/snakedok67-ux/Spool/releases"

private val LICENCES = listOf(
    "AndroidX libraries and Jetpack Compose" to "Apache License 2.0",
    "Media3 (ExoPlayer)" to "Apache License 2.0",
    "Room" to "Apache License 2.0",
    "Kotlin and kotlinx.coroutines" to "Apache License 2.0",
    "Material icons" to "Apache License 2.0",
    "Space Grotesk and DM Sans fonts" to "SIL Open Font License 1.1"
)

@Composable
fun SettingsScreen(vm: MusicViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val shortsFill by vm.shortsFill.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val songs by vm.songs.collectAsState()
    val total by vm.libraryCount.collectAsState()
    var timeRemaining by remember { mutableStateOf(Prefs.timeShowsRemaining(context)) }
    var showLicences by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ScreenHeader("Settings", appVersionLabel(context)) }

        item { SectionTitle("Playback") }
        item {
            ChoiceRow(
                title = "Skip length",
                options = AppSettings.SKIP_CHOICES.map { it to "$it s" },
                selected = settings.skipSeconds
            ) { v -> vm.updateSettings { it.copy(skipSeconds = v) } }
        }
        item {
            SwitchRow(
                "Double-tap to skip",
                "Double-tap the left or right side of a video",
                settings.doubleTapSkip
            ) { v -> vm.updateSettings { it.copy(doubleTapSkip = v) } }
        }
        item {
            SwitchRow(
                "Keep audio playing when I leave a video",
                "Off: leaving a video pauses it",
                settings.keepAudioOnExit
            ) { v -> vm.updateSettings { it.copy(keepAudioOnExit = v) } }
        }
        item {
            SwitchRow(
                "Resume movies and TV shows",
                "Pick up where you stopped",
                settings.resumeWhereLeftOff
            ) { v -> vm.updateSettings { it.copy(resumeWhereLeftOff = v) } }
        }
        item {
            SwitchRow(
                "Show time left",
                "On: the right-hand time counts down. Off: it shows the total length",
                timeRemaining
            ) { v ->
                timeRemaining = v
                Prefs.setTimeShowsRemaining(context, v)
            }
        }
        item {
            SwitchRow(
                "Fill the screen in Shorts",
                "Crops vertical videos to fill. Off keeps the whole picture",
                shortsFill
            ) { vm.toggleShortsFill() }
        }
        item { ActionRow("Equalizer", "Tone controls and bass boost") { showEq = true } }

        item { SectionTitle("Library") }
        item {
            SwitchRow(
                "Show the New section",
                "New downloads you haven't played yet",
                settings.showNew
            ) { v -> vm.updateSettings { it.copy(showNew = v) } }
        }
        item {
            ChoiceRow(
                title = "Counts as new for",
                options = AppSettings.NEW_DAY_CHOICES.map { days ->
                    days to when (days) {
                        3 -> "3 days"
                        7 -> "1 week"
                        14 -> "2 weeks"
                        else -> "1 month"
                    }
                },
                selected = settings.newDays
            ) { v -> vm.updateSettings { it.copy(newDays = v) } }
        }
        item {
            ActionRow(
                "Scan library now",
                if (scanning) "Scanning…" else "Look for new or removed files"
            ) { if (!scanning) vm.rescan() }
        }

        item { SectionTitle("Appearance") }
        item {
            SwitchRow(
                "Animated background",
                "A slow drifting glow. Turn it off to save a little battery",
                settings.animatedBackground
            ) { v -> vm.updateSettings { it.copy(animatedBackground = v) } }
        }

        item { SectionTitle("Help") }
        item {
            ActionRow(
                "Report a problem",
                "Shares a report with your phone model and recent log"
            ) { shareReport(context, songs.size, (total - songs.size).coerceAtLeast(0)) }
        }
        item {
            ActionRow("Check for updates", "Opens the Cue releases page") {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        item { SectionTitle("About") }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Cue", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cue plays the music and videos already on your phone. It has no ads and " +
                            "doesn't upload your files or what you listen to. Voice search uses your " +
                            "phone's speech recognition and only listens while its screen is open.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Version ${appVersionLabel(context)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { ActionRow("Open-source licences", "What Cue is built with") { showLicences = true } }
    }
    if (showEq) EqualizerSheet { showEq = false }

    if (showLicences) {
        AlertDialog(
            onDismissRequest = { showLicences = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Open-source licences", style = MaterialTheme.typography.headlineMedium) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LICENCES.forEach { (name, licence) ->
                        Column {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                licence,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLicences = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = Ember,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Ember, checkedThumbColor = Ink)
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options, key = { it.second }) { (value, label) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Ember.copy(alpha = 0.18f),
                        selectedLabelColor = Ember
                    )
                )
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}