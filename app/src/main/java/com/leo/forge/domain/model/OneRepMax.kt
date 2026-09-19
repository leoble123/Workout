package com.leo.forge.domain.model

import kotlin.math.pow

/**
 * Estimated 1RM.
 *
 * Brzycki is the better fit in the low-rep range; Epley is the saner one as reps climb,
 * because Brzycki's 36/(37-reps) term runs away toward its asymptote (a 20-rep set at
 * 100 kg reads as 212 kg, against Epley's 167). Hypertrophy work lives at 8-15 reps, so
 * the estimate blends from Brzycki toward Epley as reps rise, and stops extrapolating
 * past [REP_CEILING] where every one of these formulas is noise.
 */
object OneRepMax {

    fun epley(weight: Double, reps: Int): Double =
        if (reps <= 1) weight else weight * (1.0 + reps / 30.0)

    fun brzycki(weight: Double, reps: Int): Double =
        if (reps <= 1) weight else weight * (36.0 / (37.0 - reps).coerceAtLeast(1.0))

    /** Past this many effective reps the estimate stops climbing rather than inventing strength. */
    const val REP_CEILING = 15

    /** @param rir reps left in reserve; counted as extra reps performed. */
    fun estimate(weight: Double, reps: Int, rir: Int? = null): Double {
        if (weight <= 0.0 || reps <= 0) return 0.0
        val effective = (reps + (rir ?: 0)).coerceAtMost(REP_CEILING)
        if (effective <= 1) return weight
        val b = brzycki(weight, effective)
        val e = epley(weight, effective)
        // 0 at <=5 reps (all Brzycki), 1 at >=10 reps (all Epley).
        val towardEpley = ((effective - 5).coerceIn(0, 5)) / 5.0
        return b * (1 - towardEpley) + e * towardEpley
    }

    /** Load that should allow [targetReps] at [targetRir], given an estimated max. */
    fun loadFor(e1rm: Double, targetReps: Int, targetRir: Int): Double {
        val effective = targetReps + targetRir
        if (effective <= 1) return e1rm
        return e1rm / (1.0 + effective / 30.0)
    }

    /** Fraction of 1RM a set at [reps] represents. Used for warm-up ramps. */
    fun percentOfMax(reps: Int): Double = 1.0 / (1.0 + reps / 30.0)

    fun roundToIncrement(value: Double, increment: Double): Double {
        if (increment <= 0.0) return value
        return (value / increment).let { Math.round(it).toDouble() } * increment
    }

    @Suppress("unused")
    fun wilks(total: Double, bodyweightKg: Double, male: Boolean): Double {
        val c = if (male) doubleArrayOf(-216.0475144, 16.2606339, -0.002388645, -0.00113732, 7.01863E-06, -1.291E-08)
        else doubleArrayOf(594.31747775582, -27.23842536447, 0.82112226871, -0.00930733913, 4.731582E-05, -9.054E-08)
        var denom = c[0]
        for (i in 1..5) denom += c[i] * bodyweightKg.pow(i)
        return total * 500.0 / denom
    }
}
