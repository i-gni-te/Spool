package com.example.spool.domain

import kotlin.math.max
import kotlin.math.sqrt

/**
 * The swipe works on a "level" from 0 to 1 that looks evenly spaced to the eye. The phone's
 * brightness is a different scale, so this converts between the two.
 */
object BrightnessCurve {
    const val MIN_LINEAR = 0.01f

    /** The phone's brightness (0.01 to 1) for a swipe level. */
    fun toLinear(level: Float): Float {
        val l = level.coerceIn(0f, 1f)
        return max(MIN_LINEAR, l * l)
    }

    /** The swipe level for the phone's current brightness. */
    fun toLevel(linear: Float): Float = sqrt(linear.coerceIn(MIN_LINEAR, 1f))
}