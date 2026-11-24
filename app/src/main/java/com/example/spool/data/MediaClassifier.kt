package com.example.spool.data

/** Best-effort guesses from the file name, folder and length. Android has no "this is a movie" flag. */
object MediaClassifier {
    const val SONG = 0
    const val VIDEO = 1 // music videos, and anything that isn't a movie or an episode
    const val SHORT = 2
    const val MOVIE = 3
    const val SHOW = 4  // one episode of a TV show

    /** A video under this length with a meaningless name is a short. */
    private const val SHORT_MAX_MS = 90_000L
    private const val MOVIE_MIN_MS = 70L * 60_000L
    private const val RELEASE_MOVIE_MIN_MS = 40L * 60_000L
    private const val FOLDER_EPISODE_MIN_MS = 10L * 60_000L

    // Spelled with letters, but not a "real" name
    private val genericWords = setOf(
        "video", "videos", "vid", "download", "downloaded", "untitled", "clip", "movie",
        "file", "tmp", "temp", "new", "tiktok", "snaptik", "ssstik", "tikmate", "reel",
        "reels", "img", "mov", "pxl", "screen", "recording", "screenrecord", "snapchat",
        "instagram", "whatsapp"
    )
    private const val VOWELS = "aeiouy"
    private val separators = Regex("[\\s_\\-.()\\[\\]+@#]+")

    private val episodeMarkers = listOf(
        Regex("""(?i)\bs\d{1,2}[ ._-]?e\d{1,3}\b"""),   // S01E02
        Regex("""(?i)\b\d{1,2}x\d{2,3}\b"""),            // 1x03
        Regex("""(?i)\bseason[ ._-]?\d{1,2}\b"""),
        Regex("""(?i)\bepisode[ ._-]?\d{1,3}\b""")
    )
    private val releaseTags = Regex(
        """(?i)\b(1080p|720p|480p|2160p|4k|bluray|brrip|bdrip|web-?dl|webrip|hdrip|hdtv|dvdrip|x264|x265|hevc)\b"""
    )
    private val year = Regex("""\b(19|20)\d{2}\b""")
    private val seasonFolder = Regex("""(?i)^(season[ ._-]?|s)\d{1,2}$""")
    private val showFolders = setOf("tv", "tv shows", "tvshows", "tv series", "series", "shows")
    private val movieFolders = setOf("movies", "movie", "films", "film", "cinema")

    fun classifyVideo(displayName: String, durationMs: Long, folder: String? = null): Int {
        if (durationMs < SHORT_MAX_MS && looksRandom(displayName)) return SHORT

        val name = displayName.substringBeforeLast('.', displayName)
        val segments = folder.orEmpty().lowercase().split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // TV episode: a marker in the name, or a TV/season folder and a real episode length
        val episodeInName = episodeMarkers.any { it.containsMatchIn(name) }
        val episodeFolder = durationMs >= FOLDER_EPISODE_MIN_MS &&
                segments.any { it in showFolders || seasonFolder.matches(it) }
        if (episodeInName || episodeFolder) return SHOW

        // Movie: very long, or fairly long with a release-style name or a movies folder
        val released = year.containsMatchIn(name) && releaseTags.containsMatchIn(name)
        val movieFolder = segments.any { it in movieFolders }
        return when {
            durationMs >= MOVIE_MIN_MS -> MOVIE
            (released || movieFolder) && durationMs >= RELEASE_MOVIE_MIN_MS -> MOVIE
            else -> VIDEO
        }
    }

    /** True when the name has no readable word, e.g. VID_20240101_1234, 7a8f9c1b, xkqzjwvt. */
    fun looksRandom(displayName: String): Boolean {
        val base = displayName.substringBeforeLast('.', displayName).trim()
        if (base.isEmpty()) return true
        return base.split(separators).none { isReadableWord(it) }
    }

    private fun isReadableWord(token: String): Boolean {
        if (token.isEmpty() || !token.all { it.isLetter() }) return false
        val ascii = token.all { it.code < 128 }
        if (token.length < (if (ascii) 3 else 2)) return false
        val lower = token.lowercase()
        if (lower in genericWords) return false
        if (ascii) {
            // Real words have a normal share of vowels and no long consonant runs
            if (lower.count { it in VOWELS } / lower.length.toFloat() < 0.15f) return false
            if (hasConsonantRun(lower, 5)) return false
            // "aXbYcZ"-style case mixing is a sign of a random id
            val hasLower = token.any { it.isLowerCase() }
            if (hasLower && token.drop(1).count { it.isUpperCase() } >= 3) return false
        }
        return true
    }

    private fun hasConsonantRun(lower: String, length: Int): Boolean {
        var run = 0
        for (ch in lower) {
            if (ch in VOWELS) {
                run = 0
            } else {
                run++
                if (run >= length) return true
            }
        }
        return false
    }
}