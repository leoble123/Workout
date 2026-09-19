package com.leo.forge.domain.model

import androidx.compose.runtime.Immutable

/**
 * Muscles are the unit of account for hypertrophy programming: volume is planned,
 * progressed and auto-regulated per muscle, not per exercise.
 */
enum class Muscle(val display: String, val group: MuscleGroup) {
    CHEST("Chest", MuscleGroup.PUSH),
    FRONT_DELTS("Front delts", MuscleGroup.PUSH),
    SIDE_DELTS("Side delts", MuscleGroup.PUSH),
    REAR_DELTS("Rear delts", MuscleGroup.PULL),
    TRICEPS("Triceps", MuscleGroup.PUSH),
    LATS("Lats", MuscleGroup.PULL),
    UPPER_BACK("Upper back", MuscleGroup.PULL),
    TRAPS("Traps", MuscleGroup.PULL),
    BICEPS("Biceps", MuscleGroup.PULL),
    FOREARMS("Forearms", MuscleGroup.PULL),
    QUADS("Quads", MuscleGroup.LEGS),
    HAMSTRINGS("Hamstrings", MuscleGroup.LEGS),
    GLUTES("Glutes", MuscleGroup.LEGS),
    CALVES("Calves", MuscleGroup.LEGS),
    ADDUCTORS("Adductors", MuscleGroup.LEGS),
    ABS("Abs", MuscleGroup.CORE),
    LOWER_BACK("Lower back", MuscleGroup.CORE);
}

enum class MuscleGroup { PUSH, PULL, LEGS, CORE }

enum class EquipmentCategory(val display: String) {
    FREE_WEIGHT("Free weights"),
    MACHINE("Machines & cables"),
    BODYWEIGHT("Bodyweight"),
    OTHER("Other"),
}

/**
 * Deliberately finer-grained than "machine".
 *
 * A pin stack and a plate-loaded machine are not the same tool: one steps in 5 kg and the
 * other takes whatever plates you put on it. And in a minimal gym the difference between
 * owning a pull-up bar and owning a lat pulldown decides half the programme.
 */
enum class Equipment(val display: String, val category: EquipmentCategory) {
    BARBELL("Barbell", EquipmentCategory.FREE_WEIGHT),
    EZ_BAR("EZ / curl bar", EquipmentCategory.FREE_WEIGHT),
    TRAP_BAR("Trap bar", EquipmentCategory.FREE_WEIGHT),
    DUMBBELL("Dumbbells", EquipmentCategory.FREE_WEIGHT),
    KETTLEBELL("Kettlebells", EquipmentCategory.FREE_WEIGHT),
    MACHINE_SELECTORIZED("Machine (pin stack)", EquipmentCategory.MACHINE),
    MACHINE_PLATE_LOADED("Machine (plate loaded)", EquipmentCategory.MACHINE),
    CABLE("Cable stack", EquipmentCategory.MACHINE),
    SMITH("Smith machine", EquipmentCategory.MACHINE),
    BODYWEIGHT("Bodyweight", EquipmentCategory.BODYWEIGHT),
    PULL_UP_BAR("Pull-up bar", EquipmentCategory.BODYWEIGHT),
    DIP_STATION("Dip bars", EquipmentCategory.BODYWEIGHT),
    BANDS("Bands", EquipmentCategory.OTHER),
    LANDMINE("Landmine", EquipmentCategory.OTHER),
    OTHER("Other", EquipmentCategory.OTHER);
}

/**
 * Pattern is used by the generator to avoid stacking three near-identical movements
 * (e.g. three horizontal presses) into one session.
 */
enum class MovementPattern {
    HORIZONTAL_PUSH, VERTICAL_PUSH, HORIZONTAL_PULL, VERTICAL_PULL,
    SQUAT, HINGE, LUNGE, ISOLATION, CARRY, CORE
}

enum class SetType { WARMUP, WORKING, MYOREP, DROP, BACKOFF }

enum class SplitType(val display: String, val daysPerWeek: IntRange) {
    FULL_BODY("Full body", 2..4),
    UPPER_LOWER("Upper / Lower", 4..4),
    PUSH_PULL_LEGS("Push / Pull / Legs", 3..6),
    ARNOLD("Chest+Back / Shoulders+Arms / Legs", 3..6);
}

enum class SessionStatus { PLANNED, IN_PROGRESS, COMPLETED, SKIPPED }

enum class MesoStatus { ACTIVE, COMPLETED, ABANDONED }

/** Per-muscle, per-session feedback that drives week-over-week set progression. */
enum class Pump(val display: String, val score: Int) {
    NONE("No pump", 0), MODERATE("Moderate", 1), AMAZING("Amazing", 2)
}

enum class Soreness(val display: String, val score: Int) {
    NEVER_GOT("Never got sore", 0),
    HEALED_A_WHILE_AGO("Healed a while ago", 1),
    HEALED_JUST_ON_TIME("Healed just on time", 2),
    STILL_SORE("Still sore", 3)
}

enum class Workload(val display: String, val score: Int) {
    EASY("Easy", 0), PRETTY_GOOD("Pretty good", 1), PUSHED_LIMITS("Pushed my limits", 2), TOO_MUCH("Too much", 3)
}

@Immutable
data class Weight(val kg: Double) {
    val lb: Double get() = kg * 2.2046226218
}

enum class Units(val display: String) { KG("kg"), LB("lb") }
