package com.example.spool

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spool.data.MediaItemEntity
import com.example.spool.data.MediaRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as MusicApp).repository
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { ScanTest(repo) }
            }
        }
    }
}

@Composable
fun ScanTest(repo: MediaRepository) {
    val scope = rememberCoroutineScope()
    val count by repo.count.collectAsState(initial = 0)
    var query by remember { mutableStateOf("") }
    val results by remember(query) { repo.search(query) }
        .collectAsState(initial = emptyList<MediaItemEntity>())

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.READ_MEDIA_AUDIO] == true) {
            scope.launch { repo.rescan() }
        }
    }

    Column(Modifier.statusBarsPadding().padding(16.dp)) {
        Button(onClick = {
            launcher.launch(
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
            )
        }) { Text("Grant access and scan") }

        Text("Items in library: $count", Modifier.padding(vertical = 8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search artist or title") }
        )
        LazyColumn {
            items(results) { item ->
                Text("${item.artist} - ${item.title}${if (item.isVideo) " [video]" else ""}")
            }
        }
    }
}