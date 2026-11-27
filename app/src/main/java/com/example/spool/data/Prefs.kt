package com.example.spool.data

import android.content.Context
import com.example.spool.player.LoopMode
import com.example.spool.domain.SongSort

/** Tiny settings store (SharedPreferences, no extra dependency). */
object Prefs {

    private const val TIME_SHOWS_REMAINING = "time_shows_remaining"

    /** True (the default): the right-hand time shows time left; false: the total length. */
    fun timeShowsRemaining(context: Context): Boolean =
        prefs(context).getBoolean(TIME_SHOWS_REMAINING, true)

    fun setTimeShowsRemaining(context: Context, remaining: Boolean) {
        prefs(context).edit().putBoolean(TIME_SHOWS_REMAINING, remaining).apply()
    }
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
            .getOrDefault(LoopMode.ALL)

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

    private const val SONG_SORT = "song_sort"
    private const val NEW_SINCE = "new_since"

    fun songSort(context: Context): SongSort =
        runCatching { SongSort.valueOf(prefs(context).getString(SONG_SORT, null).orEmpty()) }
            .getOrDefault(SongSort.AZ)

    fun setSongSort(context: Context, sort: SongSort) {
        prefs(context).edit().putString(SONG_SORT, sort.name).apply()
    }

    /** Files added after this moment (and unplayed) count as new. Pressing Clear moves it to now. */
    fun newSince(context: Context): Long = prefs(context).getLong(NEW_SINCE, 0L)

    fun setNewSince(context: Context, millis: Long) {
        prefs(context).edit().putLong(NEW_SINCE, millis).apply()
    }
    private const val SKIP_SECONDS = "skip_seconds"

    private const val GAPLESS = "gapless"
    private const val WIDGET_ARTIST = "widget_artist"
    private const val WIDGET_TITLE = "widget_title"
    private const val DOUBLE_TAP_SKIP = "double_tap_skip"
    private const val KEEP_AUDIO_ON_EXIT = "keep_audio_on_exit"
    private const val RESUME_WHERE_LEFT_OFF = "resume_where_left_off"
    private const val SHOW_NEW = "show_new"
    private const val NEW_DAYS = "new_days"
    private const val FADE_SECONDS = "fade_seconds"
    private const val AUTO_HIDE_MINI = "auto_hide_mini"
    private const val REPORT_VIA = "report_via"
    private const val THEME_STYLE = "theme_style"
    private const val THEME_MODE = "theme_mode"

    private const val SWIPE_GESTURES = "swipe_gestures"
    private const val ANIMATED_BACKGROUND = "animated_background"

    fun settings(context: Context): AppSettings {
        val p = prefs(context)
        val d = AppSettings()
        return AppSettings(
            skipSeconds = p.getInt(SKIP_SECONDS, d.skipSeconds)
                .takeIf { it in AppSettings.SKIP_CHOICES } ?: d.skipSeconds,
            doubleTapSkip = p.getBoolean(DOUBLE_TAP_SKIP, d.doubleTapSkip),
            swipeGestures = p.getBoolean(SWIPE_GESTURES, d.swipeGestures),
            keepAudioOnExit = p.getBoolean(KEEP_AUDIO_ON_EXIT, d.keepAudioOnExit),
            resumeWhereLeftOff = p.getBoolean(RESUME_WHERE_LEFT_OFF, d.resumeWhereLeftOff),
            gapless = p.getBoolean(GAPLESS, d.gapless),
            fadeSeconds = p.getInt(FADE_SECONDS, d.fadeSeconds)
                .takeIf { it in AppSettings.FADE_CHOICES } ?: d.fadeSeconds,
            autoHideMini = p.getBoolean(AUTO_HIDE_MINI, d.autoHideMini),
            showNew = p.getBoolean(SHOW_NEW, d.showNew),
            newDays = p.getInt(NEW_DAYS, d.newDays)
                .takeIf { it in AppSettings.NEW_DAY_CHOICES } ?: d.newDays,
            animatedBackground = p.getBoolean(ANIMATED_BACKGROUND, d.animatedBackground),
            reportVia = runCatching { ReportVia.valueOf(p.getString(REPORT_VIA, null).orEmpty()) }
                .getOrDefault(d.reportVia),
            themeStyle = runCatching { ThemeStyle.valueOf(p.getString(THEME_STYLE, null).orEmpty()) }
                .getOrDefault(d.themeStyle),
            themeMode = runCatching { ThemeMode.valueOf(p.getString(THEME_MODE, null).orEmpty()) }
                .getOrDefault(d.themeMode)
        )
    }

    fun saveSettings(context: Context, s: AppSettings) {
        prefs(context).edit()
            .putInt(SKIP_SECONDS, s.skipSeconds)
            .putBoolean(DOUBLE_TAP_SKIP, s.doubleTapSkip)
            .putBoolean(SWIPE_GESTURES, s.swipeGestures)
            .putBoolean(KEEP_AUDIO_ON_EXIT, s.keepAudioOnExit)
            .putBoolean(RESUME_WHERE_LEFT_OFF, s.resumeWhereLeftOff)
            .putBoolean(GAPLESS, s.gapless)
            .putInt(FADE_SECONDS, s.fadeSeconds)
            .putBoolean(AUTO_HIDE_MINI, s.autoHideMini)
            .putBoolean(SHOW_NEW, s.showNew)
            .putInt(NEW_DAYS, s.newDays)
            .putBoolean(ANIMATED_BACKGROUND, s.animatedBackground)
            .putString(REPORT_VIA, s.reportVia.name)
            .putString(THEME_STYLE, s.themeStyle.name)
            .putString(THEME_MODE, s.themeMode.name)
            .apply()
    }

    /** The last song or video that played, kept apart so each page's mini-player has its own. */
    fun lastItem(context: Context, video: Boolean): MiniItem? {
        val p = prefs(context)
        val key = if (video) "last_video" else "last_song"
        val uri = p.getString("${key}_uri", null) ?: return null
        return MiniItem(
            uri = uri,
            title = p.getString("${key}_title", "").orEmpty(),
            artist = p.getString("${key}_artist", "").orEmpty(),
            isShort = p.getBoolean("${key}_short", false)
        )
    }

    fun saveLastItem(context: Context, video: Boolean, item: MiniItem) {
        val key = if (video) "last_video" else "last_song"
        prefs(context).edit()
            .putString("${key}_uri", item.uri)
            .putString("${key}_title", item.title)
            .putString("${key}_artist", item.artist)
            .putBoolean("${key}_short", item.isShort)
            .apply()
    }

    /** The last song shown on the home-screen widget, so it still reads right after a restart. */
    fun saveWidgetInfo(context: Context, title: String, artist: String) {
        prefs(context).edit().putString(WIDGET_TITLE, title).putString(WIDGET_ARTIST, artist).apply()
    }

    fun widgetInfo(context: Context): Pair<String, String> {
        val p = prefs(context)
        return p.getString(WIDGET_TITLE, "").orEmpty() to p.getString(WIDGET_ARTIST, "").orEmpty()
    }

    private const val SONGS_AS_FOLDERS = "songs_as_folders"

    fun songsAsFolders(context: Context): Boolean =
        prefs(context).getBoolean(SONGS_AS_FOLDERS, false)

    fun setSongsAsFolders(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(SONGS_AS_FOLDERS, value).apply()
    }

    private const val EQ_ENABLED = "eq_enabled"
    private const val EQ_LEVELS = "eq_levels"
    private const val EQ_BASS = "eq_bass"

    fun eqSettings(context: Context): EqSettings {
        val p = prefs(context)
        return EqSettings(
            enabled = p.getBoolean(EQ_ENABLED, false),
            bandLevels = p.getString(EQ_LEVELS, "").orEmpty()
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() },
            bassStrength = p.getInt(EQ_BASS, 0)
        )
    }

    fun saveEqSettings(context: Context, s: EqSettings) {
        prefs(context).edit()
            .putBoolean(EQ_ENABLED, s.enabled)
            .putString(EQ_LEVELS, s.bandLevels.joinToString(","))
            .putInt(EQ_BASS, s.bassStrength)
            .apply()
    }


}