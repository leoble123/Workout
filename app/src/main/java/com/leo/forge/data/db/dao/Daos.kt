package com.leo.forge.data.db.dao

import androidx.room.*
import com.leo.forge.data.db.entity.*
import com.leo.forge.domain.model.MesoStatus
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE archived = 0 ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE archived = 0 ORDER BY name")
    suspend fun allOnce(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE archived = 0 AND primaryMuscle = :muscle ORDER BY name")
    suspend fun forMuscle(muscle: Muscle): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun byId(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE archived = 0 AND name LIKE '%' || :q || '%' ORDER BY isFavorite DESC, name LIMIT 60")
    fun search(q: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ExerciseEntity)

    @Update suspend fun update(item: ExerciseEntity)
}

@Dao
interface MesocycleDao {
    @Query("SELECT * FROM mesocycles WHERE status = :status ORDER BY createdAt DESC LIMIT 1")
    fun observeCurrent(status: MesoStatus = MesoStatus.ACTIVE): Flow<MesocycleEntity?>

    @Query("SELECT * FROM mesocycles WHERE status = :status ORDER BY createdAt DESC LIMIT 1")
    suspend fun current(status: MesoStatus = MesoStatus.ACTIVE): MesocycleEntity?

    @Query("SELECT * FROM mesocycles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MesocycleEntity>>

    @Query("SELECT * FROM mesocycles WHERE id = :id")
    suspend fun byId(id: Long): MesocycleEntity?

    @Insert suspend fun insert(m: MesocycleEntity): Long
    @Update suspend fun update(m: MesocycleEntity)

    @Query("UPDATE mesocycles SET status = :status WHERE status = :from")
    suspend fun retireAll(from: MesoStatus = MesoStatus.ACTIVE, status: MesoStatus = MesoStatus.ABANDONED)

    @Insert suspend fun insertDay(d: PlannedDayEntity): Long
    @Insert suspend fun insertPlanned(p: PlannedExerciseEntity): Long
    @Insert suspend fun insertPlannedAll(p: List<PlannedExerciseEntity>)
    @Update suspend fun updatePlanned(p: PlannedExerciseEntity)
    @Delete suspend fun deletePlanned(p: PlannedExerciseEntity)

    @Transaction
    @Query("SELECT * FROM planned_days WHERE mesocycleId = :mesoId ORDER BY dayIndex")
    fun observeDays(mesoId: Long): Flow<List<PlannedDayWithExercises>>

    @Transaction
    @Query("SELECT * FROM planned_days WHERE mesocycleId = :mesoId ORDER BY dayIndex")
    suspend fun days(mesoId: Long): List<PlannedDayWithExercises>

    @Transaction
    @Query("SELECT * FROM planned_days WHERE id = :id")
    suspend fun day(id: Long): PlannedDayWithExercises?
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(status: SessionStatus = SessionStatus.IN_PROGRESS): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY startedAt DESC LIMIT 1")
    suspend fun active(status: SessionStatus = SessionStatus.IN_PROGRESS): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions WHERE mesocycleId = :mesoId AND weekIndex = :week AND plannedDayId = :dayId AND status = 'COMPLETED'")
    suspend fun completedCount(mesoId: Long, week: Int, dayId: Long): Int

    @Query("SELECT * FROM sessions WHERE mesocycleId = :mesoId AND status = 'COMPLETED' ORDER BY startedAt")
    suspend fun forMeso(mesoId: Long): List<SessionEntity>

    /** Re-importing the same export must not duplicate history. */
    @Query("SELECT COUNT(*) FROM sessions WHERE startedAt = :startedAt AND label = :label")
    suspend fun existsAt(startedAt: Long, label: String): Int

    @Query("SELECT COUNT(*) FROM sessions WHERE status = 'COMPLETED' AND startedAt >= :since")
    fun observeCompletedSince(since: Long): Flow<Int>

    @Insert suspend fun insert(s: SessionEntity): Long
    @Update suspend fun update(s: SessionEntity)
    @Delete suspend fun delete(s: SessionEntity)

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeWithSets(id: Long): Flow<SessionWithSets?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun withSets(id: Long): SessionWithSets?
}

@Dao
interface SessionExerciseDao {

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY orderIndex, id")
    fun observeForSession(sessionId: Long): Flow<List<SessionExerciseEntity>>

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY orderIndex, id")
    suspend fun forSession(sessionId: Long): List<SessionExerciseEntity>

    @Query("SELECT COUNT(*) FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun countFor(sessionId: Long): Int

    @Insert suspend fun insert(e: SessionExerciseEntity): Long
    @Insert suspend fun insertAll(e: List<SessionExerciseEntity>)
    @Update suspend fun update(e: SessionExerciseEntity)
    @Delete suspend fun delete(e: SessionExerciseEntity)
}

@Dao
interface SetLogDao {
    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY setIndex")
    fun observeForSession(sessionId: Long): Flow<List<SetLogEntity>>

    @Insert suspend fun insert(s: SetLogEntity): Long
    @Insert suspend fun insertAll(s: List<SetLogEntity>)
    @Update suspend fun update(s: SetLogEntity)
    @Delete suspend fun delete(s: SetLogEntity)

    /**
     * Working sets from the most recent session that trained [exerciseId].
     * This is the sole input to the next session's load suggestion.
     */
    @Query(
        """
        SELECT * FROM set_logs
        WHERE exerciseId = :exerciseId AND sessionId != :excludeSessionId AND type = 'WORKING'
          AND sessionId = (
            SELECT sessionId FROM set_logs
            WHERE exerciseId = :exerciseId AND sessionId != :excludeSessionId AND type = 'WORKING'
            ORDER BY completedAt DESC LIMIT 1
          )
        ORDER BY setIndex
        """
    )
    suspend fun lastPerformance(exerciseId: String, excludeSessionId: Long = -1L): List<SetLogEntity>

    @Query("SELECT MAX(e1rmKg) FROM set_logs WHERE exerciseId = :exerciseId AND type = 'WORKING'")
    suspend fun bestE1rm(exerciseId: String): Double?

    @Query("SELECT MAX(weightKg) FROM set_logs WHERE exerciseId = :exerciseId AND reps >= :reps AND type = 'WORKING'")
    suspend fun bestWeightForReps(exerciseId: String, reps: Int): Double?

    @Query(
        """
        SELECT completedAt AS at, MAX(e1rmKg) AS value FROM set_logs
        WHERE exerciseId = :exerciseId AND type = 'WORKING' AND e1rmKg > 0
        GROUP BY sessionId ORDER BY at
        """
    )
    fun observeE1rmSeries(exerciseId: String): Flow<List<TimePoint>>

    @Query("SELECT DISTINCT exerciseId FROM set_logs ORDER BY completedAt DESC LIMIT :limit")
    fun observeRecentExerciseIds(limit: Int = 40): Flow<List<String>>

    /** Hard working sets per muscle since [since] - the number that is compared to landmarks. */
    @Query(
        """
        SELECT e.primaryMuscle AS muscle, COUNT(*) AS sets, COALESCE(SUM(s.weightKg * s.reps), 0) AS volume
        FROM set_logs s JOIN exercises e ON e.id = s.exerciseId
        WHERE s.type = 'WORKING' AND s.completedAt >= :since
        GROUP BY e.primaryMuscle
        """
    )
    fun observeMuscleVolumeSince(since: Long): Flow<List<MuscleVolume>>

    @Query(
        """
        SELECT COALESCE(SUM(weightKg * reps), 0) FROM set_logs
        WHERE type = 'WORKING' AND completedAt >= :since
        """
    )
    fun observeTonnageSince(since: Long): Flow<Double>

    @Query("DELETE FROM set_logs WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: Long)

    @Query("SELECT COUNT(*) FROM set_logs")
    suspend fun count(): Int

    @Query("DELETE FROM set_logs WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun clearExerciseInSession(sessionId: Long, exerciseId: String)

    /** All-time bests per exercise, so records never require scrolling a history feed. */
    @Query(
        """
        SELECT exerciseId AS exerciseId,
               MAX(e1rmKg) AS bestE1rm,
               MAX(weightKg) AS bestWeight,
               MAX(completedAt) AS lastTrained,
               COUNT(*) AS totalSets,
               COALESCE(SUM(weightKg * reps), 0) AS totalVolume
        FROM set_logs WHERE type = 'WORKING' AND reps > 0
        GROUP BY exerciseId
        """
    )
    fun observeRecords(): Flow<List<ExerciseRecord>>

    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId AND type = 'WORKING' ORDER BY e1rmKg DESC LIMIT 1")
    fun observeTopSet(exerciseId: String): Flow<SetLogEntity?>

    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId AND type = 'WORKING' ORDER BY completedAt DESC LIMIT :limit")
    fun observeSetsFor(exerciseId: String, limit: Int = 300): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs WHERE type = 'WORKING' AND completedAt >= :since ORDER BY completedAt")
    fun observeWorkingSetsSince(since: Long): Flow<List<SetLogEntity>>
}

@Dao
interface FeedbackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(f: MuscleFeedbackEntity)

    @Query("SELECT * FROM muscle_feedback WHERE sessionId = :sessionId")
    suspend fun forSession(sessionId: Long): List<MuscleFeedbackEntity>

    @Query(
        """
        SELECT f.* FROM muscle_feedback f
        JOIN sessions s ON s.id = f.sessionId
        WHERE f.muscle = :muscle AND s.mesocycleId = :mesoId AND s.weekIndex = :week
        """
    )
    suspend fun forMuscleInWeek(muscle: Muscle, mesoId: Long, week: Int): List<MuscleFeedbackEntity>
}

@Dao
interface LandmarkDao {
    @Query("SELECT * FROM personal_landmarks")
    suspend fun all(): List<PersonalLandmarkEntity>

    @Query("SELECT * FROM personal_landmarks WHERE muscle = :muscle")
    suspend fun byMuscle(muscle: Muscle): PersonalLandmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(l: PersonalLandmarkEntity)
}

@Dao
interface BodyweightDao {
    @Query("SELECT * FROM bodyweight ORDER BY epochDay")
    fun observeAll(): Flow<List<BodyweightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(b: BodyweightEntity)

    @Query("SELECT * FROM bodyweight ORDER BY epochDay DESC LIMIT 1")
    suspend fun latest(): BodyweightEntity?
}
