package com.example.spool.domain

import com.example.spool.data.MediaItemEntity
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RandomQueueTest {

    private fun songs(n: Int) = (1..n).map {
        MediaItemEntity("uri$it", "T$it", "A", "", 1000L, false, "a t$it")
    }

    @Test
    fun `plays every song once per round`() {
        val q = RandomQueue(songs(10), Random(1))
        repeat(3) {
            val round = List(10) { q.next()!!.uri }
            assertEquals(10, round.toSet().size)
        }
    }

    @Test
    fun `never repeats back to back, even across rounds`() {
        val q = RandomQueue(songs(5), Random(2))
        val played = List(200) { q.next()!!.uri }
        played.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
    }

    @Test
    fun `previous goes back and next replays history`() {
        val q = RandomQueue(songs(10), Random(3))
        val a = q.next()!!
        val b = q.next()!!
        val c = q.next()!!
        assertEquals(b, q.previous())
        assertEquals(a, q.previous())
        assertNull(q.previous())
        assertEquals(b, q.next())
        assertEquals(c, q.next())
    }

    @Test
    fun `single song keeps playing`() {
        val q = RandomQueue(songs(1), Random(4))
        repeat(5) { assertEquals("uri1", q.next()!!.uri) }
    }

    @Test
    fun `empty queue returns null`() {
        val q = RandomQueue(emptyList(), Random(5))
        assertNull(q.next())
        assertNull(q.previous())
    }

    @Test
    fun `startWith plays that song first then continues without repeating it`() {
        val list = songs(5)
        val q = RandomQueue(list, Random(6))
        assertEquals(list[2], q.startWith(list[2]))
        val rest = List(4) { q.next()!!.uri }
        assertEquals(4, rest.toSet().size)
        assertEquals(false, rest.contains(list[2].uri))
    }

    @Test
    fun `hasNextInRound is false once every song has played`() {
        val q = RandomQueue(songs(3), Random(7))
        assertEquals(true, q.hasNextInRound())
        repeat(3) { q.next() }
        assertEquals(false, q.hasNextInRound())
        q.previous()
        assertEquals(true, q.hasNextInRound())
    }
    @Test
    fun `snapshot splits current, remaining and played`() {
        val q = RandomQueue(songs(5), Random(8))
        assertEquals(0, q.snapshot().played.size)
        repeat(3) { q.next() }
        val s = q.snapshot()
        assertEquals(q.current, s.current)
        assertEquals(2, s.played.size)
        assertEquals(2, s.remaining.size)
        assertEquals(5, s.total)
    }

    @Test
    fun `starting with a chosen song moves it out of remaining`() {
        val list = songs(4)
        val q = RandomQueue(list, Random(9))
        q.startWith(list[2])
        val s = q.snapshot()
        assertEquals(list[2], s.current)
        assertEquals(3, s.remaining.size)
        assertEquals(false, s.remaining.contains(list[2]))
    }

    @Test
    fun `a saved queue restores with the same current, remaining and played songs`() {
        val list = songs(6)
        val q = RandomQueue(list, Random(10))
        repeat(3) { q.next() }
        val before = q.snapshot()

        val restored = RandomQueue.restore(q.state(), list, Random(11))!!
        val after = restored.snapshot()
        assertEquals(before.current, after.current)
        assertEquals(before.remaining, after.remaining)
        assertEquals(before.played.map { it.uri }, after.played.map { it.uri })
    }

    @Test
    fun `restore drops songs that no longer exist but keeps the current one`() {
        val list = songs(5)
        val q = RandomQueue(list, Random(12))
        repeat(2) { q.next() }
        val current = q.current!!
        val gone = list.first { it.uri != current.uri }

        val restored = RandomQueue.restore(q.state(), list.filter { it.uri != gone.uri }, Random(13))!!
        assertEquals(current, restored.current)
        assertEquals(false, restored.snapshot().remaining.contains(gone))
    }

    @Test
    fun `restore gives nothing when the current song is gone`() {
        val list = songs(4)
        val q = RandomQueue(list, Random(14))
        q.next()
        val current = q.current!!
        assertEquals(null, RandomQueue.restore(q.state(), list.filter { it.uri != current.uri }))
    }
}