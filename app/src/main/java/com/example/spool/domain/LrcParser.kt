package com.example.spool.domain

data class LyricLine(val timeMs: Long, val text: String)

/** [synced] is true when the lines have times (an .lrc file) and can follow the music. */
data class LyricsDoc(val lines: List<LyricLine>, val synced: Boolean) {
    /** The line being sung at [positionMs], or -1 before the first line. */
    fun indexAt(positionMs: Long): Int {
        if (!synced) return -1
        var low = 0
        var high = lines.lastIndex
        var answer = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }
}

/** Reads .lrc text ("[01:23.45]words") or plain lyrics. */
object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""(?i)^\[offset:\s*([+-]?\d+)\s*]$""")
    private val metaTag = Regex("""^\[[A-Za-z#]+:.*]$""")
    private val wordTime = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun parse(raw: String): LyricsDoc {
        val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        var offset = 0L
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                plain += ""
                continue
            }
            val offsetMatch = offsetTag.find(line)
            if (offsetMatch != null) {
                offset = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                continue
            }

            // Leading time tags: [00:10.00][01:40.00]Chorus
            val times = mutableListOf<Long>()
            var index = 0
            while (true) {
                val match = timeTag.matchAt(line, index) ?: break
                times += toMillis(match)
                index = match.range.last + 1
            }

            if (times.isNotEmpty()) {
                val body = line.substring(index).replace(wordTime, "").trim()
                times.forEach { timed += LyricLine(it, body) }
            } else if (!metaTag.matches(line)) {
                plain += line.replace(wordTime, "")
            }
        }

        if (timed.isNotEmpty()) {
            val adjusted = timed
                .map { it.copy(timeMs = (it.timeMs - offset).coerceAtLeast(0L)) }
                .sortedBy { it.timeMs }
            return LyricsDoc(adjusted, synced = true)
        }

        val lines = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        return LyricsDoc(lines.map { LyricLine(0L, it) }, synced = false)
    }

    private fun toMillis(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
        val fractionMs = if (fraction.isEmpty()) 0L else ("0.$fraction".toDouble() * 1000).toLong()
        return minutes * 60_000L + seconds * 1_000L + fractionMs
    }
}