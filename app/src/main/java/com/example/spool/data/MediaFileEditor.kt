package com.example.spool.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RenameResult {
    /** [fileName] is what the phone says the file is called now. */
    data class Success(val fileName: String) : RenameResult
    data class Failure(val reason: String) : RenameResult
}

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
    suspend fun rename(uri: String, newBaseName: String): RenameResult =
        withContext(Dispatchers.IO) {
            val target = Uri.parse(uri)
            val oldName = displayName(uri)
                ?: return@withContext RenameResult.Failure("couldn't read the current file name")
            val base = sanitize(newBaseName)
            if (base.isEmpty()) {
                return@withContext RenameResult.Failure("that name isn't allowed")
            }

            val extension = oldName.substringAfterLast('.', "")
            val newName = if (extension.isEmpty()) base else "$base.$extension"
            if (newName == oldName) {
                return@withContext RenameResult.Failure("that's already the file's name")
            }

            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                val rows = context.contentResolver.update(target, values, null, null)

                // Read it back: trust what the phone says the file is called now
                val after = displayName(uri)
                when {
                    after == null -> RenameResult.Failure("the file disappeared")
                    after != oldName -> RenameResult.Success(after)
                    rows > 0 -> RenameResult.Failure("the phone kept the old name")
                    else -> RenameResult.Failure("Android didn't allow it")
                }
            } catch (e: SecurityException) {
                RenameResult.Failure("Cue wasn't given permission for this file")
            } catch (e: Exception) {
                RenameResult.Failure(e.message ?: "something went wrong")
            }
        }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "").trim().trimEnd('.').trim().take(120)
}