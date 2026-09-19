package com.leo.forge.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.db.entity.SessionExerciseEntity
import com.leo.forge.data.prefs.ForgeSettings
import com.leo.forge.data.repo.Deviation
import com.leo.forge.data.repo.ExercisePlanUi
import com.leo.forge.data.seed.ExerciseGuide
import com.leo.forge.domain.model.*
import com.leo.forge.timer.RestState
import com.leo.forge.ui.components.*
import com.leo.forge.ui.theme.Forge
import com.leo.forge.ui.theme.LocalHapticsEnabled
import com.leo.forge.ui.theme.NumericStyle
import com.leo.forge.ui.theme.loadText
import com.leo.forge.ui.theme.pressScale
import com.leo.forge.ui.theme.tonnageText
import com.leo.forge.ui.theme.unitLabel

/**
 * Logging a workout.
 *
 * Every set in the session is on screen as a row you can fill in and tick, in any order.
 * There is deliberately no notion of a "current set": a focus that the app moves for you is
 * a second source of truth about where you are, and it will sooner or later disagree with
 * where you actually are.
 */
@Composable
fun SessionScreen(
    settings: ForgeSettings,
    onDone: () -> Unit,
    vm: SessionViewModel = viewModel(factory = SessionViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val picker by vm.picker.collectAsStateWithLifecycle()
    val deviations by vm.deviations.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var showFinish by remember { mutableStateOf(false) }
    var showAbandon by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var swapTarget by remember { mutableStateOf<SessionExerciseEntity?>(null) }
    var helpFor by remember { mutableStateOf<ExerciseEntity?>(null) }

    LaunchedEffect(settings.showRir) { vm.setShowRir(settings.showRir) }

    // Nothing logged means nothing to confirm; a dialog there is just a speed bump.
    BackHandler { if (state.doneSets == 0) onDone() else showAbandon = true }

    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize().background(Forge.colors.background)) {
        SessionTopBar(
            state = state,
            onFinish = { vm.prepareFinish(); showFinish = true },
            onLeave = { if (state.doneSets == 0) onDone() else showAbandon = true },
            onManualRest = { vm.startRest(DEFAULT_MANUAL_REST) },
        )
        StatsStrip(state)

        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(state.plans, key = { _, p -> p.id }) { _, plan ->
                ExerciseCard(
                    plan = plan,
                    state = state,
                    onToggleSet = { si -> vm.toggleSet(plan, si, settings.autoStartRest, settings.showRir) },
                    onWeight = { si, v -> vm.updateWeight(plan.exercise.id, si, v) },
                    onReps = { si, v -> vm.updateReps(plan.exercise.id, si, v) },
                    onRir = { si, r -> vm.setRir(plan.exercise.id, si, r) },
                    onToggleWarmup = { si -> vm.toggleWarmup(plan.exercise.id, si) },
                    onAddSet = { plan.sessionExercise?.let { vm.changeSets(it, 1) } },
                    onRemoveSet = { plan.sessionExercise?.let { vm.changeSets(it, -1) } },
                    onSwap = { swapTarget = plan.sessionExercise },
                    onRemove = { plan.sessionExercise?.let { vm.removeExercise(it) } },
                    onNotes = { text -> plan.sessionExercise?.let { vm.setExerciseNotes(it, text) } },
                    onRest = { seconds -> plan.sessionExercise?.let { vm.setExerciseRest(it, seconds) } },
                    onHelp = { helpFor = plan.exercise },
                )
            }

            item {
                SecondaryButton("Add exercise", Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) {
                    showPicker = true
                }
            }

            if (!state.loading && state.plans.isEmpty()) {
                item {
                    EmptyState(
                        "Empty workout",
                        "Add an exercise and Forge fills in the weights from the last time you did it.",
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.prBanner != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            LaunchedEffect(state.prBanner) {
                if (state.prBanner != null) {
                    kotlinx.coroutines.delay(5_000)
                    vm.clearPr()
                }
            }
            Box(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                PrBanner(state.prBanner.orEmpty()) { vm.clearPr() }
            }
        }

        (state.rest as? RestState.Running)?.let { running ->
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                RestBar(running = running, onNudge = vm::nudgeRest, onSkip = vm::skipRest)
            }
        }
    }

    helpFor?.let { ExerciseHelpSheet(it) { helpFor = null } }

    if (showPicker) {
        ExercisePickerSheet(
            library = picker.library,
            availableIds = picker.availableIds,
            title = "Add exercises",
            confirmLabel = "Add",
            multiSelect = true,
            onDismiss = { showPicker = false },
            onConfirm = { ids ->
                ids.forEach(vm::addExercise)
                showPicker = false
            },
        )
    }

    swapTarget?.let { target ->
        ExercisePickerSheet(
            library = picker.library,
            availableIds = picker.availableIds,
            title = "Swap for",
            confirmLabel = "Swap",
            multiSelect = false,
            onDismiss = { swapTarget = null },
            onConfirm = { ids ->
                ids.firstOrNull()?.let { vm.swapExercise(target, it) }
                swapTarget = null
            },
        )
    }

    if (showFinish) {
        FinishSheet(
            muscles = state.musclesTrained,
            deviations = deviations,
            onDismiss = { showFinish = false },
            onConfirm = { feedback, keepChanges ->
                showFinish = false
                vm.finish(feedback, keepChanges, onDone)
            },
        )
    }

    if (showAbandon) {
        AlertDialog(
            onDismissRequest = { showAbandon = false },
            containerColor = Forge.colors.surface2,
            title = { Text("Leave this workout?", color = Forge.colors.textPrimary) },
            text = {
                Text(
                    "${state.doneSets} sets are logged. You can come back to it, or end it here.",
                    color = Forge.colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbandon = false; onDone() }) {
                    Text("Keep it open", color = Forge.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAbandon = false; vm.abandon(onDone) }) {
                    Text("Discard", color = Forge.colors.danger)
                }
            },
        )
    }
}

private const val DEFAULT_MANUAL_REST = 120

// ---------------------------------------------------------------- header

@Composable
private fun SessionTopBar(
    state: SessionUiState,
    onFinish: () -> Unit,
    onLeave: () -> Unit,
    onManualRest: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onLeave) {
            Icon(Icons.Rounded.ExpandMore, "Leave", tint = Forge.colors.textSecondary)
        }
        Text(
            state.session?.label ?: "Workout",
            style = MaterialTheme.typography.titleLarge,
            color = Forge.colors.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onManualRest) {
            Icon(Icons.Rounded.Timer, "Start a rest", tint = Forge.colors.textSecondary)
        }
        Spacer(Modifier.width(4.dp))
        PrimaryButton("Finish", onClick = onFinish)
    }
}

/** Duration, volume and set count - the three numbers worth glancing at mid-session. */
@Composable
private fun StatsStrip(state: SessionUiState) {
    val elapsed by rememberElapsedSeconds(state.session?.startedAt ?: System.currentTimeMillis())
    val (volume, volumeUnit) = tonnageText(state.volumeKg)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        StatCell("Duration", formatClock(elapsed), Modifier.weight(1f))
        StatCell("Volume", "$volume $volumeUnit", Modifier.weight(1f))
        StatCell("Sets", "${state.doneSets}", Modifier.weight(1f))
    }
    HorizontalDivider(color = Forge.colors.outline)
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Forge.colors.textTertiary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = NumericStyle.copy(fontSize = 17.sp), color = Forge.colors.accent)
    }
}

// ---------------------------------------------------------------- exercise

@Composable
private fun ExerciseCard(
    plan: ExercisePlanUi,
    state: SessionUiState,
    onToggleSet: (Int) -> Unit,
    onWeight: (Int, String) -> Unit,
    onReps: (Int, String) -> Unit,
    onRir: (Int, Int) -> Unit,
    onToggleWarmup: (Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onNotes: (String) -> Unit,
    onRest: (Int) -> Unit,
    onHelp: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var restMenu by remember { mutableStateOf(false) }
    var notes by remember(plan.id) { mutableStateOf(plan.sessionExercise?.notes.orEmpty()) }
    val cue = remember(plan.exercise.id) { ExerciseGuide.forExercise(plan.exercise.id) }
    val restSeconds = plan.sessionExercise?.restSeconds ?: plan.prescription.restSeconds

    ForgeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Forge.colors.surface3),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        plan.exercise.primaryMuscle.display.take(1),
                        style = MaterialTheme.typography.labelLarge,
                        color = Forge.colors.accent,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    plan.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Forge.colors.accent,
                    maxLines = 2,
                    modifier = Modifier.weight(1f).clickable { onHelp() },
                )
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.MoreVert, "More", tint = Forge.colors.textTertiary, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(menu, { menu = false }, containerColor = Forge.colors.surface3) {
                        DropdownMenuItem(
                            text = { Text("How to do it", color = Forge.colors.textPrimary) },
                            onClick = { menu = false; onHelp() },
                        )
                        DropdownMenuItem(
                            text = { Text("Swap exercise", color = Forge.colors.textPrimary) },
                            onClick = { menu = false; onSwap() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove a set", color = Forge.colors.textPrimary) },
                            onClick = { menu = false; onRemoveSet() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove exercise", color = Forge.colors.danger) },
                            onClick = { menu = false; onRemove() },
                        )
                    }
                }
            }

            if (cue != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    cue.execution,
                    style = MaterialTheme.typography.bodySmall,
                    color = Forge.colors.textSecondary,
                    maxLines = 2,
                    modifier = Modifier.clickable { onHelp() },
                )
            }

            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = notes,
                onValueChange = { notes = it; onNotes(it) },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Forge.colors.textPrimary),
                cursorBrush = SolidColor(Forge.colors.accent),
                decorationBox = { inner ->
                    if (notes.isEmpty()) {
                        Text(
                            "Add notes here…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Forge.colors.textTertiary,
                        )
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { restMenu = true }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Timer, null, tint = Forge.colors.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (restSeconds <= 0) "Rest timer: off" else "Rest timer: ${formatClock(restSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Forge.colors.accent,
                    )
                }
                DropdownMenu(restMenu, { restMenu = false }, containerColor = Forge.colors.surface3) {
                    listOf(0, 60, 90, 120, 150, 180, 210, 240, 300).forEach { seconds ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (seconds == 0) "Off" else formatClock(seconds),
                                    color = if (seconds == restSeconds) Forge.colors.accent else Forge.colors.textPrimary,
                                )
                            },
                            onClick = { restMenu = false; onRest(seconds) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SetTableHeader(showRir = state.showRir)

            plan.prescription.targets.forEach { target ->
                val key = state.key(plan.exercise.id, target.setIndex)
                val entry = state.entries[key]
                SetRow(
                    setIndex = target.setIndex,
                    entry = entry,
                    logged = state.loggedSet(plan.exercise.id, target.setIndex),
                    previous = state.previousSet(plan, target.setIndex),
                    showRir = state.showRir,
                    onWeight = { onWeight(target.setIndex, it) },
                    onReps = { onReps(target.setIndex, it) },
                    onRir = { onRir(target.setIndex, it) },
                    onToggleWarmup = { onToggleWarmup(target.setIndex) },
                    onToggle = { onToggleSet(target.setIndex) },
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Forge.colors.surface3)
                    .clickable { onAddSet() }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = Forge.colors.textSecondary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add set", style = MaterialTheme.typography.labelMedium, color = Forge.colors.textSecondary)
            }
        }
    }
}

// ---------------------------------------------------------------- set table

private val SET_COL = 34.dp
private val NUM_COL = 62.dp
private val RIR_COL = 46.dp
private val TICK_COL = 42.dp

@Composable
private fun SetTableHeader(showRir: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("SET", Modifier.width(SET_COL))
        HeaderCell("PREVIOUS", Modifier.weight(1f))
        HeaderCell(unitLabel().uppercase(), Modifier.width(NUM_COL), TextAlign.Center)
        HeaderCell("REPS", Modifier.width(NUM_COL), TextAlign.Center)
        if (showRir) HeaderCell("RIR", Modifier.width(RIR_COL), TextAlign.Center)
        Spacer(Modifier.width(TICK_COL))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Forge.colors.textTertiary,
        textAlign = align,
        modifier = modifier,
    )
}

/**
 * One set.
 *
 * Fields arrive pre-filled with what the engine expects of you, so the common case is a
 * single tap on the tick. PREVIOUS carries what you did last time, which is what stops the
 * pre-filled number being something you have to take on faith.
 */
@Composable
private fun SetRow(
    setIndex: Int,
    entry: SetEntry?,
    logged: com.leo.forge.data.db.entity.SetLogEntity?,
    previous: com.leo.forge.data.db.entity.SetLogEntity?,
    showRir: Boolean,
    onWeight: (String) -> Unit,
    onReps: (String) -> Unit,
    onRir: (Int) -> Unit,
    onToggleWarmup: () -> Unit,
    onToggle: () -> Unit,
) {
    val done = logged != null
    val warmup = (logged?.type ?: entry?.type) == SetType.WARMUP
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (done) Forge.colors.accent.copy(alpha = 0.10f) else Color.Transparent)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(SET_COL)
                .height(32.dp)
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Forge.colors.surface3)
                .clickable { onToggleWarmup() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (warmup) "W" else "${setIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = if (warmup) Forge.colors.warn else Forge.colors.textSecondary,
            )
        }

        Text(
            previous?.let { "${loadText(it.weightKg)} × ${it.reps}" } ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = Forge.colors.textTertiary,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )

        CellField(
            value = entry?.weight.orEmpty(),
            onValueChange = onWeight,
            modifier = Modifier.width(NUM_COL),
            decimal = true,
            done = done,
        )
        Spacer(Modifier.width(6.dp))
        CellField(
            value = entry?.reps.orEmpty(),
            onValueChange = onReps,
            modifier = Modifier.width(NUM_COL),
            done = done,
        )

        if (showRir) {
            Spacer(Modifier.width(6.dp))
            var rirMenu by remember { mutableStateOf(false) }
            Box(Modifier.width(RIR_COL)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, Forge.colors.outline, RoundedCornerShape(9.dp))
                        .clickable { rirMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${entry?.rir ?: 2}",
                        style = NumericStyle.copy(fontSize = 14.sp),
                        color = Forge.colors.textPrimary,
                    )
                }
                DropdownMenu(rirMenu, { rirMenu = false }, containerColor = Forge.colors.surface3) {
                    (0..4).forEach { r ->
                        DropdownMenuItem(
                            text = { Text(if (r == 4) "4+" else "$r", color = Forge.colors.textPrimary) },
                            onClick = { rirMenu = false; onRir(r) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(6.dp))
        val interaction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .pressScale(interaction, pressedScale = 0.88f)
                .size(TICK_COL - 4.dp, 34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (done) Forge.colors.accent else Forge.colors.surface3)
                .clickable(interactionSource = interaction, indication = null) {
                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                if (done) "Undo set" else "Complete set",
                tint = if (done) Forge.colors.onAccent else Forge.colors.textTertiary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun CellField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    done: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = NumericStyle.copy(
            fontSize = 15.sp,
            color = Forge.colors.textPrimary,
            textAlign = TextAlign.Center,
        ),
        singleLine = true,
        cursorBrush = SolidColor(Forge.colors.accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        decorationBox = { inner ->
            Box(
                modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (done) Color.Transparent else Forge.colors.surface2)
                    .border(
                        1.dp,
                        if (done) Color.Transparent else Forge.colors.outline,
                        RoundedCornerShape(9.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) { inner() }
        },
    )
}

// ---------------------------------------------------------------- finishing

@Composable
private fun PrBanner(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Forge.colors.surface2)
            .border(1.dp, Forge.colors.pr, RoundedCornerShape(18.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.EmojiEvents, null, tint = Forge.colors.pr, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("PR", style = MaterialTheme.typography.labelSmall, color = Forge.colors.pr)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Forge.colors.textPrimary, maxLines = 2)
        }
    }
}

@Composable
private fun FinishSheet(
    muscles: List<Muscle>,
    deviations: List<Deviation>,
    onDismiss: () -> Unit,
    onConfirm: (Map<Muscle, Triple<Pump?, Soreness?, Workload?>>, Boolean) -> Unit,
) {
    val answers = remember { mutableStateMapOf<Muscle, Triple<Pump?, Soreness?, Workload?>>() }
    var keepChanges by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Forge.colors.surface1) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 620.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (deviations.isNotEmpty()) {
                item {
                    Text(
                        "You changed the plan",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Forge.colors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    deviations.forEach {
                        Text(
                            "· ${it.describe}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Forge.colors.textSecondary,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    KeepChoice(keepChanges) { keepChanges = it }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Weights are not part of this - those are learned from what you logged either way.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Forge.colors.textTertiary,
                    )
                    Spacer(Modifier.height(22.dp))
                }
            }

            item {
                Text("How did that go?", style = MaterialTheme.typography.headlineMedium, color = Forge.colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "This is what decides next week's set counts. Skip it and volume just creeps up by one set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Forge.colors.textSecondary,
                )
                Spacer(Modifier.height(16.dp))
            }

            items(muscles.size) { i ->
                val muscle = muscles[i]
                val current = answers[muscle] ?: Triple(null, null, null)
                Column(Modifier.padding(bottom = 18.dp)) {
                    Text(muscle.display, style = MaterialTheme.typography.titleMedium, color = Forge.colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    ChoiceRow("Pump", Pump.entries.map { it.display }, Pump.entries.indexOfFirst { it == current.first }) { idx ->
                        answers[muscle] = current.copy(first = Pump.entries[idx])
                    }
                    Spacer(Modifier.height(6.dp))
                    ChoiceRow("Soreness", Soreness.entries.map { it.display }, Soreness.entries.indexOfFirst { it == current.second }) { idx ->
                        answers[muscle] = (answers[muscle] ?: current).copy(second = Soreness.entries[idx])
                    }
                    Spacer(Modifier.height(6.dp))
                    ChoiceRow("Workload", Workload.entries.map { it.display }, Workload.entries.indexOfFirst { it == current.third }) { idx ->
                        answers[muscle] = (answers[muscle] ?: current).copy(third = Workload.entries[idx])
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                PrimaryButton("Finish workout", Modifier.fillMaxWidth()) { onConfirm(answers.toMap(), keepChanges) }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Skip feedback", Modifier.fillMaxWidth()) { onConfirm(emptyMap(), keepChanges) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Once, at the end: was that a one-off, or how the block should look from now on? */
@Composable
private fun KeepChoice(keep: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        listOf(false to "Just this session", true to "Update my program").forEach { (value, label) ->
            val selected = keep == value
            Box(
                Modifier
                    .weight(1f)
                    .padding(end = if (value) 0.dp else 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Forge.colors.accent else Forge.colors.surface3)
                    .clickable { onChange(value) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Forge.colors.onAccent else Forge.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Forge.colors.textTertiary)
        Spacer(Modifier.height(4.dp))
        FlowRowCompat {
            options.forEachIndexed { i, opt ->
                val selected = i == selectedIndex
                Box(
                    Modifier
                        .padding(end = 6.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Forge.colors.accent else Forge.colors.surface3)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        opt,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Forge.colors.onAccent else Forge.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Minimal wrap layout; avoids depending on an experimental FlowRow signature. */
@Composable
private fun FlowRowCompat(content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        var x = 0
        var y = 0
        var rowHeight = 0
        val positions = mutableListOf<Pair<Int, Int>>()
        placeables.forEach { p ->
            if (x + p.width > constraints.maxWidth) {
                x = 0
                y += rowHeight
                rowHeight = 0
            }
            positions += x to y
            x += p.width
            rowHeight = maxOf(rowHeight, p.height)
        }
        layout(constraints.maxWidth, y + rowHeight) {
            placeables.forEachIndexed { i, p -> p.place(positions[i].first, positions[i].second) }
        }
    }
}
