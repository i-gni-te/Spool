package com.example.spool.domain

import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import kotlin.random.Random

/** What a queue is built from. */
enum class PlayScope { AUTO, SONGS, VIDEOS, SHORTS }

/** [anchor] is the item to start with, if one is already decided. */
data class QueuePlan(val pool: List<MediaItemEntity>, val anchor: MediaItemEntity?)

object QueueFilter {
    private fun scopeOf(item: MediaItemEntity): PlayScope = when (item.kind) {
        MediaClassifier.SONG -> PlayScope.SONGS
        MediaClassifier.VIDEO -> PlayScope.VIDEOS
        else -> PlayScope.SHORTS
    }

    private fun matches(item: MediaItemEntity, scope: PlayScope): Boolean = when (scope) {
        PlayScope.SONGS -> item.kind == MediaClassifier.SONG
        PlayScope.VIDEOS -> item.kind == MediaClassifier.VIDEO
        PlayScope.SHORTS -> item.kind == MediaClassifier.SHORT
        PlayScope.AUTO -> item.kind != MediaClassifier.SHORT
    }

    /**
     * A tapped item ([first]) decides the queue: it only queues its own kind.
     * Without one, [scope] decides. AUTO picks a random start, then stays on that type.
     */
    fun plan(
        items: List<MediaItemEntity>,
        scope: PlayScope,
        first: MediaItemEntity? = null,
        random: Random = Random.Default
    ): QueuePlan {
        val effective = if (first != null) scopeOf(first) else scope
        var pool = items.filter { matches(it, effective) }
        if (first != null && pool.none { it.uri == first.uri }) pool = pool + first

        var anchor = first
        if (anchor == null && scope == PlayScope.AUTO && pool.isNotEmpty()) {
            val pick = pool[random.nextInt(pool.size)]
            anchor = pick
            pool = pool.filter { it.isVideo == pick.isVideo }
        }
        return QueuePlan(pool, anchor)
    }
}