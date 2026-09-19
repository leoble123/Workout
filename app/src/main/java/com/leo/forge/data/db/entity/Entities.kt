package com.leo.forge.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.leo.forge.domain.model.*

@Entity(
    tableName = "exercises",
    indices = [Index("primaryMuscle"), Index(value = ["name"], unique = true), Index("archived")]
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: Muscle,
    val secondaryMuscles: List<Muscle> = emptyList(),
    val equipment: Equipment,
    val pattern: MovementPattern,
    val isUnilateral: Boolean = false,
    val repLow: Int = 8,
    val repHigh: Int = 12,
    /** Smallest load jump that is actually achievable for this implement. */
    val loadIncrementKg: Double = 2.5,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val archived: Boolean = false,
    val notes: String? = null,
)

@Entity(tableName = "mesocycles", indices = [Index("status")])
data class MesocycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val split: SplitType,
    val daysPerWeek: Int,
    val totalWeeks: Int,
    val startedAtEpochDay: Long,
    val currentWeek: Int = 0,
    val status: MesoStatus = MesoStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "planned_days",
    foreignKeys = [ForeignKey(
        entity = MesocycleEntity::class, parentColumns = ["id"],
        childColumns = ["mesocycleId"], onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("mesocycleId")]
)
data class PlannedDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mesocycleId: Long,
    val dayIndex: Int,
    val label: String,
)

@Entity(
    tableName = "planned_exercises",
    foreignKeys = [
        ForeignKey(entity = PlannedDayEntity::class, parentColumns = ["id"], childColumns = ["plannedDayId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("plannedDayId"), Index("exerciseId")]
)
data class PlannedExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plannedDayId: Long,
    val exerciseId: String,
    val orderIndex: Int,
    val repLow: Int,
    val repHigh: Int,
    /** Sets prescribed in week 1; later weeks add on top via the volume ramp. */
    val baseSets: Int,
    val restSeconds: Int = 150,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(
        entity = MesocycleEntity::class, parentColumns = ["id"],
        childColumns = ["mesocycleId"], onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("mesocycleId"), Index("startedAt"), Index("status")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mesocycleId: Long? = null,
    val plannedDayId: Long? = null,
    val weekIndex: Int = 0,
    val label: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val notes: String? = null,
    /** Denormalised so history lists never have to sum thousands of set rows. */
    val totalVolumeKg: Double = 0.0,
    val totalSets: Int = 0,
)

@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("sessionId"), Index("exerciseId"),
        Index(value = ["exerciseId", "completedAt"]),
        Index(value = ["exerciseId", "e1rmKg"]),
    ]
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val plannedExerciseId: Long? = null,
    val setIndex: Int,
    val type: SetType = SetType.WORKING,
    val weightKg: Double,
    val reps: Int,
    val rir: Int? = null,
    val completedAt: Long,
    val restSecondsBefore: Int? = null,
    /** Stored, not computed on read, so e1RM charts and PR lookups stay index-backed. */
    val e1rmKg: Double = 0.0,
    /** What the engine suggested, kept so its accuracy can be scored later. */
    val targetWeightKg: Double? = null,
    val targetReps: Int? = null,
    val isPr: Boolean = false,
    val notes: String? = null,
)

@Entity(
    tableName = "muscle_feedback",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class, parentColumns = ["id"],
        childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index(value = ["sessionId", "muscle"], unique = true)]
)
data class MuscleFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val muscle: Muscle,
    val pump: Pump? = null,
    val soreness: Soreness? = null,
    val workload: Workload? = null,
    val recordedAt: Long = System.currentTimeMillis(),
)

/** Landmarks learned for this user, overriding the population defaults. */
@Entity(tableName = "personal_landmarks")
data class PersonalLandmarkEntity(
    @PrimaryKey val muscle: Muscle,
    val mv: Int,
    val mev: Int,
    val mav: Int,
    val mrv: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "bodyweight")
data class BodyweightEntity(
    @PrimaryKey val epochDay: Long,
    val kg: Double,
)
