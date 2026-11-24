package com.example.spool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spool.player.AudioFx
import com.example.spool.ui.theme.Ember
import com.example.spool.ui.theme.Ink

private fun hzLabel(hz: Int): String =
    if (hz >= 1000) "%.1f kHz".format(hz / 1000f).replace(".0 ", " ") else "$hz Hz"

private fun dbLabel(millibels: Int): String = "%+.1f dB".format(millibels / 100f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by AudioFx.settings.collectAsState()
    val info by AudioFx.info.collectAsState()
    val unavailable by AudioFx.unavailable.collectAsState()
    val eqInfo = info

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Equalizer",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { on -> AudioFx.update(context) { it.copy(enabled = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Ember, checkedThumbColor = Ink)
                )
            }

            when {
                unavailable -> Text(
                    "This phone doesn't offer an equalizer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                eqInfo == null -> Text(
                    "Play a song or video once, then come back to tune the sound.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> {
                    if (eqInfo.presets.isNotEmpty()) {
                        Text(
                            "Presets",
                            style = MaterialTheme.typography.labelLarge,
                            color = Ember
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(eqInfo.presets) { index, name ->
                                FilterChip(
                                    selected = false,
                                    onClick = { AudioFx.presetRequests.tryEmit(index) },
                                    label = { Text(name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }

                    Text(
                        "Bands",
                        style = MaterialTheme.typography.labelLarge,
                        color = Ember,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    eqInfo.bandHz.forEachIndexed { i, hz ->
                        val level = settings.bandLevels.getOrElse(i) { 0 }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                hzLabel(hz),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(64.dp)
                            )
                            Slider(
                                value = level.toFloat(),
                                onValueChange = { value ->
                                    AudioFx.update(context) { s ->
                                        val levels = s.bandLevels.toMutableList()
                                        while (levels.size <= i) levels.add(0)
                                        levels[i] = value.toInt()
                                        s.copy(enabled = true, bandLevels = levels)
                                    }
                                },
                                valueRange = eqInfo.minLevel.toFloat()..eqInfo.maxLevel.toFloat(),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Ember,
                                    activeTrackColor = Ember,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            Text(
                                dbLabel(level),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(64.dp)
                            )
                        }
                    }

                    if (eqInfo.bassSupported) {
                        Text(
                            "Bass boost",
                            style = MaterialTheme.typography.labelLarge,
                            color = Ember,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = settings.bassStrength.toFloat(),
                                onValueChange = { value ->
                                    AudioFx.update(context) {
                                        it.copy(enabled = true, bassStrength = value.toInt())
                                    }
                                },
                                valueRange = 0f..1000f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Ember,
                                    activeTrackColor = Ember,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            Text(
                                "${settings.bassStrength / 10}%",
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(48.dp)
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            AudioFx.update(context) {
                                it.copy(bandLevels = List(it.bandLevels.size) { 0 }, bassStrength = 0)
                            }
                        }
                    ) { Text("Reset to flat") }
                }
            }
        }
    }
}