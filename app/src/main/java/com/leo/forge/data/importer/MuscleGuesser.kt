package com.leo.forge.data.importer

import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.MovementPattern
import com.leo.forge.domain.model.Muscle

/**
 * Best-effort classification of an exercise name that is not in the library.
 *
 * [Guess.confident] is false when the name only matched a weak cue or nothing at all,
 * and the importer surfaces those for a one-tap correction rather than pretending.
 */
object MuscleGuesser {

    data class Guess(val muscle: Muscle, val pattern: MovementPattern, val confident: Boolean)

    /**
     * Ordered most-specific first, and that order is load-bearing: "leg curl" has to be
     * tested before "curl", or every hamstring curl in an imported history files itself
     * as a biceps exercise. Same trap for "glute kickback" vs "kickback" and
     * "upright row" vs "row".
     */
    private val rules: List<Triple<List<String>, Muscle, MovementPattern>> = listOf(
        // -- specific phrases that contain a more general keyword
        Triple(listOf("reverse nordic", "sissy squat"), Muscle.QUADS, MovementPattern.ISOLATION),
        Triple(listOf("pike push"), Muscle.FRONT_DELTS, MovementPattern.VERTICAL_PUSH),
        Triple(listOf("leg curl", "hamstring curl", "nordic"), Muscle.HAMSTRINGS, MovementPattern.ISOLATION),
        Triple(listOf("wrist curl", "reverse wrist"), Muscle.FOREARMS, MovementPattern.ISOLATION),
        Triple(listOf("reverse curl"), Muscle.FOREARMS, MovementPattern.ISOLATION),
        Triple(listOf("glute kickback", "cable kickback glute"), Muscle.GLUTES, MovementPattern.ISOLATION),
        Triple(listOf("upright row"), Muscle.SIDE_DELTS, MovementPattern.ISOLATION),
        Triple(listOf("reverse pec deck", "rear delt", "reverse fly", "reverse flye", "face pull"), Muscle.REAR_DELTS, MovementPattern.ISOLATION),
        Triple(listOf("front raise"), Muscle.FRONT_DELTS, MovementPattern.ISOLATION),
        Triple(listOf("lateral raise", "side raise", "lat raise"), Muscle.SIDE_DELTS, MovementPattern.ISOLATION),
        Triple(listOf("calf", "calve"), Muscle.CALVES, MovementPattern.ISOLATION),
        Triple(listOf("leg extension"), Muscle.QUADS, MovementPattern.ISOLATION),
        Triple(listOf("back extension", "hyperextension", "reverse hyper"), Muscle.LOWER_BACK, MovementPattern.HINGE),
        Triple(listOf("leg raise", "knee raise"), Muscle.ABS, MovementPattern.CORE),
        Triple(listOf("pull-through", "pull through"), Muscle.GLUTES, MovementPattern.HINGE),
        Triple(listOf("close-grip bench", "close grip bench", "jm press"), Muscle.TRICEPS, MovementPattern.HORIZONTAL_PUSH),

        // -- general keywords
        Triple(listOf("shrug"), Muscle.TRAPS, MovementPattern.ISOLATION),
        Triple(listOf("curl"), Muscle.BICEPS, MovementPattern.ISOLATION),
        Triple(listOf("pushdown", "tricep", "skull", "overhead extension", "kickback"), Muscle.TRICEPS, MovementPattern.ISOLATION),
        Triple(listOf("quad"), Muscle.QUADS, MovementPattern.ISOLATION),
        Triple(listOf("hamstring"), Muscle.HAMSTRINGS, MovementPattern.ISOLATION),
        Triple(listOf("hip thrust", "glute", "abduction"), Muscle.GLUTES, MovementPattern.HINGE),
        Triple(listOf("adductor"), Muscle.ADDUCTORS, MovementPattern.ISOLATION),
        Triple(listOf("crunch", "sit-up", "situp", "ab wheel", "plank", "rollout"), Muscle.ABS, MovementPattern.CORE),
        Triple(listOf("good morning"), Muscle.HAMSTRINGS, MovementPattern.HINGE),
        Triple(listOf("romanian", "rdl", "stiff-leg", "stiff leg"), Muscle.HAMSTRINGS, MovementPattern.HINGE),
        Triple(listOf("deadlift"), Muscle.GLUTES, MovementPattern.HINGE),
        Triple(listOf("squat", "leg press", "hack", "lunge", "step-up", "step up"), Muscle.QUADS, MovementPattern.SQUAT),
        Triple(listOf("pulldown", "pull-up", "pullup", "chin-up", "chinup", "pullover"), Muscle.LATS, MovementPattern.VERTICAL_PULL),
        Triple(listOf("row"), Muscle.UPPER_BACK, MovementPattern.HORIZONTAL_PULL),
        Triple(listOf("fly", "flye", "pec deck"), Muscle.CHEST, MovementPattern.ISOLATION),
        Triple(listOf("bench", "chest press", "push-up", "pushup", "dip"), Muscle.CHEST, MovementPattern.HORIZONTAL_PUSH),
        Triple(listOf("shoulder press", "overhead press", "ohp", "military"), Muscle.FRONT_DELTS, MovementPattern.VERTICAL_PUSH),
        Triple(listOf("wrist", "forearm"), Muscle.FOREARMS, MovementPattern.ISOLATION),
        Triple(listOf("carry", "farmer"), Muscle.FOREARMS, MovementPattern.CARRY),
    )

    fun guess(name: String): Guess {
        val n = name.lowercase()
        for ((keys, muscle, pattern) in rules) {
            if (keys.any { n.contains(it) }) return Guess(muscle, pattern, confident = true)
        }
        if (n.contains("press")) return Guess(Muscle.CHEST, MovementPattern.HORIZONTAL_PUSH, confident = false)
        if (n.contains("raise")) return Guess(Muscle.SIDE_DELTS, MovementPattern.ISOLATION, confident = false)
        if (n.contains("extension")) return Guess(Muscle.TRICEPS, MovementPattern.ISOLATION, confident = false)
        return Guess(Muscle.CHEST, MovementPattern.ISOLATION, confident = false)
    }

    fun guessEquipment(name: String): Equipment {
        val n = name.lowercase()
        return when {
            n.contains("smith") -> Equipment.SMITH
            n.contains("landmine") || n.contains("t-bar") || n.contains("meadows") -> Equipment.LANDMINE
            n.contains("trap bar") || n.contains("hex bar") -> Equipment.TRAP_BAR
            n.contains("ez") -> Equipment.EZ_BAR
            n.contains("kettlebell") -> Equipment.KETTLEBELL
            n.contains("dumbbell") -> Equipment.DUMBBELL
            n.contains("barbell") -> Equipment.BARBELL
            n.contains("cable") || n.contains("pulldown") || n.contains("pull-through") -> Equipment.CABLE
            n.contains("pull-up") || n.contains("pullup") || n.contains("chin-up") ||
                n.contains("chinup") || n.contains("hanging") -> Equipment.PULL_UP_BAR
            n.contains("dip") -> Equipment.DIP_STATION
            n.contains("band") -> Equipment.BANDS
            n.contains("leg press") || n.contains("hack squat") || n.contains("pendulum") ||
                n.contains("plate loaded") || n.contains("plate-loaded") -> Equipment.MACHINE_PLATE_LOADED
            n.contains("machine") || n.contains("pec deck") || n.contains("leg extension") ||
                n.contains("leg curl") || n.contains("abduction") || n.contains("adductor") -> Equipment.MACHINE_SELECTORIZED
            n.contains("push-up") || n.contains("pushup") || n.contains("bodyweight") ||
                n.contains("plank") || n.contains("nordic") || n.contains("sissy") -> Equipment.BODYWEIGHT
            else -> Equipment.OTHER
        }
    }
}
