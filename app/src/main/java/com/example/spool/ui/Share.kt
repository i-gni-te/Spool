package com.example.spool.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.spool.data.MediaItemEntity

/** Sends the file itself to the app the user picks (WhatsApp, Bluetooth, email…). */
fun shareFile(context: Context, item: MediaItemEntity) {
    val uri = Uri.parse(item.uri)
    val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        ?: if (item.isVideo) "video/*" else "audio/*"
    val send = Intent(Intent.ACTION_SEND).apply {
        this.type = type
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, item.title)
        clipData = ClipData.newRawUri(item.title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, "Share " + item.title))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app to share with", Toast.LENGTH_SHORT).show()
    }
}