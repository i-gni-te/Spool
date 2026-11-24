package com.example.spool.domain

import com.example.spool.data.MediaItemEntity

data class FolderGroup(
    val key: String,
    val name: String,
    val path: String,
    val songs: List<MediaItemEntity>
)

/** Groups songs by the folder they sit in, for the Folders view. */
object FolderIndex {
    /** Stands in for songs the phone gave no folder for. */
    const val NO_FOLDER = "<none>"

    fun keyOf(item: MediaItemEntity): String = item.folder.ifEmpty { NO_FOLDER }

    fun build(
        songs: List<MediaItemEntity>,
        order: Comparator<MediaItemEntity> = MediaOrder.byName
    ): List<FolderGroup> =
        songs.groupBy { keyOf(it) }
            .map { (key, items) ->
                FolderGroup(
                    key = key,
                    name = nameOf(key),
                    path = pathOf(key),
                    songs = items.sortedWith(order)
                )
            }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.path.lowercase() }))

    /** "Music/Afrobeats/" is shown as "Afrobeats". */
    private fun nameOf(key: String): String {
        if (key == NO_FOLDER) return "Other"
        return key.trimEnd('/').substringAfterLast('/').ifBlank { "Storage" }
    }

    private fun pathOf(key: String): String =
        if (key == NO_FOLDER) "" else key.trimEnd('/')
}