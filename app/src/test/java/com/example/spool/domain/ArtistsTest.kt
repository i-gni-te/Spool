package com.example.spool.domain

import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistParserTest {
    private fun split(s: String) = ArtistParser.split(s)

    @Test
    fun `commas split credited artists`() {
        assertEquals(listOf("Spice", "Shaggy", "Sean Paul"), split("Spice, Shaggy, Sean Paul"))
        assertEquals(listOf("Mike Dimes", "Denzel Curry"), split("Mike Dimes, Denzel Curry"))
    }

    @Test
    fun `feat, x and ampersand split`() {
        assertEquals(listOf("Drake", "Future"), split("Drake feat. Future"))
        assertEquals(listOf("Drake", "Future"), split("Drake (feat. Future)"))
        assertEquals(listOf("Burna Boy", "Wizkid"), split("Burna Boy x Wizkid"))
        assertEquals(listOf("Burna Boy", "Wizkid"), split("Burna Boy & Wizkid"))
    }

    @Test
    fun `known single acts stay whole`() {
        assertEquals(listOf("Tyler, The Creator"), split("Tyler, The Creator"))
        assertEquals(
            listOf("Tyler, The Creator", "Frank Ocean"),
            split("Tyler, The Creator, Frank Ocean")
        )
        assertEquals(listOf("Earth, Wind & Fire"), split("Earth, Wind & Fire"))
        assertEquals(listOf("Simon & Garfunkel"), split("Simon & Garfunkel"))
    }

    @Test
    fun `slashes and trailing letters stay whole`() {
        assertEquals(listOf("AC/DC"), split("AC/DC"))
        assertEquals(listOf("Malcolm X"), split("Malcolm X"))
    }

    @Test
    fun `unknown and duplicates are dropped`() {
        assertEquals(emptyList<String>(), split("Unknown artist"))
        assertEquals(listOf("Drake"), split("Drake, drake"))
    }
}

class ArtistIndexTest {
    private fun item(id: String, artist: String, kind: Int = MediaClassifier.SONG) =
        MediaItemEntity(id, id, artist, "", 1000L, kind != MediaClassifier.SONG, "k $id", kind)

    @Test
    fun `counts each credited artist and skips shorts`() {
        val list = ArtistIndex.build(
            listOf(
                item("1", "Spice, Shaggy"),
                item("2", "Shaggy"),
                item("3", "Shaggy", MediaClassifier.SHORT)
            )
        )
        assertEquals("Shaggy", list[0].name)
        assertEquals(2, list[0].total)
        assertEquals("Spice", list[1].name)
        assertEquals(1, list[1].total)
    }
}