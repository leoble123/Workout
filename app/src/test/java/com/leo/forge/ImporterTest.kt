package com.leo.forge

import com.leo.forge.data.importer.Csv
import com.leo.forge.data.importer.HevyCsvImporter
import com.leo.forge.data.importer.MuscleGuesser
import com.leo.forge.domain.model.Muscle
import org.junit.Assert.*
import org.junit.Test

class ImporterTest {

    @Test
    fun `quoted commas do not shift the later columns`() {
        val rows = Csv.parse("a,b,c\n\"x,y\",z,w")
        assertEquals(listOf("a", "b", "c"), rows[0])
        assertEquals(listOf("x,y", "z", "w"), rows[1])
    }

    @Test
    fun `escaped quotes survive`() {
        val rows = Csv.parse("note,v\n\"he said \"\"go\"\"\",1")
        assertEquals("he said \"go\"", rows[1][0])
    }

    @Test
    fun `a newline inside a quoted note does not split the row`() {
        val rows = Csv.parse("note,v\n\"line one\nline two\",5")
        assertEquals(2, rows.size)
        assertEquals("line one\nline two", rows[1][0])
        assertEquals("5", rows[1][1])
    }

    @Test
    fun `a UTF-8 BOM does not poison the first header`() {
        val rows = Csv.parse("﻿title,reps\nPush,8")
        assertEquals("title", rows[0][0])
        assertEquals("title", Csv.normalizeHeader(rows[0][0]))
    }

    @Test
    fun `CRLF line endings parse the same as LF`() {
        val rows = Csv.parse("a,b\r\n1,2\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows)
    }

    @Test
    fun `header matching is tolerant of spelling and punctuation`() {
        assertEquals("weightkg", Csv.normalizeHeader("Weight (kg)"))
        assertEquals("weightkg", Csv.normalizeHeader("weight_kg"))
        assertEquals("exercisetitle", Csv.normalizeHeader("Exercise Title"))
    }

    @Test
    fun `the timestamp formats Hevy has shipped all parse`() {
        listOf(
            "2024-01-16 07:30:00",
            "2024-01-16T07:30:00",
            "16 Jan 2024, 07:30",
            "16/01/2024 07:30",
            "2024-01-16",
        ).forEach {
            assertNotNull("failed to parse: $it", HevyCsvImporter.parseTimestamp(it))
        }
    }

    @Test
    fun `epoch seconds and millis are told apart`() {
        val seconds = HevyCsvImporter.parseTimestamp("1705390200")!!
        val millis = HevyCsvImporter.parseTimestamp("1705390200000")!!
        assertEquals(millis, seconds)
    }

    @Test
    fun `an unparseable date is reported rather than guessed`() {
        assertNull(HevyCsvImporter.parseTimestamp("sometime last tuesday"))
    }

    @Test
    fun `common exercise names are classified confidently`() {
        assertEquals(Muscle.BICEPS, MuscleGuesser.guess("Incline Dumbbell Curl").muscle)
        assertEquals(Muscle.SIDE_DELTS, MuscleGuesser.guess("Cable Lateral Raise").muscle)
        assertEquals(Muscle.HAMSTRINGS, MuscleGuesser.guess("Seated Leg Curl").muscle)
        assertEquals(Muscle.QUADS, MuscleGuesser.guess("Hack Squat").muscle)
        assertTrue(MuscleGuesser.guess("Barbell Row").confident)
    }

    @Test
    fun `specific names are not swallowed by the general keyword they contain`() {
        // Regression: a generic "curl" rule ahead of "leg curl" filed every hamstring
        // curl in an imported history as a biceps exercise. Same shape of bug for
        // kickback, row and pec deck.
        assertEquals(Muscle.HAMSTRINGS, MuscleGuesser.guess("Lying Leg Curl").muscle)
        assertEquals(Muscle.FOREARMS, MuscleGuesser.guess("Barbell Wrist Curl").muscle)
        assertEquals(Muscle.FOREARMS, MuscleGuesser.guess("Reverse Curl").muscle)
        assertEquals(Muscle.BICEPS, MuscleGuesser.guess("Preacher Curl").muscle)

        assertEquals(Muscle.GLUTES, MuscleGuesser.guess("Cable Glute Kickback").muscle)
        assertEquals(Muscle.TRICEPS, MuscleGuesser.guess("Cable Kickback").muscle)

        assertEquals(Muscle.SIDE_DELTS, MuscleGuesser.guess("Upright Row").muscle)
        assertEquals(Muscle.UPPER_BACK, MuscleGuesser.guess("Seated Cable Row").muscle)

        assertEquals(Muscle.REAR_DELTS, MuscleGuesser.guess("Reverse Pec Deck").muscle)
        assertEquals(Muscle.CHEST, MuscleGuesser.guess("Pec Deck").muscle)

        assertEquals(Muscle.QUADS, MuscleGuesser.guess("Leg Extension").muscle)
        assertEquals(Muscle.TRICEPS, MuscleGuesser.guess("Overhead Extension").muscle)

        assertEquals(Muscle.CALVES, MuscleGuesser.guess("Seated Calf Raise").muscle)
        assertEquals(Muscle.FRONT_DELTS, MuscleGuesser.guess("Dumbbell Front Raise").muscle)
    }

    @Test
    fun `every seeded exercise name classifies back to its own muscle`() {
        // The guesser only runs on names absent from the library, but agreeing with the
        // library on names it does know is a cheap check that the rules are not nonsense.
        val mismatches = com.leo.forge.data.seed.ExerciseSeed.all
            .filter { MuscleGuesser.guess(it.name).confident }
            .filter { MuscleGuesser.guess(it.name).muscle != it.primaryMuscle }
            .map { "${it.name}: seeded ${it.primaryMuscle}, guessed ${MuscleGuesser.guess(it.name).muscle}" }
        assertTrue("guesser disagrees with the library on:\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun `an unrecognisable name is flagged for review instead of silently filed`() {
        assertFalse(MuscleGuesser.guess("Zercher Widowmaker 3000").confident)
    }
}
