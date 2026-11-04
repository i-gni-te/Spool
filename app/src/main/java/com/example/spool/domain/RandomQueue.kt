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
}