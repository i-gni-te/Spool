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

    fun recentlyPlayed(limit: Int = 12): Flow<List<MediaItemEntity>> = dao.recentlyPlayed(limit)

    fun mostPlayed(limit: Int = 12): Flow<List<PlayedTrack>> = dao.mostPlayed(limit)

    val favorites: Flow<Set<String>> = dao.favoriteUris().map { it.toHashSet() }

    suspend fun favoriteUris(): Set<String> = dao.favoriteUrisOnce().toHashSet()

    suspend fun setFavorite(uri: String, favorite: Boolean) {
        if (favorite) dao.putFavorite(Favorite(uri, System.currentTimeMillis()))
        else dao.removeFavorite(uri)
    }
    suspend fun recordPlay(uri: String) {
        dao.ensureStat(uri)
        dao.bumpStat(uri, System.currentTimeMillis())
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
    }
    /** The user moved a video between Shorts and Videos. Survives rescans. */
    suspend fun setKind(uri: String, kind: Int) {
        dao.putOverride(KindOverride(uri, kind))
        dao.updateKind(uri, kind)
    }

    /** Adds new files, updates changed ones, removes files that no longer exist. */
    suspend fun rescan() {
        val found = scanner.scan()

        // The user's own choices win over the automatic guess
        val overrides = dao.allOverrides().associate { it.uri to it.kind }
        val applied = if (overrides.isEmpty()) {
            found
        } else {
            found.map { item ->
                val kind = overrides[item.uri]
                if (kind != null && item.isVideo) item.copy(kind = kind) else item
            }
        }
        dao.upsertAll(applied)

        val foundUris = found.map { it.uri }.toHashSet()
        dao.allUris()
            .filter { it !in foundUris }
            .chunked(500)
            .forEach { dao.deleteByUris(it) }

        // Forget choices about files that no longer exist
        overrides.keys
            .filter { it !in foundUris }
            .chunked(500)
            .forEach { dao.deleteOverrides(it) }
        // Forget hearts on files that no longer exist
        dao.favoriteUrisOnce()
            .filter { it !in foundUris }
            .chunked(500)
            .forEach { dao.deleteFavorites(it) }
        // Forget lyrics of files that no longer exist
        dao.lyricsUris()
            .filter { it !in foundUris }
            .chunked(500)
            .forEach { dao.deleteLyricsFor(it) }
    }
    suspend fun saveWatchPosition(uri: String, positionMs: Long, durationMs: Long) =
        dao.putWatchPosition(WatchPosition(uri, positionMs, durationMs, System.currentTimeMillis()))

    suspend fun clearWatchPosition(uri: String) = dao.clearWatchPosition(uri)

    suspend fun watchPosition(uri: String): WatchPosition? = dao.watchPosition(uri)

    val playStats: Flow<List<PlayStat>> = dao.allStats()

    suspend fun statsMap(): Map<String, PlayStat> = dao.allStatsOnce().associateBy { it.uri }

    fun continueWatching(limit: Int = 10): Flow<List<ResumeVideo>> = dao.continueWatching(limit)

    suspend fun saveSubtitle(entry: SubtitleFile) = dao.putSubtitle(entry)

    suspend fun subtitleFor(videoUri: String): SubtitleFile? = dao.subtitleFor(videoUri)

    suspend fun allSubtitles(): List<SubtitleFile> = dao.allSubtitles()

    fun lyricsFor(uri: String): Flow<LyricsEntry?> = dao.lyricsFlow(uri)

    suspend fun saveLyrics(uri: String, text: String) =
        dao.putLyrics(LyricsEntry(uri, text, System.currentTimeMillis()))

    suspend fun removeLyrics(uri: String) = dao.deleteLyrics(uri)
}