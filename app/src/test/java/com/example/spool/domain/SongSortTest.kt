package com.example.spool.domain

import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import com.example.spool.data.PlayStat
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSortTest {
    private fun song(id: String, title: String, added: Long) =
        MediaItemEntity(id, title, "A", "", 1000L, false, "k $id", MediaClassifier.SONG, added)

    private val list = listOf(song("1", "B", 100), song("2", "A", 300), song("3", "C", 200))

    @Test
    fun `recently added puts the newest first`() {
        val sorted = list.sortedWith(SongSort.RECENTLY_ADDED.comparator(emptyMap()))
        assertEquals(listOf("2", "3", "1"), sorted.map { it.uri })
    }

    @Test
    fun `most played puts the most played first, then A to Z`() {
        val stats = mapOf(
            "1" to PlayStat("1", plays = 5, lastPlayedAt = 10),
            "3" to PlayStat("3", plays = 2, lastPlayedAt = 20)
        )
        val sorted = list.sortedWith(SongSort.MOST_PLAYED.comparator(stats))
        assertEquals(listOf("1", "3", "2"), sorted.map { it.uri })
    }

    @Test
    fun `in order play follows the chosen sort`() {
        val queue = RandomQueue(
            list, Random(1), shuffle = false,
            order = SongSort.RECENTLY_ADDED.comparator(emptyMap())
        )
        queue.startWith(list.first { it.uri == "2" })
        assertEquals("3", queue.next()!!.uri)
        assertEquals("1", queue.next()!!.uri)
    }
    @Test
    fun `favorites sort lists by name`() {
        val sorted = list.sortedWith(SongSort.FAVORITES.comparator(emptyMap()))
        assertEquals(listOf("2", "1", "3"), sorted.map { it.uri })
    }
}