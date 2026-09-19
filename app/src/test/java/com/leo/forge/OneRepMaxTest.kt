package com.leo.forge

import com.leo.forge.domain.model.OneRepMax
import org.junit.Assert.*
import org.junit.Test

class OneRepMaxTest {

    @Test
    fun `a single rep is its own max`() {
        assertEquals(100.0, OneRepMax.estimate(100.0, 1), 0.001)
    }

    @Test
    fun `more reps at the same load means a higher estimated max`() {
        assertTrue(OneRepMax.estimate(100.0, 8) > OneRepMax.estimate(100.0, 5))
    }

    @Test
    fun `reps left in reserve count toward the estimate`() {
        assertTrue(OneRepMax.estimate(100.0, 8, rir = 3) > OneRepMax.estimate(100.0, 8, rir = 0))
    }

    @Test
    fun `high rep sets do not run away the way raw Epley does`() {
        // Epley alone would put a 20-rep set at 1.67x load; the blend must be more conservative.
        val blended = OneRepMax.estimate(100.0, 20)
        assertTrue("blend was $blended", blended < OneRepMax.epley(100.0, 20))
    }

    @Test
    fun `junk input does not produce a number`() {
        assertEquals(0.0, OneRepMax.estimate(0.0, 10), 0.001)
        assertEquals(0.0, OneRepMax.estimate(100.0, 0), 0.001)
    }

    @Test
    fun `rounding snaps to the implement's real increment`() {
        assertEquals(82.5, OneRepMax.roundToIncrement(83.1, 2.5), 0.001)
        assertEquals(85.0, OneRepMax.roundToIncrement(83.1, 5.0), 0.001)
    }

    @Test
    fun `loadFor is the rough inverse of estimate`() {
        val e1rm = OneRepMax.estimate(100.0, 8, rir = 2)
        val load = OneRepMax.loadFor(e1rm, targetReps = 8, targetRir = 2)
        assertEquals(100.0, load, 1.5)
    }
}
