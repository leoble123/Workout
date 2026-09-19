package com.leo.forge.domain.mesocycle

import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.MovementPattern
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.SplitType
import com.leo.forge.domain.progression.ProgressionEngine
import com.leo.forge.domain.volume.Landmarks
import com.leo.forge.domain.volume.VolumeLandmarks
import kotlin.math.ceil
import kotlin.math.roundToInt

data class MesoSpec(
    val name: String,
    val split: SplitType,
    val daysPerWeek: Int,
    val totalWeeks: Int = 5,
    val availableEquipment: Set<Equipment> = Equipment.entries.toSet(),
    val emphasis: Set<Muscle> = emptySet(),
    /**
     * Exercises actually performable at the active gym, already resolved from its
     * equipment, stations and per-exercise overrides. Null means "no gym configured,
     * assume everything".
     */
    val availableExerciseIds: Set<String>? = null,
)

data class GeneratedExercise(
    val exerciseId: String,
    val sets: Int,
    val repLow: Int,
    val repHigh: Int,
    val restSeconds: Int,
)

data class GeneratedDay(val label: String, val muscles: List<Muscle>, val exercises: List<GeneratedExercise>)

data class GeneratedMeso(
    val spec: MesoSpec,
    val days: List<GeneratedDay>,
    /** Muscles with no performable exercise at this gym; surfaced rather than silently dropped. */
    val unfilledMuscles: List<Muscle> = emptyList(),
)

/**
 * Builds a whole mesocycle - day split, exercise selection, and week-1 set counts -
 * so that planning a block is never a thing you sit down and do.
 *
 * Volume is allocated top-down: each muscle gets its weekly MEV, that total is split
 * across the days that train it, and only then are exercises chosen to carry it. Picking
 * exercises first is what produces the usual accident of 30 sets of chest and 4 of hamstrings.
 */
object MesocycleGenerator {

    private data class Template(val key: String, val label: String, val muscles: List<Muscle>)

    private val PUSH = Template("push", "Push", listOf(Muscle.CHEST, Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.TRICEPS))
    private val PULL = Template("pull", "Pull", listOf(Muscle.LATS, Muscle.UPPER_BACK, Muscle.REAR_DELTS, Muscle.TRAPS, Muscle.BICEPS))
    private val LEGS = Template("legs", "Legs", listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.ABS))
    private val UPPER = Template("upper", "Upper", listOf(Muscle.CHEST, Muscle.LATS, Muscle.UPPER_BACK, Muscle.SIDE_DELTS, Muscle.REAR_DELTS, Muscle.TRICEPS, Muscle.BICEPS))
    private val LOWER = Template("lower", "Lower", listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.ABS))
    private val FULL = Template("full", "Full body", listOf(Muscle.CHEST, Muscle.LATS, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.SIDE_DELTS, Muscle.BICEPS, Muscle.TRICEPS, Muscle.ABS))
    private val CHEST_BACK = Template("chestback", "Chest & Back", listOf(Muscle.CHEST, Muscle.LATS, Muscle.UPPER_BACK))
    private val SHOULDERS_ARMS = Template("shoulderarms", "Shoulders & Arms", listOf(Muscle.SIDE_DELTS, Muscle.REAR_DELTS, Muscle.FRONT_DELTS, Muscle.BICEPS, Muscle.TRICEPS))

    /** Bigger muscles go first in a session, while you are freshest. */
    private val MUSCLE_ORDER = listOf(
        Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.CHEST, Muscle.LATS, Muscle.UPPER_BACK,
        Muscle.GLUTES, Muscle.FRONT_DELTS, Muscle.TRAPS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS,
        Muscle.TRICEPS, Muscle.BICEPS, Muscle.ADDUCTORS, Muscle.CALVES, Muscle.FOREARMS,
        Muscle.ABS, Muscle.LOWER_BACK,
    )

    private fun cycleFor(split: SplitType): List<Template> = when (split) {
        SplitType.PUSH_PULL_LEGS -> listOf(PUSH, PULL, LEGS)
        SplitType.UPPER_LOWER -> listOf(UPPER, LOWER)
        SplitType.FULL_BODY -> listOf(FULL)
        SplitType.ARNOLD -> listOf(CHEST_BACK, SHOULDERS_ARMS, LEGS)
    }

    private fun isCompound(p: MovementPattern) = p != MovementPattern.ISOLATION && p != MovementPattern.CORE

    /**
     * Muscles that take well to being trained most days: small, isolation-driven, and quick
     * to recover. Emphasising one of these raises its frequency as well as its volume, which
     * is what people actually mean by "I need more abs" - twice a week on leg day is not it.
     *
     * Chest and quads are deliberately absent. Emphasising those buys more sets on the days
     * that already train them, not a chest slot on leg day.
     */
    private val HIGH_FREQUENCY = setOf(
        Muscle.ABS, Muscle.CALVES, Muscle.SIDE_DELTS,
        Muscle.REAR_DELTS, Muscle.FOREARMS, Muscle.TRAPS,
    )

    private fun musclesFor(template: Template, emphasis: Set<Muscle>): List<Muscle> =
        template.muscles + emphasis.filter { it in HIGH_FREQUENCY && it !in template.muscles }

    fun generate(spec: MesoSpec, library: List<ExerciseEntity>, personal: Map<Muscle, Landmarks> = emptyMap()): GeneratedMeso {
        val cycle = cycleFor(spec.split)
        val days = spec.daysPerWeek.coerceIn(1, 7)

        // Lay out the week, suffixing repeats so "Push A" and "Push B" are distinguishable.
        val chosen = (0 until days).map { cycle[it % cycle.size] }
        val occurrence = mutableMapOf<String, Int>()
        val repeats = chosen.groupingBy { it.key }.eachCount()
        val labelled = chosen.map { t ->
            val n = occurrence.getOrDefault(t.key, 0)
            occurrence[t.key] = n + 1
            val label = if (repeats.getValue(t.key) > 1) "${t.label} ${('A' + n)}" else t.label
            Triple(t, label, n)
        }

        // Weekly set budget per muscle at the start of the block.
        val weeklySets: Map<Muscle, Int> = Muscle.entries.associateWith { m ->
            val base = VolumeLandmarks.plannedSets(m, weekIndex = 0, totalWeeks = spec.totalWeeks, personal = personal[m])
            val lm = personal[m] ?: VolumeLandmarks.of(m)
            if (m in spec.emphasis) (base * 1.3f).roundToInt().coerceAtMost(lm.mav) else base
        }

        // How many of this week's days actually train each muscle.
        val daysTraining: Map<Muscle, Int> = Muscle.entries.associateWith { m ->
            labelled.count { (t, _, _) -> m in musclesFor(t, spec.emphasis) }
        }

        val usedGlobally = mutableSetOf<String>()
        val unfilled = linkedSetOf<Muscle>()

        val generatedDays = labelled.map { (template, label, rotation) ->
            val usedToday = mutableSetOf<String>()
            val perMuscle = musclesFor(template, spec.emphasis)
                .sortedBy { MUSCLE_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .mapNotNull { muscle ->
                    val weekly = weeklySets[muscle] ?: 0
                    val spread = daysTraining[muscle] ?: 1
                    if (weekly <= 0 || spread <= 0) return@mapNotNull null
                    // Round up so the weekly target is met rather than quietly undershot.
                    val setsToday = ceil(weekly.toDouble() / spread).toInt().coerceAtLeast(2)
                    muscle to setsToday
                }

            val exercises = perMuscle.flatMap { (muscle, setsToday) ->
                val count = when {
                    setsToday <= 3 -> 1
                    setsToday <= 7 -> 2
                    else -> 3
                }
                val picks = pick(
                    library, muscle, count, rotation, spec.availableEquipment,
                    spec.availableExerciseIds, usedToday, usedGlobally,
                )
                if (picks.isEmpty()) {
                    unfilled += muscle
                    return@flatMap emptyList()
                }

                // Spread the muscle's sets over its exercises, remainder to the first (heaviest) one.
                val each = setsToday / picks.size
                val extra = setsToday % picks.size
                picks.mapIndexed { i, ex ->
                    GeneratedExercise(
                        exerciseId = ex.id,
                        sets = (each + if (i < extra) 1 else 0).coerceAtLeast(2),
                        repLow = ex.repLow,
                        repHigh = ex.repHigh,
                        restSeconds = ProgressionEngine.restSecondsFor(ex),
                    )
                }
            }

            // Compounds before isolations across the whole session, not just within a muscle.
            val byId = library.associateBy { it.id }
            val ordered = exercises.sortedWith(
                compareBy(
                    { if (isCompound(byId[it.exerciseId]?.pattern ?: MovementPattern.ISOLATION)) 0 else 1 },
                    { exercises.indexOf(it) },
                )
            )
            GeneratedDay(label, musclesFor(template, spec.emphasis), ordered)
        }

        return GeneratedMeso(spec, generatedDays, unfilled.toList())
    }

    /**
     * Picks [count] exercises for [muscle], preferring a compound first and then a
     * different movement pattern, so a day does not end up as three variations of one thing.
     * [rotation] shifts the selection per day so Push A and Push B differ.
     */
    private fun pick(
        library: List<ExerciseEntity>,
        muscle: Muscle,
        count: Int,
        rotation: Int,
        available: Set<Equipment>,
        availableIds: Set<String>?,
        usedToday: MutableSet<String>,
        usedGlobally: MutableSet<String>,
    ): List<ExerciseEntity> {
        val candidates = library
            .filter { !it.archived && it.primaryMuscle == muscle }
            .filter { availableIds?.contains(it.id) ?: (it.equipment in available) }
            .sortedWith(compareBy({ if (isCompound(it.pattern)) 0 else 1 }, { it.name }))
        if (candidates.isEmpty()) return emptyList()

        val picked = mutableListOf<ExerciseEntity>()
        val patterns = mutableSetOf<MovementPattern>()

        fun sweep(allowRepeatPattern: Boolean, allowGlobalRepeat: Boolean) {
            for (i in candidates.indices) {
                if (picked.size >= count) return
                val cand = candidates[(rotation + i) % candidates.size]
                if (cand.id in usedToday) continue
                if (!allowGlobalRepeat && cand.id in usedGlobally) continue
                if (!allowRepeatPattern && cand.pattern in patterns) continue
                picked += cand
                patterns += cand.pattern
                usedToday += cand.id
                usedGlobally += cand.id
            }
        }

        sweep(allowRepeatPattern = false, allowGlobalRepeat = false)
        sweep(allowRepeatPattern = true, allowGlobalRepeat = false)
        sweep(allowRepeatPattern = true, allowGlobalRepeat = true)
        return picked
    }
}
