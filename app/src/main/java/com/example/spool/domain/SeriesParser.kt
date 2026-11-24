package com.example.spool.domain

import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import com.example.spool.data.TextNormalizer

data class EpisodeInfo(val showName: String, val season: Int, val episode: Int)
data class EpisodeEntry(val item: MediaItemEntity, val season: Int, val episode: Int)

data class Series(val key: String, val name: String, val episodes: List<EpisodeEntry>) {
    val seasonCount: Int get() = episodes.map { it.season }.distinct().size
}

/** Works out the show, season and episode number from a file name and its folder. */
object SeriesParser {
    private val sxe = Regex("""(?i)\bs(\d{1,2})[ ._-]?e(\d{1,3})\b""")
    private val nxn = Regex("""(?i)\b(\d{1,2})x(\d{2,3})\b""")
    private val seasonWord = Regex("""(?i)\bseason[ ._-]?(\d{1,2})\b""")
    private val episodeWord = Regex("""(?i)\bepisode[ ._-]?(\d{1,3})\b""")
    private val seasonFolder = Regex("""(?i)^(?:season[ ._-]?|s)(\d{1,2})$""")
    private val tags = Regex(
        """(?i)\b(1080p|720p|480p|2160p|4k|bluray|brrip|bdrip|web-?dl|webrip|hdrip|hdtv|dvdrip|x264|x265|hevc|h\.?264|h\.?265|aac|ac3)\b.*"""
    )
    private val brackets = Regex("""[\[(][^\])]*[\])]""")
    private val spaces = Regex("\\s+")
    private val genericFolders = setOf(
        "tv", "tv shows", "tvshows", "tv series", "series", "shows", "download", "downloads",
        "video", "videos", "movies", "movie", "films", "film", "dcim", "camera"
    )

    fun parse(title: String, folder: String = ""): EpisodeInfo {
        var season: Int? = null
        var episode: Int? = null
        var cut = title.length

        val a = sxe.find(title)
        val b = if (a == null) nxn.find(title) else null
        when {
            a != null -> {
                season = a.groupValues[1].toInt()
                episode = a.groupValues[2].toInt()
                cut = a.range.first
            }
            b != null -> {
                season = b.groupValues[1].toInt()
                episode = b.groupValues[2].toInt()
                cut = b.range.first
            }
            else -> {
                val s = seasonWord.find(title)
                val e = episodeWord.find(title)
                if (s != null) {
                    season = s.groupValues[1].toInt()
                    cut = minOf(cut, s.range.first)
                }
                if (e != null) {
                    episode = e.groupValues[1].toInt()
                    cut = minOf(cut, e.range.first)
                }
            }
        }

        val segments = folder.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        if (season == null) {
            season = segments.firstNotNullOfOrNull {
                seasonFolder.find(it)?.groupValues?.get(1)?.toIntOrNull()
            }
        }

        var name = clean(title.substring(0, cut.coerceIn(0, title.length)))
        if (name.isBlank()) name = showFromFolder(segments)
        if (name.isBlank()) name = clean(title)
        if (name.isBlank()) name = "Other"

        return EpisodeInfo(name, season ?: 1, episode ?: 0)
    }

    fun info(item: MediaItemEntity): EpisodeInfo = parse(item.title, item.folder)

    /** Episodes of the same show share a key, whatever the spelling or dots in the file names. */
    fun key(item: MediaItemEntity): String = TextNormalizer.normalize(info(item).showName)

    /** Season, then episode, then name. */
    fun orderFor(items: List<MediaItemEntity>): Comparator<MediaItemEntity> {
        val info = items.associate { it.uri to info(it) }
        return compareBy<MediaItemEntity>(
            { info[it.uri]?.season ?: 0 },
            { info[it.uri]?.episode ?: 0 },
            { it.title.lowercase() }
        )
    }

    private fun showFromFolder(segments: List<String>): String =
        segments
            .filter { it.lowercase() !in genericFolders && !seasonFolder.containsMatchIn(it) }
            .lastOrNull()
            ?.let { clean(it) }
            .orEmpty()

    private fun clean(text: String): String {
        var t = text.replace(brackets, " ").replace(tags, "")
        t = t.replace('.', ' ').replace('_', ' ')
        t = t.replace(spaces, " ").trim().trimEnd('-', '–', ':', ' ').trim()
        // "the office" -> "The Office", but leave names that already have capitals alone
        return if (t == t.lowercase()) {
            t.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        } else {
            t
        }
    }
}

object SeriesIndex {
    /** One entry per show, with its episodes in watching order. */
    fun build(episodes: List<MediaItemEntity>): List<Series> =
        episodes.groupBy { SeriesParser.key(it) }
            .map { (key, items) ->
                val entries = items
                    .map { item ->
                        val i = SeriesParser.info(item)
                        EpisodeEntry(item, i.season, i.episode)
                    }
                    .sortedWith(
                        compareBy<EpisodeEntry>(
                            { it.season },
                            { it.episode },
                            { it.item.title.lowercase() }
                        )
                    )
                val name = items
                    .map { SeriesParser.info(it).showName }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }!!.key
                Series(key, name, entries)
            }
            .sortedBy { it.name.lowercase() }
}

/** "Breaking Bad · S1E3" for episodes, the plain title for everything else. */
fun MediaItemEntity.displayTitle(): String {
    if (kind != MediaClassifier.SHOW) return title
    val i = SeriesParser.info(this)
    val tag = if (i.episode > 0) "S${i.season}E${i.episode}" else "S${i.season}"
    return "${i.showName} · $tag"
}