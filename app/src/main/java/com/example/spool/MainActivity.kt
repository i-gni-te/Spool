package com.example.spool

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.example.spool.ui.Destination
import com.example.spool.ui.HomeScreen
import com.example.spool.ui.ImmersiveMode
import com.example.spool.ui.MiniPlayer
import com.example.spool.ui.MusicViewModel
import com.example.spool.ui.NowPlayingScreen
import com.example.spool.ui.PermissionScreen
import com.example.spool.ui.SongsScreen
import com.example.spool.ui.SpoolNavBar
import com.example.spool.ui.VideoPlayerScreen
import com.example.spool.ui.VideosScreen
import com.example.spool.ui.findActivity
import com.example.spool.ui.theme.SpoolTheme
import com.example.spool.ui.theme.spoolBackground

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()
    private var hasAccess by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshAccess()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        hasAccess = checkAccess()
        setContent {
            SpoolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().spoolBackground(),
                    color = ComposeColor.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    SpoolRoot(
                        vm = viewModel,
                        hasAccess = hasAccess,
                        onRequestAccess = ::requestAccess,
                        onOpenSettings = ::openSettings
                    )
                }
            }
        }
    }

    // Also fires when returning from system settings, and rescans on every app open
    override fun onResume() {
        super.onResume()
        refreshAccess()
    }

    private fun checkAccess(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun refreshAccess() {
        hasAccess = checkAccess()
        if (hasAccess) viewModel.rescan()
    }

    private fun requestAccess() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    private fun openSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }
}

@Composable
fun SpoolRoot(
    vm: MusicViewModel,
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit
) {
    if (!hasAccess) {
        PermissionScreen(onAllow = onRequestAccess, onOpenSettings = onOpenSettings)
        return
    }

    val playerState by vm.playerState.collectAsState()
    val videoPlayer by vm.videoPlayer.collectAsState()
    val loop by vm.loop.collectAsState()
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalContext.current.findActivity()

    var destination by rememberSaveable { mutableStateOf(Destination.AUTO) }
    var videoOpen by rememberSaveable { mutableStateOf(false) }
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }

    // A video the user just started opens the full-screen video player
    LaunchedEffect(Unit) { vm.openVideoEvents.collect { videoOpen = true } }

    // Each full-screen player only exists for its own kind of item
    LaunchedEffect(playerState.hasVideo) {
        if (playerState.hasVideo) nowPlayingOpen = false else videoOpen = false
    }

    val video: Player? = if (videoOpen && playerState.hasVideo) videoPlayer else null
    val nowPlaying = nowPlayingOpen && !playerState.hasVideo && playerState.title.isNotBlank()

    // Back minimizes: sound keeps going, the mini-player appears
    BackHandler(enabled = video != null) { videoOpen = false }
    BackHandler(enabled = nowPlaying) { nowPlayingOpen = false }

    // Landscape video hides the system bars
    ImmersiveMode(enabled = video != null && landscape)

    // Once the video player closes, go back to following the phone's rotation
    LaunchedEffect(video != null) {
        if (video == null) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val toggleOrientation: () -> Unit = {
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    when {
        video != null -> VideoPlayerScreen(
            player = video,
            state = playerState,
            zoom = zoom,
            loop = loop,
            onCycleLoop = vm::cycleLoop,
            onPrevious = vm::previous,
            onToggle = vm::togglePlayPause,
            onNext = vm::next,
            onToggleZoom = { zoom = !zoom },
            onToggleOrientation = toggleOrientation,
            onClose = { videoOpen = false }
        )

        nowPlaying -> NowPlayingScreen(
            player = videoPlayer,
            state = playerState,
            loop = loop,
            onCycleLoop = vm::cycleLoop,
            onPrevious = vm::previous,
            onToggle = vm::togglePlayPause,
            onNext = vm::next,
            onClose = { nowPlayingOpen = false }
        )

        else -> Scaffold(
            containerColor = ComposeColor.Transparent,
            bottomBar = {
                Column {
                    MiniPlayer(
                        state = playerState,
                        onPrevious = vm::previous,
                        onToggle = vm::togglePlayPause,
                        onNext = vm::next,
                        onOpen = {
                            if (playerState.hasVideo) videoOpen = true else nowPlayingOpen = true
                        }
                    )
                    SpoolNavBar(selected = destination, onSelect = { destination = it })
                }
            }
        ) { padding ->
            val content = Modifier.padding(padding)
            when (destination) {
                Destination.AUTO -> HomeScreen(vm, content)
                Destination.SONGS -> SongsScreen(vm, content)
                Destination.VIDEOS -> VideosScreen(vm, content)
            }
        }
    }
}