package com.leo.forge.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leo.forge.core.container
import com.leo.forge.data.db.entity.SessionEntity
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.data.repo.ExerciseRepository
import com.leo.forge.data.repo.WorkoutRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Immutable
data class HistoryState(val sessions: List<SessionEntity> = emptyList())

class HistoryViewModel(workouts: WorkoutRepository) : ViewModel() {
    val state: StateFlow<HistoryState> = workouts.observeRecent(200)
        .map { HistoryState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    companion object {
        val Factory = viewModelFactory {
            initializer { HistoryViewModel(container.workouts) }
        }
    }
}

@Immutable
data class ExerciseGroup(val exerciseId: String, val name: String, val sets: List<SetLogEntity>)

@Immutable
data class SessionDetailState(
    val session: SessionEntity? = null,
    val byExercise: List<ExerciseGroup> = emptyList(),
)

class SessionDetailViewModel(
    private val sessionId: Long,
    private val workouts: WorkoutRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    /** Removes the workout and everything logged in it. Records and totals stop counting it. */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            workouts.deleteSession(sessionId)
            onDone()
        }
    }

    val state: StateFlow<SessionDetailState> = combine(
        workouts.observeSession(sessionId),
        workouts.observeSets(sessionId),
        exercises.observeAll(),
    ) { session, sets, library ->
        val names = library.associate { it.id to it.name }
        SessionDetailState(
            session = session,
            // Preserve the order the exercises were actually performed in.
            byExercise = sets.groupBy { it.exerciseId }
                .map { (id, s) -> ExerciseGroup(id, names[id] ?: id, s.sortedBy { it.setIndex }) }
                .sortedBy { g -> g.sets.minOfOrNull { it.setIndex } ?: 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailState())

    companion object {
        fun factory(sessionId: Long) = viewModelFactory {
            initializer {
                val c = container
                SessionDetailViewModel(sessionId, c.workouts, c.exercises)
            }
        }
    }
}
