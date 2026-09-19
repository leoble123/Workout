package com.leo.forge.data.repo

import com.leo.forge.data.db.ForgeDatabase
import com.leo.forge.data.db.entity.*
import com.leo.forge.data.seed.GymSeed
import com.leo.forge.domain.gym.Availability
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.Units
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class GymRepository(private val db: ForgeDatabase) {

    fun observeAll(): Flow<List<GymEntity>> = db.gyms().observeAll()
    fun observeActive(): Flow<GymEntity?> = db.gyms().observeActive()
    suspend fun active(): GymEntity? = db.gyms().active()

    /** What the active gym's plates are marked in; drives every load increment. */
    fun observeUnits(): Flow<Units> = observeActive().map { it?.units ?: Units.KG }
    suspend fun units(): Units = active()?.units ?: Units.KG

    fun observeEquipment(gymId: Long): Flow<List<GymEquipmentEntity>> = db.gyms().observeEquipment(gymId)
    fun observeStations(gymId: Long): Flow<List<GymStationEntity>> = db.gyms().observeStations(gymId)
    fun observeOverrides(gymId: Long): Flow<List<ExerciseAvailabilityEntity>> = db.gyms().observeOverrides(gymId)
    fun observeStationExerciseIds(gymId: Long): Flow<List<String>> = db.gyms().observeExerciseIdsFromStations(gymId)
    fun observeStationLinks(stationIds: List<Long>): Flow<List<StationExerciseEntity>> =
        db.gyms().observeStationExercises(stationIds)

    /**
     * Creates a starting gym the first time the app runs. Without one, the generator has
     * no idea what is on the floor and quietly assumes a fully-stocked commercial gym.
     */
    suspend fun ensureDefault(preset: GymSeed.GymPreset) {
        if (db.gyms().count() > 0) return
        applyPreset(preset, makeActive = true)
    }

    suspend fun applyPreset(preset: GymSeed.GymPreset, makeActive: Boolean = true): Long {
        val gymId = db.gyms().insert(
            GymEntity(name = preset.name, units = preset.units, notes = preset.notes)
        )
        Equipment.entries.forEach { eq ->
            db.gyms().upsertEquipment(
                GymEquipmentEntity(
                    gymId = gymId,
                    equipment = eq,
                    available = preset.equipment[eq] ?: false,
                    detail = preset.equipmentDetail[eq],
                )
            )
        }
        // Only link exercises that actually exist in the library, so a preset referring to
        // an exercise removed later cannot break the foreign key.
        val known = db.exercises().allOnce().map { it.id }.toSet()
        preset.stations.forEachIndexed { i, st ->
            val stationId = db.gyms().insertStation(
                GymStationEntity(gymId = gymId, name = st.name, brand = st.brand, notes = st.notes, orderIndex = i)
            )
            db.gyms().addStationExercises(
                st.exerciseIds.filter { it in known }.map { StationExerciseEntity(stationId = stationId, exerciseId = it) }
            )
        }
        if (makeActive) db.gyms().makeActive(gymId)
        return gymId
    }

    suspend fun rename(gym: GymEntity, name: String) = db.gyms().update(gym.copy(name = name))
    suspend fun setUnits(gym: GymEntity, units: Units) = db.gyms().update(gym.copy(units = units))
    suspend fun setNotes(gym: GymEntity, notes: String?) = db.gyms().update(gym.copy(notes = notes))
    suspend fun makeActive(gymId: Long) = db.gyms().makeActive(gymId)
    suspend fun delete(gym: GymEntity) = db.gyms().delete(gym)
    suspend fun addGym(name: String, units: Units): Long = applyPreset(
        GymSeed.fullGym(units).copy(name = name), makeActive = false,
    )

    suspend fun setEquipmentAvailable(gymId: Long, equipment: Equipment, available: Boolean) {
        db.gyms().upsertEquipment(
            (db.gyms().equipment(gymId).firstOrNull { it.equipment == equipment }
                ?: GymEquipmentEntity(gymId = gymId, equipment = equipment))
                .copy(available = available)
        )
    }

    suspend fun setEquipmentDetail(gymId: Long, equipment: Equipment, detail: String?) {
        db.gyms().upsertEquipment(
            (db.gyms().equipment(gymId).firstOrNull { it.equipment == equipment }
                ?: GymEquipmentEntity(gymId = gymId, equipment = equipment))
                .copy(detail = detail?.takeIf { it.isNotBlank() })
        )
    }

    suspend fun addStation(gymId: Long, name: String, brand: String?, notes: String?): Long =
        db.gyms().insertStation(
            GymStationEntity(
                gymId = gymId, name = name, brand = brand?.takeIf { it.isNotBlank() },
                notes = notes?.takeIf { it.isNotBlank() },
                orderIndex = db.gyms().stations(gymId).size,
            )
        )

    suspend fun updateStation(station: GymStationEntity) = db.gyms().updateStation(station)
    suspend fun deleteStation(station: GymStationEntity) = db.gyms().deleteStation(station)

    suspend fun setStationExercise(stationId: Long, exerciseId: String, present: Boolean) {
        if (present) db.gyms().addStationExercise(StationExerciseEntity(stationId = stationId, exerciseId = exerciseId))
        else db.gyms().removeStationExercise(stationId, exerciseId)
    }

    suspend fun setOverride(gymId: Long, exerciseId: String, available: Boolean?, notes: String? = null) {
        if (available == null && notes.isNullOrBlank()) {
            db.gyms().clearOverride(gymId, exerciseId)
            return
        }
        val existing = db.gyms().override(gymId, exerciseId)
        db.gyms().upsertOverride(
            ExerciseAvailabilityEntity(
                id = existing?.id ?: 0,
                gymId = gymId,
                exerciseId = exerciseId,
                available = available ?: existing?.available ?: true,
                notes = notes?.takeIf { it.isNotBlank() } ?: existing?.notes,
            )
        )
    }

    /** The set of exercises performable at the active gym; null when no gym is configured. */
    suspend fun availableExerciseIds(): Set<String>? {
        val gym = active() ?: return null
        val library = db.exercises().allOnce()
        return Availability.resolve(
            library = library,
            availableEquipment = db.gyms().equipment(gym.id).filter { it.available }.map { it.equipment }.toSet(),
            stationExerciseIds = db.gyms().exerciseIdsFromStations(gym.id).toSet(),
            overrides = db.gyms().overrides(gym.id).associate { it.exerciseId to it.available },
        )
    }

    fun observeAvailableExerciseIds(): Flow<Set<String>?> = observeActive().flatMapLatest { gym ->
        if (gym == null) flowOf(null)
        else combine(
            db.exercises().observeAll(),
            db.gyms().observeEquipment(gym.id),
            db.gyms().observeExerciseIdsFromStations(gym.id),
            db.gyms().observeOverrides(gym.id),
        ) { library, equipment, stationIds, overrides ->
            Availability.resolve(
                library = library,
                availableEquipment = equipment.filter { it.available }.map { it.equipment }.toSet(),
                stationExerciseIds = stationIds.toSet(),
                overrides = overrides.associate { it.exerciseId to it.available },
            )
        }
    }
}
