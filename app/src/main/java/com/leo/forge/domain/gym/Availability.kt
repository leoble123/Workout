package com.leo.forge.domain.gym

import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.domain.model.Equipment

/**
 * Decides what you can actually do at a given gym.
 *
 * Resolution is most-specific-wins: an explicit yes/no you set for one exercise beats
 * everything, then a station that provides it, then the broad equipment categories. That
 * ordering is what lets a sparse gym say "no machines, except the shoulder press and the
 * pec deck on that one combo unit" without lying in either direction.
 */
object Availability {

    fun resolve(
        library: List<ExerciseEntity>,
        availableEquipment: Set<Equipment>,
        stationExerciseIds: Set<String>,
        overrides: Map<String, Boolean>,
    ): Set<String> = library.asSequence()
        .filter { !it.archived }
        .filter { isAvailable(it, availableEquipment, stationExerciseIds, overrides) }
        .map { it.id }
        .toSet()

    fun isAvailable(
        exercise: ExerciseEntity,
        availableEquipment: Set<Equipment>,
        stationExerciseIds: Set<String>,
        overrides: Map<String, Boolean>,
    ): Boolean {
        overrides[exercise.id]?.let { return it }
        if (exercise.id in stationExerciseIds) return true
        return exercise.equipment in availableEquipment
    }

    /** Why an exercise is or is not on the menu, for the gym screen. */
    fun reason(
        exercise: ExerciseEntity,
        availableEquipment: Set<Equipment>,
        stationExerciseIds: Set<String>,
        overrides: Map<String, Boolean>,
    ): String = when {
        overrides[exercise.id] == true -> "You marked this as available here"
        overrides[exercise.id] == false -> "You marked this as unavailable here"
        exercise.id in stationExerciseIds -> "One of your stations does this"
        exercise.equipment in availableEquipment -> "You have ${exercise.equipment.display.lowercase()}"
        else -> "No ${exercise.equipment.display.lowercase()} at this gym"
    }
}
