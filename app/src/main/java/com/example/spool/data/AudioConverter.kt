package com.example.spool.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.spool.domain.ConversionNames
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ConversionResult {
    /** [fileName] is what the phone named the new file. */
    data class Done(val fileName: String) : ConversionResult
    data class Failed(val reason: String) : ConversionResult
}

/** Saves an audio-only copy of a video into Music/Cue. */
@OptIn(UnstableApi::class)
class AudioConverter(private val context: Context) {

    suspend fun convert(
        item: MediaItemEntity,
        onProgress: (Float) -> Unit
    ): ConversionResult = withContext(Dispatchers.Main) {
        val hasAudio = withContext(Dispatchers.IO) { hasAudioTrack(item.uri) }
        if (!hasAudio) {
            return@withContext ConversionResult.Failed("This video has no sound to extract.")
        }

        val folder = File(context.cacheDir, "convert").apply { mkdirs() }
        val temp = File(folder, "export_${System.currentTimeMillis()}.m4a")
        try {
            val error = export(Uri.parse(item.uri), temp, onProgress)
            if (error != null) {
                return@withContext ConversionResult.Failed(
                    "The phone couldn't convert this video's audio (${error.errorCodeName})."
                )
            }
            if (!temp.exists() || temp.length() == 0L) {
                return@withContext ConversionResult.Failed("The converted file came out empty.")
            }
            val saved = withContext(Dispatchers.IO) { publish(temp, item) }
                ?: return@withContext ConversionResult.Failed("Couldn't save the audio file.")
            ConversionResult.Done(saved)
        } finally {
            temp.delete() // also runs if the user cancels
        }
    }

    /** Runs the export, reporting progress. Returns null on success, or the error. */
    private suspend fun export(
        input: Uri,
        output: File,
        onProgress: (Float) -> Unit
    ): ExportException? = coroutineScope {
        val finished = CompletableDeferred<ExportException?>()

        // AAC sound is copied as it is; anything else is converted to AAC
        val transformer = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished.complete(null)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    finished.complete(exportException)
                }
            })
            .build()

        val edited = EditedMediaItem.Builder(MediaItem.fromUri(input))
            .setRemoveVideo(true)
            .build()
        transformer.start(edited, output.absolutePath)

        val holder = ProgressHolder()
        val poller = launch {
            while (isActive) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
                delay(250)
            }
        }
        try {
            finished.await()
        } finally {
            poller.cancel()
            if (!finished.isCompleted) transformer.cancel() // the user cancelled
        }
    }

    private fun hasAudioTrack(uri: String): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (e: Exception) {
            true // can't tell, so let the converter try
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Copies the finished file into Music/Cue. Returns the name the phone gave it. */
    private fun publish(temp: File, item: MediaItemEntity): String? {
        val artist = item.artist.takeIf { it != MediaScanner.UNKNOWN_ARTIST }
        val name = ConversionNames.outputName(item.title, artist) + ".m4a"
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/Cue")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.TITLE, item.title)
            if (artist != null) put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.MediaColumns.IS_PENDING, 1) // hidden until it is fully written
        }
        val uri = resolver.insert(collection, values) ?: return null

        return try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("no output stream")
            out.use { stream -> temp.inputStream().use { it.copyTo(stream) } }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            displayNameOf(uri) ?: name
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) } // no half-written file left behind
            null
        }
    }

    private fun displayNameOf(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}