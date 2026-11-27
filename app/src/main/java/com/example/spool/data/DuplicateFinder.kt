package com.example.spool.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.example.spool.domain.DuplicateGroup
import com.example.spool.domain.DuplicateRules
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Finds songs and videos that exist more than once. Files must match in size and length, and
 * then samples from the start, middle and end of each file must match too.
 */
class DuplicateFinder(private val context: Context) {

    suspend fun find(
        items: List<MediaItemEntity>,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val sizes = loadSizes()
        val candidates = DuplicateRules.candidateGroups(items, sizes)
        val total = candidates.sumOf { it.size }
        var done = 0
        onProgress(0, total)

        val found = mutableListOf<DuplicateGroup>()
        for (group in candidates) {
            val size = sizes[group.first().uri] ?: 0L
            val byHash = HashMap<String, MutableList<MediaItemEntity>>()
            for (item in group) {
                ensureActive()
                val hash = sampleHash(Uri.parse(item.uri), size)
                if (hash != null) byHash.getOrPut(hash) { mutableListOf() } += item
                done++
                onProgress(done, total)
            }
            byHash.values.filter { it.size >= 2 }.forEach { found += DuplicateGroup(size, it) }
        }
        // The biggest savings first
        found.sortedByDescending { it.wastedBytes }
    }

    /** File sizes for every song and video, in one pass. */
    private fun loadSizes(): Map<String, Long> {
        val sizes = HashMap<String, Long>()
        val collections = listOf(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        )
        for (collection in collections) {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE),
                null, null, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (c.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                    sizes[uri.toString()] = c.getLong(sizeCol)
                }
            }
        }
        return sizes
    }

    /** A fingerprint from the file's size plus its start, middle and end (all of it if small). */
    private fun sampleHash(uri: Uri, size: Long): String? = runCatching {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return@runCatching null
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(size.toString().toByteArray())
        // AutoCloseInputStream owns the descriptor, so it is closed exactly once
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            val channel = input.channel
            if (size <= CHUNK * 3) {
                readRange(channel, 0L, size, digest)
            } else {
                readRange(channel, 0L, CHUNK, digest)
                readRange(channel, size / 2 - CHUNK / 2, CHUNK, digest)
                readRange(channel, size - CHUNK, CHUNK, digest)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun readRange(channel: FileChannel, start: Long, length: Long, digest: MessageDigest) {
        val buffer = ByteBuffer.allocate(64 * 1024)
        var position = start
        var remaining = length
        while (remaining > 0) {
            buffer.clear()
            buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
            val read = channel.read(buffer, position)
            if (read <= 0) break
            digest.update(buffer.array(), 0, read)
            position += read
            remaining -= read
        }
    }

    private companion object {
        const val CHUNK = 256L * 1024
    }
}