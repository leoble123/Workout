package com.leo.forge.data.db.dao

import androidx.room.*
import com.leo.forge.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GymDao {

    @Query("SELECT * FROM gyms ORDER BY createdAt")
    fun observeAll(): Flow<List<GymEntity>>

    @Query("SELECT * FROM gyms WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<GymEntity?>

    @Query("SELECT * FROM gyms WHERE isActive = 1 LIMIT 1")
    suspend fun active(): GymEntity?

    @Query("SELECT COUNT(*) FROM gyms")
    suspend fun count(): Int

    @Insert suspend fun insert(gym: GymEntity): Long
    @Update suspend fun update(gym: GymEntity)
    @Delete suspend fun delete(gym: GymEntity)

    @Query("UPDATE gyms SET isActive = 0")
    suspend fun clearActive()

    @Transaction
    suspend fun makeActive(gymId: Long) {
        clearActive()
        setActive(gymId)
    }

    @Query("UPDATE gyms SET isActive = 1 WHERE id = :gymId")
    suspend fun setActive(gymId: Long)

    // --- equipment
    @Query("SELECT * FROM gym_equipment WHERE gymId = :gymId")
    fun observeEquipment(gymId: Long): Flow<List<GymEquipmentEntity>>

    @Query("SELECT * FROM gym_equipment WHERE gymId = :gymId")
    suspend fun equipment(gymId: Long): List<GymEquipmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEquipment(e: GymEquipmentEntity)

    @Query("UPDATE gym_equipment SET available = :available WHERE gymId = :gymId AND equipment = :equipment")
    suspend fun setEquipmentAvailable(gymId: Long, equipment: com.leo.forge.domain.model.Equipment, available: Boolean)

    @Query("UPDATE gym_equipment SET detail = :detail WHERE gymId = :gymId AND equipment = :equipment")
    suspend fun setEquipmentDetail(gymId: Long, equipment: com.leo.forge.domain.model.Equipment, detail: String?)

    // --- stations
    @Query("SELECT * FROM gym_stations WHERE gymId = :gymId ORDER BY orderIndex, id")
    fun observeStations(gymId: Long): Flow<List<GymStationEntity>>

    @Query("SELECT * FROM gym_stations WHERE gymId = :gymId ORDER BY orderIndex, id")
    suspend fun stations(gymId: Long): List<GymStationEntity>

    @Insert suspend fun insertStation(s: GymStationEntity): Long
    @Update suspend fun updateStation(s: GymStationEntity)
    @Delete suspend fun deleteStation(s: GymStationEntity)

    @Query("SELECT * FROM station_exercises WHERE stationId IN (:stationIds)")
    fun observeStationExercises(stationIds: List<Long>): Flow<List<StationExerciseEntity>>

    @Query(
        """
        SELECT se.exerciseId FROM station_exercises se
        JOIN gym_stations gs ON gs.id = se.stationId
        WHERE gs.gymId = :gymId
        """
    )
    suspend fun exerciseIdsFromStations(gymId: Long): List<String>

    @Query(
        """
        SELECT se.exerciseId FROM station_exercises se
        JOIN gym_stations gs ON gs.id = se.stationId
        WHERE gs.gymId = :gymId
        """
    )
    fun observeExerciseIdsFromStations(gymId: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addStationExercise(link: StationExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addStationExercises(links: List<StationExerciseEntity>)

    @Query("DELETE FROM station_exercises WHERE stationId = :stationId AND exerciseId = :exerciseId")
    suspend fun removeStationExercise(stationId: Long, exerciseId: String)

    @Query("DELETE FROM station_exercises WHERE stationId = :stationId")
    suspend fun clearStationExercises(stationId: Long)

    // --- per-exercise overrides
    @Query("SELECT * FROM exercise_availability WHERE gymId = :gymId")
    fun observeOverrides(gymId: Long): Flow<List<ExerciseAvailabilityEntity>>

    @Query("SELECT * FROM exercise_availability WHERE gymId = :gymId")
    suspend fun overrides(gymId: Long): List<ExerciseAvailabilityEntity>

    @Query("SELECT * FROM exercise_availability WHERE gymId = :gymId AND exerciseId = :exerciseId")
    suspend fun override(gymId: Long, exerciseId: String): ExerciseAvailabilityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(o: ExerciseAvailabilityEntity)

    @Query("DELETE FROM exercise_availability WHERE gymId = :gymId AND exerciseId = :exerciseId")
    suspend fun clearOverride(gymId: Long, exerciseId: String)
}
