package com.leo.forge.domain.volume

import com.leo.forge.domain.model.Muscle

/**
 * Weekly hard-set landmarks per muscle.
 *
 *  MV  - maintenance volume, enough to hold size
 *  MEV - minimum effective volume, where a mesocycle starts
 *  MAV - adaptive volume, the productive middle the ramp lives in
 *  MRV - maximum recoverable volume, the ceiling a mesocycle ends at
 *
 * These are population starting points. [com.leo.forge.domain.progression.VolumeAutoregulator]
 * moves the user's personal numbers away from them as real feedback arrives, so a wrong
 * default costs at most a week.
 */
data class Landmarks(val mv: Int, val mev: Int, val mav: Int, val mrv: Int)

object VolumeLandmarks {

    private val table: Map<Muscle, Landmarks> = mapOf(
        Muscle.CHEST to Landmarks(mv = 4, mev = 8, mav = 16, mrv = 22),
        Muscle.FRONT_DELTS to Landmarks(0, 0, 8, 12),
        Muscle.SIDE_DELTS to Landmarks(6, 8, 18, 26),
        Muscle.REAR_DELTS to Landmarks(0, 6, 16, 24),
        Muscle.TRICEPS to Landmarks(4, 6, 14, 20),
        Muscle.LATS to Landmarks(6, 10, 18, 24),
        Muscle.UPPER_BACK to Landmarks(6, 10, 18, 24),
        Muscle.TRAPS to Landmarks(0, 4, 12, 20),
        Muscle.BICEPS to Landmarks(4, 8, 16, 22),
        Muscle.FOREARMS to Landmarks(0, 2, 10, 16),
        Muscle.QUADS to Landmarks(6, 8, 16, 20),
        Muscle.HAMSTRINGS to Landmarks(4, 6, 13, 18),
        Muscle.GLUTES to Landmarks(0, 4, 12, 16),
        Muscle.CALVES to Landmarks(6, 8, 16, 20),
        Muscle.ADDUCTORS to Landmarks(0, 0, 8, 12),
        Muscle.ABS to Landmarks(0, 6, 16, 25),
        Muscle.LOWER_BACK to Landmarks(0, 2, 8, 12),
    )

    fun of(muscle: Muscle): Landmarks = table.getValue(muscle)

    /**
     * Sets for [muscle] in a given week of a mesocycle: a linear ramp from MEV toward MRV
     * across the accumulation weeks, then a deload at roughly half of MEV.
     *
     * @param weekIndex zero-based
     * @param totalWeeks including the deload week
     */
    fun plannedSets(muscle: Muscle, weekIndex: Int, totalWeeks: Int, personal: Landmarks? = null): Int {
        val lm = personal ?: of(muscle)
        val deloadWeek = totalWeeks - 1
        if (weekIndex >= deloadWeek) return (lm.mev / 2).coerceAtLeast(if (lm.mev == 0) 0 else 2)
        val accumulationWeeks = deloadWeek.coerceAtLeast(1)
        if (accumulationWeeks == 1) return lm.mev
        val t = weekIndex.toFloat() / (accumulationWeeks - 1).toFloat()
        // Stop just shy of true MRV: hitting the ceiling is what makes the next meso start dug-in.
        val top = lm.mev + ((lm.mrv - lm.mev) * 0.85f)
        return Math.round(lm.mev + (top - lm.mev) * t)
    }
}
