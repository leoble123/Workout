package com.leo.forge

import com.leo.forge.data.seed.ExerciseSeed
import com.leo.forge.data.seed.GymSeed
import com.leo.forge.domain.gym.Availability
import com.leo.forge.domain.mesocycle.MesoSpec
import com.leo.forge.domain.mesocycle.MesocycleGenerator
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.SplitType
import com.leo.forge.domain.model.Units
import org.junit.Assert.*
import org.junit.Test

class GymTest {

    private val library = ExerciseSeed.all
    private val bench = library.first { it.id == "barbell_bench_press" }

    @Test
    fun `an explicit override beats a station and the equipment list`() {
        assertFalse(
            Availability.isAvailable(bench, setOf(Equipment.BARBELL), setOf(bench.id), mapOf(bench.id to false))
        )
        assertTrue(
            Availability.isAvailable(bench, emptySet(), emptySet(), mapOf(bench.id to true))
        )
    }

    @Test
    fun `a station makes an exercise available even when its category is off`() {
        assertTrue(Availability.isAvailable(bench, emptySet(), setOf(bench.id), emptyMap()))
        assertFalse(Availability.isAvailable(bench, emptySet(), emptySet(), emptyMap()))
    }

    @Test
    fun `every exercise a preset station references actually exists`() {
        val known = library.map { it.id }.toSet()
        GymSeed.minimalCableGym("test", Units.LB).stations.forEach { station ->
            station.exerciseIds.forEach {
                assertTrue("${station.name} references unknown exercise '$it'", it in known)
            }
        }
    }

    @Test
    fun `a barbell with no rack is not a back squat`() {
        val squat = library.first { it.id == "back_squat" }
        val row = library.first { it.id == "barbell_row" }
        val onlyBarbell = setOf(Equipment.BARBELL)

        assertFalse("squat needs something to unrack from",
            Availability.isAvailable(squat, onlyBarbell, emptySet(), emptyMap()))
        assertTrue("a row is picked up off the floor",
            Availability.isAvailable(row, onlyBarbell, emptySet(), emptyMap()))
        assertTrue("with a rack it is back on",
            Availability.isAvailable(squat, onlyBarbell + Equipment.RACK, emptySet(), emptyMap()))
    }

    @Test
    fun `a bench press needs both a rack and a bench`() {
        val bench = library.first { it.id == "barbell_bench_press" }
        assertFalse(Availability.isAvailable(bench, setOf(Equipment.BARBELL, Equipment.RACK), emptySet(), emptyMap()))
        assertFalse(Availability.isAvailable(bench, setOf(Equipment.BARBELL, Equipment.BENCH), emptySet(), emptyMap()))
        assertTrue(
            Availability.isAvailable(
                bench, setOf(Equipment.BARBELL, Equipment.RACK, Equipment.BENCH), emptySet(), emptyMap(),
            )
        )
    }

    @Test
    fun `the rackless gym keeps dumbbell pressing but drops barbell pressing`() {
        val preset = GymSeed.minimalCableGym("test", Units.LB)
        val ids = Availability.resolve(
            library = library,
            availableEquipment = preset.equipment.filterValues { it }.keys,
            stationExerciseIds = preset.stations.flatMap { it.exerciseIds }.toSet(),
            overrides = emptyMap(),
        )
        assertFalse("no rack, so no back squat", "back_squat" in ids)
        assertFalse("no rack, so no barbell bench", "barbell_bench_press" in ids)
        assertTrue("benches exist, so dumbbell pressing is on", "dumbbell_bench_press" in ids)
        assertTrue("a row needs no rack", "barbell_row" in ids)
        assertTrue("and neither does an RDL", "romanian_deadlift" in ids)
    }

    @Test
    fun `the default profiles are sane and differ in what they can do`() {
        val presets = GymSeed.defaults()
        assertEquals(2, presets.size)
        val known = library.map { it.id }.toSet()
        presets.forEach { preset ->
            preset.stations.forEach { st ->
                st.exerciseIds.forEach { assertTrue("${st.name}: unknown '$it'", it in known) }
            }
        }
        fun count(p: com.leo.forge.data.seed.GymSeed.GymPreset) = Availability.resolve(
            library, p.equipment.filterValues { it }.keys,
            p.stations.flatMap { it.exerciseIds }.toSet(), emptyMap(),
        ).size
        assertTrue("a full gym should do more than a sparse one", count(presets[1]) > count(presets[0]))
    }

    @Test
    fun `the cable-led preset yields a usable set of exercises`() {
        val preset = GymSeed.minimalCableGym("test", Units.LB)
        val ids = Availability.resolve(
            library = library,
            availableEquipment = preset.equipment.filterValues { it }.keys,
            stationExerciseIds = preset.stations.flatMap { it.exerciseIds }.toSet(),
            overrides = emptyMap(),
        )
        assertTrue("only ${ids.size} exercises", ids.size > 50)
        // The combo station grants exactly two machine movements, and no others.
        assertTrue("machine_shoulder_press" in ids)
        assertTrue("pec_deck" in ids)
        assertFalse("a leg extension that is not there", "leg_extension" in ids)
        assertFalse("a leg press that is not there", "leg_press" in ids)
    }

    @Test
    fun `a generated program only ever uses exercises the gym has`() {
        val preset = GymSeed.minimalCableGym("test", Units.LB)
        val ids = Availability.resolve(
            library = library,
            availableEquipment = preset.equipment.filterValues { it }.keys,
            stationExerciseIds = preset.stations.flatMap { it.exerciseIds }.toSet(),
            overrides = emptyMap(),
        )
        val meso = MesocycleGenerator.generate(
            MesoSpec("t", SplitType.PUSH_PULL_LEGS, daysPerWeek = 6, availableExerciseIds = ids), library,
        )
        meso.days.flatMap { it.exercises }.forEach {
            assertTrue("${it.exerciseId} is not available at this gym", it.exerciseId in ids)
        }
        meso.days.forEach { assertTrue("${it.label} came out empty", it.exercises.isNotEmpty()) }
    }

    @Test
    fun `muscles the gym cannot train are reported rather than silently dropped`() {
        // Cables only: nothing here trains quads.
        val cableOnly = library.filter { it.equipment == Equipment.CABLE }.map { it.id }.toSet()
        val meso = MesocycleGenerator.generate(
            MesoSpec("cables", SplitType.PUSH_PULL_LEGS, daysPerWeek = 3, availableExerciseIds = cableOnly), library,
        )
        assertTrue("quads should be flagged", Muscle.QUADS in meso.unfilledMuscles)
        meso.days.flatMap { it.exercises }.forEach { assertTrue(it.exerciseId in cableOnly) }
    }

    @Test
    fun `dumbbells alone still cover quads and hamstrings`() {
        val ids = Availability.resolve(
            library, setOf(Equipment.DUMBBELL, Equipment.BODYWEIGHT), emptySet(), emptyMap(),
        )
        listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES).forEach { m ->
            assertTrue("nothing for $m", library.any { it.id in ids && it.primaryMuscle == m })
        }
    }
}
