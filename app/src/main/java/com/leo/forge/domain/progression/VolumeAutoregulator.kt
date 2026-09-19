package com.leo.forge.domain.progression

import com.leo.forge.domain.model.Pump
import com.leo.forge.domain.model.Soreness
import com.leo.forge.domain.model.Workload

/**
 * Decides how many sets a muscle gets next week from how the last one actually felt.
 *
 * A fixed ramp assumes recovery it cannot observe. Three questions after a session -
 * pump, soreness, workload - are enough to tell "add volume" from "you are already
 * behind on recovery", which is the difference between a productive block and a dug hole.
 */
object VolumeAutoregulator {

    data class Verdict(val setChange: Int, val reason: String)

    fun next(pump: Pump?, soreness: Soreness?, workload: Workload?): Verdict {
        // Absent feedback: creep up conservatively rather than stall.
        if (pump == null && soreness == null && workload == null) {
            return Verdict(1, "No feedback logged, so a cautious +1 set.")
        }

        val p = pump ?: Pump.MODERATE
        val s = soreness ?: Soreness.HEALED_JUST_ON_TIME
        val w = workload ?: Workload.PRETTY_GOOD

        return when {
            s == Soreness.STILL_SORE ->
                Verdict(-1, "Still sore going in - you are accumulating faster than you recover. Pulling a set.")

            w == Workload.TOO_MUCH ->
                Verdict(-1, "Last session was past your limit. Backing off a set to keep quality up.")

            s == Soreness.HEALED_JUST_ON_TIME && w == Workload.PUSHED_LIMITS ->
                Verdict(0, "Recovery landed exactly on time at a hard workload - you are at your ceiling. Holding.")

            p == Pump.NONE && s == Soreness.NEVER_GOT && w == Workload.EASY ->
                Verdict(3, "No pump, no soreness, easy session - well under your effective volume. +3 sets.")

            s == Soreness.NEVER_GOT && w == Workload.EASY ->
                Verdict(2, "Never got sore and it felt easy - room to add. +2 sets.")

            s == Soreness.NEVER_GOT ->
                Verdict(2, "No soreness at all, so there is recovery headroom. +2 sets.")

            s == Soreness.HEALED_A_WHILE_AGO && w != Workload.PUSHED_LIMITS ->
                Verdict(2, "Healed well before the next session. +2 sets.")

            s == Soreness.HEALED_A_WHILE_AGO ->
                Verdict(1, "Recovered early but the session was hard. +1 set.")

            else ->
                Verdict(1, "Recovery and effort both in range - standard +1 set.")
        }
    }

    /** Collapses several sessions' feedback for one muscle into a single week's change. */
    fun weekly(feedback: List<Triple<Pump?, Soreness?, Workload?>>): Verdict {
        if (feedback.isEmpty()) return Verdict(1, "No feedback logged, so a cautious +1 set.")
        val verdicts = feedback.map { (p, s, w) -> next(p, s, w) }
        // The most conservative session wins: recovery is the binding constraint.
        return verdicts.minByOrNull { it.setChange }!!
    }
}
