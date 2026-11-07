package com.example.spool.domain

import com.example.spool.data.MediaItemEntity
import kotlin.random.Random

/**
 * The play queue. It plays in list order or in shuffled order, and the user can switch at any
 * time. In shuffle mode each song plays once per round and never twice in a row. Songs added
 * with "Play next" or "Add to queue" jump the line in either mode.
 * (The name is historical: shuffling is now just one of its modes.)
 */
class RandomQueue(
    songs: List<MediaItemEntity>,
    private val random: Random = Random.Default,
    shuffle: Boolean = true
) {
    /** What the queue screen shows. */
    data class Snapshot(
        val current: MediaItemEntity?,
        val queuedByUser: List<MediaItemEntity>,
        val remaining: List<MediaItemEntity>,
        val played: List<MediaItemEntity>,
        val shuffled: Boolean
    ) {
        val total: Int
            get() = (if (current != null) 1 else 0) +
                    queuedByUser.size + remaining.size + played.size
    }

    /** Everything needed to rebuild the queue after the app was closed. */
    data class State(
        val all: List<String>,
        val unplayed: List<String>,
        val history: List<String>,
        val index: Int,
        val upNext: List<String> = emptyList(),
        val shuffle: Boolean = true
    )

    var shuffle: Boolean = shuffle

    private val byName = MediaOrder.byName
    private val all = songs.distinctBy { it.uri }
    private val ordered = all.sortedWith(byName)
    private val orderPos: Map<String, Int> = ordered.mapIndexed { i, s -> s.uri to i }.toMap()
    private val unplayed = all.toMutableList()
    private val history = mutableListOf<MediaItemEntity>()
    private val upNext = mutableListOf<MediaItemEntity>()
    private var index = -1

    val size: Int get() = all.size
    val current: MediaItemEntity? get() = history.getOrNull(index)

    /**
     * Next song: replays forward through history if you went back, then anything the user
     * queued, then the next one in order (or at random when shuffling).
     */
    fun next(): MediaItemEntity? {
        if (index < history.lastIndex) {
            index++
            return history[index]
        }
        val pick = if (upNext.isNotEmpty()) upNext.removeAt(0) else pickFromPool() ?: return null
        history.add(pick)
        index = history.lastIndex
        return pick
    }

    /** Previous song, or null if already at the first one. */
    fun previous(): MediaItemEntity? {
        if (index <= 0) return null
        index--
        return history[index]
    }

    /** Plays a chosen song now; what follows depends on the mode. */
    fun startWith(song: MediaItemEntity): MediaItemEntity {
        while (history.size > index + 1) history.removeAt(history.lastIndex) // drop forward history
        unplayed.removeAll { it.uri == song.uri }
        upNext.removeAll { it.uri == song.uri }
        history.add(song)
        index = history.lastIndex
        return song
    }

    /** "Play next" puts the song at the front of the line, "Add to queue" at the end. */
    fun enqueue(song: MediaItemEntity, atFront: Boolean) {
        upNext.removeAll { it.uri == song.uri }
        if (atFront) upNext.add(0, song) else upNext.add(song)
    }

    /** True if another song is available without starting a new round. */
    fun hasNextInRound(): Boolean =
        index < history.lastIndex || unplayed.isNotEmpty() || upNext.isNotEmpty()

    fun find(uri: String): MediaItemEntity? =
        all.firstOrNull { it.uri == uri } ?: upNext.firstOrNull { it.uri == uri }

    /** The queue as the user sees it. */
    fun snapshot(): Snapshot {
        val now = current
        val queued = upNext.toList()
        return if (shuffle) shuffledSnapshot(now, queued) else orderedSnapshot(now, queued)
    }

    private fun shuffledSnapshot(now: MediaItemEntity?, queued: List<MediaItemEntity>): Snapshot {
        val queuedUris = queued.mapTo(HashSet()) { it.uri }
        val unplayedUris = unplayed.mapTo(HashSet()) { it.uri }
        val remaining = ordered.filter {
            it.uri in unplayedUris && it.uri != now?.uri && it.uri !in queuedUris
        }
        val playedUris = all
            .filter { it.uri !in unplayedUris && it.uri != now?.uri }
            .mapTo(HashSet()) { it.uri }
        // Most recently played first
        val played = history.asReversed()
            .filter { it.uri in playedUris }
            .distinctBy { it.uri }
        return Snapshot(now, queued, remaining, played, shuffled = true)
    }

    private fun orderedSnapshot(now: MediaItemEntity?, queued: List<MediaItemEntity>): Snapshot {
        val queuedUris = queued.mapTo(HashSet()) { it.uri }
        val pos = positionInList()
        val after = ordered.drop(pos + 1)
            .filter { it.uri != now?.uri && it.uri !in queuedUris }
        val before = ordered.take(pos + 1)
            .filter { it.uri != now?.uri && it.uri !in queuedUris }
        return Snapshot(now, queued, after, before, shuffled = false)
    }

    /** A copy of the state for saving. Only the most recent history is kept. */
    fun state(): State {
        val drop = (history.size - MAX_SAVED_HISTORY).coerceAtLeast(0)
        return State(
            all = all.map { it.uri },
            unplayed = unplayed.map { it.uri },
            history = history.drop(drop).map { it.uri },
            index = (index - drop).coerceAtLeast(-1),
            upNext = upNext.map { it.uri },
            shuffle = shuffle
        )
    }

    private fun pickFromPool(): MediaItemEntity? {
        if (all.isEmpty()) return null
        return if (shuffle) pickRandom() else pickInOrder()
    }

    private fun pickInOrder(): MediaItemEntity {
        val pick = ordered[(positionInList() + 1) % ordered.size]
        unplayed.remove(pick) // so switching to shuffle later skips what already played
        return pick
    }

    /** Where we are in the sorted list: the current song, or the last list song that played. */
    private fun positionInList(): Int {
        for (i in index downTo 0) {
            val pos = orderPos[history[i].uri]
            if (pos != null) return pos
        }
        return -1
    }

    private fun pickRandom(): MediaItemEntity {
        if (unplayed.isEmpty()) unplayed.addAll(all) // new round
        val last = history.lastOrNull()
        val candidates =
            if (unplayed.size > 1 && last != null) unplayed.filter { it.uri != last.uri }
            else unplayed
        val pick = candidates[random.nextInt(candidates.size)]
        unplayed.remove(pick)
        return pick
    }

    companion object {
        private const val MAX_SAVED_HISTORY = 300

        /** Rebuilds a saved queue. Songs that no longer exist are dropped. */
        fun restore(
            state: State,
            songs: List<MediaItemEntity>,
            random: Random = Random.Default
        ): RandomQueue? {
            val byUri = songs.associateBy { it.uri }
            val pool = state.all.mapNotNull { byUri[it] }
            val currentUri = state.history.getOrNull(state.index) ?: return null
            if (pool.isEmpty() || byUri[currentUri] == null) return null

            val queue = RandomQueue(pool, random, state.shuffle)
            queue.unplayed.clear()
            queue.unplayed.addAll(state.unplayed.mapNotNull { byUri[it] })
            queue.history.addAll(state.history.mapNotNull { byUri[it] })
            queue.upNext.addAll(state.upNext.mapNotNull { byUri[it] })
            queue.index = queue.history.indexOfLast { it.uri == currentUri }
            return queue
        }
    }
}