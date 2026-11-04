package com.example.spool.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.spool.player.LoopMode
import com.example.spool.ui.theme.Ember
import com.example.spool.ui.theme.Ink
import com.example.spool.ui.theme.SpoolGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** In-memory thumbnail cache, plus a list of files known to have no thumbnail. */
private object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val missing = java.util.Collections.synchronizedSet(HashSet<String>())

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
    fun isMissing(key: String) = key in missing
    fun markMissing(key: String) { missing.add(key) }
}

/** Real thumbnail or album art when the file has one, otherwise a generated cover. */
@Composable
fun MediaThumb(
    uri: String,
    seed: String,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    sizePx: Int = 360,
    badge: String? = null,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(ThumbCache.get(uri), uri) {
        if (value == null && uri.isNotEmpty() && !ThumbCache.isMissing(uri)) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.loadThumbnail(Uri.parse(uri), Size(sizePx, sizePx), null)
                }.getOrNull()
            }
            if (loaded != null) ThumbCache.put(uri, loaded) else ThumbCache.markMissing(uri)
            value = loaded
        }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Cover(seed = seed, modifier = Modifier.fillMaxSize(), isVideo = isVideo, corner = corner)
        }
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onGo: (() -> Unit)? = null
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            imeAction = if (onGo != null) ImeAction.Go else ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onGo = { onGo?.invoke() },
            onDone = { focus.clearFocus() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ember,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = Ember
        )
    )
}

@Composable
fun PlayPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(SpoolGradient)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Ink)
        Spacer(Modifier.size(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One button cycling Off, Loop queue and Loop one. */
@Composable
fun LoopButton(
    mode: LoopMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    idleTint: Color = Color.White.copy(alpha = 0.6f)
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (mode == LoopMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = when (mode) {
                LoopMode.OFF -> "Loop off"
                LoopMode.ALL -> "Loop queue"
                LoopMode.ONE -> "Loop one"
            },
            tint = if (mode == LoopMode.OFF) idleTint else Ember
        )
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes % 60, totalSeconds % 60)
    else "%d:%02d".format(minutes, totalSeconds % 60)
}