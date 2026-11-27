package com.example.spool.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.widget.RemoteViews
import com.example.spool.MainActivity
import com.example.spool.R
import com.example.spool.data.Prefs

object WidgetActions {
    const val PLAY_PAUSE = "com.example.spool.widget.PLAY_PAUSE"
    const val NEXT = "com.example.spool.widget.NEXT"
    const val SHUFFLE = "com.example.spool.widget.SHUFFLE"
}

/** The home-screen widget: cover, title, artist, and shuffle, play/pause and next buttons. */
class CueWidget : AppWidgetProvider() {

    // Added to the home screen, resized, or the phone restarted
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context))
    }

    companion object {
        private var lastTitle = ""
        private var lastArtist = ""
        private var lastPlaying = false
        private var lastArtHash = 0
        private var lastCover: Bitmap? = null

        /** Called by the player whenever the song or its play state changes. */
        fun update(
            context: Context,
            title: String,
            artist: String,
            playing: Boolean,
            artwork: ByteArray?
        ) {
            val artHash = artwork?.contentHashCode() ?: 0
            if (title == lastTitle && artist == lastArtist &&
                playing == lastPlaying && artHash == lastArtHash
            ) return

            if (artHash != lastArtHash) lastCover = artwork?.let { decodeCover(it) }
            lastTitle = title
            lastArtist = artist
            lastPlaying = playing
            lastArtHash = artHash
            if (title.isNotBlank()) Prefs.saveWidgetInfo(context, title, artist)

            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CueWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildViews(context))
        }

        private fun buildViews(context: Context): RemoteViews {
            val saved = Prefs.widgetInfo(context)
            val live = lastTitle.isNotBlank()
            val title = if (live) lastTitle else saved.first.ifBlank { "Cue" }
            val artist = when {
                live -> lastArtist
                saved.first.isBlank() -> "Tap shuffle to start"
                else -> saved.second
            }

            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            return RemoteViews(context.packageName, R.layout.widget_cue).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_artist, artist)
                setImageViewResource(
                    R.id.widget_play,
                    if (lastPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                )
                val cover = lastCover
                if (cover != null) {
                    setImageViewBitmap(R.id.widget_cover, cover)
                } else {
                    setImageViewResource(R.id.widget_cover, R.drawable.widget_cover_placeholder)
                }
                setOnClickPendingIntent(R.id.widget_open, openApp)
                setOnClickPendingIntent(R.id.widget_cover, openApp)
                setOnClickPendingIntent(R.id.widget_play, action(context, WidgetActions.PLAY_PAUSE, 1))
                setOnClickPendingIntent(R.id.widget_next, action(context, WidgetActions.NEXT, 2))
                setOnClickPendingIntent(R.id.widget_shuffle, action(context, WidgetActions.SHUFFLE, 3))
            }
        }

        private fun action(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, WidgetActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        /** A small square cover with rounded corners (widgets can't round pictures themselves). */
        private fun decodeCover(bytes: ByteArray): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 200) sample *= 2
            val source = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return@runCatching null

            val side = minOf(source.width, source.height)
            val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(
                    Matrix().apply {
                        postTranslate(-(source.width - side) / 2f, -(source.height - side) / 2f)
                    }
                )
            }
            Canvas(out).drawRoundRect(
                RectF(0f, 0f, side.toFloat(), side.toFloat()),
                side * 0.18f,
                side * 0.18f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            )
            out
        }.getOrNull()
    }
}