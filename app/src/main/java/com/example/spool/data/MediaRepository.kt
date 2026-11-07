package com.example.spool.data

import kotlinx.coroutines.flow.Flow

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

    suspend fun remove(uri: String) = dao.deleteByUris(listOf(uri))

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
    }
}