package com.leo.forge

import com.leo.forge.data.seed.ExerciseSeed
import com.leo.forge.domain.mesocycle.MesoSpec
import com.leo.forge.domain.mesocycle.MesocycleGenerator
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.SplitType
import org.junit.Assert.*
import org.junit.Test

class GeneratorTest {

    private val library = ExerciseSeed.all

    @Test
    fun `a six day PPL lays out A and B versions of each day`() {
        val meso = MesocycleGenerator.generate(
            MesoSpec("test", SplitType.PUSH_PULL_LEGS, daysPerWeek = 6), library,
        )
        assertEquals(6, meso.days.size)
        assertEquals(
            listOf("Push A", "Pull A", "Legs A", "Push B", "Pull B", "Legs B"),
            meso.days.map { it.label },
        )
    }

    @Test
    fun `no day repeats the same exercise twice`() {
        val meso = MesocycleGenerator.generate(
            MesoSpec("test", SplitType.PUSH_PULL_LEGS, daysPerWeek = 6), library,
        )
        meso.days.forEach { day ->
            val ids = day.exercises.map { it.exerciseId }
            assertEquals("duplicate in ${day.label}: $ids", ids.size, ids.distinct().size)
        }
    }

    @Test
    fun `the A and B versions of a day are not the same session twice`() {
        val meso = MesocycleGenerator.generate(
            MesoSpec("test", SplitType.PUSH_PULL_LEGS, daysPerWeek = 6), library,
        )
        val pushA = meso.days.first { it.label == "Push A" }.exercises.map { it.exerciseId }.toSet()
        val pushB = meso.days.first { it.label == "Push B" }.exercises.map { it.exerciseId }.toSet()
        assertTrue("Push A and Push B overlapped entirely", (pushA intersect pushB).size < pushA.size)
    }

    @Test
    fun `every day gets exercises and every set count is sane`() {
        SplitType.entries.forEach { split ->
            val days = split.daysPerWeek.last
            val meso = MesocycleGenerator.generate(MesoSpec("t", split, daysPerWeek = days), library)
            meso.days.forEach { day ->
                assertTrue("${split.display}/${day.label} was empty", day.exercises.isNotEmpty())
                day.exercises.forEach {
                    assertTrue(it.sets in 1..8)
                    assertTrue(it.repLow in 1..30 && it.repHigh >= it.repLow)
                    assertTrue(it.restSeconds in 30..400)
                }
            }
        }
    }

    @Test
    fun `compounds are ordered before isolations within a session`() {
        val byId = library.associateBy { it.id }
        val meso = MesocycleGenerator.generate(
            MesoSpec("t", SplitType.UPPER_LOWER, daysPerWeek = 4), library,
        )
        meso.days.forEach { day ->
            val isCompound = day.exercises.map {
                val p = byId.getValue(it.exerciseId).pattern
                p != com.leo.forge.domain.model.MovementPattern.ISOLATION &&
                    p != com.leo.forge.domain.model.MovementPattern.CORE
            }
            val firstIsolation = isCompound.indexOfFirst { !it }
            if (firstIsolation >= 0) {
                assertFalse(
                    "a compound appeared after an isolation in ${day.label}",
                    isCompound.drop(firstIsolation).any { it },
                )
            }
        }
    }

    @Test
    fun `restricting equipment only ever picks what you actually have`() {
        val only = setOf(Equipment.DUMBBELL, Equipment.BODYWEIGHT)
        val byId = library.associateBy { it.id }
        val meso = MesocycleGenerator.generate(
            MesoSpec("home", SplitType.FULL_BODY, daysPerWeek = 3, availableEquipment = only), library,
        )
        meso.days.flatMap { it.exercises }.forEach {
            assertTrue(byId.getValue(it.exerciseId).equipment in only)
        }
    }

    @Test
    fun `emphasis actually buys the muscle more sets`() {
        val plain = MesocycleGenerator.generate(
            MesoSpec("a", SplitType.PUSH_PULL_LEGS, daysPerWeek = 6), library,
        )
        val emphasised = MesocycleGenerator.generate(
            MesoSpec("b", SplitType.PUSH_PULL_LEGS, daysPerWeek = 6, emphasis = setOf(Muscle.SIDE_DELTS)), library,
        )
        val byId = library.associateBy { it.id }
        fun setsFor(m: com.leo.forge.domain.mesocycle.GeneratedMeso, muscle: Muscle) =
            m.days.flatMap { it.exercises }
                .filter { byId.getValue(it.exerciseId).primaryMuscle == muscle }
                .sumOf { it.sets }

        assertTrue(setsFor(emphasised, Muscle.SIDE_DELTS) > setsFor(plain, Muscle.SIDE_DELTS))
    }

    @Test
    fun `every muscle can actually be picked as an emphasis`() {
        // The emphasis chips are built from BodyPart, so a muscle missing from BodyPart is a
        // muscle you simply cannot emphasise. Abs were missing exactly that way.
        val offered = com.leo.forge.domain.model.BodyPart.entries.flatMap { it.muscles }.toSet()
        Muscle.entries.forEach { assertTrue("$it cannot be emphasised", it in offered) }
        assertEquals(Muscle.entries.size, offered.size)
    }

    @Test
    fun `emphasising abs trains them more often, not just harder on leg day`() {
        val byId = library.associateBy { it.id }
        fun daysTrainingAbs(meso: com.leo.forge.domain.mesocycle.GeneratedMeso) =
            meso.days.count { day ->
                day.exercises.any { byId.getValue(it.exerciseId).primaryMuscle == Muscle.ABS }
            }
        fun absSets(meso: com.leo.forge.domain.mesocycle.GeneratedMeso) =
            meso.days.flatMap { it.exercises }
                .filter { byId.getValue(it.exerciseId).primaryMuscle == Muscle.ABS }
                .sumOf { it.sets }

        val plain = MesocycleGenerator.generate(
            MesoSpec("a", SplitType.PUSH_PULL_LEGS, daysPerWeek = 6), library,
        )
        val emphasised = MesocycleGenerator.generate(
            MesoSpec("b", SplitType.PUSH_PULL_LEGS, daysPerWeek = 6, emphasis = setOf(Muscle.ABS)), library,
        )

        assertTrue(
            "abs frequency ${daysTrainingAbs(plain)} -> ${daysTrainingAbs(emphasised)}",
            daysTrainingAbs(emphasised) > daysTrainingAbs(plain),
        )
        assertTrue(absSets(emphasised) > absSets(plain))
    }

    @Test
    fun `emphasising a big muscle does not smear it across every day`() {
        val byId = library.associateBy { it.id }
        val emphasised = MesocycleGenerator.generate(
            MesoSpec("c", SplitType.PUSH_PULL_LEGS, daysPerWeek = 6, emphasis = setOf(Muscle.CHEST)), library,
        )
        val legDays = emphasised.days.filter { it.label.startsWith("Legs") }
        assertTrue("legs days should stay legs days", legDays.isNotEmpty())
        legDays.forEach { day ->
            assertTrue(
                "chest turned up on ${day.label}",
                day.exercises.none { byId.getValue(it.exerciseId).primaryMuscle == Muscle.CHEST },
            )
        }
    }

    @Test
    fun `the seeded library covers every muscle the generator can ask for`() {
        val covered = library.map { it.primaryMuscle }.toSet()
        Muscle.entries.forEach { assertTrue("no exercise for $it", it in covered) }
    }

    @Test
    fun `seeded exercise ids and names are unique`() {
        assertEquals(library.size, library.map { it.id }.distinct().size)
        assertEquals(library.size, library.map { it.name.lowercase() }.distinct().size)
    }
}
