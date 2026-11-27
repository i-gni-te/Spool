package com.example.spool.domain

import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateRulesTest {
    private fun item(
        id: String,
        folder: String,
        title: String = "Song",
        duration: Long = 200_000L,
        added: Long = 0L
    ) = MediaItemEntity(
        uri = id, title = title, artist = "A", album = "", durationMs = duration,
        isVideo = false, searchKey = "k", kind = MediaClassifier.SONG,
        addedAt = added, folder = folder
    )

    @Test
    fun `same size and length make a candidate group`() {
        val a = item("1", "Music/")
        val b = item("2", "Download/")
        val c = item("3", "Music/", duration = 100_000L)
        val sizes = mapOf("1" to 5_000_000L, "2" to 5_000_000L, "3" to 5_000_000L)
        val groups = DuplicateRules.candidateGroups(listOf(a, b, c), sizes)
        assertEquals(1, groups.size)
        assertEquals(setOf("1", "2"), groups[0].map { it.uri }.toSet())
    }

    @Test
    fun `tiny files and files of different size are ignored`() {
        val a = item("1", "Music/")
        val b = item("2", "Download/")
        assertEquals(
            0,
            DuplicateRules.candidateGroups(listOf(a, b), mapOf("1" to 5_000L, "2" to 5_000L)).size
        )
        assertEquals(
            0,
            DuplicateRules.candidateGroups(
                listOf(a, b), mapOf("1" to 5_000_000L, "2" to 6_000_000L)
            ).size
        )
    }

    @Test
    fun `keep the copy in a real folder rather than WhatsApp or Download`() {
        val keep = DuplicateRules.suggestKeep(
            listOf(
                item("w", "Android/media/com.whatsapp/WhatsApp/Media/"),
                item("d", "Download/"),
                item("m", "Music/")
            )
        )
        assertEquals("m", keep.uri)
    }

    @Test
    fun `with equal folders keep the oldest`() {
        val keep = DuplicateRules.suggestKeep(
            listOf(item("new", "Music/", added = 200), item("old", "Music/", added = 100))
        )
        assertEquals("old", keep.uri)
    }

    @Test
    fun `a readable name beats a scrambled one`() {
        val keep = DuplicateRules.suggestKeep(
            listOf(
                item("a", "Music/", title = "x7f3k9qz"),
                item("b", "Music/", title = "Last Last")
            )
        )
        assertEquals("b", keep.uri)
    }
}