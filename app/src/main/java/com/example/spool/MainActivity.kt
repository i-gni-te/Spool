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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.example.spool.ui.ImmersiveMode
import com.example.spool.ui.MusicViewModel
import com.example.spool.ui.NowPlayingBar
import com.example.spool.ui.PermissionScreen
import com.example.spool.ui.SearchScreen
import com.example.spool.ui.VideoOverlay
import com.example.spool.ui.VideoSurface
import com.example.spool.ui.findActivity
import com.example.spool.ui.theme.SpoolTheme

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
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    val video: Player? = if (playerState.hasVideo) videoPlayer else null
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalContext.current.findActivity()
    var zoom by remember { mutableStateOf(false) }

    // Landscape video goes fully immersive: no status or navigation bars
    ImmersiveMode(enabled = video != null && landscape)

    // Once the video is gone, go back to following the phone's rotation
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

    if (video != null && landscape) {
        // Landscape video: the whole screen is the video, with floating controls
        Box(Modifier.fillMaxSize()) {
            VideoSurface(video, zoom, Modifier.fillMaxSize())
            VideoOverlay(
                state = playerState,
                zoom = zoom,
                fullscreen = true,
                onPrevious = vm::previous,
                onToggle = vm::togglePlayPause,
                onNext = vm::next,
                onToggleZoom = { zoom = !zoom },
                onToggleOrientation = toggleOrientation,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Scaffold(
            bottomBar = {
                if (video == null) {
                    NowPlayingBar(
                        state = playerState,
                        onPrevious = vm::previous,
                        onToggle = vm::togglePlayPause,
                        onNext = vm::next
                    )
                }
            }
        ) { padding ->
            if (video != null) {
                // Portrait video: video on top with floating controls, filter list below
                Column(Modifier.padding(padding)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                        VideoSurface(video, zoom, Modifier.fillMaxSize())
                        VideoOverlay(
                            state = playerState,
                            zoom = zoom,
                            fullscreen = false,
                            onPrevious = vm::previous,
                            onToggle = vm::togglePlayPause,
                            onNext = vm::next,
                            onToggleZoom = { zoom = !zoom },
                            onToggleOrientation = toggleOrientation,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    SearchScreen(vm, Modifier.weight(1f))
                }
            } else {
                SearchScreen(vm, Modifier.padding(padding))
            }
        }
    }
}