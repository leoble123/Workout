package com.leo.forge.data.importer

import com.leo.forge.data.db.ForgeDatabase
import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.db.entity.SessionEntity
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Imports a Hevy CSV export into Forge's own schema.
 *
 * Hevy has shipped several header spellings and date formats over the years, so columns are
 * matched by normalised name with aliases and timestamps are tried against a list of formats
 * rather than one. Anything genuinely unreadable is counted and reported instead of being
 * dropped quietly - a history import that loses a third of your sets without saying so is
 * worse than one that refuses.
 */
class HevyCsvImporter(private val db: ForgeDatabase) {

    data class Report(
        val sessionsImported: Int = 0,
        val setsImported: Int = 0,
        val newExercisesCreated: Int = 0,
        val sessionsSkippedAsDuplicate: Int = 0,
        val rowsSkipped: Int = 0,
        val needsMuscleReview: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val error: String? = null,
    ) {
        val ok: Boolean get() = error == null
    }

    private data class Row(
        val title: String,
        val start: Long,
        val end: Long?,
        val exerciseName: String,
        val setIndex: Int,
        val setType: String,
        val weightKg: Double?,
        val reps: Int?,
        val rpe: Double?,
        val notes: String?,
    )

    suspend fun import(text: String): Report = withContext(Dispatchers.Default) {
        val rows = Csv.parse(text)
        if (rows.size < 2) return@withContext Report(error = "That file has no data rows in it.")

        val header = rows.first().map { Csv.normalizeHeader(it) }
        fun col(vararg aliases: String): Int =
            aliases.map { Csv.normalizeHeader(it) }.firstNotNullOfOrNull { a ->
                header.indexOf(a).takeIf { it >= 0 }
            } ?: -1

        val cTitle = col("title", "workout_name", "name")
        val cStart = col("start_time", "starttime", "date", "workout_date")
        val cEnd = col("end_time", "endtime")
        val cExercise = col("exercise_title", "exercise_name", "exercise")
        val cSetIndex = col("set_index", "set_order", "set_number", "set")
        val cSetType = col("set_type", "settype")
        val cWeight = col("weight_kg", "weight", "weightkg", "weight_lbs", "weightlbs")
        val cReps = col("reps", "repetitions")
        val cRpe = col("rpe")
        val cNotes = col("exercise_notes", "notes")

        val missing = buildList {
            if (cExercise < 0) add("exercise_title")
            if (cStart < 0) add("start_time")
            if (cReps < 0) add("reps")
        }
        if (missing.isNotEmpty()) {
            return@withContext Report(
                error = "This does not look like a Hevy export - missing column(s): ${missing.joinToString()}. " +
                    "In Hevy: Settings > Export Data > Export as CSV."
            )
        }

        // Hevy can export in pounds depending on account units; convert if the header says so.
        val weightInPounds = cWeight >= 0 && header[cWeight].contains("lb")

        val warnings = mutableListOf<String>()
        var rowsSkipped = 0
        val parsed = mutableListOf<Row>()

        for (r in rows.drop(1)) {
            fun cell(i: Int): String? = if (i in r.indices) r[i].trim().takeIf { it.isNotEmpty() } else null
            val exercise = cell(cExercise)
            val startRaw = cell(cStart)
            if (exercise == null || startRaw == null) { rowsSkipped++; continue }
            val start = parseTimestamp(startRaw)
            if (start == null) {
                rowsSkipped++
                if (warnings.none { it.startsWith("Unrecognised date") }) {
                    warnings += "Unrecognised date format: \"$startRaw\". Those rows were skipped."
                }
                continue
            }
            val reps = cell(cReps)?.toDoubleOrNull()?.toInt()
            val rawWeight = cell(cWeight)?.replace(',', '.')?.toDoubleOrNull()
            val weight = rawWeight?.let { if (weightInPounds) it / 2.2046226218 else it }

            // Duration/distance-only entries (planks, carries, cardio) carry no reps.
            if (reps == null || reps <= 0) { rowsSkipped++; continue }

            parsed += Row(
                title = cell(cTitle) ?: "Workout",
                start = start,
                end = cell(cEnd)?.let { parseTimestamp(it) },
                exerciseName = exercise,
                setIndex = cell(cSetIndex)?.toDoubleOrNull()?.toInt() ?: 0,
                setType = (cell(cSetType) ?: "normal").lowercase(),
                weightKg = weight,
                reps = reps,
                rpe = cell(cRpe)?.replace(',', '.')?.toDoubleOrNull(),
                notes = cell(cNotes),
            )
        }

        if (parsed.isEmpty()) {
            return@withContext Report(rowsSkipped = rowsSkipped, error = "No usable set rows found in that file.")
        }

        // --- resolve exercises, creating custom ones for anything not in the library
        val exerciseDao = db.exercises()
        val nameToId = mutableMapOf<String, String>()
        val needsReview = mutableListOf<String>()
        var created = 0

        for (name in parsed.map { it.exerciseName }.distinct()) {
            val existing = exerciseDao.byName(name) ?: exerciseDao.byName(canonicalName(name))
            if (existing != null) { nameToId[name] = existing.id; continue }
            val guess = MuscleGuesser.guess(name)
            val id = "hevy_" + slug(name)
            exerciseDao.upsert(
                ExerciseEntity(
                    id = id,
                    name = name,
                    primaryMuscle = guess.muscle,
                    equipment = MuscleGuesser.guessEquipment(name),
                    pattern = guess.pattern,
                    repLow = 8,
                    repHigh = 12,
                    loadIncrementKg = 2.5,
                    isCustom = true,
                    notes = if (guess.confident) null else "Imported from Hevy - confirm the muscle group.",
                )
            )
            nameToId[name] = id
            created++
            if (!guess.confident) needsReview += name
        }

        // --- group rows into sessions
        val sessionDao = db.sessions()
        val setDao = db.setLogs()
        val bestE1rm = mutableMapOf<String, Double>()

        val grouped = parsed.groupBy { it.start to it.title }.toList().sortedBy { it.first.first }
        var sessionsImported = 0
        var setsImported = 0
        var duplicates = 0

        for ((key, sessionRows) in grouped) {
            val (start, title) = key
            if (sessionExists(start, title)) { duplicates++; continue }

            val end = sessionRows.firstNotNullOfOrNull { it.end }
            val working = sessionRows.filter { it.setType != "warmup" }
            val volume = working.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

            val sessionId = sessionDao.insert(
                SessionEntity(
                    label = title,
                    startedAt = start,
                    finishedAt = end ?: start,
                    status = SessionStatus.COMPLETED,
                    totalVolumeKg = volume,
                    totalSets = working.size,
                    notes = "Imported from Hevy",
                )
            )

            val logs = sessionRows.sortedBy { it.setIndex }.mapIndexed { i, row ->
                val exId = nameToId.getValue(row.exerciseName)
                val weight = row.weightKg ?: 0.0
                val reps = row.reps ?: 0
                // Hevy records RPE; Forge reasons in reps-in-reserve.
                val rir = row.rpe?.let { (10.0 - it).toInt().coerceIn(0, 10) }
                val type = when (row.setType) {
                    "warmup" -> SetType.WARMUP
                    "dropset", "drop" -> SetType.DROP
                    "failure" -> SetType.WORKING
                    else -> SetType.WORKING
                }
                val e1rm = if (type == SetType.WORKING) OneRepMax.estimate(weight, reps, rir) else 0.0
                val prior = bestE1rm[exId] ?: 0.0
                val isPr = type == SetType.WORKING && e1rm > prior && e1rm > 0.0
                if (isPr) bestE1rm[exId] = e1rm

                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = exId,
                    setIndex = i,
                    type = type,
                    weightKg = weight,
                    reps = reps,
                    rir = rir,
                    completedAt = start + i * 1000L,
                    e1rmKg = e1rm,
                    isPr = isPr,
                    notes = row.notes,
                )
            }
            setDao.insertAll(logs)
            sessionsImported++
            setsImported += logs.size
        }

        Report(
            sessionsImported = sessionsImported,
            setsImported = setsImported,
            newExercisesCreated = created,
            sessionsSkippedAsDuplicate = duplicates,
            rowsSkipped = rowsSkipped,
            needsMuscleReview = needsReview.distinct(),
            warnings = warnings,
        )
    }

    private suspend fun sessionExists(start: Long, title: String): Boolean =
        db.sessions().existsAt(start, title) > 0

    private fun slug(name: String): String =
        name.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
            .replace(Regex("_+"), "_").trim('_')

    private fun canonicalName(name: String): String =
        name.replace(Regex("\\s*\\(.*?\\)\\s*"), " ").trim()

    companion object {
        private val ZONE: ZoneId = ZoneId.systemDefault()

        private val FORMATS = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "d MMM yyyy, HH:mm",
            "d MMM yyyy, h:mm a",
            "MMM d yyyy, HH:mm",
            "MMM d, yyyy, h:mm a",
            "dd/MM/yyyy HH:mm",
            "MM/dd/yyyy HH:mm",
            "dd-MM-yyyy HH:mm",
        ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

        fun parseTimestamp(raw: String): Long? {
            val s = raw.trim().removeSuffix("Z")
            s.toLongOrNull()?.let { n ->
                // Heuristic: 10-digit values are seconds, 13-digit are millis.
                return if (n > 100_000_000_000L) n else n * 1000L
            }
            for (f in FORMATS) {
                runCatching { return LocalDateTime.parse(s, f).atZone(ZONE).toInstant().toEpochMilli() }
            }
            runCatching { return java.time.Instant.parse(raw.trim()).toEpochMilli() }
            runCatching { return java.time.LocalDate.parse(s).atStartOfDay(ZONE).toInstant().toEpochMilli() }
            return null
        }
    }
}
