package com.leo.forge.ui.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leo.forge.core.container
import com.leo.forge.data.db.dao.MuscleVolume
import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.repo.ExerciseRepository
import com.leo.forge.data.repo.StatsRepository
import com.leo.forge.ui.components.ChartPoint
import kotlinx.coroutines.flow.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Immutable
data class StatsState(
    val muscleVolume: List<MuscleVolume> = emptyList(),
    val tonnage: Double = 0.0,
    val sessions: Int = 0,
    val trackable: List<ExerciseEntity> = emptyList(),
    val selected: ExerciseEntity? = null,
    val series: List<ChartPoint> = emptyList(),
)

class StatsViewModel(
    private val stats: StatsRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    private val weekStart: Long = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val selectedId = MutableStateFlow<String?>(null)

    /** Only exercises with logged sets are offered - an empty chart is not a choice worth making. */
    private val trackable: Flow<List<ExerciseEntity>> = combine(
        stats.recentExerciseIds(60), exercises.observeAll(),
    ) { ids, library ->
        val byId = library.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }

    private val series: Flow<List<ChartPoint>> = combine(selectedId, trackable) { id, list ->
        id ?: list.firstOrNull()?.id
    }.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else stats.e1rmSeries(id).map { points -> points.map { ChartPoint(it.at, it.value) } }
    }

    val state: StateFlow<StatsState> = combine(
        stats.muscleVolumeSince(weekStart),
        stats.tonnageSince(weekStart),
        stats.sessionsSince(weekStart),
        trackable,
        series,
    ) { volume, tonnage, sessions, list, points ->
        val chosen = list.firstOrNull { it.id == selectedId.value } ?: list.firstOrNull()
        StatsState(volume, tonnage, sessions, list, chosen, points)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun select(id: String) { selectedId.value = id }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                StatsViewModel(c.stats, c.exercises)
            }
        }
    }
}
