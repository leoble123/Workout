package com.leo.forge.domain.progression

import androidx.compose.runtime.Immutable
import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.domain.model.OneRepMax
import kotlin.math.max

/**
 * A prescription for one set: exactly what to load and what to hit.
 *
 * [rationale] is carried all the way to the UI on purpose. A number you cannot
 * interrogate is a number you stop trusting, and then you are back to guessing.
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
 * The scheme is double progression inside the exercise's rep range, autoregulated by
 * reps-in-reserve, with the RIR target tightening as the mesocycle accumulates fatigue.
 * Load only moves once the top of the rep range is reached, so a good day adds reps and
 * a great day adds weight - which keeps the jumps loadable on real equipment.
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
        com.leo.forge.domain.model.MovementPattern.SQUAT,
        com.leo.forge.domain.model.MovementPattern.HINGE -> 210
        com.leo.forge.domain.model.MovementPattern.HORIZONTAL_PUSH,
        com.leo.forge.domain.model.MovementPattern.VERTICAL_PUSH,
        com.leo.forge.domain.model.MovementPattern.HORIZONTAL_PULL,
        com.leo.forge.domain.model.MovementPattern.VERTICAL_PULL -> 180
        com.leo.forge.domain.model.MovementPattern.LUNGE,
        com.leo.forge.domain.model.MovementPattern.CARRY -> 150
        com.leo.forge.domain.model.MovementPattern.ISOLATION,
        com.leo.forge.domain.model.MovementPattern.CORE -> 90
    }

    /**
     * @param lastSets working sets from the most recent session that trained this exercise,
     *                 ordered by set index. Empty on the exercise's first ever appearance.
     * @param setCount how many sets this week's volume ramp calls for.
     */
    fun prescribe(
        exercise: ExerciseEntity,
        lastSets: List<SetLogEntity>,
        setCount: Int,
        weekIndex: Int,
        totalWeeks: Int,
        repLow: Int = exercise.repLow,
        repHigh: Int = exercise.repHigh,
    ): ExercisePrescription {
        val targetRir = rirForWeek(weekIndex, totalWeeks)
        val deload = isDeloadWeek(weekIndex, totalWeeks)
        val increment = exercise.loadIncrementKg

        if (lastSets.isEmpty()) {
            // Nothing to progress from. Estimate from the best set on any exercise is
            // unreliable across movements, so ask rather than invent a number.
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
                }
            )
        }

        val targets = (0 until setCount).map { i ->
            // A ramped set count means later sets have no counterpart last week; fall back
            // to the final logged set, which is the most fatigued and so the safest anchor.
            val previous = lastSets.getOrNull(i) ?: lastSets.last()
            val carriedOver = i >= lastSets.size

            if (deload) {
                val load = OneRepMax.roundToIncrement(previous.weightKg * 0.85, increment)
                return@map SetTarget(
                    setIndex = i,
                    weightKg = load,
                    reps = repLow,
                    targetRir = DELOAD_RIR,
                    rationale = "Deload: about 85% of last week's load, well short of failure. " +
                        "This is where the growth from the block actually lands.",
                )
            }

            val lastRir = previous.rir ?: targetRir
            val hitTop = previous.reps >= repHigh
            val hitRange = previous.reps >= repLow
            val effortLeft = lastRir - targetRir

            when {
                // Cleared the top of the range with room to spare: a double jump is earned.
                hitTop && effortLeft >= 2 && increment > 0 -> SetTarget(
                    setIndex = i,
                    weightKg = previous.weightKg + increment * 2,
                    reps = repLow,
                    targetRir = targetRir,
                    rationale = "Last time: ${previous.reps} reps @ ${fmt(previous.weightKg)} with $lastRir left. " +
                        "Topped the range with ${effortLeft} spare, so up ${fmt(increment * 2)}.",
                )

                // Cleared the top of the range: standard load increase, reps reset to the bottom.
                hitTop && increment > 0 -> SetTarget(
                    setIndex = i,
                    weightKg = previous.weightKg + increment,
                    reps = repLow,
                    targetRir = targetRir,
                    rationale = "Last time: ${previous.reps} reps @ ${fmt(previous.weightKg)}. " +
                        "Top of the range, so up ${fmt(increment)} and back to $repLow.",
                )

                // Bodyweight or banded, where load cannot move: keep adding reps.
                hitTop -> SetTarget(
                    setIndex = i,
                    weightKg = previous.weightKg,
                    reps = previous.reps + 1,
                    targetRir = targetRir,
                    rationale = "No loadable increment here, so add a rep: ${previous.reps + 1}.",
                )

                // Inside the range: add a rep at the same load. This is the common case.
                hitRange -> SetTarget(
                    setIndex = i,
                    weightKg = previous.weightKg,
                    reps = (previous.reps + 1).coerceAtMost(repHigh),
                    targetRir = targetRir,
                    rationale = "Last time: ${previous.reps} reps @ ${fmt(previous.weightKg)}. " +
                        "Same load, one more rep" +
                        if (targetRir < lastRir) " and a rep closer to failure this week." else ".",
                )

                // Missed the bottom of the range badly: the load was wrong, walk it back.
                previous.reps < repLow - 2 && increment > 0 -> SetTarget(
                    setIndex = i,
                    weightKg = max(increment, previous.weightKg - increment),
                    reps = repLow,
                    targetRir = targetRir,
                    rationale = "Only ${previous.reps} reps last time, under the $repLow floor. " +
                        "Down ${fmt(increment)} to get back inside the range.",
                )

                // Just under: hold and try to convert.
                else -> SetTarget(
                    setIndex = i,
                    weightKg = previous.weightKg,
                    reps = repLow,
                    targetRir = targetRir,
                    rationale = "Just under the range last time. Same load, aim for $repLow clean reps.",
                )
            }.let { if (carriedOver) it.copy(rationale = it.rationale + " (Added set this week.)") else it }
        }

        return ExercisePrescription(exercise.id, targets, restSecondsFor(exercise))
    }

    private fun fmt(kg: Double): String =
        if (kg == kg.toLong().toDouble()) "${kg.toLong()} kg" else String.format("%.1f kg", kg)
}
