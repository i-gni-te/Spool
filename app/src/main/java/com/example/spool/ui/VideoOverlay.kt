package com.example.spool.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import com.example.spool.data.Prefs
import com.example.spool.player.LoopMode
import com.example.spool.player.PlayerState
import com.example.spool.ui.theme.Ember
import com.example.spool.ui.theme.SpoolGradient
import kotlinx.coroutines.delay

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun backIcon(seconds: Int) = when (seconds) {
    5 -> Icons.Filled.Replay5
    30 -> Icons.Filled.Replay30
    else -> Icons.Filled.Replay10
}

private fun forwardIcon(seconds: Int) = when (seconds) {
    5 -> Icons.Filled.Forward5
    30 -> Icons.Filled.Forward30
    else -> Icons.Filled.Forward10
}

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
    swipeGestures: Boolean,
    state: PlayerState,
    zoom: Boolean,
    loop: LoopMode,
    locked: Boolean,
    pip: Boolean,
    skipSeconds: Int,
    doubleTapSkip: Boolean,
    onLockedChange: (Boolean) -> Unit,
    onCycleLoop: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onToggleZoom: () -> Unit,
    onToggleOrientation: () -> Unit,
    onEnterPip: () -> Unit,
    onSubtitleFile: (Uri) -> Unit,
    onClose: () -> Unit
) {
    // Playback speed belongs to the video: songs always play at normal speed
    DisposableEffect(player) {
        onDispose { player.setPlaybackSpeed(1f) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(player, zoom, Modifier.fillMaxSize())
        // In the small picture-in-picture window there are no controls to show
        if (!pip) {
            VideoOverlay(
                player = player,
                state = state,
                zoom = zoom,
                loop = loop,
                locked = locked,
                skipSeconds = skipSeconds,
                doubleTapSkip = doubleTapSkip,
                swipeGestures = swipeGestures,
                onLockedChange = onLockedChange,
                onCycleLoop = onCycleLoop,
                onPrevious = onPrevious,
                onToggle = onToggle,
                onNext = onNext,
                onToggleZoom = onToggleZoom,
                onToggleOrientation = onToggleOrientation,
                onEnterPip = onEnterPip,
                onSubtitleFile = onSubtitleFile,
                onClose = onClose,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun VideoOverlay(
    player: Player,
    state: PlayerState,
    zoom: Boolean,
    loop: LoopMode,
    locked: Boolean,
    skipSeconds: Int,
    doubleTapSkip: Boolean,
    swipeGestures: Boolean,
    onLockedChange: (Boolean) -> Unit,
    onCycleLoop: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onToggleZoom: () -> Unit,
    onToggleOrientation: () -> Unit,
    onEnterPip: () -> Unit,
    onSubtitleFile: (Uri) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adjuster = rememberSwipeAdjuster()
    val context = LocalContext.current
    var showTracks by remember { mutableStateOf(false) }
    val subtitleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) {
            // Keep access to the file so it still works next time
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onSubtitleFile(picked)
        }
    }

    var visible by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var flash by remember { mutableIntStateOf(0) } // -1 skipped back, 1 skipped forward
    var flashTick by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }

    // Auto-hide 3 seconds after the last touch, but only while playing
    LaunchedEffect(visible, state.isPlaying, touches, locked) {
        if (visible && state.isPlaying) {
            delay(3_000)
            visible = false
        }
    }
    // The skip flash disappears by itself
    LaunchedEffect(flashTick) {
        if (flashTick > 0) {
            delay(600)
            flash = 0
        }
    }

    // Wraps a button action so every press restarts the auto-hide timer
    fun act(block: () -> Unit): () -> Unit = {
        touches++
        block()
    }

    fun skip(direction: Int) {
        val skipMs = skipSeconds * 1000L
        val duration = player.duration
        val limit = if (duration > 0) duration else Long.MAX_VALUE
        player.seekTo((player.currentPosition + direction * skipMs).coerceIn(0L, limit))
        flash = direction
        flashTick++
    }

    Box(
        modifier.pointerInput(locked, doubleTapSkip, swipeGestures, skipSeconds) {
            // One handler tells taps, double-taps and swipes apart, so they can't fight
            detectVideoGestures(
                swipeEnabled = swipeGestures && !locked,
                doubleTapEnabled = doubleTapSkip && !locked,
                onTap = { visible = !visible },
                onDoubleTap = { leftSide -> skip(if (leftSide) -1 else 1) },
                onSwipeStart = { leftSide ->
                    adjuster.begin(if (leftSide) AdjustKind.BRIGHTNESS else AdjustKind.VOLUME)
                },
                onSwipe = { delta -> adjuster.change(delta) },
                onSwipeEnd = { adjuster.finish() }
            )
        }
    ) {
        // Skip feedback
        if (flash != 0 && !locked) {
            // The brightness or volume level, shown while you swipe
            SwipeIndicator(adjuster)
            Box(
                Modifier
                    .align(if (flash < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(18.dp)
            ) {
                Icon(
                    if (flash < 0) backIcon(skipSeconds) else forwardIcon(skipSeconds),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
        }

        if (locked) {
            // Locked: only an unlock button, and only after a tap on the screen
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(
                        Modifier
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onLockedChange(false) }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null, tint = Color.White)
                        Text(
                            "Tap to unlock",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        } else {
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
                    // Top: minimize, the title, then subtitles and picture-in-picture
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
                        IconButton(onClick = act { showTracks = true }) {
                            Icon(
                                Icons.Filled.Subtitles,
                                contentDescription = "Subtitles and audio",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = act(onEnterPip)) {
                            Icon(
                                Icons.Filled.PictureInPictureAlt,
                                contentDescription = "Picture in picture",
                                tint = Color.White
                            )
                        }
                    }

                    // Middle: previous, skip back, play/pause, skip forward, next
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = act(onPrevious), modifier = Modifier.size(44.dp)) {
                            Icon(
                                Icons.Filled.SkipPrevious, "Previous",
                                Modifier.size(28.dp), tint = Color.White
                            )
                        }
                        IconButton(onClick = act { skip(-1) }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                backIcon(skipSeconds), "Back $skipSeconds seconds",
                                Modifier.size(32.dp), tint = Color.White
                            )
                        }
                        IconButton(onClick = act(onToggle), modifier = Modifier.size(68.dp)) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.isPlaying) "Pause" else "Play",
                                Modifier.size(52.dp), tint = Color.White
                            )
                        }
                        IconButton(onClick = act { skip(1) }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                forwardIcon(skipSeconds), "Forward $skipSeconds seconds",
                                Modifier.size(32.dp), tint = Color.White
                            )
                        }
                        IconButton(onClick = act(onNext), modifier = Modifier.size(44.dp)) {
                            Icon(
                                Icons.Filled.SkipNext, "Next",
                                Modifier.size(28.dp), tint = Color.White
                            )
                        }
                    }

                    // Bottom: time bar, then lock, mode, speed, fit/fill, rotate
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SeekBar(
                            player = player,
                            onTouch = { touches++ },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = act { onLockedChange(true) }) {
                                Icon(Icons.Filled.Lock, "Lock controls", tint = Color.White)
                            }
                            LoopButton(
                                mode = loop,
                                onClick = act(onCycleLoop),
                                idleTint = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                text = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x",
                                color = if (speed == 1f) Color.White.copy(alpha = 0.6f) else Ember,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        touches++
                                        val next = SPEEDS[(SPEEDS.indexOf(speed) + 1) % SPEEDS.size]
                                        player.setPlaybackSpeed(next)
                                        speed = next
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            )
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
                    }
                }
            }
        }
    }

    if (showTracks) {
        TracksDialog(
            player = player,
            onAddSubtitleFile = { subtitleLauncher.launch(arrayOf("*/*")) },
            onDismiss = { showTracks = false }
        )
    }
}

/**
 * The time bar: a gradient track you can tap or drag, the time played on the left,
 * and on the right the time left (tap it to switch to the total length).
 */
@Composable
fun SeekBar(
    player: Player,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White
) {
    val context = LocalContext.current
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var showRemaining by remember { mutableStateOf(Prefs.timeShowsRemaining(context)) }

    LaunchedEffect(player) {
        while (true) {
            if (!dragging) {
                position = player.currentPosition
                duration = player.duration.coerceAtLeast(0L) // unknown duration is negative
            }
            delay(250)
        }
    }

    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownFraction = if (dragging) dragFraction else fraction
    val shownPosition = if (dragging) (dragFraction * duration).toLong() else position
    val remaining = (duration - shownPosition).coerceAtLeast(0L)

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 8.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (duration > 0) {
                            val target = ((offset.x / widthPx).coerceIn(0f, 1f) * duration).toLong()
                            player.seekTo(target)
                            position = target
                            onTouch()
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                            onTouch()
                        },
                        onDragEnd = {
                            if (duration > 0) {
                                val target = (dragFraction * duration).toLong()
                                player.seekTo(target)
                                position = target
                            }
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                            onTouch()
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val trackHeight = 6.dp.toPx()
                val radius = trackHeight / 2f
                val centerY = size.height / 2f
                val top = centerY - radius

                drawRoundRect(
                    color = contentColor.copy(alpha = 0.25f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(radius)
                )
                drawRoundRect(
                    brush = SpoolGradient,
                    topLeft = Offset(0f, top),
                    size = Size((size.width * shownFraction).coerceAtLeast(trackHeight), trackHeight),
                    cornerRadius = CornerRadius(radius)
                )
                val thumbCenter = Offset(size.width * shownFraction, centerY)
                val thumbRadius = (if (dragging) 11.dp else 8.dp).toPx()
                drawCircle(Color.White, thumbRadius, thumbCenter)
                drawCircle(Ember, thumbRadius, thumbCenter, style = Stroke(width = 2.dp.toPx()))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                formatDuration(shownPosition),
                color = contentColor,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    duration <= 0 -> "--:--"
                    showRemaining -> "-" + formatDuration(remaining)
                    else -> formatDuration(duration)
                },
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        showRemaining = !showRemaining
                        Prefs.setTimeShowsRemaining(context, showRemaining)
                        onTouch()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}