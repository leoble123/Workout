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
    val equipment: List<com.leo.forge.data.db.entity.GymEquipmentEntity> = emptyList(),
    val gymId: Long? = null,
) {
    fun has(equipment: Equipment): Boolean =
        this.equipment.firstOrNull { it.equipment == equipment }?.available ?: false
}

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
    private val activeGym = gyms.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val gymSummary: StateFlow<GymSummary> = combine(
        activeGym,
        gyms.observeAvailableExerciseIds(),
        exercises.observeAll(),
        activeGym.flatMapLatest { g -> if (g == null) flowOf(emptyList()) else gyms.observeEquipment(g.id) },
    ) { gym, ids, library, equipment ->
        GymSummary(
            name = gym?.name,
            units = gym?.units,
            availableCount = ids?.size ?: library.size,
            library = library,
            availableIds = ids,
            equipment = equipment,
            gymId = gym?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GymSummary())

    /**
     * Equipment edited in the builder writes straight to the gym profile rather than being a
     * separate per-block answer - there is only one true answer to "what is on the floor",
     * and keeping two of them is how they drift apart.
     */
    fun setEquipment(equipment: Equipment, available: Boolean) = viewModelScope.launch {
        gymSummary.value.gymId?.let { gyms.setEquipmentAvailable(it, equipment, available) }
    }

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
