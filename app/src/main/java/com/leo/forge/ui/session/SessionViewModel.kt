package com.leo.forge.ui.session

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leo.forge.core.container
import com.leo.forge.data.db.entity.MesocycleEntity
import com.leo.forge.data.db.entity.SessionEntity
import com.leo.forge.data.db.entity.SetLogEntity
import com.leo.forge.data.repo.ExercisePlanUi
import com.leo.forge.data.repo.GymRepository
import com.leo.forge.data.repo.ProgramRepository
import com.leo.forge.data.repo.WorkoutRepository
import com.leo.forge.domain.model.*
import com.leo.forge.timer.RestState
import com.leo.forge.timer.RestTimer
import kotlinx.coroutines.flow.*
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
data class SetEntry(val weight: String, val reps: String, val rir: Int, val exactKg: Double? = null)

@Immutable
data class SessionUiState(
    val loading: Boolean = true,
    val session: SessionEntity? = null,
    val meso: MesocycleEntity? = null,
    val plans: List<ExercisePlanUi> = emptyList(),
    val entries: Map<String, SetEntry> = emptyMap(),
    val logged: List<SetLogEntity> = emptyList(),
    val rest: RestState = RestState.Idle,
    val prBanner: String? = null,
    val dismissed: Boolean = false,
) {
    fun key(exerciseId: String, setIndex: Int) = "$exerciseId:$setIndex"

    fun loggedSet(exerciseId: String, setIndex: Int): SetLogEntity? =
        logged.firstOrNull { it.exerciseId == exerciseId && it.setIndex == setIndex }

    val totalSets: Int get() = plans.sumOf { it.prescription.targets.size }
    val doneSets: Int get() = logged.count { it.type == SetType.WORKING }

    /** The first unlogged set, which is what the screen keeps in front of you. */
    val nextFocus: Pair<Int, Int>?
        get() {
            plans.forEachIndexed { ei, plan ->
                plan.prescription.targets.forEach { t ->
                    if (loggedSet(plan.exercise.id, t.setIndex) == null) return ei to t.setIndex
                }
            }
            return null
        }

    val musclesTrained: List<Muscle>
        get() = plans.map { it.exercise.primaryMuscle }.distinct()
}

class SessionViewModel(
    private val workouts: WorkoutRepository,
    private val program: ProgramRepository,
    private val gyms: GymRepository,
    private val restTimer: RestTimer,
) : ViewModel() {

    private val units = MutableStateFlow(Units.KG)

    private val plans = MutableStateFlow<List<ExercisePlanUi>>(emptyList())
    private val entries = MutableStateFlow<Map<String, SetEntry>>(emptyMap())
    private val meso = MutableStateFlow<MesocycleEntity?>(null)
    private val loading = MutableStateFlow(true)
    private val prBanner = MutableStateFlow<String?>(null)
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    init {
        viewModelScope.launch {
            // Prescriptions are computed once, when the session opens. Recomputing them as
            // sets land would let the targets move under you mid-workout.
            val session = sessionFlow.filterNotNull().first()
            val u = gyms.units()
            units.value = u
            // By id: reopening a session from a finished block must not silently
            // re-plan it against whatever block is active now.
            val m = session.mesocycleId?.let { program.mesocycle(it) }
            meso.value = m
            val day = if (m == null) null else session.plannedDayId?.let { id ->
                program.days(m.id).firstOrNull { it.day.id == id }
            }
            if (m != null && day != null) {
                val computed = workouts.prescribe(m, day, session.weekIndex, excludeSessionId = session.id)
                plans.value = computed
                entries.value = computed.flatMap { plan ->
                    plan.prescription.targets.map { t ->
                        "${plan.exercise.id}:${t.setIndex}" to SetEntry(
                            weight = if (t.weightKg > 0) Load.format(Load.toDisplay(t.weightKg, u)) else "",
                            reps = t.reps.toString(),
                            rir = t.targetRir,
                            exactKg = t.weightKg.takeIf { it > 0 },
                        )
                    }
                }.toMap()
            }
            loading.value = false
        }
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

    private fun edit(exerciseId: String, setIndex: Int, block: (SetEntry) -> SetEntry) {
        val key = "$exerciseId:$setIndex"
        entries.update { map ->
            val current = map[key] ?: SetEntry("", "", 2)
            map + (key to block(current))
        }
    }

    /** Logs one set. Returns true when it set a personal best. */
    fun logSet(plan: ExercisePlanUi, setIndex: Int, autoStartRest: Boolean, nextLabel: String) {
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
                plannedExerciseId = plan.planned.id,
                setIndex = setIndex,
                weightKg = weight,
                reps = reps,
                rir = entry.rir,
                target = target,
                restSecondsBefore = (restTimer.state.value as? RestState.Running)?.totalSeconds,
            )
            if (isPr) prBanner.value = "${plan.exercise.name} - best estimated 1RM yet"
            if (autoStartRest) {
                restTimer.start(plan.prescription.restSeconds, nextLabel)
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

    fun finish(feedback: Map<Muscle, Triple<Pump?, Soreness?, Workload?>>, onDone: () -> Unit) {
        val session = sessionFlow.value ?: return onDone()
        viewModelScope.launch {
            restTimer.stop()
            workouts.finishSession(session.id, feedback)
            dismissed.value = true
            onDone()
        }
    }

    fun abandon(onDone: () -> Unit) {
        val session = sessionFlow.value ?: return onDone()
        viewModelScope.launch {
            restTimer.stop()
            workouts.abandonSession(session.id)
            dismissed.value = true
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                SessionViewModel(c.workouts, c.program, c.gyms, c.restTimer)
            }
        }
    }
}
