package com.example.spool.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaFileEditor(private val context: Context) {

    suspend fun displayName(uri: String): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(
                Uri.parse(uri),
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    /** Only call this after the user has granted write access (MediaStore.createWriteRequest). */
    suspend fun rename(uri: String, newBaseName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = Uri.parse(uri)
            val oldName = displayName(uri) ?: return@withContext false
            val extension = oldName.substringAfterLast('.', "")
            val base = sanitize(newBaseName)
            if (base.isEmpty()) return@withContext false

            val newName = if (extension.isEmpty()) base else "$base.$extension"
            val (_, title) = TextNormalizer.parseFileName(newName)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                put(MediaStore.MediaColumns.TITLE, title)
            }
            context.contentResolver.update(target, values, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "").trim().take(120)
}