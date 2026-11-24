package com.example.spool.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun `timed lines are read and sorted`() {
        val doc = LrcParser.parse("[00:15.50]World\n[00:12.00]Hello")
        assertTrue(doc.synced)
        assertEquals(listOf("Hello", "World"), doc.lines.map { it.text })
        assertEquals(listOf(12_000L, 15_500L), doc.lines.map { it.timeMs })
    }

    @Test
    fun `a line with several times is repeated`() {
        val doc = LrcParser.parse("[00:10.00][00:40.00]Chorus")
        assertEquals(listOf(10_000L, 40_000L), doc.lines.map { it.timeMs })
        assertEquals(listOf("Chorus", "Chorus"), doc.lines.map { it.text })
    }

    @Test
    fun `info tags are skipped and the offset is applied`() {
        val doc = LrcParser.parse("[ar:Someone]\n[ti:Song]\n[offset:500]\n[00:10.00]Hi")
        assertEquals(1, doc.lines.size)
        assertEquals(9_500L, doc.lines[0].timeMs)
    }

    @Test
    fun `word timing tags are removed`() {
        val doc = LrcParser.parse("[00:01.00]<00:01.00>Hel<00:01.50>lo")
        assertEquals("Hello", doc.lines[0].text)
    }

    @Test
    fun `plain lyrics keep their lines and are not synced`() {
        val doc = LrcParser.parse("\nLine one\n\nLine two\n")
        assertFalse(doc.synced)
        assertEquals(listOf("Line one", "", "Line two"), doc.lines.map { it.text })
    }

    @Test
    fun `the current line follows the position`() {
        val doc = LrcParser.parse("[00:10.00]A\n[00:20.00]B\n[00:30.00]C")
        assertEquals(-1, doc.indexAt(5_000))
        assertEquals(0, doc.indexAt(10_000))
        assertEquals(1, doc.indexAt(25_000))
        assertEquals(2, doc.indexAt(99_000))
    }
}