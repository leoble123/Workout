package com.leo.forge.data.seed

import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.Units

/**
 * Starting points for a gym profile.
 *
 * Station names here are descriptive, not manufacturer model numbers - the app has no way
 * to verify a model number and an invented one is worse than a blank you can edit. What
 * matters to the generator is the exercise list each station grants, which is what these
 * presets actually carry.
 */
object GymSeed {

    data class StationPreset(
        val name: String,
        val brand: String? = null,
        val notes: String? = null,
        val exerciseIds: List<String>,
    )

    data class GymPreset(
        val name: String,
        val units: Units,
        val notes: String?,
        val equipment: Map<Equipment, Boolean>,
        val equipmentDetail: Map<Equipment, String> = emptyMap(),
        val stations: List<StationPreset>,
        /** Two of the smallest plate this gym stocks, in [units]. Null uses the default. */
        val barbellIncrement: Double? = null,
    )

    /** Everything a two-arm adjustable pulley can do, taken straight from the library. */
    val cableExerciseIds: List<String> =
        ExerciseSeed.all.filter { it.equipment == Equipment.CABLE }.map { it.id }

    private val crunchAndCurlStation = listOf(
        "cable_crunch", "cable_woodchop",
        "cable_curl", "cable_hammer_curl", "bayesian_cable_curl",
        "triceps_pushdown", "rope_pushdown", "overhead_cable_extension",
        "straight_arm_pulldown", "cable_lateral_raise", "cable_front_raise",
        "cable_upright_row", "cable_shrug",
    )

    /**
     * A sparse, cable-led gym: cables, dumbbells, benches and loose barbells, but no rack
     * and no leg machines.
     *
     * Machine categories are deliberately off - the two machine movements that exist come
     * from the combo station rather than from a blanket "has machines". RACK being off is
     * what keeps back squats and barbell bench out of a generated session: a loose barbell
     * with nothing to unrack from does not give you either.
     */
    fun minimalCableGym(
        name: String,
        units: Units,
        barbellIncrement: Double? = null,
    ) = GymPreset(
        name = name,
        units = units,
        notes = "Cables, dumbbells, benches and loose barbells. No rack. " +
            "Rename the stations to whatever they actually say on them.",
        barbellIncrement = barbellIncrement,
        equipment = mapOf(
            Equipment.CABLE to true,
            Equipment.DUMBBELL to true,
            Equipment.BENCH to true,
            Equipment.BARBELL to true,
            Equipment.BODYWEIGHT to true,
            Equipment.RACK to false,
            Equipment.EZ_BAR to false,
            Equipment.TRAP_BAR to false,
            Equipment.KETTLEBELL to false,
            Equipment.MACHINE_SELECTORIZED to false,
            Equipment.MACHINE_PLATE_LOADED to false,
            Equipment.SMITH to false,
            Equipment.PULL_UP_BAR to false,
            Equipment.DIP_STATION to false,
            Equipment.BANDS to false,
            Equipment.LANDMINE to false,
            Equipment.OTHER to false,
        ),
        equipmentDetail = mapOf(
            Equipment.CABLE to "Genesis stations - see below.",
            Equipment.BARBELL to "Loose bars, no rack. Light bar.",
        ),
        stations = listOf(
            StationPreset(
                name = "Dual-arm cable / functional trainer",
                brand = "Genesis",
                notes = "Two adjustable pulleys. The workhorse - most of the programme runs off this one.",
                exerciseIds = cableExerciseIds,
            ),
            StationPreset(
                name = "Cable crunch & biceps station",
                brand = "Genesis",
                notes = "High pulley for crunches, low pulley for curls. Correct the name and the " +
                    "exercise list if it does more or less than this.",
                exerciseIds = crunchAndCurlStation,
            ),
            StationPreset(
                name = "Shoulder press & chest fly",
                brand = "Genesis",
                notes = "Dual-function selectorized unit. If the fly arm also reverses, add " +
                    "Reverse Pec Deck to this station.",
                exerciseIds = listOf("machine_shoulder_press", "pec_deck"),
            ),
        ),
    )

    /** A fully-equipped commercial gym: everything on, no stations needed. */
    fun fullGym(units: Units, name: String = "Commercial gym") = GymPreset(
        name = name,
        units = units,
        notes = null,
        equipment = Equipment.entries.associateWith { true },
        stations = emptyList(),
    )

    /**
     * The profiles created on first run. The first is made active.
     *
     * Two gyms rather than one because the point of the profile is switching: travel
     * changes what is on the floor and what the plates are marked in, and re-ticking
     * fifteen categories on arrival is precisely the friction worth removing.
     */
    fun defaults(): List<GymPreset> = listOf(
        minimalCableGym(
            name = "Current gym (Australia)",
            units = Units.LB,
            // Smallest plate stocked is 2.5 lb, so the smallest honest jump is a pair of them.
            barbellIncrement = 5.0,
        ),
        fullGym(Units.LB, name = "Home gym (USA)"),
    )
}
