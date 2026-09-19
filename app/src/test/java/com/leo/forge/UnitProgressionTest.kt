package com.leo.forge

import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.domain.model.*
import com.leo.forge.domain.progression.ProgressionEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * The travelling-lifter case: a history logged in one gym's unit must still produce
 * suggestions that are loadable in the gym you are standing in.
 */
class UnitProgressionTest {

    private val bench = ExerciseEntity(
        id = "bench", name = "Barbell Bench Press",
        primaryMuscle = Muscle.CHEST, equipment = Equipment.BARBELL,
        pattern = MovementPattern.HORIZONTAL_PUSH,
        repLow = 5, repHigh = 10, loadIncrementKg = 2.5,
    )

    private val pinStack = bench.copy(
        id = "press", equipment = Equipment.MACHINE_SELECTORIZED, loadIncrementKg = 5.0,
    )

    private fun set(reps: Int, kg: Double, rir: Int?) = SetLogEntity(
        sessionId = 1, exerciseId = "bench", setIndex = 0,
        weightKg = kg, reps = reps, rir = rir, completedAt = 0L,
    )

    private fun displayOf(exercise: ExerciseEntity, lastKg: Double, reps: Int, rir: Int, units: Units): Double {
        val p = ProgressionEngine.prescribe(
            exercise, listOf(set(reps, lastKg, rir)), setCount = 1,
            weekIndex = 0, totalWeeks = 5, units = units,
        )
        return Load.toDisplay(p.targets.single().weightKg, units)
    }

    @Test
    fun `in a pound gym the suggestion lands on a five pound step`() {
        val lb = displayOf(bench, lastKg = 100.0, reps = 10, rir = 3, units = Units.LB)
        assertEquals("suggested $lb lb", 0.0, lb % 5.0, 0.01)
    }

    @Test
    fun `in a kilo gym the same history lands on a 2 and a half kilo step`() {
        val kg = displayOf(bench, lastKg = 100.0, reps = 10, rir = 3, units = Units.KG)
        assertEquals("suggested $kg kg", 0.0, kg % 2.5, 0.01)
        assertEquals(102.5, kg, 0.01)
    }

    @Test
    fun `a pound pin stack moves in ten pound steps`() {
        val lb = displayOf(pinStack, lastKg = 60.0, reps = 10, rir = 3, units = Units.LB)
        assertEquals("suggested $lb lb", 0.0, lb % 10.0, 0.01)
    }

    @Test
    fun `flying between gyms never produces an unloadable number`() {
        // A history accumulated in kg, then read in a pound gym, and vice versa.
        listOf(60.0, 77.5, 102.5, 140.0).forEach { kg ->
            listOf(8, 10).forEach { reps ->
                val lb = displayOf(bench, kg, reps, 3, Units.LB)
                assertEquals("kg=$kg reps=$reps -> $lb lb", 0.0, lb % 5.0, 0.01)
                val k = displayOf(bench, kg, reps, 3, Units.KG)
                assertEquals("kg=$kg reps=$reps -> $k kg", 0.0, k % 2.5, 0.01)
            }
        }
    }

    @Test
    fun `a gym that only stocks small plates gets its own step`() {
        // 11 / 5 / 2.5 lb plates: the smallest honest jump is a pair of 2.5s.
        val p = ProgressionEngine.prescribe(
            bench, listOf(set(10, 100.0, 3)), setCount = 1, weekIndex = 0, totalWeeks = 5,
            units = Units.LB, gymBarbellIncrement = 5.0,
        )
        val lb = Load.toDisplay(p.targets.single().weightKg, Units.LB)
        assertEquals(0.0, lb % 5.0, 0.01)
    }

    @Test
    fun `a gym override does not leak onto the cable stack`() {
        val cable = bench.copy(id = "c", equipment = Equipment.CABLE, loadIncrementKg = 2.5)
        assertEquals(
            Load.incrementLb(Equipment.CABLE),
            Load.increment(Equipment.CABLE, Units.LB, 2.5, gymBarbellIncrement = 5.0),
            0.001,
        )
        assertEquals("c", cable.id)
    }

    @Test
    fun `the deload is loadable too`() {
        val p = ProgressionEngine.prescribe(
            bench, listOf(set(8, 100.0, 1)), setCount = 1,
            weekIndex = 4, totalWeeks = 5, units = Units.LB,
        )
        val lb = Load.toDisplay(p.targets.single().weightKg, Units.LB)
        assertEquals(0.0, lb % 5.0, 0.01)
    }

    @Test
    fun `the rationale quotes the unit you are actually training in`() {
        val p = ProgressionEngine.prescribe(
            bench, listOf(set(10, 100.0, 3)), setCount = 1,
            weekIndex = 0, totalWeeks = 5, units = Units.LB,
        )
        val why = p.targets.single().rationale
        assertTrue("rationale was: $why", why.contains("lb"))
        assertFalse("rationale leaked kg: $why", why.contains(" kg"))
    }
}
