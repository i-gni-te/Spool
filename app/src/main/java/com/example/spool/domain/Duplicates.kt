package com.example.spool.domain

import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity

/** Copies of the same file. [sizeBytes] is the size of each copy. */
data class DuplicateGroup(val sizeBytes: Long, val copies: List<MediaItemEntity>) {
    val wastedBytes: Long get() = sizeBytes * (copies.size - 1)
}

object DuplicateRules {
    /** Files this small aren't worth comparing. */
    private const val MIN_SIZE = 10_000L

    /** Files with the same size and length are candidates. Their contents are checked next. */
    fun candidateGroups(
        items: List<MediaItemEntity>,
        sizes: Map<String, Long>
    ): List<List<MediaItemEntity>> =
        items
            .filter { (sizes[it.uri] ?: 0L) >= MIN_SIZE }
            .groupBy { Triple(sizes[it.uri] ?: 0L, it.isVideo, it.durationMs) }
            .values
            .filter { it.size >= 2 }

    /** Lower is better to keep: a normal folder, with a readable name. */
    private fun keepScore(item: MediaItemEntity): Int {
        val folder = item.folder.lowercase()
        var score = when {
            "whatsapp" in folder || "telegram" in folder ||
                    "bluetooth" in folder || "snapchat" in folder -> 3
            "download" in folder -> 2
            else -> 0
        }
        if (MediaClassifier.looksRandom(item.title)) score += 1
        return score
    }

    /** The copy to keep: best folder and name, then the oldest. */
    fun suggestKeep(copies: List<MediaItemEntity>): MediaItemEntity =
        copies.sortedWith(compareBy<MediaItemEntity>({ keepScore(it) }, { it.addedAt })).first()
}