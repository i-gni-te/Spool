package com.example.spool.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

    suspend fun remove(uri: String) = dao.deleteByUris(listOf(uri))


    /** Adds new files, updates changed ones, removes files that no longer exist. */
    suspend fun rescan() {
        val found = scanner.scan()
        dao.upsertAll(found)
        val foundUris = found.map { it.uri }.toHashSet()
        dao.allUris()
            .filter { it !in foundUris }
            .chunked(500)
            .forEach { dao.deleteByUris(it) }
    }
}