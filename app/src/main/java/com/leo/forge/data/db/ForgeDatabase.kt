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
    ],
    version = 1,
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

    companion object {
        fun build(context: Context): ForgeDatabase =
            Room.databaseBuilder(context, ForgeDatabase::class.java, "forge.db")
                // Foreign keys are declared on the entities; without this SQLite ignores them.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
