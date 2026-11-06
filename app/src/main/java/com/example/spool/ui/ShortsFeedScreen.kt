package com.example.spool.ui

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.spool.R
import com.example.spool.data.MediaItemEntity
import com.example.spool.data.MediaScanner
import com.example.spool.player.PlayerState
import kotlinx.coroutines.delay

/** The feed repeats the list this many times so swiping up never runs out. */
private const val LOOPS = 1000

@Composable
fun ShortsFeedScreen(
    feed: List<MediaItemEntity>,
    startUri: String,
    player: Player?,
    state: PlayerState,
    fill: Boolean,
    vm: MusicViewModel,
    onToggleFill: () -> Unit,
    onTogglePlay: () -> Unit,
    onPlayItem: (String) -> Unit,
    onClose: () -> Unit
) {
    if (feed.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val size = feed.size
    val startIndex = feed.indexOfFirst { it.uri == startUri }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = if (size > 1) size * (LOOPS / 2) + startIndex else 0,
        pageCount = { if (size > 1) size * LOOPS else 1 }
    )

    // Whenever a page settles, that short plays
    val currentOnPlay by rememberUpdatedState(onPlayItem)
    LaunchedEffect(pagerState, feed) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentOnPlay(feed[page % size].uri)
        }
    }

    val current = feed[pagerState.settledPage % size]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = feed[page % size]
            ShortPage(
                item = item,
                player = player,
                fill = fill,
                // Show the live video only once the player has switched to this short
                showVideo = player != null && pagerState.settledPage == page && state.uri == item.uri,
                isPlaying = state.isPlaying,
                onTogglePlay = onTogglePlay
            )
        }

        // Soft gradient behind the caption
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))
                )
        )

        // Top bar: minimize on the left; fit/fill and the menu on the right
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Minimize",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleFill) {
                Icon(
                    if (fill) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (fill) "Fit to screen" else "Fill screen",
                    tint = Color.White
                )
            }
            ItemMenuButton(item = current, vm = vm, tint = Color.White)
        }

        // Caption
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text(
                current.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (current.artist != MediaScanner.UNKNOWN_ARTIST) {
                Text(
                    current.artist,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        ThinProgress(player, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun ShortPage(
    item: MediaItemEntity,
    player: Player?,
    fill: Boolean,
    showVideo: Boolean,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTogglePlay() }
    ) {
        // The thumbnail shows until the video's first frame, and during swipes
        MediaThumb(
            uri = item.uri,
            seed = item.title,
            isVideo = true,
            modifier = Modifier.fillMaxSize(),
            corner = 0.dp,
            sizePx = 720,
            contentScale = if (fill) ContentScale.Crop else ContentScale.Fit
        )
        if (showVideo && player != null) {
            ShortSurface(player, fill, Modifier.fillMaxSize())
        }
        if (showVideo && !isPlaying) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play",
                modifier = Modifier.align(Alignment.Center).size(88.dp),
                tint = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

/** Fits the whole picture by default; in fill mode, vertical clips fill the screen. */
@OptIn(UnstableApi::class)
@Composable
private fun ShortSurface(videoPlayer: Player, fill: Boolean, modifier: Modifier = Modifier) {
    var portrait by remember {
        mutableStateOf(videoPlayer.videoSize.height >= videoPlayer.videoSize.width)
    }
    DisposableEffect(videoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0) portrait = videoSize.height >= videoSize.width
            }
        }
        videoPlayer.addListener(listener)
        onDispose { videoPlayer.removeListener(listener) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            (LayoutInflater.from(context).inflate(R.layout.short_player_view, null, false)
                    as PlayerView).apply { player = videoPlayer }
        },
        update = { view ->
            view.player = videoPlayer
            view.resizeMode =
                if (fill && portrait) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        onRelease = { it.player = null }
    )
}

@Composable
private fun ThinProgress(player: Player?, modifier: Modifier = Modifier) {
    var fraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (player != null) {
            val duration = player.duration
            fraction =
                if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                else 0f
            delay(200)
        }
    }
    Box(modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(Color.White))
    }
}