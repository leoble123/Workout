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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.forge.data.prefs.ForgeSettings
import com.leo.forge.data.db.entity.SessionExerciseEntity
import com.leo.forge.data.repo.ExercisePlanUi
import com.leo.forge.domain.model.*
import com.leo.forge.domain.progression.SetTarget
import com.leo.forge.timer.RestState
import com.leo.forge.ui.components.*
import com.leo.forge.ui.components.FOOTER_HEIGHT
import com.leo.forge.ui.theme.Forge
import com.leo.forge.ui.theme.LocalHapticsEnabled
import com.leo.forge.ui.theme.Motion
import com.leo.forge.ui.theme.NumericStyle
import com.leo.forge.ui.theme.loadWithUnit
import com.leo.forge.ui.theme.unitLabel
import com.leo.forge.ui.theme.pressScale

@Composable
fun SessionScreen(
    settings: ForgeSettings,
    onDone: () -> Unit,
    vm: SessionViewModel = viewModel(factory = SessionViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showFinish by remember { mutableStateOf(false) }
    var showAbandon by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var helpFor by remember { mutableStateOf<com.leo.forge.data.db.entity.ExerciseEntity?>(null) }
    var swapTarget by remember { mutableStateOf<SessionExerciseEntity?>(null) }
    val picker by vm.picker.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Nothing logged means nothing to confirm; a dialog there is just a speed bump.
    BackHandler { if (state.doneSets == 0) onDone() else showAbandon = true }

    // Honour the keep-awake setting only while a workout is actually open, and always
    // release it on the way out.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Keep the settings-driven RIR preference in sync with the view model.
    LaunchedEffect(settings.showRir) { vm.setShowRir(settings.showRir) }

    // A running countdown always names the set it is counting down to, even after a jump.
    val resting = state.rest is RestState.Running
    LaunchedEffect(state.upNextLabel, resting) {
        if (resting) vm.syncRestLabel(state.upNextLabel)
    }

    // Keep the current set in view without the user chasing it.
    val focus = state.focus
    LaunchedEffect(focus, settings.autoAdvance) {
        if (!settings.autoAdvance || focus == null) return@LaunchedEffect
        val target = (focus.first + 1).coerceAtMost(state.plans.size)
        // Scrolling something already on screen is the twitch that makes a list feel unsteady.
        val alreadyVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == target }
        if (!alreadyVisible) listState.animateScrollToItem(target)
    }

    Column(Modifier.fillMaxSize().background(Forge.colors.background)) {
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SessionHeader(
                    state = state,
                    onFinish = { showFinish = true },
                    onAbandon = { showAbandon = true },
                )
            }

            itemsIndexed(state.plans, key = { _, p -> p.id }) { index, plan ->
                ExerciseBlock(
                    plan = plan,
                    state = state,
                    isCurrentExercise = focus?.first == index,
                    onStepWeight = { si, dir -> vm.stepWeight(plan.exercise.id, si, dir) },
                    onStepReps = { si, dir -> vm.stepReps(plan.exercise.id, si, dir) },
                    onWeight = { si, v -> vm.updateWeight(plan.exercise.id, si, v) },
                    onReps = { si, v -> vm.updateReps(plan.exercise.id, si, v) },
                    onRir = { si, r -> vm.setRir(plan.exercise.id, si, r) },
                    onLog = { si ->
                        val next = nextLabelAfter(state, plan, si)
                        vm.logSet(plan, si, settings.autoStartRest, next, settings.showRir)
                    },
                    onUndo = { vm.undo(it) },
                    onFocusSet = { si -> vm.focusSet(plan.exercise.id, si) },
                    onHelp = { helpFor = plan.exercise },
                    onAddSet = { plan.sessionExercise?.let { vm.changeSets(it, 1) } },
                    onRemoveSet = { plan.sessionExercise?.let { vm.changeSets(it, -1) } },
                    onSwap = { swapTarget = plan.sessionExercise },
                    onRemove = { plan.sessionExercise?.let { vm.removeExercise(it) } },
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                SecondaryButton(
                    "Add exercise",
                    Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Add,
                ) { showPicker = true }
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

        // One footer that always states exactly one true thing: resting, next up, or done.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LaunchedEffect(state.prBanner) {
                if (state.prBanner != null) {
                    kotlinx.coroutines.delay(5_000)
                    vm.clearPr()
                }
            }
            AnimatedVisibility(
                visible = state.prBanner != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                PrBanner(state.prBanner.orEmpty()) { vm.clearPr() }
            }

            val rest = state.rest
            if (rest is RestState.Running) {
                RestBar(running = rest, onNudge = vm::nudgeRest, onSkip = vm::skipRest)
            } else {
                UpNextBar(
                    state = state,
                    onFinish = { showFinish = true },
                    onGoToCurrent = {
                        focus?.let { scope.launch { listState.animateScrollToItem(it.first + 1) } }
                    },
                )
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
            onDismiss = { showFinish = false },
            onConfirm = { feedback ->
                showFinish = false
                vm.finish(feedback, onDone)
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
                    if (state.doneSets == 0) "Nothing is logged yet, so the session will just be discarded."
                    else "${state.doneSets} sets are logged. You can come back to it, or end it here.",
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

private fun nextLabelAfter(state: SessionUiState, plan: ExercisePlanUi, setIndex: Int): String {
    val remainingHere = plan.prescription.targets.count { it.setIndex > setIndex }
    if (remainingHere > 0) return "${plan.exercise.name} · set ${setIndex + 2}"
    val idx = state.plans.indexOfFirst { it.id == plan.id }
    return state.plans.getOrNull(idx + 1)?.exercise?.name ?: "Last set done"
}

@Composable
private fun SessionHeader(state: SessionUiState, onFinish: () -> Unit, onAbandon: () -> Unit) {
    val started = state.session?.startedAt ?: System.currentTimeMillis()
    val elapsed by rememberElapsedSeconds(started)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.meso?.let { "Week ${state.session?.weekIndex?.plus(1) ?: 1} of ${it.totalWeeks}" } ?: "Freestyle",
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.textTertiary,
                )
                Text(
                    state.session?.label ?: "Workout",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Forge.colors.textPrimary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatClock(elapsed), style = NumericStyle.copy(fontSize = 20.sp), color = Forge.colors.textPrimary)
                Text(
                    "${state.doneSets}/${state.totalSets} sets",
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Finish", Modifier.weight(1f), onClick = onFinish)
            SecondaryButton("Leave", icon = Icons.Rounded.Close, onClick = onAbandon)
        }
    }
}

/**
 * The footer when nothing is resting: what you are on, or that you are finished.
 *
 * It exists so there is exactly one place to look for "what now". Between the rest countdown
 * and this, that question always has a visible answer, and neither can contradict the list
 * because both read the same focus.
 */
@Composable
private fun UpNextBar(state: SessionUiState, onFinish: () -> Unit, onGoToCurrent: () -> Unit) {
    val focus = state.focus
    val complete = state.isComplete
    val shape = RoundedCornerShape(24.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .height(FOOTER_HEIGHT)
            .clip(shape)
            .background(Forge.colors.surface2)
            .border(1.dp, if (complete) Forge.colors.accent else Forge.colors.outline, shape)
            .then(if (focus != null) Modifier.clickable { onGoToCurrent() } else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (complete) Forge.colors.accent else Forge.colors.surface3),
            contentAlignment = Alignment.Center,
        ) {
            if (complete) {
                Icon(Icons.Rounded.Check, null, tint = Forge.colors.onAccent, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    "${(focus?.second ?: 0) + 1}",
                    style = NumericStyle.copy(fontSize = 15.sp),
                    color = Forge.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                when {
                    complete -> "Session done"
                    state.plans.isEmpty() -> "Nothing added yet"
                    else -> "UP NEXT"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Forge.colors.textTertiary,
            )
            Text(
                when {
                    complete -> "${state.doneSets} sets logged"
                    state.plans.isEmpty() -> "Add an exercise to begin"
                    else -> state.upNextLabel
                },
                style = MaterialTheme.typography.titleMedium,
                color = Forge.colors.textPrimary,
                maxLines = 1,
            )
        }

        if (complete) {
            Spacer(Modifier.width(10.dp))
            PrimaryButton("Finish", onClick = onFinish)
        } else {
            focus?.let { slot ->
                state.targetAt(slot)?.let { target ->
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (target.weightKg > 0) "${loadWithUnit(target.weightKg)} × ${target.reps}"
                        else "× ${target.reps}",
                        style = NumericStyle.copy(fontSize = 14.sp),
                        color = Forge.colors.accent,
                    )
                }
            }
        }
    }
}

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
private fun ExerciseBlock(
    plan: ExercisePlanUi,
    state: SessionUiState,
    isCurrentExercise: Boolean,
    onStepWeight: (Int, Int) -> Unit,
    onStepReps: (Int, Int) -> Unit,
    onWeight: (Int, String) -> Unit,
    onReps: (Int, String) -> Unit,
    onRir: (Int, Int) -> Unit,
    onLog: (Int) -> Unit,
    onUndo: (com.leo.forge.data.db.entity.SetLogEntity) -> Unit,
    onFocusSet: (Int) -> Unit,
    onHelp: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
) {
    var showWhy by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val targets = plan.prescription.targets

    // A finished exercise folds away. Everything you are not doing is noise.
    val finished = targets.isNotEmpty() && targets.all { state.loggedSet(plan.exercise.id, it.setIndex) != null }
    var expanded by remember(plan.id) { mutableStateOf(false) }
    val showSets = !finished || expanded || isCurrentExercise

    ForgeCard(
        Modifier.fillMaxWidth(),
        color = if (isCurrentExercise) Forge.colors.surface2 else Forge.colors.surface1,
        onClick = if (finished && !isCurrentExercise) ({ expanded = !expanded }) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (finished) {
                    Box(
                        Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(Forge.colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = Forge.colors.onAccent, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        plan.exercise.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (finished && !isCurrentExercise) Forge.colors.textSecondary else Forge.colors.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (finished && !showSets) {
                        val done = targets.mapNotNull { state.loggedSet(plan.exercise.id, it.setIndex) }
                        val best = done.maxByOrNull { it.weightKg * it.reps }
                        Text(
                            "${done.size} sets" + (best?.let { " · top ${loadWithUnit(it.weightKg)} × ${it.reps}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = Forge.colors.textTertiary,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(plan.exercise.primaryMuscle.display)
                            Chip("${plan.repLow}-${plan.repHigh} reps")
                            if (state.showRir) Chip("RIR ${targets.firstOrNull()?.targetRir ?: 2}")
                        }
                    }
                }
                if (!finished || showSets) HelpButton(onClick = onHelp)
                if (!finished || showSets) IconButton(onClick = { showWhy = !showWhy }) {
                    Icon(
                        Icons.Rounded.Info,
                        "Why this weight",
                        tint = if (showWhy) Forge.colors.accent else Forge.colors.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Rounded.MoreVert, "More",
                            tint = Forge.colors.textTertiary, modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = Forge.colors.surface3,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add a set", color = Forge.colors.textPrimary) },
                            onClick = { showMenu = false; onAddSet() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove a set", color = Forge.colors.textPrimary) },
                            onClick = { showMenu = false; onRemoveSet() },
                        )
                        DropdownMenuItem(
                            text = { Text("Swap exercise", color = Forge.colors.textPrimary) },
                            onClick = { showMenu = false; onSwap() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove exercise", color = Forge.colors.danger) },
                            onClick = { showMenu = false; onRemove() },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showWhy, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        targets.firstOrNull()?.rationale.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Forge.colors.textSecondary,
                    )
                }
            }

            if (showSets) {
            Spacer(Modifier.height(12.dp))

            targets.forEach { target ->
                val logged = state.loggedSet(plan.exercise.id, target.setIndex)
                val isActive = state.focus == (state.plans.indexOfFirst { it.id == plan.id } to target.setIndex)
                SetRow(
                    target = target,
                    entry = state.entries[state.key(plan.exercise.id, target.setIndex)],
                    logged = logged,
                    isActive = isActive,
                    onStepWeight = { d -> onStepWeight(target.setIndex, d) },
                    onStepReps = { d -> onStepReps(target.setIndex, d) },
                    onWeight = { v -> onWeight(target.setIndex, v) },
                    onReps = { v -> onReps(target.setIndex, v) },
                    onRir = { r -> onRir(target.setIndex, r) },
                    onLog = { onLog(target.setIndex) },
                    onUndo = { logged?.let(onUndo) },
                    onFocus = { onFocusSet(target.setIndex) },
                    showRir = state.showRir,
                    showJumpHint = isCurrentExercise,
                )
            }
            }
        }
    }
}

@Composable
private fun SetRow(
    target: SetTarget,
    entry: SetEntry?,
    logged: com.leo.forge.data.db.entity.SetLogEntity?,
    isActive: Boolean,
    onStepWeight: (Int) -> Unit,
    onStepReps: (Int) -> Unit,
    onWeight: (String) -> Unit,
    onReps: (String) -> Unit,
    onRir: (Int) -> Unit,
    onLog: () -> Unit,
    onUndo: () -> Unit,
    onFocus: () -> Unit,
    showRir: Boolean,
    showJumpHint: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current

    when {
        logged != null -> Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onUndo() }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(Forge.colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = Forge.colors.onAccent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "${loadWithUnit(logged.weightKg)} × ${logged.reps}" + (logged.rir?.let { "  @ $it RIR" } ?: ""),
                style = NumericStyle.copy(fontSize = 16.sp),
                color = Forge.colors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (logged.isPr) Chip("PR", color = Forge.colors.pr, background = Forge.colors.surface3)
            else Text("tap to undo", style = MaterialTheme.typography.labelSmall, color = Forge.colors.textTertiary)
        }

        isActive -> Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Forge.colors.surface3)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SET ${target.setIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.accent,
                )
                Spacer(Modifier.weight(1f))
                if (target.isEstimateOnly) {
                    Text(
                        "pick a starting load",
                        style = MaterialTheme.typography.labelSmall,
                        color = Forge.colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ValueStepper(
                    value = entry?.weight.orEmpty(),
                    label = unitLabel(),
                    onValueChange = onWeight,
                    onStep = onStepWeight,
                    decimal = true,
                )
                Spacer(Modifier.width(6.dp))
                ValueStepper(
                    value = entry?.reps.orEmpty(),
                    label = "reps",
                    onValueChange = onReps,
                    onStep = onStepReps,
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "LOG SET",
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.textTertiary,
                )
                Spacer(Modifier.height(4.dp))
                LogButton {
                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLog()
                }
                }
            }
            if (showRir) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RIR", style = MaterialTheme.typography.labelSmall, color = Forge.colors.textTertiary)
                Spacer(Modifier.width(8.dp))
                (0..4).forEach { r ->
                    val selected = entry?.rir == r
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Forge.colors.accent else Forge.colors.surface1)
                            .clickable { onRir(r) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (r == 4) "4+" else "$r",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Forge.colors.onAccent else Forge.colors.textSecondary,
                        )
                    }
                }
            }
            }
        }

        // Upcoming: tap to jump straight to it, for sets done out of order.
        else -> Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onFocus() }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, Forge.colors.outline, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${target.setIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.textTertiary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (target.weightKg > 0) "${loadWithUnit(target.weightKg)} × ${target.reps}" else "— × ${target.reps}",
                style = NumericStyle.copy(fontSize = 15.sp),
                color = Forge.colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            if (showJumpHint) {
                Text(
                    "tap to jump",
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun LogButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(interaction, pressedScale = 0.9f)
            .size(62.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Forge.colors.accent)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Forge.colors.onAccent),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, "Log set", tint = Forge.colors.onAccent, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun FinishSheet(
    muscles: List<Muscle>,
    onDismiss: () -> Unit,
    onConfirm: (Map<Muscle, Triple<Pump?, Soreness?, Workload?>>) -> Unit,
) {
    val answers = remember { mutableStateMapOf<Muscle, Triple<Pump?, Soreness?, Workload?>>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Forge.colors.surface1,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 620.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
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
                PrimaryButton("Finish workout", Modifier.fillMaxWidth()) { onConfirm(answers.toMap()) }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Skip feedback", Modifier.fillMaxWidth()) { onConfirm(emptyMap()) }
                Spacer(Modifier.height(24.dp))
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
    Layout(content = content) { measurables, constraints ->
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
