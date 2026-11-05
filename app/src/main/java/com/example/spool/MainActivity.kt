package com.example.spool

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.example.spool.ui.Destination
import com.example.spool.ui.FileRequest
import com.example.spool.ui.HomeScreen
import com.example.spool.ui.ImmersiveMode
import com.example.spool.ui.MiniPlayer
import com.example.spool.ui.MusicViewModel
import com.example.spool.ui.NowPlayingScreen
import com.example.spool.ui.OnboardingScreen
import com.example.spool.ui.PermissionScreen
import com.example.spool.ui.RenameRequest
import com.example.spool.ui.ShortsFeedScreen
import com.example.spool.ui.SongsScreen
import com.example.spool.ui.SpoolNavBar
import com.example.spool.ui.TrashRequest
import com.example.spool.ui.VideoPlayerScreen
import com.example.spool.ui.VideosScreen
import com.example.spool.ui.findActivity
import com.example.spool.ui.theme.SpoolTheme
import com.example.spool.ui.theme.spoolBackground
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()
    private var hasAccess by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshAccess()
        }

    // Delete and rename need the user's OK through a system dialog
    private var pendingFileRequest: FileRequest? = null
    private val fileRequestLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val request = pendingFileRequest
            pendingFileRequest = null
            if (request != null) {
                viewModel.onFileRequestResult(request, result.resultCode == RESULT_OK)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen() // must come before super.onCreate
        super.onCreate(savedInstanceState)

        // Keep the splash up briefly, then fade and grow it away
        val shownAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - shownAt < 600 }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(260)
                .withEndAction { provider.remove() }
                .start()
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        hasAccess = checkAccess()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fileRequests.collect { handleFileRequest(it) }
            }
        }

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

    private fun handleFileRequest(request: FileRequest) {
        val uri = Uri.parse(request.item.uri)
        val pendingIntent = when (request) {
            // Moves the file to the trash, where it stays recoverable for about 30 days
            is TrashRequest -> MediaStore.createTrashRequest(contentResolver, listOf(uri), true)
            is RenameRequest -> MediaStore.createWriteRequest(contentResolver, listOf(uri))
        }
        pendingFileRequest = request
        fileRequestLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
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
    val onboardingDone by vm.onboardingDone.collectAsState()
    if (!onboardingDone) {
        OnboardingScreen(
            hasAccess = hasAccess,
            onAllow = onRequestAccess,
            onOpenSettings = onOpenSettings,
            onDone = vm::finishOnboarding
        )
        return
    }
    // Access taken away later (in system settings): ask again
    if (!hasAccess) {
        PermissionScreen(onAllow = onRequestAccess, onOpenSettings = onOpenSettings)
        return
    }

    val playerState by vm.playerState.collectAsState()
    val videoPlayer by vm.videoPlayer.collectAsState()
    val loop by vm.loop.collectAsState()
    val feed by vm.feed.collectAsState()
    val feedStart by vm.feedStart.collectAsState()
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalContext.current.findActivity()

    var destination by rememberSaveable { mutableStateOf(Destination.AUTO) }
    var videoOpen by rememberSaveable { mutableStateOf(false) }
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var shortsOpen by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }

    // A video the user just started opens the full-screen video player
    LaunchedEffect(Unit) { vm.openVideoEvents.collect { videoOpen = true } }
    // A short opens the swipe feed
    LaunchedEffect(Unit) { vm.openShortsEvents.collect { shortsOpen = true } }

    // Each full-screen player only exists for its own kind of item
    LaunchedEffect(playerState.hasVideo) {
        if (playerState.hasVideo) nowPlayingOpen = false else videoOpen = false
    }

    val video: Player? =
        if (videoOpen && playerState.hasVideo && !playerState.isShort) videoPlayer else null
    val nowPlaying = nowPlayingOpen && !playerState.hasVideo && playerState.title.isNotBlank()

    // Back minimizes: sound keeps going (the feed pauses), the mini-player appears
    BackHandler(enabled = shortsOpen) {
        shortsOpen = false
        vm.pause()
    }
    BackHandler(enabled = video != null) { videoOpen = false }
    BackHandler(enabled = nowPlaying) { nowPlayingOpen = false }

    // Landscape video hides the system bars
    ImmersiveMode(enabled = video != null && landscape)

    // The feed is portrait-only; otherwise follow the phone's rotation unless a video chose one
    LaunchedEffect(video != null, shortsOpen) {
        activity?.requestedOrientation = when {
            shortsOpen -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            video == null -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else -> return@LaunchedEffect
        }
    }

    val toggleOrientation: () -> Unit = {
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    when {
        shortsOpen -> ShortsFeedScreen(
            feed = feed,
            startUri = feedStart,
            player = videoPlayer,
            state = playerState,
            vm = vm,
            onTogglePlay = vm::togglePlayPause,
            onPlayItem = vm::playShort,
            onClose = {
                shortsOpen = false
                vm.pause()
            }
        )

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
                            when {
                                playerState.isShort -> vm.reopenShortsFeed()
                                playerState.hasVideo -> videoOpen = true
                                else -> nowPlayingOpen = true
                            }
                        }
                    )
                    SpoolNavBar(selected = destination, onSelect = { destination = it })
                }
            }
        ) { padding ->
            val content = Modifier.padding(padding)
            when (destination) {
                Destination.AUTO -> HomeScreen(
                    vm = vm,
                    onSeeAllSongs = { destination = Destination.SONGS },
                    modifier = content
                )
                Destination.SONGS -> SongsScreen(vm, content)
                Destination.VIDEOS -> VideosScreen(vm, content)
            }
        }
    }
}