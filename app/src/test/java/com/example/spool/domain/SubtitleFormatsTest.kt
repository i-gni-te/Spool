package com.example.spool.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleFormatsTest {
    @Test
    fun `common subtitle files are recognised whatever the case`() {
        assertEquals("application/x-subrip", SubtitleFormats.mimeFor("Movie.2020.EN.srt"))
        assertEquals("application/x-subrip", SubtitleFormats.mimeFor("MOVIE.SRT"))
        assertEquals("text/vtt", SubtitleFormats.mimeFor("a.vtt"))
        assertEquals("text/x-ssa", SubtitleFormats.mimeFor("a.ass"))
        assertEquals("text/x-ssa", SubtitleFormats.mimeFor("a.ssa"))
    }

    @Test
    fun `other files are not subtitles`() {
        assertNull(SubtitleFormats.mimeFor("notes.txt"))
        assertNull(SubtitleFormats.mimeFor("noextension"))
    }
}