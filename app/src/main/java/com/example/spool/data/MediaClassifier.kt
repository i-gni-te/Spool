package com.example.spool.data

/** Best-effort guesses from the file name and length. Android has no "this is a TikTok" flag. */
object MediaClassifier {
    const val SONG = 0
    const val VIDEO = 1
    const val SHORT = 2

    /** A video under this length with a meaningless name is a short. */
    private const val SHORT_MAX_MS = 90_000L

    // Spelled with letters, but not a "real" name
    private val genericWords = setOf(
        "video", "videos", "vid", "download", "downloaded", "untitled", "clip", "movie",
        "file", "tmp", "temp", "new", "tiktok", "snaptik", "ssstik", "tikmate", "reel",
        "reels", "img", "mov", "pxl", "screen", "recording", "screenrecord", "snapchat",
        "instagram", "whatsapp"
    )
    private const val VOWELS = "aeiouy"
    private val separators = Regex("[\\s_\\-.()\\[\\]+@#]+")

    fun classifyVideo(displayName: String, durationMs: Long): Int =
        if (durationMs < SHORT_MAX_MS && looksRandom(displayName)) SHORT else VIDEO

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