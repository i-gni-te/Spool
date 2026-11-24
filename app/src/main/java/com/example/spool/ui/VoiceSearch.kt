package com.example.spool.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.spool.ui.theme.Amber
import com.example.spool.ui.theme.Ember
import com.example.spool.ui.theme.Ink
import com.example.spool.ui.theme.Lavender
import kotlinx.coroutines.delay

sealed interface VoiceState {
    data object Idle : VoiceState
    /** [level] is how loud you are right now, from 0 to 1. [partial] is what's been understood so far. */
    data class Listening(val level: Float = 0f, val partial: String = "") : VoiceState
    data class Heard(val text: String) : VoiceState
    data class Failed(val message: String) : VoiceState
}

/** Runs the phone's speech recognition and reports progress, so Cue can draw its own screen. */
class VoiceRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit
) {
    var state by mutableStateOf<VoiceState>(VoiceState.Idle)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var usingOnDevice = false
    private val handler = Handler(Looper.getMainLooper())

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    /** Uses recognition on the phone itself when it's available (works offline), else the normal service. */
    fun start(preferOnDevice: Boolean = true) {
        stop()
        val made: Pair<SpeechRecognizer, Boolean>? = when {
            preferOnDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context) to true
            SpeechRecognizer.isRecognitionAvailable(context) ->
                SpeechRecognizer.createSpeechRecognizer(context) to false
            else -> null
        }
        if (made == null) {
            state = VoiceState.Failed("Voice search isn't available on this phone.")
            return
        }
        val (rec, onDevice) = made
        usingOnDevice = onDevice
        recognizer = rec
        state = VoiceState.Listening()
        rec.setRecognitionListener(listener)
        rec.startListening(intent)
    }

    fun stop() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            val current = state
            if (current is VoiceState.Listening) {
                state = current.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            val current = state
            if (text.isNotBlank() && current is VoiceState.Listening) {
                state = current.copy(partial = text)
            }
        }

        override fun onResults(results: Bundle?) {
            if (recognizer == null) return
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty().trim()
            if (text.isBlank()) {
                state = VoiceState.Failed("I didn't catch that. Try again.")
            } else {
                state = VoiceState.Heard(text)
                onResult(text)
            }
        }

        override fun onError(error: Int) {
            if (recognizer == null) return
            when (error) {
                SpeechRecognizer.ERROR_CLIENT -> Unit // we cancelled it ourselves

                // The phone can't do this language on its own: use the normal service instead
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    if (usingOnDevice) handler.post { start(preferOnDevice = false) }
                    else state = VoiceState.Failed("Voice search doesn't support this language here.")

                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    state = VoiceState.Failed("I didn't hear anything. Try again.")

                SpeechRecognizer.ERROR_AUDIO ->
                    state = VoiceState.Failed("Couldn't use the microphone.")

                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER ->
                    state = VoiceState.Failed("Voice search needs a connection on this phone.")

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    state = VoiceState.Failed("Cue needs the microphone permission.")

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    state = VoiceState.Failed("Another app is using voice recognition.")

                else -> state = VoiceState.Failed("Voice search failed (code $error).")
            }
        }
    }
}

@Composable
private fun rememberVoiceRecognizer(onResult: (String) -> Unit): VoiceRecognizer {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val recognizer = remember { VoiceRecognizer(context) { latest(it) } }
    DisposableEffect(recognizer) { onDispose { recognizer.stop() } }
    return recognizer
}

/** The mic button for search fields: asks for the microphone once, then shows Cue's own listening sheet. */
@Composable
fun VoiceSearchButton(onHeard: (String) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var micMissing by remember { mutableStateOf(false) }
    val recognizer = rememberVoiceRecognizer { text -> onHeard(text) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micMissing = !granted
        if (granted) recognizer.start()
    }

    fun begin() {
        open = true
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            micMissing = false
            recognizer.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Show what was heard for a moment, then close the sheet
    val state = recognizer.state
    LaunchedEffect(state) {
        if (state is VoiceState.Heard) {
            delay(400)
            recognizer.stop()
            open = false
        }
    }

    IconButton(onClick = { begin() }) {
        Icon(Icons.Filled.Mic, contentDescription = "Voice search")
    }

    if (open) {
        VoiceSheet(
            state = state,
            micMissing = micMissing,
            onRetry = { begin() },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            },
            onDismiss = {
                recognizer.stop()
                open = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSheet(
    state: VoiceState,
    micMissing: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val listening = state as? VoiceState.Listening
    val (title, body) = when {
        micMissing ->
            "Cue needs the microphone" to
                    "It only listens while this screen is open, and never records or keeps your voice."
        state is VoiceState.Failed -> "Couldn't hear you" to state.message
        state is VoiceState.Heard -> "Playing" to "“${state.text}”"
        listening != null && listening.partial.isNotBlank() -> "Listening…" to "“${listening.partial}”"
        listening != null -> "Listening…" to "Say an artist or a song"
        else -> "Getting ready…" to ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            VoiceOrb(level = listening?.level ?: 0f, active = listening != null)
            Text(
                body,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (listening != null && listening.partial.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                when {
                    micMissing -> {
                        TextButton(onClick = onRetry) { Text("Allow") }
                        Button(onClick = onOpenSettings) { Text("Open settings") }
                    }
                    state is VoiceState.Failed -> Button(onClick = onRetry) { Text("Try again") }
                }
            }
        }
    }
}

/** A glowing orb that swells with your voice, with soft rings drifting out from it. */
@Composable
private fun VoiceOrb(level: Float, active: Boolean) {
    val smooth by animateFloatAsState(if (active) level else 0f, tween(140), label = "level")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "ring"
    )

    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.2f
            val orb = base * (1f + smooth * 0.45f)
            if (active) {
                for (i in 0..1) {
                    val t = (pulse + i * 0.5f) % 1f
                    drawCircle(
                        color = Ember.copy(alpha = (1f - t) * 0.35f),
                        radius = orb * (1.1f + t * 0.9f),
                        center = center
                    )
                }
            }
            drawCircle(
                brush = Brush.radialGradient(listOf(Amber, Ember, Lavender), center = center, radius = orb),
                radius = orb,
                center = center
            )
        }
        Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(36.dp)
        )
    }
}