package com.example.spool.domain

import com.example.spool.data.MediaItemEntity
import kotlin.random.Random

/**
 * Picks the next song at random from songs not yet played this round.
 * Once every song has played, a new round starts. A song never plays twice
 * in a row, even across rounds (unless it is the only song).
 */
class RandomQueue(
    songs: List<MediaItemEntity>,
    private val random: Random = Random.Default
) {
    /** What the queue screen shows. */
    data class Snapshot(
        val current: MediaItemEntity?,
        val remaining: List<MediaItemEntity>,
        val played: List<MediaItemEntity>
    ) {
        val total: Int get() = (if (current != null) 1 else 0) + remaining.size + played.size
    }

    /** Everything needed to rebuild the queue after the app was closed. */
    data class State(
        val all: List<String>,
        val unplayed: List<String>,
        val history: List<String>,
        val index: Int
    )

    private val all = songs.distinctBy { it.uri }
    private val unplayed = all.toMutableList()
    private val history = mutableListOf<MediaItemEntity>()
    private var index = -1

    val size: Int get() = all.size
    val current: MediaItemEntity? get() = history.getOrNull(index)

    /** Next song: replays forward through history if you went back, otherwise a new random pick. */
    fun next(): MediaItemEntity? {
        if (all.isEmpty()) return null
        if (index < history.lastIndex) {
            index++
            return history[index]
        }
        val pick = pickRandom()
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

    /** Plays a chosen song first; the shuffle continues from the rest without repeating it. */
    fun startWith(song: MediaItemEntity): MediaItemEntity {
        while (history.size > index + 1) history.removeAt(history.lastIndex) // drop forward history
        unplayed.removeAll { it.uri == song.uri }
        history.add(song)
        index = history.lastIndex
        return song
    }

    /** True if another song is available without starting a new round. */
    fun hasNextInRound(): Boolean = index < history.lastIndex || unplayed.isNotEmpty()

    fun find(uri: String): MediaItemEntity? = all.firstOrNull { it.uri == uri }

    /** The queue as the user sees it: playing now, still to play, already played. */
    fun snapshot(): Snapshot {
        val now = current
        val unplayedUris = unplayed.mapTo(HashSet()) { it.uri }
        val byName = compareBy<MediaItemEntity>({ it.artist.lowercase() }, { it.title.lowercase() })

        val remaining = all
            .filter { it.uri in unplayedUris && it.uri != now?.uri }
            .sortedWith(byName)
        val playedUris = all
            .filter { it.uri !in unplayedUris && it.uri != now?.uri }
            .mapTo(HashSet()) { it.uri }
        // Most recently played first
        val played = history.asReversed()
            .filter { it.uri in playedUris }
            .distinctBy { it.uri }
        return Snapshot(now, remaining, played)
    }

    /** A copy of the state for saving. Only the most recent history is kept. */
    fun state(): State {
        val drop = (history.size - MAX_SAVED_HISTORY).coerceAtLeast(0)
        return State(
            all = all.map { it.uri },
            unplayed = unplayed.map { it.uri },
            history = history.drop(drop).map { it.uri },
            index = (index - drop).coerceAtLeast(-1)
        )
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

            val queue = RandomQueue(pool, random)
            queue.unplayed.clear()
            queue.unplayed.addAll(state.unplayed.mapNotNull { byUri[it] })
            queue.history.addAll(state.history.mapNotNull { byUri[it] })
            queue.index = queue.history.indexOfLast { it.uri == currentUri }
            return queue
        }
    }
}