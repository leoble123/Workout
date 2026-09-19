package com.leo.forge.ui.gym

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leo.forge.core.container
import com.leo.forge.data.db.entity.*
import com.leo.forge.data.repo.ExerciseRepository
import com.leo.forge.data.repo.GymRepository
import com.leo.forge.data.seed.GymSeed
import com.leo.forge.domain.gym.Availability
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.Units
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Immutable
data class GymState(
    val loading: Boolean = true,
    val gyms: List<GymEntity> = emptyList(),
    val gym: GymEntity? = null,
    val equipment: List<GymEquipmentEntity> = emptyList(),
    val stations: List<GymStationEntity> = emptyList(),
    val stationLinks: List<StationExerciseEntity> = emptyList(),
    val overrides: List<ExerciseAvailabilityEntity> = emptyList(),
    val library: List<ExerciseEntity> = emptyList(),
) {
    val availableIds: Set<String>
        get() = Availability.resolve(
            library = library,
            availableEquipment = equipment.filter { it.available }.map { it.equipment }.toSet(),
            stationExerciseIds = stationLinks.map { it.exerciseId }.toSet(),
            overrides = overrides.associate { it.exerciseId to it.available },
        )

    fun detailFor(equipment: Equipment): String =
        this.equipment.firstOrNull { it.equipment == equipment }?.detail.orEmpty()

    fun isAvailable(equipment: Equipment): Boolean =
        this.equipment.firstOrNull { it.equipment == equipment }?.available ?: false

    fun exerciseIdsFor(stationId: Long): Set<String> =
        stationLinks.filter { it.stationId == stationId }.map { it.exerciseId }.toSet()

    /** Muscles with nothing trainable here - the generator cannot cover these. */
    val uncoveredMuscles: List<Muscle>
        get() {
            val ids = availableIds
            return Muscle.entries.filter { m ->
                library.none { it.id in ids && it.primaryMuscle == m }
            }
        }

    val availableCount: Int get() = availableIds.size
}

class GymViewModel(
    private val gyms: GymRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    private val activeGym = gyms.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val details: Flow<Triple<List<GymEquipmentEntity>, List<GymStationEntity>, List<ExerciseAvailabilityEntity>>> =
        activeGym.flatMapLatest { gym ->
            if (gym == null) flowOf(Triple(emptyList(), emptyList(), emptyList()))
            else combine(
                gyms.observeEquipment(gym.id),
                gyms.observeStations(gym.id),
                gyms.observeOverrides(gym.id),
            ) { e, s, o -> Triple(e, s, o) }
        }

    private val links: Flow<List<StationExerciseEntity>> = details
        .map { it.second.map { s -> s.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids -> if (ids.isEmpty()) flowOf(emptyList()) else gyms.observeStationLinks(ids) }

    val state: StateFlow<GymState> = combine(
        gyms.observeAll(), activeGym, details, links, exercises.observeAll(),
    ) { all, gym, d, l, library ->
        GymState(
            loading = false,
            gyms = all,
            gym = gym,
            equipment = d.first,
            stations = d.second,
            overrides = d.third,
            stationLinks = l,
            library = library,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GymState())

    fun setUnits(units: Units) = viewModelScope.launch {
        state.value.gym?.let { gyms.setUnits(it, units) }
    }

    fun rename(name: String) = viewModelScope.launch {
        state.value.gym?.let { gyms.rename(it, name) }
    }

    fun setNotes(notes: String) = viewModelScope.launch {
        state.value.gym?.let { gyms.setNotes(it, notes.takeIf { n -> n.isNotBlank() }) }
    }

    fun setEquipment(equipment: Equipment, available: Boolean) = viewModelScope.launch {
        state.value.gym?.let { gyms.setEquipmentAvailable(it.id, equipment, available) }
    }

    fun setEquipmentDetail(equipment: Equipment, detail: String) = viewModelScope.launch {
        state.value.gym?.let { gyms.setEquipmentDetail(it.id, equipment, detail) }
    }

    fun addStation(name: String) = viewModelScope.launch {
        state.value.gym?.let { gyms.addStation(it.id, name.ifBlank { "New station" }, null, null) }
    }

    fun updateStation(station: GymStationEntity) = viewModelScope.launch { gyms.updateStation(station) }
    fun deleteStation(station: GymStationEntity) = viewModelScope.launch { gyms.deleteStation(station) }

    fun toggleStationExercise(stationId: Long, exerciseId: String, present: Boolean) = viewModelScope.launch {
        gyms.setStationExercise(stationId, exerciseId, present)
    }

    fun setOverride(exerciseId: String, available: Boolean?) = viewModelScope.launch {
        state.value.gym?.let { gyms.setOverride(it.id, exerciseId, available) }
    }

    fun addGymFromPreset(full: Boolean, units: Units) = viewModelScope.launch {
        val preset = if (full) GymSeed.fullGym(units) else GymSeed.cableLedGym(units)
        gyms.applyPreset(preset, makeActive = true)
    }

    fun switchTo(gymId: Long) = viewModelScope.launch { gyms.makeActive(gymId) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                GymViewModel(c.gyms, c.exercises)
            }
        }
    }
}
