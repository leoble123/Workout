package com.leo.forge.domain.insights

import androidx.compose.runtime.Immutable
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.domain.model.Load
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.Units
import com.leo.forge.domain.volume.Landmarks
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class Mover(
    val exerciseId: String,
    val name: String,
    val startE1rm: Double,
    val endE1rm: Double,
    val sessions: Int,
) {
    val deltaKg: Double get() = endE1rm - startE1rm
    val deltaPct: Double get() = if (startE1rm <= 0) 0.0 else (endE1rm - startE1rm) / startE1rm * 100.0
}

@Immutable
data class Insights(
    val weeks: Int,
    val sessions: Int,
    val prCount: Int,
    val totalVolumeKg: Double,
    val volumeChangePct: Double,
    val movers: List<Mover>,
    val stalled: List<Mover>,
    val underMev: List<Muscle>,
    val overMrv: List<Muscle>,
    val headline: String,
    val paragraphs: List<String>,
    val enoughData: Boolean,
)

/**
 * Reads your training log and writes it up.
 *
 * This runs entirely on the device: no account, no network call, nothing leaves the phone.
 * It is arithmetic and rules rather than a language model, which means it is instant, free,
 * offline, and - more usefully - it cannot invent a number. Every figure below is computed
 * from sets you actually logged.
 */
object InsightsEngine {

    /** Below this a "trend" is noise: two sessions is a line through two points. */
    private const val MIN_SESSIONS_FOR_TREND = 3
    private const val STALL_THRESHOLD_PCT = 1.0

    fun analyse(
        sets: List<SetLogEntity>,
        names: Map<String, String>,
        weeklySetsByMuscle: Map<Muscle, Int>,
        landmarks: (Muscle) -> Landmarks,
        units: Units,
        nowMillis: Long = System.currentTimeMillis(),
    ): Insights {
        if (sets.isEmpty()) {
            return empty("Nothing logged yet.", listOf(
                "Train a few sessions, or bring your Hevy history across from Settings, and this " +
                    "fills in with what actually changed."
            ))
        }

        val zone = ZoneId.systemDefault()
        val firstDay = Instant.ofEpochMilli(sets.minOf { it.completedAt }).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(firstDay, lastDay).toInt().coerceAtLeast(1)
        val weeks = ((days + 6) / 7).coerceAtLeast(1)

        val sessionIds = sets.map { it.sessionId }.distinct()
        val prCount = sets.count { it.isPr }
        val totalVolume = sets.sumOf { it.weightKg * it.reps }

        // Volume: first half of the window against the second.
        val midpoint = sets.minOf { it.completedAt } + (nowMillis - sets.minOf { it.completedAt }) / 2
        val firstHalf = sets.filter { it.completedAt < midpoint }.sumOf { it.weightKg * it.reps }
        val secondHalf = sets.filter { it.completedAt >= midpoint }.sumOf { it.weightKg * it.reps }
        val volumeChange = if (firstHalf <= 0) 0.0 else (secondHalf - firstHalf) / firstHalf * 100.0

        // Per exercise: best e1RM in its first session against its most recent.
        val byExercise = sets.filter { it.e1rmKg > 0 }.groupBy { it.exerciseId }
        val trends = byExercise.mapNotNull { (id, logs) ->
            val perSession = logs.groupBy { it.sessionId }
                .map { (_, s) -> s.minOf { it.completedAt } to s.maxOf { it.e1rmKg } }
                .sortedBy { it.first }
            if (perSession.size < 2) return@mapNotNull null
            Mover(
                exerciseId = id,
                name = names[id] ?: id,
                startE1rm = perSession.first().second,
                endE1rm = perSession.last().second,
                sessions = perSession.size,
            )
        }

        val movers = trends.filter { it.deltaPct > STALL_THRESHOLD_PCT }.sortedByDescending { it.deltaPct }
        val stalled = trends
            .filter { it.sessions >= MIN_SESSIONS_FOR_TREND && it.deltaPct <= STALL_THRESHOLD_PCT }
            .sortedBy { it.deltaPct }

        val underMev = weeklySetsByMuscle.filter { (m, s) -> s in 1 until landmarks(m).mev }.keys.toList()
        val overMrv = weeklySetsByMuscle.filter { (m, s) -> s > landmarks(m).mrv }.keys.toList()

        val enough = sessionIds.size >= 3 && trends.isNotEmpty()

        fun show(kg: Double) = "${Load.format(abs(Load.toDisplay(kg, units)))} ${units.display}"

        val headline = when {
            !enough -> "Early days - ${sessionIds.size} session${plural(sessionIds.size)} logged."
            movers.isEmpty() -> "Holding steady across ${trends.size} lifts over $weeks week${plural(weeks)}."
            movers.size >= trends.size * 0.6 ->
                "Up on ${movers.size} of ${trends.size} lifts over $weeks week${plural(weeks)}."
            else -> "${movers.size} lift${plural(movers.size)} moving, ${stalled.size} flat, over $weeks week${plural(weeks)}."
        }

        val paragraphs = buildList {
            add(
                "Across $weeks week${plural(weeks)} you logged ${sessionIds.size} session" +
                    "${plural(sessionIds.size)} and ${sets.size} working sets, " +
                    "about ${"%.1f".format(sessionIds.size.toDouble() / weeks)} sessions a week. " +
                    if (prCount > 0) "$prCount set${plural(prCount)} came in as a personal best."
                    else "No personal bests in that stretch."
            )

            if (!enough) {
                add(
                    "There is not enough history yet to call a trend - an exercise needs at least two " +
                        "sessions before a line through it means anything. Keep logging and this gets sharper."
                )
                return@buildList
            }

            movers.take(3).takeIf { it.isNotEmpty() }?.let { top ->
                add(
                    "Strongest movers: " + top.joinToString("; ") {
                        "${it.name} up ${show(it.deltaKg)} (${it.deltaPct.roundToInt()}%) over ${it.sessions} sessions"
                    } + "."
                )
            }

            stalled.take(3).takeIf { it.isNotEmpty() }?.let { flat ->
                add(
                    "Flat or slipping: " + flat.joinToString("; ") {
                        "${it.name} (${if (it.deltaPct < 0) "" else "+"}${it.deltaPct.roundToInt()}% over ${it.sessions} sessions)"
                    } + ". A stall across three or more sessions usually means fatigue rather than " +
                        "effort - a lighter week, or dropping a set, tends to restart it faster than pushing harder."
                )
            }

            add(
                when {
                    abs(volumeChange) < 10 -> "Training volume has held roughly level across the period."
                    volumeChange > 0 -> "Volume is up ${volumeChange.roundToInt()}% in the back half of this " +
                        "stretch. That is the intended direction inside a block, as long as recovery keeps up."
                    else -> "Volume is down ${abs(volumeChange).roundToInt()}% in the back half. If that was a " +
                        "deload it is working as designed; if it was not, it is worth knowing."
                }
            )

            if (underMev.isNotEmpty()) {
                add(
                    "Below your minimum effective volume this week: ${underMev.joinToString { it.display }}. " +
                        "Enough to maintain, not enough to grow."
                )
            }
            if (overMrv.isNotEmpty()) {
                add(
                    "Above your maximum recoverable volume this week: ${overMrv.joinToString { it.display }}. " +
                        "Past this point extra sets mostly buy fatigue."
                )
            }
        }

        return Insights(
            weeks = weeks,
            sessions = sessionIds.size,
            prCount = prCount,
            totalVolumeKg = totalVolume,
            volumeChangePct = volumeChange,
            movers = movers,
            stalled = stalled,
            underMev = underMev,
            overMrv = overMrv,
            headline = headline,
            paragraphs = paragraphs,
            enoughData = enough,
        )
    }

    /** Everything with a trend, best first - the input to the movers chart. */
    fun allTrends(insights: Insights): List<Mover> =
        (insights.movers + insights.stalled).sortedByDescending { it.deltaPct }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    private fun empty(headline: String, paragraphs: List<String>) = Insights(
        weeks = 0, sessions = 0, prCount = 0, totalVolumeKg = 0.0, volumeChangePct = 0.0,
        movers = emptyList(), stalled = emptyList(), underMev = emptyList(), overMrv = emptyList(),
        headline = headline, paragraphs = paragraphs, enoughData = false,
    )
}
