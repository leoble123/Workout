package com.leo.forge.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: gyms, and a finer-grained equipment vocabulary.
 *
 * The CREATE statements are taken verbatim from Room's own exported schema rather than
 * written by hand, because a migration that merely looks right leaves the database one
 * column-order mismatch away from refusing to open at launch.
 *
 * The equipment rewrites run most-specific first, so plate-loaded machines are not first
 * collapsed into pin stacks. Nothing here touches a training row: an imported Hevy history
 * comes through intact.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `gyms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `units` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `notes` TEXT, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_gyms_isActive` ON `gyms` (`isActive`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `gym_equipment` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gymId` INTEGER NOT NULL, `equipment` TEXT NOT NULL, `available` INTEGER NOT NULL, `detail` TEXT, FOREIGN KEY(`gymId`) REFERENCES `gyms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_gym_equipment_gymId` ON `gym_equipment` (`gymId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_gym_equipment_gymId_equipment` ON `gym_equipment` (`gymId`, `equipment`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `gym_stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gymId` INTEGER NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, `notes` TEXT, `orderIndex` INTEGER NOT NULL, FOREIGN KEY(`gymId`) REFERENCES `gyms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_gym_stations_gymId` ON `gym_stations` (`gymId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `station_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stationId` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, FOREIGN KEY(`stationId`) REFERENCES `gym_stations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_station_exercises_stationId` ON `station_exercises` (`stationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_station_exercises_exerciseId` ON `station_exercises` (`exerciseId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_station_exercises_stationId_exerciseId` ON `station_exercises` (`stationId`, `exerciseId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_availability` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gymId` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, `available` INTEGER NOT NULL, `notes` TEXT, FOREIGN KEY(`gymId`) REFERENCES `gyms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_availability_gymId` ON `exercise_availability` (`gymId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_availability_exerciseId` ON `exercise_availability` (`exerciseId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_availability_gymId_exerciseId` ON `exercise_availability` (`gymId`, `exerciseId`)")

        // Exercises whose equipment became more specific in v2.
        db.execSQL("UPDATE exercises SET equipment = 'MACHINE_PLATE_LOADED' WHERE id IN ('hack_squat', 'pendulum_squat', 'leg_press', 'machine_hip_thrust', 'standing_calf_raise', 'leg_press_calf_raise', 'reverse_hyperextension')")
        db.execSQL("UPDATE exercises SET equipment = 'EZ_BAR' WHERE id IN ('skull_crusher', 'ez_bar_curl', 'preacher_curl', 'reverse_curl')")
        db.execSQL("UPDATE exercises SET equipment = 'LANDMINE' WHERE id IN ('t_bar_row', 'meadows_row')")
        db.execSQL("UPDATE exercises SET equipment = 'PULL_UP_BAR' WHERE id IN ('pull_up', 'chin_up', 'hanging_leg_raise', 'hanging_knee_raise')")
        db.execSQL("UPDATE exercises SET equipment = 'DIP_STATION' WHERE id IN ('weighted_dip', 'triceps_dip')")
        // Anything still marked MACHINE was a pin stack.
        db.execSQL("UPDATE exercises SET equipment = 'MACHINE_SELECTORIZED' WHERE equipment = 'MACHINE'")
    }
}


/**
 * v2 -> v3: session exercises, extra equipment requirements, and a per-gym barbell step.
 *
 * session_exercises is the important one. A session used to be a read-only view of a planned
 * day, which meant a workout could not exist without a generated mesocycle - you could not
 * simply open the app and lift. Both planned and freestyle sessions now materialise into
 * these rows, so adding, swapping and dropping exercises works identically in either.
 *
 * requiresAlso is added with a SQL default so the NOT NULL column can be back-filled;
 * Room does not enforce a default the entity does not declare, so this stays compatible.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `session_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `targetSets` INTEGER NOT NULL, `repLow` INTEGER NOT NULL, `repHigh` INTEGER NOT NULL, `restSeconds` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId` ON `session_exercises` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_exerciseId` ON `session_exercises` (`exerciseId`)")

        db.execSQL("ALTER TABLE exercises ADD COLUMN requiresAlso TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE gyms ADD COLUMN barbellIncrement REAL")

        // A barbell with nothing to unrack from is not a back squat.
        db.execSQL("UPDATE exercises SET requiresAlso = 'BENCH' WHERE id IN ('bulgarian_split_squat', 'chest_supported_dumbbell_shrug', 'chest_supported_row', 'concentration_curl', 'decline_sit_up', 'dumbbell_bench_press', 'dumbbell_fly', 'dumbbell_hip_thrust', 'dumbbell_pullover', 'dumbbell_row', 'hip_thrust', 'incline_dumbbell_curl', 'incline_dumbbell_fly', 'incline_dumbbell_press', 'preacher_curl', 'seated_dumbbell_shoulder_press', 'skull_crusher', 'smith_incline_press', 'smith_machine_bench_press', 'spider_curl', 'step_up')")
        db.execSQL("UPDATE exercises SET requiresAlso = 'RACK' WHERE id IN ('back_squat', 'front_squat', 'good_morning')")
        db.execSQL("UPDATE exercises SET requiresAlso = 'RACK,BENCH' WHERE id IN ('barbell_bench_press', 'close_grip_bench_press', 'decline_barbell_bench_press', 'incline_barbell_bench_press', 'jm_press')")
    }
}


/**
 * v3 -> v4: a note against an exercise in a session.
 *
 * Nullable with no default, which matches the entity exactly, so the column can simply be
 * appended without rebuilding the table.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE session_exercises ADD COLUMN notes TEXT")
    }
}
