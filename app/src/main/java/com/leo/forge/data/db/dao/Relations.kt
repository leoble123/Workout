package com.leo.forge.data.db.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.leo.forge.data.db.entity.*
import com.leo.forge.domain.model.Muscle

data class PlannedDayWithExercises(
    @Embedded val day: PlannedDayEntity,
    @Relation(parentColumn = "id", entityColumn = "plannedDayId")
    val exercises: List<PlannedExerciseEntity>,
)

data class SessionWithSets(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<SetLogEntity>,
)

data class TimePoint(val at: Long, val value: Double)

data class MuscleVolume(val muscle: Muscle, val sets: Int, val volume: Double)

/** All-time bests for one exercise. */
data class ExerciseRecord(
    val exerciseId: String,
    val bestE1rm: Double,
    val bestWeight: Double,
    val lastTrained: Long,
    val totalSets: Int,
    val totalVolume: Double,
)
