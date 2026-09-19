package com.leo.forge.data.repo

import androidx.compose.runtime.Immutable
import com.leo.forge.data.db.ForgeDatabase
import com.leo.forge.data.db.dao.MuscleVolume
import com.leo.forge.data.db.dao.PlannedDayWithExercises
import com.leo.forge.data.db.dao.TimePoint
import com.leo.forge.data.db.entity.*
import com.leo.forge.data.seed.ExerciseSeed
import com.leo.forge.domain.mesocycle.MesoSpec
import com.leo.forge.domain.mesocycle.MesocycleGenerator
import com.leo.forge.domain.model.*
import com.leo.forge.domain.progression.ExercisePrescription
import com.leo.forge.domain.progression.ProgressionEngine
import com.leo.forge.domain.progression.VolumeAutoregulator
import com.leo.forge.domain.volume.Landmarks
import com.leo.forge.domain.volume.VolumeLandmarks
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlin.math.ceil

@Immutable
data class ExercisePlanUi(
    val planned: PlannedExerciseEntity,
    val exercise: ExerciseEntity,
    val prescription: ExercisePrescription,
)

class ExerciseRepository(private val db: ForgeDatabase) {

    fun observeAll(): Flow<List<ExerciseEntity>> = db.exercises().observeAll()
    fun search(q: String): Flow<List<ExerciseEntity>> = db.exercises().search(q)
    suspend fun byId(id: String) = db.exercises().byId(id)
    suspend fun update(e: ExerciseEntity) = db.exercises().update(e)

    /**
     * Additive and idempotent. Runs on every launch rather than only on an empty table, so
     * exercises added to the library in a later version reach existing installs; the IGNORE
     * conflict strategy means anything you have edited stays yours.
     */
    suspend fun seedIfNeeded() {
        db.exercises().insertAll(ExerciseSeed.all)
    }
}

class ProgramRepository(private val db: ForgeDatabase, private val gyms: GymRepository) {

    /** Report from the most recent generation, so the UI can surface muscles it could not fill. */
    var lastGeneration: com.leo.forge.domain.mesocycle.GeneratedMeso? = null
        private set

    fun observeCurrent(): Flow<MesocycleEntity?> = db.mesocycles().observeCurrent()
    fun observeDays(mesoId: Long): Flow<List<PlannedDayWithExercises>> = db.mesocycles().observeDays(mesoId)
    suspend fun current(): MesocycleEntity? = db.mesocycles().current()
    suspend fun mesocycle(id: Long): MesocycleEntity? = db.mesocycles().byId(id)
    suspend fun days(mesoId: Long) = db.mesocycles().days(mesoId)

    suspend fun personalLandmarks(): Map<Muscle, Landmarks> =
        db.landmarks().all().associate { it.muscle to Landmarks(it.mv, it.mev, it.mav, it.mrv) }

    /** Generates a whole block and replaces whatever was running. */
    suspend fun createMesocycle(spec: MesoSpec): Long {
        val library = db.exercises().allOnce()
        // Only ever build from what the active gym can actually do.
        val resolved = spec.copy(availableExerciseIds = spec.availableExerciseIds ?: gyms.availableExerciseIds())
        val generated = MesocycleGenerator.generate(resolved, library, personalLandmarks())

        db.mesocycles().retireAll()
        val mesoId = db.mesocycles().insert(
            MesocycleEntity(
                name = spec.name,
                split = spec.split,
                daysPerWeek = spec.daysPerWeek,
                totalWeeks = spec.totalWeeks,
                startedAtEpochDay = LocalDate.now().toEpochDay(),
            )
        )
        lastGeneration = generated
        generated.days.forEachIndexed { dayIndex, day ->
            val dayId = db.mesocycles().insertDay(
                PlannedDayEntity(mesocycleId = mesoId, dayIndex = dayIndex, label = day.label)
            )
            db.mesocycles().insertPlannedAll(
                day.exercises.mapIndexed { i, ex ->
                    PlannedExerciseEntity(
                        plannedDayId = dayId,
                        exerciseId = ex.exerciseId,
                        orderIndex = i,
                        repLow = ex.repLow,
                        repHigh = ex.repHigh,
                        baseSets = ex.sets,
                        restSeconds = ex.restSeconds,
                    )
                }
            )
        }
        return mesoId
    }

    suspend fun addExerciseToDay(dayId: Long, exercise: ExerciseEntity, orderIndex: Int) {
        db.mesocycles().insertPlanned(
            PlannedExerciseEntity(
                plannedDayId = dayId,
                exerciseId = exercise.id,
                orderIndex = orderIndex,
                repLow = exercise.repLow,
                repHigh = exercise.repHigh,
                baseSets = 3,
                restSeconds = ProgressionEngine.restSecondsFor(exercise),
            )
        )
    }

    suspend fun removePlanned(p: PlannedExerciseEntity) = db.mesocycles().deletePlanned(p)

    suspend fun updateMeso(m: MesocycleEntity) = db.mesocycles().update(m)
}

class WorkoutRepository(private val db: ForgeDatabase, private val gyms: GymRepository) {

    fun observeActive(): Flow<SessionEntity?> = db.sessions().observeActive()
    fun observeRecent(limit: Int = 100): Flow<List<SessionEntity>> = db.sessions().observeRecent(limit)
    fun observeSets(sessionId: Long): Flow<List<SetLogEntity>> = db.setLogs().observeForSession(sessionId)
    fun observeSession(id: Long): Flow<SessionEntity?> = db.sessions().observeById(id)
    suspend fun session(id: Long) = db.sessions().byId(id)
    suspend fun active() = db.sessions().active()

    /**
     * Which planned day comes next, and in which week.
     *
     * Days are taken in order and the week advances once every day in it has a completed
     * session, rather than by calendar date - a missed Tuesday should not silently burn a
     * week of the block.
     */
    suspend fun nextUp(meso: MesocycleEntity): Pair<PlannedDayWithExercises, Int>? {
        val days = db.mesocycles().days(meso.id)
        if (days.isEmpty()) return null
        var week = meso.currentWeek.coerceIn(0, meso.totalWeeks - 1)
        repeat(meso.totalWeeks) {
            val pending = days.firstOrNull { db.sessions().completedCount(meso.id, week, it.day.id) == 0 }
            if (pending != null) return pending to week
            if (week >= meso.totalWeeks - 1) return null
            week++
        }
        return null
    }

    /** Weekly hard sets for [muscle] in [weekIndex], after feedback-driven adjustment. */
    suspend fun weeklySetsFor(muscle: Muscle, meso: MesocycleEntity, weekIndex: Int): Int {
        val personal = db.landmarks().byMuscle(muscle)?.let { Landmarks(it.mv, it.mev, it.mav, it.mrv) }
        val lm = personal ?: VolumeLandmarks.of(muscle)
        if (ProgressionEngine.isDeloadWeek(weekIndex, meso.totalWeeks)) {
            return VolumeLandmarks.plannedSets(muscle, weekIndex, meso.totalWeeks, personal)
        }
        var sets = VolumeLandmarks.plannedSets(muscle, 0, meso.totalWeeks, personal)
        for (w in 1..weekIndex) {
            val fb = db.feedback().forMuscleInWeek(muscle, meso.id, w - 1)
                .map { Triple(it.pump, it.soreness, it.workload) }
            sets += VolumeAutoregulator.weekly(fb).setChange
        }
        return sets.coerceIn(lm.mev.coerceAtLeast(2), lm.mrv)
    }

    /**
     * Turns a planned day into concrete prescriptions: how many sets each exercise gets
     * this week, and what load and reps to aim for on every one of them.
     */
    suspend fun prescribe(
        meso: MesocycleEntity,
        day: PlannedDayWithExercises,
        weekIndex: Int,
        excludeSessionId: Long = -1L,
    ): List<ExercisePlanUi> {
        val allDays = db.mesocycles().days(meso.id)
        val referenced = (allDays.flatMap { it.exercises } + day.exercises).map { it.exerciseId }.distinct()
        val library = db.exercises().byIds(referenced).associateBy { it.id }

        fun muscleOf(p: PlannedExerciseEntity): Muscle? = library[p.exerciseId]?.primaryMuscle

        // A muscle's weekly budget is shared out over every day that trains it.
        val daysTraining: Map<Muscle, Int> = Muscle.entries.associateWith { m ->
            allDays.count { d -> d.exercises.any { muscleOf(it) == m } }.coerceAtLeast(1)
        }

        val units = gyms.units()
        val todayByMuscle = day.exercises.groupBy { muscleOf(it) }
        val setsPerPlanned = mutableMapOf<Long, Int>()

        for ((muscle, planned) in todayByMuscle) {
            if (muscle == null) continue
            val weekly = weeklySetsFor(muscle, meso, weekIndex)
            val perDay = ceil(weekly.toDouble() / daysTraining.getValue(muscle)).toInt().coerceAtLeast(planned.size)
            val each = perDay / planned.size
            val extra = perDay % planned.size
            planned.sortedBy { it.orderIndex }.forEachIndexed { i, p ->
                setsPerPlanned[p.id] = (each + if (i < extra) 1 else 0).coerceIn(1, 8)
            }
        }

        return day.exercises.sortedBy { it.orderIndex }.mapNotNull { planned ->
            val exercise = library[planned.exerciseId] ?: return@mapNotNull null
            val setCount = setsPerPlanned[planned.id] ?: planned.baseSets
            val last = db.setLogs().lastPerformance(exercise.id, excludeSessionId)
            ExercisePlanUi(
                planned = planned,
                exercise = exercise,
                prescription = ProgressionEngine.prescribe(
                    exercise = exercise,
                    lastSets = last,
                    setCount = setCount,
                    weekIndex = weekIndex,
                    totalWeeks = meso.totalWeeks,
                    repLow = planned.repLow,
                    repHigh = planned.repHigh,
                    units = units,
                ),
            )
        }
    }

    suspend fun startSession(meso: MesocycleEntity?, day: PlannedDayWithExercises?, weekIndex: Int, label: String): Long {
        db.sessions().active()?.let { return it.id }
        return db.sessions().insert(
            SessionEntity(
                mesocycleId = meso?.id,
                plannedDayId = day?.day?.id,
                weekIndex = weekIndex,
                label = label,
                startedAt = System.currentTimeMillis(),
                status = SessionStatus.IN_PROGRESS,
            )
        )
    }

    /** Logs one set and returns whether it was a personal best on that exercise. */
    suspend fun logSet(
        sessionId: Long,
        exercise: ExerciseEntity,
        plannedExerciseId: Long?,
        setIndex: Int,
        weightKg: Double,
        reps: Int,
        rir: Int?,
        type: SetType = SetType.WORKING,
        target: com.leo.forge.domain.progression.SetTarget? = null,
        restSecondsBefore: Int? = null,
    ): Boolean {
        val e1rm = if (type == SetType.WORKING) OneRepMax.estimate(weightKg, reps, rir) else 0.0
        val best = db.setLogs().bestE1rm(exercise.id) ?: 0.0
        val isPr = type == SetType.WORKING && e1rm > best && e1rm > 0.0

        db.setLogs().insert(
            SetLogEntity(
                sessionId = sessionId,
                exerciseId = exercise.id,
                plannedExerciseId = plannedExerciseId,
                setIndex = setIndex,
                type = type,
                weightKg = weightKg,
                reps = reps,
                rir = rir,
                completedAt = System.currentTimeMillis(),
                restSecondsBefore = restSecondsBefore,
                e1rmKg = e1rm,
                isPr = isPr,
                targetWeightKg = target?.weightKg,
                targetReps = target?.reps,
            )
        )
        recomputeTotals(sessionId)
        return isPr
    }

    suspend fun updateSet(set: SetLogEntity) {
        val e1rm = if (set.type == SetType.WORKING) OneRepMax.estimate(set.weightKg, set.reps, set.rir) else 0.0
        db.setLogs().update(set.copy(e1rmKg = e1rm))
        recomputeTotals(set.sessionId)
    }

    suspend fun deleteSet(set: SetLogEntity) {
        db.setLogs().delete(set)
        recomputeTotals(set.sessionId)
    }

    private suspend fun recomputeTotals(sessionId: Long) {
        val withSets = db.sessions().withSets(sessionId) ?: return
        val working = withSets.sets.filter { it.type != SetType.WARMUP }
        db.sessions().update(
            withSets.session.copy(
                totalVolumeKg = working.sumOf { it.weightKg * it.reps },
                totalSets = working.size,
            )
        )
    }

    suspend fun finishSession(sessionId: Long, feedback: Map<Muscle, Triple<Pump?, Soreness?, Workload?>>) {
        val session = db.sessions().byId(sessionId) ?: return
        feedback.forEach { (muscle, f) ->
            db.feedback().upsert(
                MuscleFeedbackEntity(
                    sessionId = sessionId,
                    muscle = muscle,
                    pump = f.first,
                    soreness = f.second,
                    workload = f.third,
                )
            )
        }
        db.sessions().update(
            session.copy(status = SessionStatus.COMPLETED, finishedAt = System.currentTimeMillis())
        )
        advanceWeekIfComplete(session)
    }

    private suspend fun advanceWeekIfComplete(session: SessionEntity) {
        val mesoId = session.mesocycleId ?: return
        val meso = db.mesocycles().byId(mesoId) ?: return
        val days = db.mesocycles().days(mesoId)
        if (days.isEmpty()) return
        val allDone = days.all { db.sessions().completedCount(mesoId, session.weekIndex, it.day.id) > 0 }
        if (!allDone) return
        if (session.weekIndex >= meso.totalWeeks - 1) {
            db.mesocycles().update(meso.copy(status = MesoStatus.COMPLETED))
        } else {
            db.mesocycles().update(meso.copy(currentWeek = session.weekIndex + 1))
        }
    }

    suspend fun abandonSession(sessionId: Long) {
        val s = db.sessions().byId(sessionId) ?: return
        if (db.sessions().withSets(sessionId)?.sets.isNullOrEmpty()) db.sessions().delete(s)
        else db.sessions().update(s.copy(status = SessionStatus.SKIPPED, finishedAt = System.currentTimeMillis()))
    }
}

class StatsRepository(private val db: ForgeDatabase) {

    fun muscleVolumeSince(since: Long): Flow<List<MuscleVolume>> = db.setLogs().observeMuscleVolumeSince(since)
    fun tonnageSince(since: Long): Flow<Double> = db.setLogs().observeTonnageSince(since)
    fun e1rmSeries(exerciseId: String): Flow<List<TimePoint>> = db.setLogs().observeE1rmSeries(exerciseId)
    fun recentExerciseIds(limit: Int = 40): Flow<List<String>> = db.setLogs().observeRecentExerciseIds(limit)
    fun sessionsSince(since: Long): Flow<Int> = db.sessions().observeCompletedSince(since)
    fun bodyweight(): Flow<List<BodyweightEntity>> = db.bodyweight().observeAll()

    suspend fun logBodyweight(kg: Double) =
        db.bodyweight().upsert(BodyweightEntity(LocalDate.now().toEpochDay(), kg))

    suspend fun landmarksFor(muscle: Muscle): Landmarks =
        db.landmarks().byMuscle(muscle)?.let { Landmarks(it.mv, it.mev, it.mav, it.mrv) }
            ?: VolumeLandmarks.of(muscle)
}
