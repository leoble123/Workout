package com.leo.forge.data.db

import androidx.room.TypeConverter
import com.leo.forge.domain.model.*

/**
 * Enums are persisted by [Enum.name] rather than ordinal so that reordering a
 * declaration later cannot silently rewrite the meaning of existing rows.
 */
class Converters {

    @TypeConverter fun muscleToString(v: Muscle?): String? = v?.name
    @TypeConverter fun stringToMuscle(v: String?): Muscle? = v?.let { runCatching { Muscle.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun muscleListToString(v: List<Muscle>?): String = v.orEmpty().joinToString(",") { it.name }

    @TypeConverter
    fun stringToMuscleList(v: String?): List<Muscle> =
        v?.split(',')?.mapNotNull { s -> s.trim().takeIf { it.isNotEmpty() }?.let { runCatching { Muscle.valueOf(it) }.getOrNull() } }.orEmpty()

    @TypeConverter fun equipmentToString(v: Equipment?): String? = v?.name
    @TypeConverter fun stringToEquipment(v: String?): Equipment? = v?.let { runCatching { Equipment.valueOf(it) }.getOrNull() }

    @TypeConverter fun patternToString(v: MovementPattern?): String? = v?.name
    @TypeConverter fun stringToPattern(v: String?): MovementPattern? = v?.let { runCatching { MovementPattern.valueOf(it) }.getOrNull() }

    @TypeConverter fun setTypeToString(v: SetType?): String? = v?.name
    @TypeConverter fun stringToSetType(v: String?): SetType? = v?.let { runCatching { SetType.valueOf(it) }.getOrNull() }

    @TypeConverter fun splitToString(v: SplitType?): String? = v?.name
    @TypeConverter fun stringToSplit(v: String?): SplitType? = v?.let { runCatching { SplitType.valueOf(it) }.getOrNull() }

    @TypeConverter fun sessionStatusToString(v: SessionStatus?): String? = v?.name
    @TypeConverter fun stringToSessionStatus(v: String?): SessionStatus? = v?.let { runCatching { SessionStatus.valueOf(it) }.getOrNull() }

    @TypeConverter fun mesoStatusToString(v: MesoStatus?): String? = v?.name
    @TypeConverter fun stringToMesoStatus(v: String?): MesoStatus? = v?.let { runCatching { MesoStatus.valueOf(it) }.getOrNull() }

    @TypeConverter fun pumpToString(v: Pump?): String? = v?.name
    @TypeConverter fun stringToPump(v: String?): Pump? = v?.let { runCatching { Pump.valueOf(it) }.getOrNull() }

    @TypeConverter fun sorenessToString(v: Soreness?): String? = v?.name
    @TypeConverter fun stringToSoreness(v: String?): Soreness? = v?.let { runCatching { Soreness.valueOf(it) }.getOrNull() }

    @TypeConverter fun workloadToString(v: Workload?): String? = v?.name
    @TypeConverter fun stringToWorkload(v: String?): Workload? = v?.let { runCatching { Workload.valueOf(it) }.getOrNull() }
}
