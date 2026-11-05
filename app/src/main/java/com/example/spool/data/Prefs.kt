package com.example.spool.data

import android.content.Context
import com.example.spool.player.LoopMode

/** Tiny settings store (SharedPreferences, no extra dependency). */
object Prefs {
    private const val FILE = "cue_prefs"
    private const val ONBOARDING_DONE = "onboarding_done"
    private const val LOOP_MODE = "loop_mode"

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
}