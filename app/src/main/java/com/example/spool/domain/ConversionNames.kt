package com.example.spool.domain

object ConversionNames {
    private val illegal = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val spaces = Regex("\\s+")

    /** The file name (without .m4a): "Artist - Title" when the artist is known. */
    fun outputName(title: String, artist: String?): String {
        val base = if (artist.isNullOrBlank()) title else "$artist - $title"
        val cleaned = base
            .replace(illegal, "")
            .replace(spaces, " ")
            .trim()
            .trim('.')
            .trim()
            .take(120)
            .trim()
        return cleaned.ifBlank { "Audio" }
    }
}