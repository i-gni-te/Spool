package com.example.spool.domain

import com.example.spool.data.MediaItemEntity
import com.example.spool.data.PlayStat

/** How the Songs page is ordered. The queue plays in this same order. */
enum class SongSort(val label: String) {
    AZ("A–Z"),
    RECENTLY_ADDED("Recently added"),
    RECENTLY_PLAYED("Recently played"),
    MOST_PLAYED("Most played"),
    FAVORITES("Favorites"); // shows only hearted items, A–Z

    fun comparator(stats: Map<String, PlayStat>): Comparator<MediaItemEntity> = when (this) {
        AZ, FAVORITES -> MediaOrder.byName
        RECENTLY_ADDED ->
            compareByDescending<MediaItemEntity> { it.addedAt }.then(MediaOrder.byName)
        RECENTLY_PLAYED ->
            compareByDescending<MediaItemEntity> { stats[it.uri]?.lastPlayedAt ?: 0L }
                .then(MediaOrder.byName)
        MOST_PLAYED ->
            compareByDescending<MediaItemEntity> { stats[it.uri]?.plays ?: 0 }
                .thenByDescending { stats[it.uri]?.lastPlayedAt ?: 0L }
                .then(MediaOrder.byName)
    }
}