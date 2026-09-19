package com.leo.forge.ui.program

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leo.forge.core.container
import com.leo.forge.data.db.dao.PlannedDayWithExercises
import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.db.entity.MesocycleEntity
import com.leo.forge.data.repo.ExerciseRepository
import com.leo.forge.data.repo.ProgramRepository
import com.leo.forge.domain.mesocycle.MesoSpec
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.SplitType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Immutable
data class GymSummary(
    val name: String? = null,
    val units: com.leo.forge.domain.model.Units? = null,
    val availableCount: Int = 0,
    val library: List<ExerciseEntity> = emptyList(),
    val availableIds: Set<String>? = null,
)

@Immutable
data class ProgramState(
    val meso: MesocycleEntity? = null,
    val days: List<PlannedDayWithExercises> = emptyList(),
    val library: Map<String, ExerciseEntity> = emptyMap(),
    val building: Boolean = false,
)

class ProgramViewModel(
    private val program: ProgramRepository,
    private val gyms: com.leo.forge.data.repo.GymRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    private val building = MutableStateFlow(false)

    /** The gym the block will be built from, and how much it can actually do. */
    val gymSummary: StateFlow<GymSummary> = combine(
        gyms.observeActive(), gyms.observeAvailableExerciseIds(), exercises.observeAll(),
    ) { gym, ids, library ->
        GymSummary(
            name = gym?.name,
            units = gym?.units,
            availableCount = ids?.size ?: library.size,
            library = library,
            availableIds = ids,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GymSummary())

    private val mesoFlow = program.observeCurrent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val daysFlow = mesoFlow.flatMapLatest { m ->
        if (m == null) flowOf(emptyList()) else program.observeDays(m.id)
    }

    val state: StateFlow<ProgramState> = combine(
        mesoFlow, daysFlow, exercises.observeAll(), building,
    ) { meso, days, library, isBuilding ->
        ProgramState(meso, days, library.associateBy { it.id }, isBuilding)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramState())

    fun swapPlanned(planned: com.leo.forge.data.db.entity.PlannedExerciseEntity, newExerciseId: String) =
        viewModelScope.launch { program.swapPlanned(planned, newExerciseId) }

    fun removePlanned(planned: com.leo.forge.data.db.entity.PlannedExerciseEntity) =
        viewModelScope.launch { program.removePlanned(planned) }

    fun addToDay(dayId: Long, exerciseId: String, orderIndex: Int) = viewModelScope.launch {
        state.value.library[exerciseId]?.let { program.addExerciseToDay(dayId, it, orderIndex) }
    }

    fun build(
        name: String,
        split: SplitType,
        daysPerWeek: Int,
        weeks: Int,
        emphasis: Set<Muscle>,
        onDone: () -> Unit,
    ) {
        if (building.value) return
        building.value = true
        viewModelScope.launch {
            try {
                program.createMesocycle(
                    MesoSpec(
                        name = name.ifBlank { split.display },
                        split = split,
                        daysPerWeek = daysPerWeek,
                        totalWeeks = weeks,
                        // Availability comes from the gym profile, not from a checklist here.
                        emphasis = emphasis,
                    )
                )
                onDone()
            } finally {
                building.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                ProgramViewModel(c.program, c.gyms, c.exercises)
            }
        }
    }
}
