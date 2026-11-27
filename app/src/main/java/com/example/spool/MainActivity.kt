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
import com.example.spool.ui.ImmersiveMode
import com.example.spool.ui.MiniPlayer
import com.example.spool.ui.MusicViewModel
import com.example.spool.ui.NowPlayingScreen
import com.example.spool.ui.OnboardingScreen
import com.example.spool.ui.PermissionScreen
import com.example.spool.ui.RenameRequest
import com.example.spool.ui.ShortcutActions
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
import android.app.PictureInPictureParams
import android.util.Rational
import com.example.spool.ui.CrashDialog
import com.example.spool.ui.SettingsScreen
import com.example.spool.ui.DuplicatesScreen
import com.example.spool.ui.TrashManyRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.spool.data.MiniItem
import com.example.spool.player.PlayerState
import com.example.spool.ui.SystemBarIcons
import com.example.spool.ui.theme.Palette
import com.example.spool.ui.ConversionDialog
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
            val settings by viewModel.settings.collectAsState()
            SpoolTheme(style = settings.themeStyle, mode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize().spoolBackground(settings.animatedBackground),
                    color = ComposeColor.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    SpoolRoot(
                        vm = viewModel,
                        hasAccess = hasAccess,
                        onRequestAccess = ::requestAccess,
                        onOpenSettings = ::openSettings,
                        inPip = inPip,
                        onArmPip = ::armPip,
                        onEnterPip = ::enterPip,
                    )
                }
            }
        }

        if (savedInstanceState == null) handleShortcut(intent)
    }

    // A launcher shortcut tapped while Cue is already open
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    // Also fires when returning from system settings, and rescans on every app open
    override fun onResume() {
        super.onResume()
        refreshAccess()
    }

    /** Long-press shortcuts: Resume, Shuffle songs, Shuffle videos. */
    private fun handleShortcut(intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in ShortcutActions.ALL) return
        intent.action = null // so rotating or reopening doesn't run it again
        if (checkAccess()) viewModel.runShortcut(action)
    }

    private fun handleFileRequest(request: FileRequest) {
        val pendingIntent = when (request) {
            // Moves the file to the trash, where it stays recoverable for about 30 days
            is TrashRequest ->
                MediaStore.createTrashRequest(contentResolver, listOf(Uri.parse(request.item.uri)), true)
            is TrashManyRequest ->
                MediaStore.createTrashRequest(
                    contentResolver,
                    request.items.map { Uri.parse(it.uri) },
                    true
                )
            is RenameRequest ->
                MediaStore.createWriteRequest(contentResolver, listOf(Uri.parse(request.item.uri)))
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

    // Picture-in-picture
    private var inPip by mutableStateOf(false)
    private var pipAspect = Rational(16, 9)

    private fun pipParams(autoEnter: Boolean) = PictureInPictureParams.Builder()
        .setAspectRatio(pipAspect)
        .setAutoEnterEnabled(autoEnter) // pressing Home shrinks a playing video into a window
        .build()

    /** Called by the screen: ready the window while a video plays, and match its shape. */
    private fun armPip(armed: Boolean, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            val ratio = width.toFloat() / height
            pipAspect = when {
                ratio > 2.38f -> Rational(238, 100) // Android only accepts shapes within these limits
                ratio < 0.42f -> Rational(42, 100)
                else -> Rational(width, height)
            }
        }
        setPictureInPictureParams(pipParams(armed))
    }

    private fun enterPip() {
        enterPictureInPictureMode(pipParams(true))
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // The little window was closed with its X: stop the video
        if (isInPictureInPictureMode) viewModel.pause()
    }
}

@Composable
fun SpoolRoot(
    vm: MusicViewModel,
    hasAccess: Boolean,
    inPip: Boolean,
    onArmPip: (Boolean, Int, Int) -> Unit,
    onEnterPip: () -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit
) {

    val onboardingDone by vm.onboardingDone.collectAsState()
    if (!onboardingDone) {
        SystemBarIcons(lightBackground = !Palette.current.isDark)
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
        SystemBarIcons(lightBackground = !Palette.current.isDark)
        PermissionScreen(onAllow = onRequestAccess, onOpenSettings = onOpenSettings)
        return
    }

    val lyrics by vm.currentLyrics.collectAsState()
    val playerState by vm.playerState.collectAsState()
    val videoPlayer by vm.videoPlayer.collectAsState()
    val loop by vm.loop.collectAsState()
    val queue by vm.queue.collectAsState()
    val sleep by vm.sleep.collectAsState()
    val shortsFill by vm.shortsFill.collectAsState()
    val settings by vm.settings.collectAsState()
    val crash by vm.crashReport.collectAsState()
    val feed by vm.feed.collectAsState()
    val feedStart by vm.feedStart.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val lastSong by vm.lastSong.collectAsState()
    val lastVideo by vm.lastVideo.collectAsState()
    val conversion by vm.conversion.collectAsState()
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val activity = context.findActivity()

    var destination by rememberSaveable { mutableStateOf(Destination.SONGS) }
    var miniVisible by remember { mutableStateOf(true) }
    var videoOpen by rememberSaveable { mutableStateOf(false) }
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var shortsOpen by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }
    var duplicatesOpen by rememberSaveable { mutableStateOf(false) }
    var videoLocked by rememberSaveable { mutableStateOf(false) }

    // Cue crashed last time: offer to send the report
    if (crash != null) {
        CrashDialog(
            onSend = { vm.sendCrashReport(context) },
            onDismiss = vm::dismissCrashReport
        )
    }
    // Video to audio: the confirmation, the progress, and the result
    ConversionDialog(
        state = conversion,
        onConvert = vm::startConversion,
        onCancel = vm::cancelConversion,
        onDismiss = vm::dismissConversion,
        onShowSongs = {
            vm.dismissConversion()
            if (shortsOpen || videoOpen) vm.pause()
            shortsOpen = false
            videoOpen = false
            nowPlayingOpen = false
            destination = Destination.SONGS
        }
    )

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

    // Dark icons on light themes, but light icons while a video or the shorts feed is on black
    SystemBarIcons(
        lightBackground = !Palette.current.isDark && !shortsOpen && video == null
    )

    // Leaving a video pauses it (unless you chose to keep the audio going)
    val closeVideo: () -> Unit = {
        if (!settings.keepAudioOnExit) vm.pause()
        videoOpen = false
    }

    // While a video plays, pressing Home shrinks it into a floating window
    LaunchedEffect(
        video != null,
        playerState.isPlaying,
        playerState.videoWidth,
        playerState.videoHeight
    ) {
        onArmPip(
            video != null && playerState.isPlaying,
            playerState.videoWidth,
            playerState.videoHeight
        )
    }

    // Back minimizes: the feed pauses, a video follows your setting, a song keeps playing
    BackHandler(enabled = shortsOpen) {
        shortsOpen = false
        vm.pause()
    }
    // Locked: Back does nothing until the controls are unlocked
    BackHandler(enabled = video != null) { if (!videoLocked) closeVideo() }
    LaunchedEffect(video != null) { if (video == null) videoLocked = false }
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

    // The mini-player follows the page: Songs shows the last song, Videos the last video
    val onVideosPage = destination == Destination.VIDEOS
    val showsCurrent = playerState.title.isNotBlank() &&
            (destination == Destination.SETTINGS || playerState.hasVideo == onVideosPage)
    val miniItem: MiniItem? = if (showsCurrent) {
        MiniItem(playerState.uri, playerState.title, playerState.artist, playerState.isShort)
    } else {
        if (onVideosPage) lastVideo else lastSong
    }
    val miniState: PlayerState? = if (showsCurrent) {
        playerState
    } else {
        miniItem?.let {
            PlayerState(
                title = it.title,
                artist = it.artist,
                uri = it.uri,
                isPlaying = false,
                hasVideo = onVideosPage,
                isShort = it.isShort
            )
        }
    }

// Scrolling a list hides the mini-player, and scrolling back shows it again
    val autoHide = settings.autoHideMini
    val scrollConnection = remember(autoHide) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (autoHide) {
                    if (available.y < -4f) miniVisible = false
                    else if (available.y > 4f) miniVisible = true
                }
                return Offset.Zero
            }
        }
    }
// A new song, or a different page, brings it back
    LaunchedEffect(playerState.uri, destination) { miniVisible = true }

    when {
        shortsOpen -> ShortsFeedScreen(
            feed = feed,
            startUri = feedStart,
            player = videoPlayer,
            state = playerState,
            fill = shortsFill,
            vm = vm,
            onToggleFill = vm::toggleShortsFill,
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
            locked = videoLocked,
            pip = inPip,
            skipSeconds = settings.skipSeconds,
            doubleTapSkip = settings.doubleTapSkip,
            onLockedChange = { videoLocked = it },
            onCycleLoop = vm::cycleLoop,
            onPrevious = vm::previous,
            onToggle = vm::togglePlayPause,
            onNext = vm::next,
            onToggleZoom = { zoom = !zoom },
            onToggleOrientation = toggleOrientation,
            onEnterPip = onEnterPip,
            onSubtitleFile = vm::addSubtitle,
            swipeGestures = settings.swipeGestures,
            onClose = closeVideo
        )

        nowPlaying -> NowPlayingScreen(
            player = videoPlayer,
            state = playerState,
            loop = loop,
            queue = queue,
            sleep = sleep,
            onCycleLoop = vm::cycleLoop,
            onPrevious = vm::previous,
            onToggle = vm::togglePlayPause,
            onNext = vm::next,
            onPlayQueueItem = vm::playQueueItem,
            onSleepMinutes = vm::setSleepMinutes,
            onSleepEndOfTrack = vm::setSleepEndOfTrack,
            onSleepOff = vm::clearSleep,
            lyrics = lyrics,
            onAddLyricsFile = vm::addLyricsFile,
            onSaveLyricsText = vm::saveLyricsText,
            onRemoveLyrics = vm::removeLyrics,
            favorite = playerState.uri in favorites,
            onToggleFavorite = { vm.toggleFavorite(playerState.uri) },
            onClose = { nowPlayingOpen = false }
        )

        else -> Scaffold(
            containerColor = ComposeColor.Transparent,
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = miniState != null && (miniVisible || !autoHide),
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        if (miniState != null) {
                            MiniPlayer(
                                state = miniState,
                                navEnabled = showsCurrent,
                                onPrevious = vm::previous,
                                onToggle = {
                                    if (showsCurrent) vm.togglePlayPause()
                                    else miniItem?.let { vm.startMini(it) }
                                },
                                onNext = vm::next,
                                onOpen = {
                                    when {
                                        // The page's last item isn't loaded: start it
                                        !showsCurrent -> {
                                            miniItem?.let { vm.startMini(it) }
                                            if (!onVideosPage) nowPlayingOpen = true
                                        }
                                        playerState.isShort -> vm.reopenShortsFeed()
                                        playerState.hasVideo -> videoOpen = true
                                        else -> nowPlayingOpen = true
                                    }
                                }
                            )
                        }
                    }
                    SpoolNavBar(selected = destination, onSelect = { destination = it })
                }
            }
        ) { padding ->
            val content = Modifier.padding(padding).nestedScroll(scrollConnection)
            when (destination) {
                Destination.SONGS -> SongsScreen(vm, content)
                Destination.VIDEOS -> VideosScreen(vm, content)
                Destination.SETTINGS ->
                    if (duplicatesOpen) {
                        DuplicatesScreen(vm, onBack = { duplicatesOpen = false }, modifier = content)
                    } else {
                        SettingsScreen(vm, content, onOpenDuplicates = { duplicatesOpen = true })
                    }}
        }
    }
}