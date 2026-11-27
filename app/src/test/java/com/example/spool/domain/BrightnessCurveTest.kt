package com.example.spool.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessCurveTest {
    @Test
    fun `the ends are the dimmest and brightest settings`() {
        assertEquals(BrightnessCurve.MIN_LINEAR, BrightnessCurve.toLinear(0f), 0.0001f)
        assertEquals(1f, BrightnessCurve.toLinear(1f), 0.0001f)
    }

    @Test
    fun `going to the phone scale and back gives the same level`() {
        listOf(0.2f, 0.5f, 0.8f).forEach { level ->
            val back = BrightnessCurve.toLevel(BrightnessCurve.toLinear(level))
            assertEquals(level, back, 0.001f)
        }
    }

    @Test
    fun `more swipe is always brighter`() {
        var last = -1f
        for (i in 0..10) {
            val linear = BrightnessCurve.toLinear(i / 10f)
            assertTrue(linear >= last)
            last = linear
        }
    }
}