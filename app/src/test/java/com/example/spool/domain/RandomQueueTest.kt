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
}