package com.example.spool

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spool.data.MediaRepository
import com.example.spool.player.PlayerConnection
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MusicApp
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    TestScreen(app.repository, app.playerConnection)
                }
            }
        }
    }
}

@Composable
fun TestScreen(repo: MediaRepository, player: PlayerConnection) {
    val scope = rememberCoroutineScope()
    val count by repo.count.collectAsState(initial = 0)
    val nowPlaying by player.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.READ_MEDIA_AUDIO] == true) {
            scope.launch { repo.rescan() }
        }
    }

    Column(
        Modifier.statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = {
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            )
        }) { Text("Grant access and scan") }

        Text("Items in library: $count")

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Artist or title to play") }
        )
        Button(onClick = {
            player.playQuery(query) { n ->
                message = when {
                    n < 0 -> "Player not ready, try again"
                    n == 0 -> "No matches"
                    else -> "Playing from $n songs"
                }
            }
        }) { Text("Play") }

        Text(message)
        Text("Now playing: ${nowPlaying.artist} - ${nowPlaying.title}")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { player.previous() }) { Text("Prev") }
            Button(onClick = { player.togglePlayPause() }) {
                Text(if (nowPlaying.isPlaying) "Pause" else "Resume")
            }
            Button(onClick = { player.next() }) { Text("Next") }
        }
    }
}