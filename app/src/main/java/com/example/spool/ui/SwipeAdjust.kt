package com.example.spool.ui

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.spool.domain.BrightnessCurve
import com.example.spool.ui.theme.Ember
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

enum class AdjustKind { BRIGHTNESS, VOLUME }

/** Holds the brightness or volume being changed by a swipe, and applies it to the phone. */
@Stable
class SwipeAdjuster(
    private val activity: Activity?,
    private val audio: AudioManager?
) {
    /** What the indicator is showing, or null when it is hidden. */
    var kind by mutableStateOf<AdjustKind?>(null)
        private set

    /** The level the indicator shows, from 0 to 1. For volume this is the phone's real volume. */
    var level by mutableFloatStateOf(0f)
        private set

    /** True while a finger is down. The indicator never hides during that time. */
    var active by mutableStateOf(false)
        private set

    var tick by mutableIntStateOf(0)
        private set

    private var brightnessLevel = 0.5f
    private var brightnessOverridden = false
    private var volumeTarget = 0f

    fun begin(what: AdjustKind) {
        kind = what
        active = true
        when (what) {
            AdjustKind.BRIGHTNESS -> {
                // Start from where the screen really is, so the first swipe doesn't jump
                if (!brightnessOverridden) {
                    brightnessLevel = BrightnessCurve.toLevel(currentLinearBrightness())
                }
                level = brightnessLevel
            }
            AdjustKind.VOLUME -> {
                // The volume buttons may have changed it since last time
                volumeTarget = readVolume()
                level = volumeTarget
            }
        }
        tick++
    }

    /** [delta] is a fraction of the whole range: a swipe across the screen is 1.0. Up is positive. */
    fun change(delta: Float) {
        when (kind) {
            AdjustKind.BRIGHTNESS -> {
                brightnessLevel = (brightnessLevel + delta).coerceIn(0f, 1f)
                setWindowBrightness(BrightnessCurve.toLinear(brightnessLevel))
                brightnessOverridden = true
                level = brightnessLevel
            }
            AdjustKind.VOLUME -> {
                volumeTarget = (volumeTarget + delta).coerceIn(0f, 1f)
                writeVolume(volumeTarget)
                level = readVolume() // show what the phone really did
            }
            null -> return
        }
        tick++
    }

    fun finish() {
        active = false
        tick++
    }

    fun hide() {
        kind = null
    }

    /** Gives the screen brightness back to the phone's own setting. */
    fun restoreBrightness() {
        setWindowBrightness(null)
        brightnessOverridden = false
    }

    private fun currentLinearBrightness(): Float {
        val set = activity?.window?.attributes?.screenBrightness ?: -1f
        if (set >= 0f) return set
        val resolver = activity?.contentResolver ?: return 0.25f
        val system = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)
        return (system / 255f).coerceIn(BrightnessCurve.MIN_LINEAR, 1f)
    }

    /** A null [value] hands control back to the phone. */
    private fun setWindowBrightness(value: Float?) {
        val window = activity?.window ?: return
        val attributes = window.attributes
        attributes.screenBrightness = value ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attributes
    }

    private fun readVolume(): Float {
        val manager = audio ?: return 0.5f
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    private fun writeVolume(fraction: Float) {
        val manager = audio ?: return
        runCatching {
            val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            manager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (fraction.coerceIn(0f, 1f) * max).roundToInt(),
                0
            )
        }
    }
}

@Composable
fun rememberSwipeAdjuster(): SwipeAdjuster {
    val context = LocalContext.current
    val adjuster = remember(context) {
        SwipeAdjuster(context.findActivity(), context.getSystemService(AudioManager::class.java))
    }
    // Brightness goes back to normal when the video screen closes
    DisposableEffect(adjuster) { onDispose { adjuster.restoreBrightness() } }
    return adjuster
}

/** The level indicator. It stays while a finger is down and fades shortly after it lifts. */
@Composable
fun BoxScope.SwipeIndicator(adjuster: SwipeAdjuster) {
    LaunchedEffect(adjuster.tick, adjuster.active) {
        if (!adjuster.active && adjuster.kind != null) {
            delay(700)
            adjuster.hide()
        }
    }

    val shown = adjuster.kind ?: return
    val level = adjuster.level

    Column(
        Modifier
            .align(
                if (shown == AdjustKind.BRIGHTNESS) Alignment.CenterStart else Alignment.CenterEnd
            )
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = when {
                shown == AdjustKind.BRIGHTNESS -> Icons.Filled.BrightnessMedium
                level <= 0.001f -> Icons.Filled.VolumeOff
                else -> Icons.Filled.VolumeUp
            },
            contentDescription = null,
            tint = Color.White
        )
        Box(
            Modifier
                .height(120.dp)
                .width(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxHeight(level.coerceIn(0f, 1f))
                    .width(6.dp)
                    .background(Ember)
            )
        }
        Text(
            "${(level * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

/**
 * One handler for everything you do to the video: a tap, a double-tap, or a vertical swipe.
 * Each touch is exactly one of these, so they can't fight. Buttons and the time bar sit on top
 * and take their own touches first.
 */
suspend fun PointerInputScope.detectVideoGestures(
    swipeEnabled: Boolean,
    doubleTapEnabled: Boolean,
    onTap: () -> Unit,
    onDoubleTap: (leftSide: Boolean) -> Unit,
    onSwipeStart: (leftSide: Boolean) -> Unit,
    onSwipe: (delta: Float) -> Unit,
    onSwipeEnd: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown() // ignores touches a button already took
        val slop = viewConfiguration.touchSlop
        val leftSide = down.position.x < size.width / 2f
        var swiping = false
        var lastY = down.position.y

        // First: is it a tap, or the start of a swipe?
        var lifted = false
        while (!lifted) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
                ?: run {
                    if (swiping) onSwipeEnd()
                    return@awaitEachGesture
                }

            // Something else (a button, the time bar) took this touch
            if (change.isConsumed) {
                if (swiping) onSwipeEnd()
                return@awaitEachGesture
            }

            if (change.changedToUp()) {
                if (swiping) {
                    onSwipeEnd()
                    change.consume()
                    return@awaitEachGesture
                }
                change.consume()
                lifted = true
            } else if (!swiping) {
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (swipeEnabled && abs(dy) > slop && abs(dy) > abs(dx)) {
                    swiping = true
                    lastY = change.position.y
                    onSwipeStart(leftSide)
                    change.consume()
                } else if (abs(dx) > slop || abs(dy) > slop) {
                    // Moved too far to be a tap, and it isn't a swipe we handle: ignore it
                    return@awaitEachGesture
                }
            } else {
                val moved = change.position.y - lastY
                lastY = change.position.y
                onSwipe(-moved / size.height)
                change.consume()
            }
        }

        // It was a tap
        if (!doubleTapEnabled) {
            onTap()
            return@awaitEachGesture
        }

        // Wait a moment: is a second tap coming?
        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown()
        }
        if (second == null) {
            onTap()
            return@awaitEachGesture
        }

        var doubleTap = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == second.id } ?: break
            if (change.isConsumed) break
            if (change.changedToUp()) {
                change.consume()
                doubleTap = true
                break
            }
            if (abs(change.position.x - second.position.x) > slop ||
                abs(change.position.y - second.position.y) > slop
            ) break
        }

        if (doubleTap) onDoubleTap(second.position.x < size.width / 2f) else onTap()
    }
}