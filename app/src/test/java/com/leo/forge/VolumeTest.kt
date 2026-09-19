package com.leo.forge

import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.Pump
import com.leo.forge.domain.model.Soreness
import com.leo.forge.domain.model.Workload
import com.leo.forge.domain.progression.VolumeAutoregulator
import com.leo.forge.domain.volume.VolumeLandmarks
import org.junit.Assert.*
import org.junit.Test

class VolumeTest {

    @Test
    fun `the block starts at MEV and ramps upward without reaching MRV`() {
        val lm = VolumeLandmarks.of(Muscle.CHEST)
        val week0 = VolumeLandmarks.plannedSets(Muscle.CHEST, 0, 5)
        val week3 = VolumeLandmarks.plannedSets(Muscle.CHEST, 3, 5)
        assertEquals(lm.mev, week0)
        assertTrue("ramp should increase", week3 > week0)
        assertTrue("should stop short of MRV", week3 < lm.mrv)
    }

    @Test
    fun `the ramp is monotonic across accumulation weeks`() {
        val sets = (0..3).map { VolumeLandmarks.plannedSets(Muscle.LATS, it, 5) }
        sets.zipWithNext().forEach { (a, b) -> assertTrue("$a -> $b", b >= a) }
    }

    @Test
    fun `the final week deloads well below MEV`() {
        val lm = VolumeLandmarks.of(Muscle.QUADS)
        val deload = VolumeLandmarks.plannedSets(Muscle.QUADS, 4, 5)
        assertTrue(deload < lm.mev)
        assertTrue(deload > 0)
    }

    @Test
    fun `muscles with zero MEV do not get phantom deload sets`() {
        val sets = VolumeLandmarks.plannedSets(Muscle.FRONT_DELTS, 4, 5)
        assertEquals(0, sets)
    }

    @Test
    fun `still being sore pulls volume back rather than pushing on`() {
        val v = VolumeAutoregulator.next(Pump.AMAZING, Soreness.STILL_SORE, Workload.PRETTY_GOOD)
        assertEquals(-1, v.setChange)
    }

    @Test
    fun `an easy session with no pump and no soreness adds the most volume`() {
        val v = VolumeAutoregulator.next(Pump.NONE, Soreness.NEVER_GOT, Workload.EASY)
        assertEquals(3, v.setChange)
    }

    @Test
    fun `recovering just on time at a hard workload means you are at the ceiling`() {
        val v = VolumeAutoregulator.next(Pump.AMAZING, Soreness.HEALED_JUST_ON_TIME, Workload.PUSHED_LIMITS)
        assertEquals(0, v.setChange)
    }

    @Test
    fun `absent feedback creeps up by one rather than stalling`() {
        assertEquals(1, VolumeAutoregulator.next(null, null, null).setChange)
        assertEquals(1, VolumeAutoregulator.weekly(emptyList()).setChange)
    }

    @Test
    fun `the most conservative session in a week is the one that binds`() {
        val v = VolumeAutoregulator.weekly(
            listOf(
                Triple(Pump.NONE, Soreness.NEVER_GOT, Workload.EASY),      // +3
                Triple(Pump.AMAZING, Soreness.STILL_SORE, Workload.TOO_MUCH), // -1
            )
        )
        assertEquals(-1, v.setChange)
    }

    @Test
    fun `every verdict carries a reason the user can read`() {
        listOf(
            Triple(Pump.NONE, Soreness.NEVER_GOT, Workload.EASY),
            Triple(Pump.MODERATE, Soreness.HEALED_A_WHILE_AGO, Workload.PRETTY_GOOD),
            Triple(Pump.AMAZING, Soreness.STILL_SORE, Workload.TOO_MUCH),
        ).forEach { (p, s, w) ->
            assertTrue(VolumeAutoregulator.next(p, s, w).reason.isNotBlank())
        }
    }
}
