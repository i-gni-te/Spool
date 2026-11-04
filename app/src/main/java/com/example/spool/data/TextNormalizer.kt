package com.example.spool.data

import java.text.Normalizer

object TextNormalizer {
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val featWords = Regex("\\b(feat|ft|featuring)\\b")
    private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}\\s]")
    private val spaces = Regex("\\s+")

    /** Lowercase, strip accents, drop "feat." words and punctuation. */
    fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return diacritics.replace(decomposed, "")
            .lowercase()
            .replace(featWords, " ")
            .replace(nonAlphanumeric, " ")
            .replace(spaces, " ")
            .trim()
    }

    /** "Drake - Hotline Bling.mp3" -> ("Drake", "Hotline Bling"). Artist is null if not found. */
    fun parseFileName(displayName: String): Pair<String?, String> {
        val base = displayName.substringBeforeLast('.', displayName).trim()
        val parts = base.split(" - ", limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            val first = parts[0].trim()
            // "01 - Song" is a track number, not an artist
            return if (first.all { it.isDigit() }) null to parts[1].trim()
            else first to parts[1].trim()
        }
        return null to base
    }
}