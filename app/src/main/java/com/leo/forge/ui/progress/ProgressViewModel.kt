package com.leo.forge.ui.progress

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leo.forge.core.container
import com.leo.forge.data.db.dao.ExerciseRecord
import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.data.repo.ExerciseRepository
import com.leo.forge.data.repo.StatsRepository
import com.leo.forge.domain.model.BodyPart
import com.leo.forge.ui.components.ChartPoint
import kotlinx.coroutines.flow.*

@Immutable
data class RecordRow(val exercise: ExerciseEntity, val record: ExerciseRecord)

@Immutable
data class RecordsState(
    val rows: List<RecordRow> = emptyList(),
    val query: String = "",
    val bodyPart: BodyPart? = null,
) {
    val filtered: List<RecordRow>
        get() {
            val q = query.trim().lowercase()
            return rows.asSequence()
                .filter { q.isEmpty() || it.exercise.name.lowercase().contains(q) }
                .filter { bodyPart == null || BodyPart.of(it.exercise.primaryMuscle) == bodyPart }
                .toList()
        }
}

/** Personal records, searchable - not something you reconstruct by scrolling a history feed. */
class RecordsViewModel(stats: StatsRepository, exercises: ExerciseRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val bodyPart = MutableStateFlow<BodyPart?>(null)

    val state: StateFlow<RecordsState> = combine(
        stats.records(), exercises.observeAll(), query, bodyPart,
    ) { records, library, q, bp ->
        val byId = library.associateBy { it.id }
        RecordsState(
            rows = records.mapNotNull { r -> byId[r.exerciseId]?.let { RecordRow(it, r) } }
                .sortedByDescending { it.record.lastTrained },
            query = q,
            bodyPart = bp,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsState())

    fun search(q: String) { query.value = q }
    fun filter(bp: BodyPart?) { bodyPart.value = bp }

    companion object {
        val Factory = viewModelFactory {
            initializer { RecordsViewModel(container.stats, container.exercises) }
        }
    }
}

@Immutable
data class ExerciseDetailState(
    val exercise: ExerciseEntity? = null,
    val topSet: SetLogEntity? = null,
    val sets: List<SetLogEntity> = emptyList(),
    val series: List<ChartPoint> = emptyList(),
) {
    val bestWeight: Double get() = sets.maxOfOrNull { it.weightKg } ?: 0.0
    val bestReps: Int get() = sets.maxOfOrNull { it.reps } ?: 0
    val totalSets: Int get() = sets.size
    val totalVolume: Double get() = sets.sumOf { it.weightKg * it.reps }
}

class ExerciseDetailViewModel(
    exerciseId: String,
    stats: StatsRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    val state: StateFlow<ExerciseDetailState> = combine(
        exercises.observeAll(),
        stats.topSet(exerciseId),
        stats.setsFor(exerciseId),
        stats.e1rmSeries(exerciseId),
    ) { library, top, sets, series ->
        ExerciseDetailState(
            exercise = library.firstOrNull { it.id == exerciseId },
            topSet = top,
            sets = sets,
            series = series.map { ChartPoint(it.at, it.value) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailState())

    companion object {
        fun factory(exerciseId: String) = viewModelFactory {
            initializer { ExerciseDetailViewModel(exerciseId, container.stats, container.exercises) }
        }
    }
}
