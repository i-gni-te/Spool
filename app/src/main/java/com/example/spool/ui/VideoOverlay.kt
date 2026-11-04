package com.example.spool.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import com.example.spool.player.PlayerState
import com.example.spool.ui.theme.Ember
import kotlinx.coroutines.delay
import com.example.spool.player.LoopMode
import kotlin.time.Duration.Companion.milliseconds

fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Hides the status and navigation bars while enabled (swipe from an edge to peek at them). */
@Composable
fun ImmersiveMode(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Full-screen video player: black background, video centred, controls floating on top. */
@Composable
fun VideoPlayerScreen(
    player: Player,
    state: PlayerState,
    zoom: Boolean,
    loop: LoopMode,
    onCycleLoop: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onToggleZoom: () -> Unit,
    onToggleOrientation: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(player, zoom, Modifier.fillMaxSize())
        VideoOverlay(
            player = player,
            state = state,
            zoom = zoom,
            loop = loop,
            onCycleLoop = onCycleLoop,
            onPrevious = onPrevious,
            onToggle = onToggle,
            onNext = onNext,
            onToggleZoom = onToggleZoom,
            onToggleOrientation = onToggleOrientation,
            onClose = onClose,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun VideoOverlay(
    player: Player,
    state: PlayerState,
    zoom: Boolean,
    loop: LoopMode,
    onCycleLoop: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onToggleZoom: () -> Unit,
    onToggleOrientation: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }

    // Auto-hide 3 seconds after the last touch, but only while playing
    LaunchedEffect(visible, state.isPlaying, touches) {
        if (visible && state.isPlaying) {
            delay(3_000.milliseconds)
            visible = false
        }
    }

    // Wraps a button action so every press restarts the auto-hide timer
    fun act(block: () -> Unit): () -> Unit = {
        touches++
        block()
    }

    Box(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { visible = !visible }
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .safeDrawingPadding()
            ) {
                // Top row: minimize, title and artist, fit/fill, rotate
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = act(onClose)) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Minimize",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            state.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            state.artist,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LoopButton(mode = loop, onClick = act(onCycleLoop))
                    IconButton(onClick = act(onToggleZoom)) {
                        Icon(
                            if (zoom) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = if (zoom) "Fit to screen" else "Fill screen",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = act(onToggleOrientation)) {
                        Icon(
                            Icons.Filled.ScreenRotation,
                            contentDescription = "Switch portrait or landscape",
                            tint = Color.White
                        )
                    }
                }

                // Center row: previous, play/pause, next
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = act(onPrevious), modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Filled.SkipPrevious, "Previous",
                            Modifier.size(36.dp), tint = Color.White
                        )
                    }
                    IconButton(onClick = act(onToggle), modifier = Modifier.size(72.dp)) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (state.isPlaying) "Pause" else "Play",
                            Modifier.size(56.dp), tint = Color.White
                        )
                    }
                    IconButton(onClick = act(onNext), modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Filled.SkipNext, "Next",
                            Modifier.size(36.dp), tint = Color.White
                        )
                    }
                }

                // Bottom: seek bar with times
                SeekBar(
                    player = player,
                    onTouch = { touches++ },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SeekBar(player: Player, onTouch: () -> Unit, modifier: Modifier = Modifier) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player) {
        while (true) {
            if (!dragging) {
                position = player.currentPosition
                duration = player.duration.coerceAtLeast(0L) // unknown duration is negative
            }
            delay(300.milliseconds)
        }
    }

    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownPosition = if (dragging) (dragFraction * duration).toLong() else position

    Column(modifier) {
        Slider(
            value = if (dragging) dragFraction else fraction,
            onValueChange = {
                dragging = true
                dragFraction = it
                onTouch()
            },
            onValueChangeFinished = {
                player.seekTo((dragFraction * duration).toLong())
                position = (dragFraction * duration).toLong()
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Ember,
                activeTrackColor = Ember,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(formatTime(shownPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes % 60, totalSeconds % 60)
    else "%d:%02d".format(minutes, totalSeconds % 60)
}