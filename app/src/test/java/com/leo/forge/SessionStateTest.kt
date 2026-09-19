package com.leo.forge

import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.data.repo.Deviation
import com.leo.forge.data.repo.ExercisePlanUi
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.MovementPattern
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.SetType
import com.leo.forge.domain.progression.ExercisePrescription
import com.leo.forge.domain.progression.SetTarget
import com.leo.forge.ui.session.SessionUiState
import org.junit.Assert.*
import org.junit.Test

/**
 * The workout screen has no "current set" any more, so there is no focus to test. What
 * matters instead is that each row can report its own state independently of every other.
 */
class SessionStateTest {

    private fun exercise(id: String) = ExerciseEntity(
        id = id, name = id.replaceFirstChar { it.uppercase() },
        primaryMuscle = Muscle.CHEST, equipment = Equipment.BARBELL,
        pattern = MovementPattern.HORIZONTAL_PUSH,
    )

    private fun plan(id: String, sets: Int, order: Long, previous: List<SetLogEntity> = emptyList()) =
        ExercisePlanUi(
            id = order,
            exercise = exercise(id),
            repLow = 8,
            repHigh = 12,
            previous = previous,
            prescription = ExercisePrescription(
                exerciseId = id,
                restSeconds = 120,
                targets = (0 until sets).map {
                    SetTarget(setIndex = it, weightKg = 60.0, reps = 10, targetRir = 2, rationale = "x")
                },
            ),
        )

    private fun log(id: String, setIndex: Int, kg: Double = 60.0, reps: Int = 10, type: SetType = SetType.WORKING) =
        SetLogEntity(
            id = setIndex + 1L, sessionId = 1, exerciseId = id, setIndex = setIndex,
            weightKg = kg, reps = reps, type = type, completedAt = setIndex.toLong(),
        )

    private val plans = listOf(plan("bench", 3, 1L), plan("row", 3, 2L))

    @Test
    fun `sets can be ticked in any order and each reports itself`() {
        val s = SessionUiState(plans = plans, logged = listOf(log("bench", 2), log("row", 0)))
        assertNotNull(s.loggedSet("bench", 2))
        assertNull(s.loggedSet("bench", 0))
        assertNotNull(s.loggedSet("row", 0))
        assertNull(s.loggedSet("row", 1))
    }

    @Test
    fun `a session is complete only when every set is ticked`() {
        val partial = SessionUiState(plans = plans, logged = listOf(log("bench", 0)))
        assertFalse(partial.isComplete)

        val all = plans.flatMap { p -> p.prescription.targets.map { log(p.exercise.id, it.setIndex) } }
        assertTrue(SessionUiState(plans = plans, logged = all).isComplete)
    }

    @Test
    fun `warm-ups count toward neither volume nor the set tally`() {
        val s = SessionUiState(
            plans = plans,
            logged = listOf(
                log("bench", 0, type = SetType.WARMUP),
                log("bench", 1, kg = 100.0, reps = 5),
            ),
        )
        assertEquals(1, s.doneSets)
        assertEquals(500.0, s.volumeKg, 0.01)
    }

    @Test
    fun `previous performance is matched set for set`() {
        val withPrevious = plan(
            "bench", 3, 1L,
            previous = listOf(log("bench", 0, 57.5, 9), log("bench", 1, 57.5, 8)),
        )
        val s = SessionUiState(plans = listOf(withPrevious))
        assertEquals(9, s.previousSet(withPrevious, 0)?.reps)
        assertEquals(8, s.previousSet(withPrevious, 1)?.reps)
        assertNull("a third set with no counterpart shows nothing", s.previousSet(withPrevious, 2))
    }

    @Test
    fun `an empty session is not complete`() {
        assertFalse(SessionUiState(plans = emptyList()).isComplete)
    }
}

/** Working out what you changed, so finishing can ask once instead of guessing. */
class DeviationTest {

    private val names = mapOf(
        "bench" to "Bench Press", "row" to "Barbell Row",
        "cable_row" to "Seated Cable Row", "curl" to "Barbell Curl",
    )

    private fun diff(planned: List<Pair<Long, String>>, current: List<String>) =
        Deviation.diff(planned, current) { names[it] ?: it }

    @Test
    fun `following the plan is not a deviation`() {
        assertTrue(diff(listOf(1L to "bench", 2L to "row"), listOf("bench", "row")).isEmpty())
    }

    @Test
    fun `order alone is not a deviation`() {
        assertTrue(diff(listOf(1L to "bench", 2L to "row"), listOf("row", "bench")).isEmpty())
    }

    @Test
    fun `one out and one in reads as a swap`() {
        val d = diff(listOf(1L to "bench", 2L to "row"), listOf("bench", "cable_row"))
        assertEquals(1, d.size)
        val swap = d.single() as Deviation.Swapped
        assertEquals("Barbell Row", swap.from)
        assertEquals("Seated Cable Row", swap.to)
        assertEquals(2L, swap.plannedId)
        assertEquals("Barbell Row → Seated Cable Row", swap.describe)
    }

    @Test
    fun `an extra exercise is an addition`() {
        val d = diff(listOf(1L to "bench"), listOf("bench", "curl"))
        assertEquals(listOf("Added Barbell Curl"), d.map { it.describe })
    }

    @Test
    fun `a skipped exercise is a removal`() {
        val d = diff(listOf(1L to "bench", 2L to "row"), listOf("bench"))
        assertEquals(listOf("Removed Barbell Row"), d.map { it.describe })
    }

    @Test
    fun `a swap alongside an addition is reported as both`() {
        val d = diff(listOf(1L to "bench", 2L to "row"), listOf("bench", "cable_row", "curl"))
        assertEquals(2, d.size)
        assertTrue(d.any { it is Deviation.Swapped })
        assertTrue(d.any { it is Deviation.Added })
    }

    @Test
    fun `a freestyle session with no plan reports nothing`() {
        assertTrue(diff(emptyList(), emptyList()).isEmpty())
    }
}
