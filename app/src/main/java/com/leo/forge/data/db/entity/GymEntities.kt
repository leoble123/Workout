package com.leo.forge.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.Units

/**
 * A place you train.
 *
 * [units] is what this gym's plates and stacks are actually *marked in*, which is not a
 * display preference: it decides the size of every load increment the app suggests. Travel
 * to a kg gym and the suggestions must land on 2.5 kg steps, not on converted pounds.
 */
@Entity(tableName = "gyms", indices = [Index("isActive")])
data class GymEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val units: Units = Units.KG,
    val isActive: Boolean = false,
    val notes: String? = null,
    /**
     * Smallest barbell jump actually achievable here, in this gym's [units] - two of the
     * smallest plate it stocks. Null falls back to the standard for the implement. A gym
     * whose smallest plate is 2.5 lb cannot make the 2.5 kg jump the default assumes.
     */
    val barbellIncrement: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Whether a whole class of equipment exists here, plus whatever you want to write down
 * about it ("only 2 racks", "dumbbells stop at 40kg", "plates are 20/15/10/5/2.5").
 */
@Entity(
    tableName = "gym_equipment",
    foreignKeys = [ForeignKey(
        entity = GymEntity::class, parentColumns = ["id"],
        childColumns = ["gymId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("gymId"), Index(value = ["gymId", "equipment"], unique = true)],
)
data class GymEquipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gymId: Long,
    val equipment: Equipment,
    val available: Boolean = true,
    val detail: String? = null,
)

/**
 * A specific machine on the floor, named however you like ("Genesis dual-arm cable").
 *
 * A station exists because equipment categories are too blunt for a sparse gym: owning one
 * combo machine that presses and flyes is not the same as owning "machines", and a program
 * built on the latter assumption sends you to a leg extension that isn't there.
 */
@Entity(
    tableName = "gym_stations",
    foreignKeys = [ForeignKey(
        entity = GymEntity::class, parentColumns = ["id"],
        childColumns = ["gymId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("gymId")],
)
data class GymStationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gymId: Long,
    val name: String,
    val brand: String? = null,
    val notes: String? = null,
    val orderIndex: Int = 0,
)

/** Which exercises a station makes possible. */
@Entity(
    tableName = "station_exercises",
    foreignKeys = [
        ForeignKey(entity = GymStationEntity::class, parentColumns = ["id"], childColumns = ["stationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("stationId"), Index("exerciseId"), Index(value = ["stationId", "exerciseId"], unique = true)],
)
data class StationExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationId: Long,
    val exerciseId: String,
)

/**
 * A deliberate yes/no for one exercise at one gym, overriding whatever the equipment and
 * stations imply - plus room for the detail that only matters in this room ("the leg press
 * here is loaded with 20s and starts at 60").
 */
@Entity(
    tableName = "exercise_availability",
    foreignKeys = [
        ForeignKey(entity = GymEntity::class, parentColumns = ["id"], childColumns = ["gymId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("gymId"), Index("exerciseId"), Index(value = ["gymId", "exerciseId"], unique = true)],
)
data class ExerciseAvailabilityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gymId: Long,
    val exerciseId: String,
    val available: Boolean,
    val notes: String? = null,
)
