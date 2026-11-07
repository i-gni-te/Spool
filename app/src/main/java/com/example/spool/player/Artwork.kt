package com.example.spool.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.util.Size
import androidx.compose.ui.graphics.toArgb
import com.example.spool.ui.theme.artistPalette
import java.io.ByteArrayOutputStream

/** Cover art for the notification and lock screen, as JPEG bytes. */
object Artwork {
    private const val SIZE = 512

    /** The file's own art (album art or a video frame) if it has any, otherwise a generated cover. */
    fun forItem(context: Context, uri: String, artist: String, title: String): ByteArray {
        val real = runCatching {
            context.contentResolver.loadThumbnail(Uri.parse(uri), Size(SIZE, SIZE), null)
        }.getOrNull()
        return jpeg(real ?: generated(artist.ifBlank { title }))
    }

    private fun generated(seed: String): Bitmap {
        val (first, second) = artistPalette(seed)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val side = SIZE.toFloat()

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, side, side, first.toArgb(), second.toArgb(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, side, side, background)

        val letter = seed.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪"
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x990A0910.toInt()
            textSize = side * 0.5f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val baseline = side / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(letter, side / 2f, baseline, text)
        return bitmap
    }

    private fun jpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
}