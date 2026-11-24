package com.example.spool.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaScanner(private val context: Context) {

    suspend fun scan(): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        scanAudio() + scanVideo()
    }

    private fun scanAudio(): List<MediaItemEntity> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        // IS_MUSIC skips ringtones, alarms and voice notes; the floor skips tiny sound clips
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.MediaColumns.DURATION} >= $MIN_SONG_MS"
        return query(collection, selection, isVideo = false)
    }

    private fun scanVideo(): List<MediaItemEntity> {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val selection = "${MediaStore.MediaColumns.DURATION} > 0"
        return query(collection, selection, isVideo = true)
    }

    private fun query(collection: Uri, selection: String?, isVideo: Boolean): List<MediaItemEntity> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.ARTIST,
            MediaStore.MediaColumns.ALBUM,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        val items = mutableListOf<MediaItemEntity>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ALBUM)
            val durCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

            while (c.moveToNext()) {
                val displayName = c.getString(nameCol) ?: ""
                val durationMs = c.getLong(durCol)
                val folder = c.getString(pathCol).orEmpty()
                val kind = if (isVideo) {
                    MediaClassifier.classifyVideo(displayName, durationMs, folder)
                } else {
                    MediaClassifier.SONG
                }
                items += buildItem(
                    uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                    displayName = displayName,
                    rawTitle = c.getString(titleCol),
                    rawArtist = c.getString(artistCol),
                    rawAlbum = c.getString(albumCol),
                    durationMs = durationMs,
                    isVideo = isVideo,
                    kind = kind,
                    addedAt = c.getLong(addedCol) * 1000L, // the phone stores seconds
                    folder = folder
                )
            }
        }
        return items
    }

    private fun buildItem(
        uri: Uri,
        displayName: String,
        rawTitle: String?,
        rawArtist: String?,
        rawAlbum: String?,
        durationMs: Long,
        isVideo: Boolean,
        kind: Int,
        addedAt: Long,
        folder: String
    ): MediaItemEntity {
        val tagArtist = rawArtist.cleanTag()
        val (nameArtist, nameTitle) = TextNormalizer.parseFileName(displayName)

        val artist = tagArtist ?: nameArtist
        val title = when {
            tagArtist != null -> rawTitle.cleanTag() ?: nameTitle
            nameArtist != null -> nameTitle
            else -> rawTitle.cleanTag() ?: nameTitle
        }
        return MediaItemEntity(
            uri = uri.toString(),
            title = title,
            artist = artist ?: UNKNOWN_ARTIST,
            album = rawAlbum.cleanTag() ?: "",
            durationMs = durationMs,
            isVideo = isVideo,
            searchKey = TextNormalizer.normalize(listOfNotNull(artist, title).joinToString(" ")),
            kind = kind,
            addedAt = addedAt,
            folder = folder
        )
    }

    private fun String?.cleanTag(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }

    companion object {
        const val UNKNOWN_ARTIST = "Unknown artist"
        private const val MIN_SONG_MS = 30_000
    }
}