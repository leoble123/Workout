package com.leo.forge.domain.model

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Everything to do with how much is on the bar.
 *
 * Loads are stored in kilograms everywhere, but *increments are a property of the room you
 * are standing in*, not a display preference. An Australian gym's plates are marked in kg
 * and step in 2.5; a US gym's step in 5 lb. Converting a kg-rounded suggestion into pounds
 * produces numbers nobody can load ("220.5 lb"), which is precisely the friction this app
 * exists to remove - so the progression maths is done in the gym's own unit and only
 * converted back to kg for storage.
 */
object Load {

    const val LB_PER_KG = 2.2046226218

    fun toDisplay(kg: Double, units: Units): Double =
        if (units == Units.KG) kg else kg * LB_PER_KG

    fun toKg(value: Double, units: Units): Double =
        if (units == Units.KG) value else value / LB_PER_KG

    /** The smallest jump loadable on this implement, expressed in kg. */
    fun incrementKg(equipment: Equipment): Double = when (equipment) {
        Equipment.BARBELL, Equipment.EZ_BAR, Equipment.TRAP_BAR -> 2.5   // a pair of 1.25s
        Equipment.SMITH, Equipment.LANDMINE -> 2.5
        Equipment.DUMBBELL -> 2.0                                        // most racks step in 2
        Equipment.KETTLEBELL -> 4.0                                      // they come as they come
        Equipment.CABLE -> 2.5
        Equipment.MACHINE_SELECTORIZED -> 5.0                            // pin stacks are coarse
        Equipment.MACHINE_PLATE_LOADED -> 2.5
        Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR, Equipment.DIP_STATION -> 2.5 // belt plates
        Equipment.RACK, Equipment.BENCH -> 2.5    // never loaded directly; listed for completeness
        Equipment.BANDS -> 0.0                                           // progress by reps only
        Equipment.OTHER -> 2.5
    }

    /** The same jump in a gym whose plates are marked in pounds - not a converted kg value. */
    fun incrementLb(equipment: Equipment): Double = when (equipment) {
        Equipment.BARBELL, Equipment.EZ_BAR, Equipment.TRAP_BAR -> 5.0   // a pair of 2.5s
        Equipment.SMITH, Equipment.LANDMINE -> 5.0
        Equipment.DUMBBELL -> 5.0
        Equipment.KETTLEBELL -> 10.0
        Equipment.CABLE -> 5.0
        Equipment.MACHINE_SELECTORIZED -> 10.0
        Equipment.MACHINE_PLATE_LOADED -> 5.0
        Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR, Equipment.DIP_STATION -> 5.0
        Equipment.RACK, Equipment.BENCH -> 5.0
        Equipment.BANDS -> 0.0
        Equipment.OTHER -> 5.0
    }

    /**
     * Increment in [units].
     *
     * @param storedKgIncrement the exercise's own kg increment; zero means the load cannot
     *   move at all (bands), which must survive the unit switch rather than being defaulted away.
     */
    /** The implements whose step is decided by which plates the gym happens to stock. */
    private val PLATE_LOADED = setOf(
        Equipment.BARBELL, Equipment.EZ_BAR, Equipment.TRAP_BAR,
        Equipment.SMITH, Equipment.LANDMINE, Equipment.MACHINE_PLATE_LOADED,
    )

    /**
     * @param gymBarbellIncrement the smallest barbell jump this gym can actually make, in
     *   [units] - two of its smallest plate. Overrides the default for plate-loaded kit.
     */
    fun increment(
        equipment: Equipment,
        units: Units,
        storedKgIncrement: Double = -1.0,
        gymBarbellIncrement: Double? = null,
    ): Double {
        if (storedKgIncrement == 0.0) return 0.0
        if (gymBarbellIncrement != null && gymBarbellIncrement > 0.0 && equipment in PLATE_LOADED) {
            return gymBarbellIncrement
        }
        return when (units) {
            Units.KG -> if (storedKgIncrement > 0.0) storedKgIncrement else incrementKg(equipment)
            Units.LB -> incrementLb(equipment)
        }
    }

    fun round(value: Double, increment: Double): Double {
        if (increment <= 0.0) return value
        return (value / increment).roundToLong().toDouble() * increment
    }

    /** Trims a trailing ".0" so the gym floor reads "60 kg", not "60.0 kg". */
    fun format(value: Double): String {
        val rounded = (value * 100).roundToLong() / 100.0
        return if (abs(rounded - rounded.roundToLong()) < 0.001) rounded.roundToLong().toString()
        else String.format("%.1f", rounded)
    }

    fun formatWithUnit(kg: Double, units: Units): String =
        "${format(toDisplay(kg, units))} ${units.display}"

    /**
     * Total volume, in the unit a person would actually say out loud: tonnes in a kg gym,
     * thousands of pounds in a lb one.
     */
    fun formatTonnage(kg: Double, units: Units): Pair<String, String> = when (units) {
        Units.KG -> {
            val t = kg / 1000.0
            (if (t >= 10) t.roundToLong().toString() else String.format("%.1f", t)) to "t"
        }
        Units.LB -> {
            val k = kg * LB_PER_KG / 1000.0
            (if (k >= 10) k.roundToLong().toString() else String.format("%.1f", k)) to "k lb"
        }
    }
}
