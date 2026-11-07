package com.example.spool.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ArtistCount(val name: String, val total: Int)

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE searchKey LIKE '%' || :query || '%' ORDER BY artist, title")
    fun search(query: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE searchKey LIKE '%' || :query || '%'")
    suspend fun searchOnce(query: String): List<MediaItemEntity>

    @Query("SELECT * FROM media_items ORDER BY artist, title")
    fun all(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items")
    suspend fun allOnce(): List<MediaItemEntity>

    @Query(
        "SELECT artist AS name, COUNT(*) AS total FROM media_items " +
                "WHERE artist != 'Unknown artist' AND kind != 2 " +
                "GROUP BY artist ORDER BY total DESC, name LIMIT :limit"
    )
    fun topArtists(limit: Int): Flow<List<ArtistCount>>

    @Query("SELECT COUNT(*) FROM media_items")
    fun count(): Flow<Int>

    @Query("SELECT * FROM media_items WHERE uri = :uri LIMIT 1")
    suspend fun byUri(uri: String): MediaItemEntity?
    @Query("SELECT uri FROM media_items")
    suspend fun allUris(): List<String>

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query("DELETE FROM media_items WHERE uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    @Query("SELECT * FROM media_items WHERE uri IN (:uris)")
    suspend fun byUris(uris: List<String>): List<MediaItemEntity>

    @Query("UPDATE media_items SET kind = :kind WHERE uri = :uri")
    suspend fun updateKind(uri: String, kind: Int)

    @Upsert
    suspend fun putOverride(entry: KindOverride)

    @Query("SELECT * FROM kind_overrides")
    suspend fun allOverrides(): List<KindOverride>

    @Query("DELETE FROM kind_overrides WHERE uri IN (:uris)")
    suspend fun deleteOverrides(uris: List<String>)

    @Query("INSERT OR IGNORE INTO play_stats (uri, plays, lastPlayedAt) VALUES (:uri, 0, 0)")
    suspend fun ensureStat(uri: String)

    @Query("UPDATE play_stats SET plays = plays + 1, lastPlayedAt = :now WHERE uri = :uri")
    suspend fun bumpStat(uri: String, now: Long)

    @Query(
        "SELECT m.* FROM media_items m JOIN play_stats s ON s.uri = m.uri " +
                "WHERE s.lastPlayedAt > 0 AND m.kind != 2 " +
                "ORDER BY s.lastPlayedAt DESC LIMIT :limit"
    )
    fun recentlyPlayed(limit: Int): Flow<List<MediaItemEntity>>

    @Query(
        "SELECT m.*, s.plays AS plays FROM media_items m JOIN play_stats s ON s.uri = m.uri " +
                "WHERE s.plays > 0 AND m.kind != 2 " +
                "ORDER BY s.plays DESC, s.lastPlayedAt DESC LIMIT :limit"
    )
    fun mostPlayed(limit: Int): Flow<List<PlayedTrack>>
}