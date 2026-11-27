package com.example.spool.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MediaRepository(
    private val dao: MediaDao,
    private val scanner: MediaScanner
) {
    val count: Flow<Int> = dao.count()

    fun topArtists(limit: Int = 12): Flow<List<ArtistCount>> = dao.topArtists(limit)

    fun search(query: String): Flow<List<MediaItemEntity>> {
        val q = TextNormalizer.normalize(query)
        return if (q.isEmpty()) dao.all() else dao.search(q)
    }

    suspend fun searchOnce(query: String): List<MediaItemEntity> {
        val q = TextNormalizer.normalize(query)
        return if (q.isEmpty()) dao.allOnce() else dao.searchOnce(q)
    }

    suspend fun byUri(uri: String): MediaItemEntity? = dao.byUri(uri)

    suspend fun byUris(uris: List<String>): List<MediaItemEntity> =
        uris.chunked(500).flatMap { dao.byUris(it) }

    suspend fun remove(uri: String) {
        dao.deleteByUris(listOf(uri))
        dao.removeFavorite(uri)
        dao.deleteLyrics(uri)
        dao.removeNameOverride(uri)
    }

    /** The user moved a video between kinds. Survives rescans. */
    suspend fun setKind(uri: String, kind: Int) {
        dao.putOverride(KindOverride(uri, kind))
        dao.updateKind(uri, kind)
    }

    /**
     * The file was renamed: remember the new name so Cue shows it whatever the tags say,
     * and update the list straight away instead of waiting for the next scan.
     */
    suspend fun applyRename(uri: String, title: String, artist: String?) {
        val name = NameOverride(uri, title, artist)
        dao.putNameOverride(name)
        val current = dao.byUri(uri) ?: return
        dao.upsertAll(listOf(renamed(current, name)))
    }

    private fun renamed(item: MediaItemEntity, name: NameOverride): MediaItemEntity {
        val artist = name.artist ?: item.artist
        val artistForKey = artist.takeIf { it != MediaScanner.UNKNOWN_ARTIST }
        return item.copy(
            title = name.title,
            artist = artist,
            searchKey = TextNormalizer.normalize(
                listOfNotNull(artistForKey, name.title).joinToString(" ")
            )
        )
    }

    // ----- Play history -----

    fun recentlyPlayed(limit: Int = 12): Flow<List<MediaItemEntity>> = dao.recentlyPlayed(limit)

    fun mostPlayed(limit: Int = 12): Flow<List<PlayedTrack>> = dao.mostPlayed(limit)

    suspend fun recordPlay(uri: String) {
        dao.ensureStat(uri)
        dao.bumpStat(uri, System.currentTimeMillis())
    }

    val playStats: Flow<List<PlayStat>> = dao.allStats()

    suspend fun statsMap(): Map<String, PlayStat> = dao.allStatsOnce().associateBy { it.uri }

    // ----- Watch positions, subtitles -----

    suspend fun saveWatchPosition(uri: String, positionMs: Long, durationMs: Long) =
        dao.putWatchPosition(WatchPosition(uri, positionMs, durationMs, System.currentTimeMillis()))

    suspend fun clearWatchPosition(uri: String) = dao.clearWatchPosition(uri)

    suspend fun watchPosition(uri: String): WatchPosition? = dao.watchPosition(uri)

    fun continueWatching(limit: Int = 10): Flow<List<ResumeVideo>> = dao.continueWatching(limit)

    suspend fun saveSubtitle(entry: SubtitleFile) = dao.putSubtitle(entry)

    suspend fun subtitleFor(videoUri: String): SubtitleFile? = dao.subtitleFor(videoUri)

    suspend fun allSubtitles(): List<SubtitleFile> = dao.allSubtitles()

    // ----- Favorites and lyrics -----

    val favorites: Flow<Set<String>> = dao.favoriteUris().map { it.toHashSet() }

    suspend fun favoriteUris(): Set<String> = dao.favoriteUrisOnce().toHashSet()

    suspend fun setFavorite(uri: String, favorite: Boolean) {
        if (favorite) dao.putFavorite(Favorite(uri, System.currentTimeMillis()))
        else dao.removeFavorite(uri)
    }

    fun lyricsFor(uri: String): Flow<LyricsEntry?> = dao.lyricsFlow(uri)

    suspend fun saveLyrics(uri: String, text: String) =
        dao.putLyrics(LyricsEntry(uri, text, System.currentTimeMillis()))

    suspend fun removeLyrics(uri: String) = dao.deleteLyrics(uri)

    /** Adds new files, updates changed ones, removes files that no longer exist. */
    suspend fun rescan() {
        val found = scanner.scan()

        // The user's own choices win over the automatic guess
        val kinds = dao.allOverrides().associate { it.uri to it.kind }
        val names = dao.allNameOverrides().associateBy { it.uri }
        val applied = found.map { item ->
            var result = item
            val kind = kinds[item.uri]
            if (kind != null && item.isVideo) result = result.copy(kind = kind)
            val name = names[item.uri]
            if (name != null) result = renamed(result, name)
            result
        }
        dao.upsertAll(applied)

        val foundUris = found.map { it.uri }.toHashSet()
        dao.allUris()
            .filter { it !in foundUris }
            .chunked(500)
            .forEach { dao.deleteByUris(it) }

        // Forget choices about files that no longer exist
        kinds.keys
            .filter { it !in foundUris }
            .chunked(500)
            .forEach { dao.deleteOverrides(it) }

        names.keys
            .filter { it !in foundUris }
            .chunked(500)
            .forEach { dao.deleteNameOverrides(it) }

        dao.favoriteUrisOnce()
            .filter { it !in foundUris }
            .chunked(500)
            .forEach { dao.deleteFavorites(it) }

        dao.lyricsUris()
            .filter { it !in foundUris }
            .chunked(500)
            .forEach { dao.deleteLyricsFor(it) }
    }
}