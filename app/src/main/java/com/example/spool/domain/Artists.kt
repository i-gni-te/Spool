package com.example.spool.domain

import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import com.example.spool.data.TextNormalizer

data class ArtistEntry(
    val name: String,
    val key: String,
    val total: Int,
    val sampleUri: String
)

object ArtistParser {
    /** One act that contains a separator. Add more names here as you find them. */
    private val protectedNames = listOf(
        "Tyler, The Creator", "Earth, Wind & Fire", "Earth, Wind and Fire",
        "Crosby, Stills, Nash & Young", "Crosby, Stills & Nash", "Simon & Garfunkel",
        "Kool & The Gang", "Hall & Oates", "Florence + The Machine", "Mumford & Sons",
        "Brooks & Dunn", "Sam & Dave", "Ike & Tina Turner", "Bob Marley & The Wailers",
        "Gladys Knight & The Pips", "Tom Petty & The Heartbreakers",
        "Huey Lewis & The News", "Sly & The Family Stone"
    )
    private val ignored = setOf("various artists", "various", "unknown artist", "unknown", "va")

    private val whitespace = Regex("\\s+")
    private val splitter = Regex(
        " ?(?:[;,()\\[\\]]| & | \\+ | x | (?:feat|ft|featuring|vs)\\.? ) ?",
        RegexOption.IGNORE_CASE
    )
    private val leadingFeat = Regex("^(?:feat|ft|featuring|vs)\\.?\\s+", RegexOption.IGNORE_CASE)
    private val placeholder = Regex("\u0001(\\d+)\u0001")

    /** "Spice, Shaggy, Sean Paul" -> ["Spice", "Shaggy", "Sean Paul"] */
    fun split(artistTag: String): List<String> {
        var text = artistTag.trim()
        if (text.isEmpty()) return emptyList()

        // Hide known single acts so their commas and ampersands survive the split
        val saved = mutableListOf<String>()
        for (name in protectedNames) {
            text = Regex(Regex.escape(name), RegexOption.IGNORE_CASE).replace(text) { match ->
                saved += match.value
                "\u0001${saved.lastIndex}\u0001"
            }
        }

        val seen = HashSet<String>()
        val result = mutableListOf<String>()
        for (raw in text.replace(whitespace, " ").split(splitter)) {
            var part = leadingFeat.replace(raw.trim(), "").trim()
            part = placeholder.replace(part) { saved[it.groupValues[1].toInt()] }
            if (part.isEmpty()) continue
            val key = TextNormalizer.normalize(part)
            if (key.isEmpty() || key in ignored) continue
            if (seen.add(key)) result += part
        }
        return result
    }

    fun keys(artistTag: String): Set<String> =
        split(artistTag).map { TextNormalizer.normalize(it) }.toSet()
}

object ArtistIndex {
    private class Acc {
        val spellings = HashMap<String, Int>()
        var total = 0
        var sample = ""
        var sampleIsSong = false
    }

    /** One entry per artist, most tracks first. Shorts are not counted. */
    fun build(items: List<MediaItemEntity>): List<ArtistEntry> {
        val byKey = LinkedHashMap<String, Acc>()
        for (item in items) {
            if (item.kind == MediaClassifier.SHORT) continue
            val isSong = item.kind == MediaClassifier.SONG
            for (name in ArtistParser.split(item.artist)) {
                val acc = byKey.getOrPut(TextNormalizer.normalize(name)) { Acc() }
                acc.total++
                acc.spellings[name] = (acc.spellings[name] ?: 0) + 1
                // Prefer a song as the avatar, since songs are the ones with album art
                if (acc.sample.isEmpty() || (isSong && !acc.sampleIsSong)) {
                    acc.sample = item.uri
                    acc.sampleIsSong = isSong
                }
            }
        }
        return byKey.map { (key, acc) ->
            ArtistEntry(
                name = acc.spellings.maxByOrNull { it.value }!!.key,
                key = key,
                total = acc.total,
                sampleUri = acc.sample
            )
        }.sortedWith(compareByDescending<ArtistEntry> { it.total }.thenBy { it.name.lowercase() })
    }
}