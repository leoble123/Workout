package com.leo.forge

import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.data.repo.ExercisePlanUi
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.MovementPattern
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.progression.ExercisePrescription
import com.leo.forge.domain.progression.SetTarget
import com.leo.forge.ui.session.SessionUiState
import org.junit.Assert.*
import org.junit.Test

/**
 * Which set the screen is on, and - the part that used to be wrong - which one it moves to
 * after you log one you jumped ahead to.
 */
class SessionFocusTest {

    private fun exercise(id: String) = ExerciseEntity(
        id = id, name = id.replaceFirstChar { it.uppercase() },
        primaryMuscle = Muscle.CHEST, equipment = Equipment.BARBELL,
        pattern = MovementPattern.HORIZONTAL_PUSH,
    )

    private fun plan(id: String, sets: Int, order: Long) = ExercisePlanUi(
        id = order,
        exercise = exercise(id),
        repLow = 8,
        repHigh = 12,
        prescription = ExercisePrescription(
            exerciseId = id,
            restSeconds = 120,
            targets = (0 until sets).map {
                SetTarget(setIndex = it, weightKg = 60.0, reps = 10, targetRir = 2, rationale = "x")
            },
        ),
    )

    private val plans = listOf(plan("bench", 3, 1L), plan("row", 3, 2L))

    private fun logged(vararg slots: Pair<String, Int>) = slots.mapIndexed { i, (id, setIndex) ->
        SetLogEntity(
            id = i + 1L, sessionId = 1, exerciseId = id, setIndex = setIndex,
            weightKg = 60.0, reps = 10, completedAt = i.toLong(),
        )
    }

    private fun state(
        log: List<SetLogEntity> = emptyList(),
        manual: Pair<String, Int>? = null,
        last: Pair<String, Int>? = null,
    ) = SessionUiState(plans = plans, logged = log, manualFocus = manual, lastLogged = last)

    @Test
    fun `a fresh session starts on the first set`() {
        assertEquals(0 to 0, state().focus)
    }

    @Test
    fun `logging in order walks forward one set at a time`() {
        assertEquals(0 to 1, state(logged("bench" to 0), last = "bench" to 0).focus)
        assertEquals(
            0 to 2,
            state(logged("bench" to 0, "bench" to 1), last = "bench" to 1).focus,
        )
    }

    @Test
    fun `the last set of an exercise rolls into the next exercise`() {
        val s = state(
            logged("bench" to 0, "bench" to 1, "bench" to 2),
            last = "bench" to 2,
        )
        assertEquals(1 to 0, s.focus)
        assertEquals("Row · set 1", s.upNextLabel)
    }

    @Test
    fun `tapping a set makes it current`() {
        assertEquals(1 to 2, state(manual = "row" to 2).focus)
    }

    @Test
    fun `logging a set you jumped ahead to does not rewind to the top`() {
        // The regression: focus used to fall back to "first unlogged", yanking you back to
        // bench set 2 while the rest timer counted down to something else entirely.
        val s = state(logged("bench" to 0, "row" to 0), last = "row" to 0)
        assertEquals(1 to 1, s.focus)
        assertNotEquals(0 to 1, s.focus)
    }

    @Test
    fun `leftovers are picked up once there is nothing further ahead`() {
        val s = state(
            logged("bench" to 0, "bench" to 2, "row" to 0, "row" to 1, "row" to 2),
            last = "row" to 2,
        )
        assertEquals("the skipped set is still waiting", 0 to 1, s.focus)
    }

    @Test
    fun `a tapped set still wins over the forward walk`() {
        val s = state(logged("bench" to 0), manual = "row" to 2, last = "bench" to 0)
        assertEquals(1 to 2, s.focus)
    }

    @Test
    fun `a tapped set that then gets logged is ignored rather than sticking`() {
        val s = state(logged("row" to 2), manual = "row" to 2, last = "row" to 2)
        assertNotEquals(1 to 2, s.focus)
        assertEquals(0 to 0, s.focus)
    }

    @Test
    fun `a finished session reports complete and has nothing up next`() {
        val all = logged(
            "bench" to 0, "bench" to 1, "bench" to 2,
            "row" to 0, "row" to 1, "row" to 2,
        )
        val s = state(all, last = "row" to 2)
        assertNull(s.focus)
        assertTrue(s.isComplete)
        assertEquals("Last set done", s.upNextLabel)
    }

    @Test
    fun `an empty session is not complete`() {
        assertFalse(SessionUiState(plans = emptyList()).isComplete)
    }

    @Test
    fun `the label always names the set the countdown is for`() {
        val s = state(logged("bench" to 0), last = "bench" to 0)
        assertEquals("Bench · set 2", s.upNextLabel)
        assertEquals(s.focus, 0 to 1)
    }
}
