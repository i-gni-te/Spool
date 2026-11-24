package com.example.spool.data

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Date

/** Saves the error to a small file when the app crashes, so it can be reported next launch. */
object CrashLogger {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    appendLine("Time: ${Date()}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    appendLine(Log.getStackTraceString(error))
                }
                File(app.filesDir, FILE).writeText(text.take(20_000))
            }
            // Let Android finish the crash as usual
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = runCatching {
        val file = File(context.filesDir, FILE)
        if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}