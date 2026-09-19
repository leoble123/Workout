package com.leo.forge

import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.MovementPattern
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.progression.ProgressionEngine
import org.junit.Assert.*
import org.junit.Test

class ProgressionEngineTest {

    private val bench = ExerciseEntity(
        id = "bench", name = "Barbell Bench Press",
        primaryMuscle = Muscle.CHEST, equipment = Equipment.BARBELL,
        pattern = MovementPattern.HORIZONTAL_PUSH,
        repLow = 5, repHigh = 10, loadIncrementKg = 2.5,
    )

    private val pullUp = bench.copy(
        id = "pullup", name = "Pull-Up", equipment = Equipment.BANDS, loadIncrementKg = 0.0,
    )

    private fun set(reps: Int, weight: Double, rir: Int?, index: Int = 0) = SetLogEntity(
        sessionId = 1, exerciseId = "bench", setIndex = index,
        weightKg = weight, reps = reps, rir = rir, completedAt = 0L,
    )

    @Test
    fun `RIR tightens across the block then resets for the deload`() {
        assertEquals(3, ProgressionEngine.rirForWeek(0, 5))
        assertEquals(2, ProgressionEngine.rirForWeek(1, 5))
        assertEquals(1, ProgressionEngine.rirForWeek(2, 5))
        assertEquals(0, ProgressionEngine.rirForWeek(3, 5))
        assertEquals(ProgressionEngine.DELOAD_RIR, ProgressionEngine.rirForWeek(4, 5))
        assertTrue(ProgressionEngine.isDeloadWeek(4, 5))
        assertFalse(ProgressionEngine.isDeloadWeek(3, 5))
    }

    @Test
    fun `topping the rep range adds one increment and resets reps to the floor`() {
        val p = ProgressionEngine.prescribe(bench, listOf(set(10, 100.0, 3)), setCount = 1, weekIndex = 0, totalWeeks = 5)
        val t = p.targets.single()
        assertEquals(102.5, t.weightKg, 0.001)
        assertEquals(5, t.reps)
    }

    @Test
    fun `topping the range with reps to spare earns a double jump`() {
        val p = ProgressionEngine.prescribe(bench, listOf(set(10, 100.0, 5)), setCount = 1, weekIndex = 0, totalWeeks = 5)
        assertEquals(105.0, p.targets.single().weightKg, 0.001)
    }

    @Test
    fun `inside the rep range holds load and adds a rep`() {
        val p = ProgressionEngine.prescribe(bench, listOf(set(7, 100.0, 2)), setCount = 1, weekIndex = 0, totalWeeks = 5)
        val t = p.targets.single()
        assertEquals(100.0, t.weightKg, 0.001)
        assertEquals(8, t.reps)
    }

    @Test
    fun `added reps never exceed the top of the range`() {
        val p = ProgressionEngine.prescribe(bench, listOf(set(9, 100.0, 1)), setCount = 1, weekIndex = 0, totalWeeks = 5)
        assertEquals(10, p.targets.single().reps)
    }

    @Test
    fun `missing the rep floor badly walks the load back`() {
        val p = ProgressionEngine.prescribe(bench, listOf(set(2, 100.0, 0)), setCount = 1, weekIndex = 0, totalWeeks = 5)
        val t = p.targets.single()
        assertEquals(97.5, t.weightKg, 0.001)
        assertEquals(5, t.reps)
    }

    @Test
    fun `deload drops to 85 percent, rounded to a loadable increment, well short of failure`() {
        val p = ProgressionEngine.prescribe(bench, listOf(set(8, 100.0, 1)), setCount = 2, weekIndex = 4, totalWeeks = 5)
        p.targets.forEach {
            assertEquals(85.0, it.weightKg, 0.001)
            assertEquals(ProgressionEngine.DELOAD_RIR, it.targetRir)
        }
    }

    @Test
    fun `every suggested load lands on a real increment of the implement`() {
        // A machine that steps in 5 kg must never be told to load 82.5.
        val machine = bench.copy(id = "m", equipment = Equipment.MACHINE_SELECTORIZED, loadIncrementKg = 5.0)
        val p = ProgressionEngine.prescribe(machine, listOf(set(10, 80.0, 4)), setCount = 3, weekIndex = 1, totalWeeks = 5)
        p.targets.forEach { assertEquals(0.0, it.weightKg % 5.0, 0.001) }
    }

    @Test
    fun `with no loadable increment it progresses by reps instead of stalling`() {
        val p = ProgressionEngine.prescribe(pullUp, listOf(set(10, 0.0, 2)), setCount = 1, weekIndex = 0, totalWeeks = 5)
        val t = p.targets.single()
        assertEquals(0.0, t.weightKg, 0.001)
        assertEquals(11, t.reps)
    }

    @Test
    fun `a first-ever appearance asks for a load rather than inventing one`() {
        val p = ProgressionEngine.prescribe(bench, emptyList(), setCount = 3, weekIndex = 0, totalWeeks = 5)
        assertEquals(3, p.targets.size)
        p.targets.forEach {
            assertTrue(it.isEstimateOnly)
            assertEquals(0.0, it.weightKg, 0.001)
        }
    }

    @Test
    fun `sets added by the weekly ramp fall back to the last logged set`() {
        // Two sets logged last week, four prescribed this week.
        val last = listOf(set(10, 100.0, 3, 0), set(8, 100.0, 1, 1))
        val p = ProgressionEngine.prescribe(bench, last, setCount = 4, weekIndex = 0, totalWeeks = 5)
        assertEquals(4, p.targets.size)
        assertTrue(p.targets.last().rationale.contains("Added set"))
    }

    @Test
    fun `every prescription explains itself`() {
        val p = ProgressionEngine.prescribe(bench, listOf(set(8, 100.0, 2)), setCount = 2, weekIndex = 1, totalWeeks = 5)
        p.targets.forEach { assertTrue(it.rationale.isNotBlank()) }
    }
}
