package com.example.spool.ui

import android.content.Context
import android.content.Intent
import android.os.Build

fun appVersionLabel(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "${info.versionName} (build ${info.longVersionCode})"
}.getOrDefault("unknown")

/** Opens the share sheet with a ready-made report. [crash] adds the error from the last crash. */
fun shareReport(context: Context, songs: Int, videos: Int, crash: String? = null) {
    val text = buildString {
        appendLine(if (crash != null) "Cue crash report" else "Cue problem report")
        appendLine("Version: ${appVersionLabel(context)}")
        appendLine(
            "Phone: ${Build.MANUFACTURER} ${Build.MODEL}, " +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
        appendLine("Library: $songs songs, $videos videos and shorts")
        appendLine()
        if (crash != null) {
            appendLine("--- Crash ---")
            appendLine(crash)
            appendLine()
        }
        appendLine("What happened (please describe):")
        appendLine()
        appendLine("--- Recent app log ---")
        append(readRecentLog())
    }

    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_SUBJECT,
            if (crash != null) "Cue crash report" else "Cue problem report"
        )
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Send report"))
}

/** An app can read its own log lines without any special permission. */
private fun readRecentLog(): String = runCatching {
    val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "120", "-v", "time"))
    process.inputStream.bufferedReader().use { it.readText() }.takeLast(8_000)
}.getOrDefault("(log unavailable)")