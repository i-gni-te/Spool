package com.example.spool.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE searchKey LIKE '%' || :query || '%' ORDER BY artist, title")
    fun search(query: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE searchKey LIKE '%' || :query || '%'")
    suspend fun searchOnce(query: String): List<MediaItemEntity>

    @Query("SELECT COUNT(*) FROM media_items")
    fun count(): Flow<Int>

    @Query("SELECT uri FROM media_items")
    suspend fun allUris(): List<String>

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query("DELETE FROM media_items WHERE uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    @Query("SELECT * FROM media_items ORDER BY artist, title")
    fun all(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items")
    suspend fun allOnce(): List<MediaItemEntity>
}