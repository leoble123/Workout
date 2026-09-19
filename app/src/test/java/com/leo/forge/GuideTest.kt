package com.leo.forge

import com.leo.forge.data.seed.ExerciseGuide
import com.leo.forge.data.seed.ExerciseSeed
import org.junit.Assert.*
import org.junit.Test

class GuideTest {

    @Test
    fun `every exercise in the library has form notes`() {
        val missing = ExerciseSeed.all.filter { ExerciseGuide.forExercise(it.id) == null }
        assertTrue("no notes for: ${missing.joinToString { it.name }}", missing.isEmpty())
    }

    @Test
    fun `notes are substantive rather than placeholders`() {
        ExerciseSeed.all.forEach { exercise ->
            val cues = ExerciseGuide.forExercise(exercise.id)!!
            listOf("setup" to cues.setup, "execution" to cues.execution, "mistake" to cues.mistake)
                .forEach { (field, text) ->
                    assertTrue("${exercise.name} $field too short: '$text'", text.length > 40)
                    assertFalse("${exercise.name} $field is filler", text.contains("good form", ignoreCase = true))
                }
        }
    }

    @Test
    fun `an unknown exercise returns nothing rather than inventing advice`() {
        assertNull(ExerciseGuide.forExercise("hevy_some_imported_thing"))
    }

    @Test
    fun `a squat and a curl do not share the same notes`() {
        val squat = ExerciseGuide.forExercise("back_squat")!!
        val curl = ExerciseGuide.forExercise("barbell_curl")!!
        assertNotEquals(squat.setup, curl.setup)
        assertTrue(squat.execution.contains("knee", ignoreCase = true))
        assertTrue(curl.execution.contains("curl", ignoreCase = true))
    }
}
