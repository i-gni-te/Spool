package com.example.spool.domain

object SubtitleFormats {
    /** The subtitle type for a file name, or null if the player can't read it. */
    fun mimeFor(fileName: String): String? =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "srt" -> "application/x-subrip"
            "vtt" -> "text/vtt"
            "ass", "ssa" -> "text/x-ssa"
            "ttml", "dfxp", "xml" -> "application/ttml+xml"
            else -> null
        }
}