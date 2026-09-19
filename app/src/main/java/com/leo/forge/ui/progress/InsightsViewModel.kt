package com.leo.forge.ui.progress

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leo.forge.core.container
import com.leo.forge.data.db.dao.MuscleVolume
import com.leo.forge.data.repo.ExerciseRepository
import com.leo.forge.data.repo.GymRepository
import com.leo.forge.data.repo.StatsRepository
import com.leo.forge.domain.insights.Insights
import com.leo.forge.domain.insights.InsightsEngine
import com.leo.forge.domain.insights.Mover
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.Units
import com.leo.forge.domain.volume.VolumeLandmarks
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Immutable
data class InsightsState(
    val trends: List<Mover> = emptyList(),
    val muscleVolume: List<MuscleVolume> = emptyList(),
    val tonnage: Double = 0.0,
    val sessions: Int = 0,
    val units: Units = Units.KG,
    val summary: Insights? = null,
    val generating: Boolean = false,
    val canGenerate: Boolean = false,
)

class InsightsViewModel(
    private val stats: StatsRepository,
    private val exercises: ExerciseRepository,
    gyms: GymRepository,
) : ViewModel() {

    /** Ninety days: long enough for a block or two, short enough that it is still about now. */
    private val windowStart: Long = LocalDate.now().minusDays(90)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val weekStart: Long = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val summary = MutableStateFlow<Insights?>(null)
    private val generating = MutableStateFlow(false)

    private val base = combine(
        stats.workingSetsSince(windowStart),
        exercises.observeAll(),
        stats.muscleVolumeSince(weekStart),
        stats.tonnageSince(weekStart),
        gyms.observeUnits(),
    ) { sets, library, volume, tonnage, units ->
        val names = library.associate { it.id to it.name }
        val analysis = InsightsEngine.analyse(
            sets = sets,
            names = names,
            weeklySetsByMuscle = volume.associate { it.muscle to it.sets },
            landmarks = { m: Muscle -> VolumeLandmarks.of(m) },
            units = units,
        )
        InsightsState(
            trends = InsightsEngine.allTrends(analysis).take(8),
            muscleVolume = volume,
            tonnage = tonnage,
            sessions = analysis.sessions,
            units = units,
            canGenerate = sets.isNotEmpty(),
        )
    }

    val state: StateFlow<InsightsState> = combine(base, summary, generating) { b, s, g ->
        b.copy(summary = s, generating = g)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())

    /**
     * Recomputes the write-up.
     *
     * It is on a button rather than always-on because a summary that rewrites itself while you
     * read it is worse than one you asked for.
     */
    fun generate() {
        if (generating.value) return
        generating.value = true
        viewModelScope.launch {
            val sets = stats.workingSetsSince(windowStart).first()
            val library = exercises.observeAll().first()
            val volume = stats.muscleVolumeSince(weekStart).first()
            summary.value = InsightsEngine.analyse(
                sets = sets,
                names = library.associate { it.id to it.name },
                weeklySetsByMuscle = volume.associate { it.muscle to it.sets },
                landmarks = { m: Muscle -> VolumeLandmarks.of(m) },
                units = state.value.units,
            )
            generating.value = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                InsightsViewModel(c.stats, c.exercises, c.gyms)
            }
        }
    }
}
