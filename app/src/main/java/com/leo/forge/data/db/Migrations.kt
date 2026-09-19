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
