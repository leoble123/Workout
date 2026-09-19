package com.leo.forge

import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.domain.insights.InsightsEngine
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.OneRepMax
import com.leo.forge.domain.model.Units
import com.leo.forge.domain.volume.VolumeLandmarks
import org.junit.Assert.*
import org.junit.Test

class InsightsTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L

    private fun set(exerciseId: String, sessionId: Long, daysAgo: Int, kg: Double, reps: Int = 8) =
        SetLogEntity(
            sessionId = sessionId, exerciseId = exerciseId, setIndex = 0,
            weightKg = kg, reps = reps, rir = null,
            completedAt = now - daysAgo * day,
            e1rmKg = OneRepMax.estimate(kg, reps),
        )

    private fun analyse(sets: List<SetLogEntity>, volume: Map<Muscle, Int> = emptyMap()) =
        InsightsEngine.analyse(
            sets = sets,
            names = mapOf("bench" to "Bench Press", "curl" to "Barbell Curl"),
            weeklySetsByMuscle = volume,
            landmarks = { VolumeLandmarks.of(it) },
            units = Units.KG,
            nowMillis = now,
        )

    @Test
    fun `an empty log says so instead of inventing a trend`() {
        val r = analyse(emptyList())
        assertFalse(r.enoughData)
        assertEquals(0, r.sessions)
        assertTrue(r.paragraphs.isNotEmpty())
    }

    @Test
    fun `a rising lift is reported as a mover with the right numbers`() {
        val sets = listOf(
            set("bench", 1, 28, 100.0),
            set("bench", 2, 14, 105.0),
            set("bench", 3, 1, 110.0),
        )
        val r = analyse(sets)
        val bench = r.movers.first { it.exerciseId == "bench" }
        assertTrue(bench.deltaKg > 0)
        assertEquals(3, bench.sessions)
        assertTrue("headline was '${r.headline}'", r.headline.isNotBlank())
    }

    @Test
    fun `a flat lift over several sessions is called a stall`() {
        val sets = listOf(
            set("curl", 1, 21, 40.0),
            set("curl", 2, 14, 40.0),
            set("curl", 3, 7, 40.0),
        )
        val r = analyse(sets)
        assertTrue(r.stalled.any { it.exerciseId == "curl" })
        assertTrue(r.movers.none { it.exerciseId == "curl" })
    }

    @Test
    fun `one session on a lift is not a trend in either direction`() {
        val r = analyse(listOf(set("bench", 1, 3, 100.0)))
        assertTrue(r.movers.isEmpty())
        assertTrue(r.stalled.isEmpty())
    }

    @Test
    fun `volume direction is detected`() {
        val light = (1..3).map { set("bench", it.toLong(), 80 - it, 50.0, reps = 5) }
        val heavy = (4..9).map { set("bench", it.toLong(), 10 - (it - 4), 100.0, reps = 10) }
        val r = analyse(light + heavy)
        assertTrue("volume change was ${r.volumeChangePct}", r.volumeChangePct > 0)
    }

    @Test
    fun `landmark breaches are named`() {
        val r = analyse(
            listOf(set("bench", 1, 5, 100.0), set("bench", 2, 2, 105.0)),
            volume = mapOf(Muscle.CHEST to 2, Muscle.BICEPS to 40),
        )
        assertTrue(Muscle.CHEST in r.underMev)
        assertTrue(Muscle.BICEPS in r.overMrv)
    }

    @Test
    fun `personal bests are counted`() {
        val sets = listOf(
            set("bench", 1, 10, 100.0).copy(isPr = true),
            set("bench", 2, 3, 105.0).copy(isPr = true),
        )
        assertEquals(2, analyse(sets).prCount)
    }

    @Test
    fun `every paragraph is readable prose, not a stub`() {
        val sets = (1..6).map { set("bench", it.toLong(), 40 - it * 5, 100.0 + it) }
        analyse(sets).paragraphs.forEach {
            assertTrue("too short: '$it'", it.length > 30)
            assertTrue("no sentence end: '$it'", it.trim().endsWith("."))
        }
    }
}
