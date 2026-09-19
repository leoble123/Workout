package com.leo.forge.domain.progression

import androidx.compose.runtime.Immutable
import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.domain.model.Load
import com.leo.forge.domain.model.MovementPattern
import com.leo.forge.domain.model.Units
import kotlin.math.max

/**
 * A prescription for one set: exactly what to load and what to hit.
 *
 * [weightKg] is canonical storage; the UI renders it in the gym's unit. [rationale] is
 * carried all the way to the screen on purpose - a number you cannot interrogate is a
 * number you stop trusting, and then you are back to guessing.
 */
@Immutable
data class SetTarget(
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val targetRir: Int,
    val rationale: String,
    val isEstimateOnly: Boolean = false,
)

@Immutable
data class ExercisePrescription(
    val exerciseId: String,
    val targets: List<SetTarget>,
    val restSeconds: Int,
)

/**
 * Turns "what happened last time" into "what to do now".
 *
 * Double progression inside the exercise's rep range, autoregulated by reps-in-reserve,
 * with the RIR target tightening as the mesocycle accumulates fatigue. Load only moves once
 * the top of the range is reached, so a good day adds reps and a great day adds weight.
 *
 * All arithmetic happens in the *gym's* unit rather than in kilograms. A gym whose plates
 * are marked in kg steps in 2.5; one marked in pounds steps in 5 lb. Rounding in kg and
 * converting afterwards yields numbers nobody can load ("220.5 lb"), which forces exactly
 * the manual override this engine exists to remove.
 */
object ProgressionEngine {

    const val STARTING_RIR = 3
    const val DELOAD_RIR = 4

    /** Week 1 leaves 3 in the tank; the last accumulation week is taken to failure-ish. */
    fun rirForWeek(weekIndex: Int, totalWeeks: Int): Int {
        val deloadWeek = totalWeeks - 1
        if (weekIndex >= deloadWeek) return DELOAD_RIR
        return (STARTING_RIR - weekIndex).coerceAtLeast(0)
    }

    fun isDeloadWeek(weekIndex: Int, totalWeeks: Int): Boolean = weekIndex >= totalWeeks - 1

    fun restSecondsFor(exercise: ExerciseEntity): Int = when (exercise.pattern) {
        MovementPattern.SQUAT, MovementPattern.HINGE -> 210
        MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH,
        MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL -> 180
        MovementPattern.LUNGE, MovementPattern.CARRY -> 150
        MovementPattern.ISOLATION, MovementPattern.CORE -> 90
    }

    /**
     * @param lastSets working sets from the most recent session that trained this exercise,
     *                 ordered by set index. Empty on the exercise's first ever appearance.
     * @param setCount how many sets this week's volume ramp calls for.
     * @param units what this gym's plates and stacks are marked in.
     */
    fun prescribe(
        exercise: ExerciseEntity,
        lastSets: List<SetLogEntity>,
        setCount: Int,
        weekIndex: Int,
        totalWeeks: Int,
        repLow: Int = exercise.repLow,
        repHigh: Int = exercise.repHigh,
        units: Units = Units.KG,
        gymBarbellIncrement: Double? = null,
    ): ExercisePrescription {
        val targetRir = rirForWeek(weekIndex, totalWeeks)
        val deload = isDeloadWeek(weekIndex, totalWeeks)
        val step = Load.increment(exercise.equipment, units, exercise.loadIncrementKg, gymBarbellIncrement)
        val u = units.display

        fun show(displayValue: Double) = "${Load.format(displayValue)} $u"
        fun target(setIndex: Int, displayWeight: Double, reps: Int, rir: Int, why: String) = SetTarget(
            setIndex = setIndex,
            weightKg = Load.toKg(displayWeight, units),
            reps = reps,
            targetRir = rir,
            rationale = why,
        )

        if (lastSets.isEmpty()) {
            // Nothing to progress from, and guessing across movements is unreliable. Ask.
            return ExercisePrescription(
                exerciseId = exercise.id,
                restSeconds = restSecondsFor(exercise),
                targets = (0 until setCount).map { i ->
                    SetTarget(
                        setIndex = i,
                        weightKg = 0.0,
                        reps = repHigh,
                        targetRir = targetRir,
                        rationale = "First time logging this. Pick a load you could get " +
                            "$repLow-$repHigh reps with about $targetRir left, and it takes over next session.",
                        isEstimateOnly = true,
                    )
                },
            )
        }

        val targets = (0 until setCount).map { i ->
            // A ramped set count means later sets have no counterpart last week; fall back to
            // the final logged set, which is the most fatigued and so the safest anchor.
            val previous = lastSets.getOrNull(i) ?: lastSets.last()
            val carriedOver = i >= lastSets.size

            // Snap last week's load onto this gym's grid before reasoning about it, so a
            // history logged in another gym's unit cannot produce unloadable suggestions.
            val prev = Load.round(Load.toDisplay(previous.weightKg, units), step)

            if (deload) {
                return@map target(
                    i, Load.round(prev * 0.85, step), repLow, DELOAD_RIR,
                    "Deload: about 85% of last week's load, well short of failure. " +
                        "This is where the growth from the block actually lands.",
                )
            }

            val lastRir = previous.rir ?: targetRir
            val hitTop = previous.reps >= repHigh
            val hitRange = previous.reps >= repLow
            val effortLeft = lastRir - targetRir

            when {
                // Cleared the top of the range with room to spare: a double jump is earned.
                hitTop && effortLeft >= 2 && step > 0 -> target(
                    i, prev + step * 2, repLow, targetRir,
                    "Last time: ${previous.reps} reps @ ${show(prev)} with $lastRir left. " +
                        "Topped the range with $effortLeft spare, so up ${show(step * 2)}.",
                )

                // Cleared the top of the range: one increment, reps back to the floor.
                hitTop && step > 0 -> target(
                    i, prev + step, repLow, targetRir,
                    "Last time: ${previous.reps} reps @ ${show(prev)}. " +
                        "Top of the range, so up ${show(step)} and back to $repLow.",
                )

                // Bodyweight or banded, where the load cannot move: keep adding reps.
                hitTop -> target(
                    i, prev, previous.reps + 1, targetRir,
                    "No loadable increment here, so add a rep: ${previous.reps + 1}.",
                )

                // Inside the range: add a rep at the same load. The common case.
                hitRange -> target(
                    i, prev, (previous.reps + 1).coerceAtMost(repHigh), targetRir,
                    "Last time: ${previous.reps} reps @ ${show(prev)}. Same load, one more rep" +
                        if (targetRir < lastRir) " and a rep closer to failure this week." else ".",
                )

                // Missed the floor badly: the load was wrong, walk it back.
                previous.reps < repLow - 2 && step > 0 -> target(
                    i, max(step, prev - step), repLow, targetRir,
                    "Only ${previous.reps} reps last time, under the $repLow floor. " +
                        "Down ${show(step)} to get back inside the range.",
                )

                // Just under: hold and try to convert.
                else -> target(
                    i, prev, repLow, targetRir,
                    "Just under the range last time. Same load, aim for $repLow clean reps.",
                )
            }.let { if (carriedOver) it.copy(rationale = it.rationale + " (Added set this week.)") else it }
        }

        return ExercisePrescription(exercise.id, targets, restSecondsFor(exercise))
    }
}
