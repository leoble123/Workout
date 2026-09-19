package com.leo.forge.ui.session

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leo.forge.core.container
import com.leo.forge.data.db.entity.MesocycleEntity
import com.leo.forge.data.db.entity.SessionEntity
import com.leo.forge.data.db.entity.SessionExerciseEntity
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.data.repo.ExercisePlanUi
import com.leo.forge.data.repo.GymRepository
import com.leo.forge.data.repo.ProgramRepository
import com.leo.forge.data.repo.WorkoutRepository
import com.leo.forge.domain.model.*
import com.leo.forge.domain.model.Load
import com.leo.forge.timer.RestState
import com.leo.forge.timer.RestTimer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Per-set entry buffer.
 *
 * [weight] is a string in the gym's display unit, because a half-typed "12." is a legal
 * intermediate state. [exactKg] holds the prescription's original kilograms while the field
 * is untouched, so a target shown in pounds and logged unchanged round-trips exactly rather
 * than drifting by a hundredth of a kilo on every conversion.
 */
@Immutable
data class SetEntry(
    val weight: String,
    val reps: String,
    val rir: Int,
    val exactKg: Double? = null,
    val type: SetType = SetType.WORKING,
)

@Immutable
data class SessionUiState(
    val loading: Boolean = true,
    val session: SessionEntity? = null,
    val meso: MesocycleEntity? = null,
    val plans: List<ExercisePlanUi> = emptyList(),
    val entries: Map<String, SetEntry> = emptyMap(),
    val logged: List<SetLogEntity> = emptyList(),
    val rest: RestState = RestState.Idle,
    val showRir: Boolean = false,
    val prBanner: String? = null,
    val dismissed: Boolean = false,
) {
    fun key(exerciseId: String, setIndex: Int) = "$exerciseId:$setIndex"

    fun loggedSet(exerciseId: String, setIndex: Int): SetLogEntity? =
        logged.firstOrNull { it.exerciseId == exerciseId && it.setIndex == setIndex }

    /** What you did on this set last time you trained the exercise. */
    fun previousSet(plan: ExercisePlanUi, setIndex: Int): SetLogEntity? =
        plan.previous.getOrNull(setIndex)

    val totalSets: Int get() = plans.sumOf { it.prescription.targets.size }
    val doneSets: Int get() = logged.count { it.type != SetType.WARMUP }
    val volumeKg: Double get() = logged.filter { it.type != SetType.WARMUP }.sumOf { it.weightKg * it.reps }

    val isComplete: Boolean
        get() = plans.isNotEmpty() && plans.all { plan ->
            plan.prescription.targets.all { loggedSet(plan.exercise.id, it.setIndex) != null }
        }

    val musclesTrained: List<Muscle>
        get() = plans.map { it.exercise.primaryMuscle }.distinct()
}

/** Everything the exercise picker needs: the library, and what this gym can actually do. */
@Immutable
data class PickerData(
    val library: List<com.leo.forge.data.db.entity.ExerciseEntity> = emptyList(),
    val availableIds: Set<String>? = null,
)

class SessionViewModel(
    private val workouts: WorkoutRepository,
    private val program: ProgramRepository,
    private val gyms: GymRepository,
    exercises: com.leo.forge.data.repo.ExerciseRepository,
    private val restTimer: RestTimer,
) : ViewModel() {

    val picker: StateFlow<PickerData> = combine(
        exercises.observeAll(), gyms.observeAvailableExerciseIds(),
    ) { library, ids -> PickerData(library, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerData())

    private val units = MutableStateFlow(Units.KG)

    private val plans = MutableStateFlow<List<ExercisePlanUi>>(emptyList())
    private val entries = MutableStateFlow<Map<String, SetEntry>>(emptyMap())
    private val meso = MutableStateFlow<MesocycleEntity?>(null)
    private val loading = MutableStateFlow(true)
    private val prBanner = MutableStateFlow<String?>(null)

    private val _deviations = MutableStateFlow<List<com.leo.forge.data.repo.Deviation>>(emptyList())
    /** How this session strayed from its planned day; asked about once, when finishing. */
    val deviations: StateFlow<List<com.leo.forge.data.repo.Deviation>> = _deviations.asStateFlow()
    private val showRir = MutableStateFlow(false)
    private val dismissed = MutableStateFlow(false)

    private val sessionFlow: StateFlow<SessionEntity?> =
        workouts.observeActive().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val loggedFlow: Flow<List<SetLogEntity>> = sessionFlow.flatMapLatest { s ->
        if (s == null) flowOf(emptyList()) else workouts.observeSets(s.id)
    }

    val state: StateFlow<SessionUiState> = combine(
        sessionFlow, loggedFlow, plans, entries, restTimer.state,
    ) { session, logged, p, e, rest ->
        SessionUiState(
            session = session, logged = logged, plans = p, entries = e, rest = rest,
        )
    }.combine(meso) { s, m -> s.copy(meso = m) }
        .combine(loading) { s, l -> s.copy(loading = l) }
        .combine(prBanner) { s, pr -> s.copy(prBanner = pr) }
        .combine(dismissed) { s, d -> s.copy(dismissed = d) }
        .combine(showRir) { s, r -> s.copy(showRir = r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    private val sessionExercises: Flow<List<SessionExerciseEntity>> = sessionFlow.flatMapLatest { s ->
        if (s == null) flowOf(emptyList()) else workouts.observeSessionExercises(s.id)
    }

    init {
        viewModelScope.launch {
            val session = sessionFlow.filterNotNull().first()
            units.value = gyms.units()
            meso.value = session.mesocycleId?.let { program.mesocycle(it) }

            // Recompute targets when the exercise list or a set count changes - but never
            // when a set is logged, or the numbers would shift under you mid-workout.
            sessionExercises
                .map { list -> list.map { Triple(it.id, it.exerciseId, it.targetSets) } }
                .distinctUntilChanged()
                .collect {
                    val current = sessionFlow.value ?: return@collect
                    val computed = workouts.prescribeSession(current.id)
                    plans.value = computed
                    mergeEntries(computed)
                    loading.value = false
                }
        }
    }

    /** Keeps anything already typed, and seeds entries for newly added exercises only. */
    private fun mergeEntries(computed: List<ExercisePlanUi>) {
        val u = units.value
        entries.update { existing ->
            val merged = existing.toMutableMap()
            computed.forEach { plan ->
                plan.prescription.targets.forEach { t ->
                    val key = "${plan.exercise.id}:${t.setIndex}"
                    if (key !in merged) {
                        merged[key] = SetEntry(
                            weight = if (t.weightKg > 0) Load.format(Load.toDisplay(t.weightKg, u)) else "",
                            reps = t.reps.toString(),
                            rir = t.targetRir,
                            exactKg = t.weightKg.takeIf { w -> w > 0 },
                        )
                    }
                }
            }
            merged
        }
    }

    fun setShowRir(value: Boolean) { showRir.value = value }

    fun addExercise(exerciseId: String) {
        val session = sessionFlow.value ?: return
        viewModelScope.launch { workouts.addExerciseToSession(session.id, exerciseId) }
    }

    fun removeExercise(item: SessionExerciseEntity) {
        viewModelScope.launch { workouts.removeSessionExercise(item) }
    }

    fun swapExercise(item: SessionExerciseEntity, newExerciseId: String) {
        viewModelScope.launch { workouts.swapSessionExercise(item, newExerciseId) }
    }

    fun changeSets(item: SessionExerciseEntity, delta: Int) {
        viewModelScope.launch { workouts.changeTargetSets(item, delta) }
    }

    fun updateWeight(exerciseId: String, setIndex: Int, value: String) = edit(exerciseId, setIndex) {
        // Typing detaches from the prescription's exact kilograms.
        it.copy(weight = value.filter { c -> c.isDigit() || c == '.' }.take(6), exactKg = null)
    }

    fun updateReps(exerciseId: String, setIndex: Int, value: String) = edit(exerciseId, setIndex) {
        it.copy(reps = value.filter { c -> c.isDigit() }.take(3))
    }

    /** Steps by an increment that is loadable in this gym: 2.5 kg, or 5 lb. */
    fun stepWeight(exerciseId: String, setIndex: Int, direction: Int) {
        val exercise = plans.value.firstOrNull { it.exercise.id == exerciseId }?.exercise ?: return
        val step = Load.increment(exercise.equipment, units.value, exercise.loadIncrementKg)
        if (step <= 0.0) return
        edit(exerciseId, setIndex) { e ->
            val current = e.weight.toDoubleOrNull() ?: 0.0
            val next = (current + direction * step).coerceAtLeast(0.0)
            e.copy(weight = Load.format(next), exactKg = null)
        }
    }

    fun stepReps(exerciseId: String, setIndex: Int, direction: Int) = edit(exerciseId, setIndex) { e ->
        val current = e.reps.toIntOrNull() ?: 0
        e.copy(reps = (current + direction).coerceIn(0, 100).toString())
    }

    fun setRir(exerciseId: String, setIndex: Int, rir: Int) = edit(exerciseId, setIndex) { it.copy(rir = rir) }

    /** Tapping the set number flips it between a working set and a warm-up. */
    fun toggleWarmup(exerciseId: String, setIndex: Int) = edit(exerciseId, setIndex) {
        it.copy(type = if (it.type == SetType.WARMUP) SetType.WORKING else SetType.WARMUP)
    }

    fun setExerciseNotes(item: SessionExerciseEntity, notes: String) {
        viewModelScope.launch { workouts.setExerciseNotes(item, notes) }
    }

    fun setExerciseRest(item: SessionExerciseEntity, seconds: Int) {
        viewModelScope.launch { workouts.setExerciseRest(item, seconds) }
    }

    private fun edit(exerciseId: String, setIndex: Int, block: (SetEntry) -> SetEntry) {
        val key = "$exerciseId:$setIndex"
        entries.update { map ->
            val current = map[key] ?: SetEntry("", "", 2)
            map + (key to block(current))
        }
    }

    /**
     * Ticks a set, or unticks one already logged.
     *
     * A single action per row, any row, any order - there is no "current set" to be in sync
     * with, which is what makes the screen impossible to get into a confusing state.
     */
    fun toggleSet(plan: ExercisePlanUi, setIndex: Int, autoStartRest: Boolean, trackRir: Boolean) {
        val existing = state.value.loggedSet(plan.exercise.id, setIndex)
        if (existing != null) {
            viewModelScope.launch { workouts.deleteSet(existing) }
            return
        }
        logSet(plan, setIndex, autoStartRest, trackRir)
    }

    private fun logSet(plan: ExercisePlanUi, setIndex: Int, autoStartRest: Boolean, trackRir: Boolean) {
        val session = sessionFlow.value ?: return
        val key = "${plan.exercise.id}:$setIndex"
        val entry = entries.value[key] ?: return
        // Untouched targets log their original kilograms; edited ones convert from the
        // gym's unit.
        val weight = entry.exactKg ?: Load.toKg(entry.weight.toDoubleOrNull() ?: 0.0, units.value)
        val reps = entry.reps.toIntOrNull() ?: return
        if (reps <= 0) return
        val target = plan.prescription.targets.firstOrNull { it.setIndex == setIndex }

        viewModelScope.launch {
            val isPr = workouts.logSet(
                sessionId = session.id,
                exercise = plan.exercise,
                plannedExerciseId = null,
                setIndex = setIndex,
                weightKg = weight,
                reps = reps,
                // Not tracking effort means logging no opinion, rather than a made-up one.
                rir = entry.rir.takeIf { trackRir && entry.type != SetType.WARMUP },
                type = entry.type,
                target = target,
                restSecondsBefore = (restTimer.state.value as? RestState.Running)?.totalSeconds,
            )
            if (isPr) prBanner.value = "${plan.exercise.name} - best estimated 1RM yet"
            // The label names the set you just finished. A fact about the past cannot go stale.
            if (autoStartRest && entry.type != SetType.WARMUP) {
                val rest = plan.sessionExercise?.restSeconds ?: plan.prescription.restSeconds
                restTimer.start(rest, "${plan.exercise.name} · set ${setIndex + 1}")
            } else {
                // Otherwise a finished bar from the previous set would linger.
                restTimer.stop()
            }
        }
    }

    fun undo(set: SetLogEntity) {
        viewModelScope.launch { workouts.deleteSet(set) }
    }

    fun clearPr() { prBanner.value = null }

    fun nudgeRest(seconds: Int) = restTimer.nudge(seconds)
    fun skipRest() = restTimer.stop()

    /** A rest you asked for yourself, from the header. */
    fun startRest(seconds: Int) = restTimer.start(seconds, "Rest")

    /** Works out what changed, so finishing can ask about it rather than guessing. */
    fun prepareFinish() {
        val session = sessionFlow.value ?: return
        viewModelScope.launch { _deviations.value = workouts.deviations(session.id) }
    }

    fun finish(
        feedback: Map<Muscle, Triple<Pump?, Soreness?, Workload?>>,
        keepChanges: Boolean,
        onDone: () -> Unit,
    ) {
        val session = sessionFlow.value ?: return onDone()
        viewModelScope.launch {
            restTimer.stop()
            workouts.finishSession(session.id, feedback, keepChanges)
            dismissed.value = true
            onDone()
        }
    }

    fun discard(onDone: () -> Unit) {
        val session = sessionFlow.value ?: return onDone()
        viewModelScope.launch {
            restTimer.stop()
            workouts.deleteSession(session.id)
            dismissed.value = true
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                SessionViewModel(c.workouts, c.program, c.gyms, c.exercises, c.restTimer)
            }
        }
    }
}
