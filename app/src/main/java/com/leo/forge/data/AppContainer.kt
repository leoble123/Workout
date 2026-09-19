package com.leo.forge.data

import android.content.Context
import com.leo.forge.data.db.ForgeDatabase
import com.leo.forge.data.importer.HevyCsvImporter
import com.leo.forge.data.prefs.SettingsStore
import com.leo.forge.data.repo.ExerciseRepository
import com.leo.forge.data.repo.ProgramRepository
import com.leo.forge.data.repo.StatsRepository
import com.leo.forge.data.repo.WorkoutRepository
import com.leo.forge.timer.RestTimer

/**
 * Hand-rolled dependency graph.
 *
 * A single-user app does not need a DI framework, and skipping one keeps the build to a
 * single annotation processor (Room). Everything is lazy, so launching does not construct
 * the database before the first screen needs it.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val db: ForgeDatabase by lazy { ForgeDatabase.build(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val restTimer: RestTimer by lazy { RestTimer(appContext) }

    val exercises: ExerciseRepository by lazy { ExerciseRepository(db) }
    val program: ProgramRepository by lazy { ProgramRepository(db) }
    val workouts: WorkoutRepository by lazy { WorkoutRepository(db) }
    val stats: StatsRepository by lazy { StatsRepository(db) }
    val importer: HevyCsvImporter by lazy { HevyCsvImporter(db) }
}
