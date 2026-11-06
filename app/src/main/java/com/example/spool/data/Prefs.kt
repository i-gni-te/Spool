package com.example.spool.data

import android.content.Context
import com.example.spool.player.LoopMode

/** Tiny settings store (SharedPreferences, no extra dependency). */
object Prefs {
    private const val FILE = "cue_prefs"
    private const val ONBOARDING_DONE = "onboarding_done"
    private const val LOOP_MODE = "loop_mode"
    private const val SHORTS_FILL = "shorts_fill"
    private const val RESUME_URI = "resume_uri"
    private const val RESUME_POS = "resume_pos"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun onboardingDone(context: Context): Boolean =
        prefs(context).getBoolean(ONBOARDING_DONE, false)

    fun setOnboardingDone(context: Context) {
        prefs(context).edit().putBoolean(ONBOARDING_DONE, true).apply()
    }

    fun loopMode(context: Context): LoopMode =
        runCatching { LoopMode.valueOf(prefs(context).getString(LOOP_MODE, null).orEmpty()) }
            .getOrDefault(LoopMode.OFF)

    fun setLoopMode(context: Context, mode: LoopMode) {
        prefs(context).edit().putString(LOOP_MODE, mode.name).apply()
    }

    /** False (the default) fits shorts without cropping; true fills the screen. */
    fun shortsFill(context: Context): Boolean =
        prefs(context).getBoolean(SHORTS_FILL, false)

    fun setShortsFill(context: Context, fill: Boolean) {
        prefs(context).edit().putBoolean(SHORTS_FILL, fill).apply()
    }

    /** Where playback was left: the current item and how far into it. */
    fun saveResume(context: Context, uri: String, positionMs: Long) {
        prefs(context).edit().putString(RESUME_URI, uri).putLong(RESUME_POS, positionMs).apply()
    }

    fun loadResume(context: Context): Pair<String, Long>? {
        val p = prefs(context)
        val uri = p.getString(RESUME_URI, null) ?: return null
        return uri to p.getLong(RESUME_POS, 0L)
    }
}