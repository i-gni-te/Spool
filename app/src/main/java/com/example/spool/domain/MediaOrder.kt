package com.example.spool.domain

import com.example.spool.data.MediaItemEntity

/** The one sort order used by the lists and by in-order playback, so they always agree. */
object MediaOrder {
    val byName: Comparator<MediaItemEntity> =
        compareBy<MediaItemEntity>({ it.artist.lowercase() }, { it.title.lowercase() })
}