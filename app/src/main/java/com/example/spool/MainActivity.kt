package com.example.spool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.spool.ui.MusicViewModel
import com.example.spool.ui.NowPlayingBar
import com.example.spool.ui.PermissionScreen
import com.example.spool.ui.SearchScreen
import com.example.spool.ui.theme.SpoolTheme
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalConfiguration
import com.example.spool.ui.VideoSurface

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
    val vp = videoPlayer
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        bottomBar = {
            NowPlayingBar(
                state = playerState,
                onPrevious = vm::previous,
                onToggle = vm::togglePlayPause,
                onNext = vm::next
            )
        }
    ) { padding ->
        when {
            // Landscape video: fill the screen
            vp != null && playerState.hasVideo && landscape ->
                VideoSurface(vp, Modifier.padding(padding).fillMaxSize())

            // Portrait video: video on top, search below
            vp != null && playerState.hasVideo ->
                Column(Modifier.padding(padding)) {
                    VideoSurface(vp, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    SearchScreen(vm, Modifier.weight(1f))
                }

            // Audio: just the search screen
            else -> SearchScreen(vm, Modifier.padding(padding))
        }
    }
}
