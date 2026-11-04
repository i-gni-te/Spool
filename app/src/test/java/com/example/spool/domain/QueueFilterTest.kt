package com.example.spool.domain

import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueFilterTest {
    private fun item(id: String, video: Boolean, kind: Int) =
        MediaItemEntity(id, id, "A", "", 1000L, video, "a $id", kind)

    private val song = item("s", false, MediaClassifier.SONG)
    private val video = item("v", true, MediaClassifier.VIDEO)
    private val short = item("c", true, MediaClassifier.SHORT)
    private val all = listOf(song, video, short)

    @Test
    fun `each scope keeps only its own kind`() {
        assertEquals(listOf(song), QueueFilter.plan(all, PlayScope.SONGS).pool)
        assertEquals(listOf(video), QueueFilter.plan(all, PlayScope.VIDEOS).pool)
        assertEquals(listOf(short), QueueFilter.plan(all, PlayScope.SHORTS).pool)
    }

    @Test
    fun `a tapped item queues only its own kind`() {
        assertEquals(listOf(short), QueueFilter.plan(all, PlayScope.SONGS, first = short).pool)
        assertEquals(listOf(song), QueueFilter.plan(all, PlayScope.AUTO, first = song).pool)
    }

    @Test
    fun `auto never includes shorts and stays on one type`() {
        repeat(20) { seed ->
            val plan = QueueFilter.plan(all, PlayScope.AUTO, null, Random(seed))
            assertNotNull(plan.anchor)
            assertEquals(1, plan.pool.size)
            assertTrue(plan.pool.none { it.kind == MediaClassifier.SHORT })
        }
    }
}