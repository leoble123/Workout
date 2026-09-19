package com.leo.forge.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.leo.forge.data.db.dao.*
import com.leo.forge.data.db.entity.*

@Database(
    entities = [
        ExerciseEntity::class,
        MesocycleEntity::class,
        PlannedDayEntity::class,
        PlannedExerciseEntity::class,
        SessionEntity::class,
        SetLogEntity::class,
        MuscleFeedbackEntity::class,
        PersonalLandmarkEntity::class,
        BodyweightEntity::class,
        GymEntity::class,
        GymEquipmentEntity::class,
        GymStationEntity::class,
        StationExerciseEntity::class,
        ExerciseAvailabilityEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ForgeDatabase : RoomDatabase() {
    abstract fun exercises(): ExerciseDao
    abstract fun mesocycles(): MesocycleDao
    abstract fun sessions(): SessionDao
    abstract fun setLogs(): SetLogDao
    abstract fun feedback(): FeedbackDao
    abstract fun landmarks(): LandmarkDao
    abstract fun bodyweight(): BodyweightDao
    abstract fun gyms(): GymDao

    companion object {
        fun build(context: Context): ForgeDatabase =
            Room.databaseBuilder(context, ForgeDatabase::class.java, "forge.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // No destructive fallback: losing a training history to a schema bump is
                // not an acceptable failure mode, so a missing migration must fail loudly.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
