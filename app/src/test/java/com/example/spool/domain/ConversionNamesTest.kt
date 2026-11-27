package com.example.spool.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversionNamesTest {
    @Test
    fun `a known artist goes in front of the title`() {
        assertEquals("Burna Boy - Last Last", ConversionNames.outputName("Last Last", "Burna Boy"))
    }

    @Test
    fun `no artist gives just the title`() {
        assertEquals("Last Last", ConversionNames.outputName("Last Last", null))
        assertEquals("Last Last", ConversionNames.outputName("Last Last", "  "))
    }

    @Test
    fun `characters a file name can't hold are removed`() {
        assertEquals("abc", ConversionNames.outputName("a/b:c*?", null))
    }

    @Test
    fun `trailing dots and blank names are handled`() {
        assertEquals("Song", ConversionNames.outputName("Song...", null))
        assertEquals("Audio", ConversionNames.outputName("   ", null))
    }

    @Test
    fun `very long names are shortened`() {
        assertEquals(120, ConversionNames.outputName("x".repeat(200), null).length)
    }
}