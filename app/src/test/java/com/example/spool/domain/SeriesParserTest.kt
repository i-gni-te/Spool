package com.example.spool.domain

import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesParserTest {
    @Test
    fun `reads show, season and episode from the file name`() {
        assertEquals(EpisodeInfo("Breaking Bad", 1, 2), SeriesParser.parse("Breaking.Bad.S01E02.720p.BluRay"))
        assertEquals(EpisodeInfo("Game of Thrones", 1, 3), SeriesParser.parse("Game of Thrones 1x03"))
        assertEquals(EpisodeInfo("The Office Us", 3, 10), SeriesParser.parse("the.office.us.s03e10.hdtv"))
    }

    @Test
    fun `falls back to the folder when the name has no show`() {
        assertEquals(
            EpisodeInfo("Money Heist", 2, 4),
            SeriesParser.parse("Episode 4", "TV/Money Heist/Season 2/")
        )
    }

    @Test
    fun `episodes of one show share a key and play in order`() {
        fun ep(id: String, title: String) =
            MediaItemEntity(id, title, "x", "", 1000L, true, "k", MediaClassifier.SHOW)

        val e10 = ep("a", "Show.S01E10")
        val e2 = ep("b", "show S01E02")
        val s2 = ep("c", "Show.S02E01")
        assertEquals(SeriesParser.key(e10), SeriesParser.key(e2))

        val sorted = listOf(s2, e10, e2).sortedWith(SeriesParser.orderFor(listOf(s2, e10, e2)))
        assertEquals(listOf("b", "a", "c"), sorted.map { it.uri })
        assertEquals(1, SeriesIndex.build(listOf(s2, e10, e2)).size)
    }
}