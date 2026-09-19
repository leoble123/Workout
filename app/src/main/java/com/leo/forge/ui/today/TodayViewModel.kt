package com.leo.forge.ui.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leo.forge.core.container
import com.leo.forge.data.db.dao.MuscleVolume
import com.leo.forge.data.db.dao.PlannedDayWithExercises
import com.leo.forge.data.db.entity.MesocycleEntity
import com.leo.forge.data.db.entity.SessionEntity
import com.leo.forge.data.repo.ExercisePlanUi
import com.leo.forge.data.repo.ProgramRepository
import com.leo.forge.data.repo.StatsRepository
import com.leo.forge.data.repo.WorkoutRepository
import kotlinx.coroutines.flow.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Immutable
data class TodayState(
    val loading: Boolean = true,
    val meso: MesocycleEntity? = null,
    val activeSession: SessionEntity? = null,
    val nextDay: PlannedDayWithExercises? = null,
    val weekIndex: Int = 0,
    val preview: List<ExercisePlanUi> = emptyList(),
    val completedThisWeek: List<Long> = emptyList(),
    val muscleVolume: List<MuscleVolume> = emptyList(),
    val sessionsThisWeek: Int = 0,
    val tonnageThisWeek: Double = 0.0,
    val blockComplete: Boolean = false,
)

class TodayViewModel(
    private val program: ProgramRepository,
    private val workouts: WorkoutRepository,
    stats: StatsRepository,
) : ViewModel() {

    private val weekStart: Long = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val state: StateFlow<TodayState> = combine(
        program.observeCurrent(),
        workouts.observeActive(),
        workouts.observeRecent(30),
        stats.muscleVolumeSince(weekStart),
        stats.tonnageSince(weekStart),
    ) { meso, active, recent, volume, tonnage ->
        Quint(meso, active, recent, volume, tonnage)
    }.mapLatest { (meso, active, recent, volume, tonnage) ->
        if (meso == null) {
            return@mapLatest TodayState(
                loading = false,
                activeSession = active,
                muscleVolume = volume,
                tonnageThisWeek = tonnage,
                sessionsThisWeek = recent.count { it.startedAt >= weekStart },
            )
        }
        val next = workouts.nextUp(meso)
        val preview = next?.let { (day, week) ->
            workouts.prescribe(meso, day, week, excludeSessionId = active?.id ?: -1L)
        }.orEmpty()

        TodayState(
            loading = false,
            meso = meso,
            activeSession = active,
            nextDay = next?.first,
            weekIndex = next?.second ?: meso.currentWeek,
            preview = preview,
            completedThisWeek = recent.filter {
                it.mesocycleId == meso.id && it.weekIndex == (next?.second ?: meso.currentWeek)
            }.mapNotNull { it.plannedDayId },
            muscleVolume = volume,
            sessionsThisWeek = recent.count { it.startedAt >= weekStart },
            tonnageThisWeek = tonnage,
            blockComplete = next == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    suspend fun startWorkout(): Long? {
        val s = state.value
        workouts.active()?.let { return it.id }
        val meso = s.meso ?: return null
        val day = s.nextDay ?: return null
        return workouts.startSession(meso, day, s.weekIndex, day.day.label)
    }

    /** data class already supplies componentN, which is all the destructuring above needs. */
    private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                TodayViewModel(c.program, c.workouts, c.stats)
            }
        }
    }
}
