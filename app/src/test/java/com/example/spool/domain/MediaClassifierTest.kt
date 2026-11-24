package com.example.spool.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaClassifierTest {
    private fun kind(name: String, seconds: Int) =
        MediaClassifier.classifyVideo(name, seconds * 1000L)

    @Test
    fun `random names under 90 seconds are shorts`() {
        assertEquals(MediaClassifier.SHORT, kind("VID_20240101_123456.mp4", 30))
        assertEquals(MediaClassifier.SHORT, kind("7a8f9c1b2d3e.mp4", 45))
        assertEquals(MediaClassifier.SHORT, kind("xkqzjwvtp.mp4", 20))
        assertEquals(MediaClassifier.SHORT, kind("tiktok_7291234567890.mp4", 15))
    }

    @Test
    fun `real names are videos even when short`() {
        assertEquals(MediaClassifier.VIDEO, kind("Burna Boy - Last Last.mp4", 40))
        assertEquals(MediaClassifier.VIDEO, kind("Wizkid.mp4", 30))
        assertEquals(MediaClassifier.VIDEO, kind("Davido - Unavailable (Official Video).mp4", 200))
    }

    @Test
    fun `long videos are videos whatever the name`() {
        assertEquals(MediaClassifier.VIDEO, kind("VID_20240101_123456.mp4", 300))
    }

    @Test
    fun `90 seconds is the boundary`() {
        assertEquals(MediaClassifier.VIDEO, MediaClassifier.classifyVideo("xkqzjwvtp.mp4", 90_000))
        assertEquals(MediaClassifier.SHORT, MediaClassifier.classifyVideo("xkqzjwvtp.mp4", 89_999))
    }
    @Test
    fun `episodes are recognised by name or by a TV folder`() {
        assertEquals(MediaClassifier.SHOW, MediaClassifier.classifyVideo("Breaking.Bad.S01E02.720p.mkv", 45 * 60_000L))
        assertEquals(MediaClassifier.SHOW, MediaClassifier.classifyVideo("Game of Thrones 1x03.mp4", 55 * 60_000L))
        assertEquals(
            MediaClassifier.SHOW,
            MediaClassifier.classifyVideo("Episode.mp4", 40 * 60_000L, "Series/Money Heist/Season 1/")
        )
    }

    @Test
    fun `movies are long, or released-looking, or in a movies folder`() {
        assertEquals(MediaClassifier.MOVIE, MediaClassifier.classifyVideo("Inception (2010) 1080p BluRay.mkv", 148 * 60_000L))
        assertEquals(MediaClassifier.MOVIE, MediaClassifier.classifyVideo("Some Long Documentary.mp4", 80 * 60_000L))
        assertEquals(MediaClassifier.MOVIE, MediaClassifier.classifyVideo("Weekend Recap.mp4", 45 * 60_000L, "Movies/"))
    }

    @Test
    fun `music videos stay music videos`() {
        assertEquals(MediaClassifier.VIDEO, MediaClassifier.classifyVideo("Burna Boy - Last Last.mp4", 3 * 60_000L))
        assertEquals(MediaClassifier.VIDEO, MediaClassifier.classifyVideo("Davido - Unavailable (Official Video).mp4", 200_000L))
    }
}