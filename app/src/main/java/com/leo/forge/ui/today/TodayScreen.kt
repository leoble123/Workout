package com.leo.forge.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.forge.data.repo.ExercisePlanUi
import com.leo.forge.domain.progression.ProgressionEngine
import com.leo.forge.domain.volume.VolumeLandmarks
import com.leo.forge.ui.components.*
import com.leo.forge.ui.theme.Forge
import com.leo.forge.ui.theme.NumericStyle
import com.leo.forge.ui.theme.loadWithUnit
import com.leo.forge.ui.theme.tonnageText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun TodayScreen(
    onStartWorkout: () -> Unit,
    onOpenProgram: () -> Unit,
    vm: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LazyColumn(
        Modifier.fillMaxSize().background(Forge.colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.textTertiary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    state.activeSession?.let { "Workout in progress" } ?: "Today",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Forge.colors.textPrimary,
                )
            }
        }

        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("Loading", style = MaterialTheme.typography.bodyMedium, color = Forge.colors.textTertiary)
                }
            }
        } else {
            // There is always a way to start lifting. Setting up a block is optional.
            item {
                if (state.hasPlannedSession || state.activeSession != null) {
                    NextSessionCard(
                        state = state,
                        onStart = {
                            scope.launch {
                                vm.startWorkout() ?: vm.startEmptyWorkout()
                                onStartWorkout()
                            }
                        },
                    )
                } else {
                    FreestyleCard(
                        blockComplete = state.blockComplete,
                        blockEmpty = state.blockEmpty,
                        mesoName = state.meso?.name,
                        onStart = {
                            scope.launch {
                                vm.startEmptyWorkout()
                                onStartWorkout()
                            }
                        },
                        onOpenProgram = onOpenProgram,
                    )
                }
            }

            // Even with a session planned, an off-script day should not need a detour.
            if (state.hasPlannedSession && state.activeSession == null) {
                item {
                    SecondaryButton("Start an empty workout instead", Modifier.fillMaxWidth()) {
                        scope.launch {
                            vm.startEmptyWorkout()
                            onStartWorkout()
                        }
                    }
                }
            }
        }

        if (state.meso != null && !state.blockComplete) {
            item { SectionHeader("This week") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Sessions", state.sessionsThisWeek.toString(), Modifier.weight(1f))
                    val (volume, volumeUnit) = tonnageText(state.tonnageThisWeek)
                    StatTile("Tonnage", volume, Modifier.weight(1f), unit = volumeUnit)
                }
            }
        }

        if (state.muscleVolume.isNotEmpty()) {
            item { SectionHeader("Weekly volume vs your landmarks") }
            item {
                ForgeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        state.muscleVolume
                            .sortedByDescending { it.sets }
                            .take(10)
                            .forEach { mv ->
                                VolumeMeter(
                                    label = mv.muscle.display,
                                    sets = mv.sets,
                                    landmarks = VolumeLandmarks.of(mv.muscle),
                                )
                            }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ticks mark MEV and MRV - the floor a block starts on and the ceiling it ends at.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Forge.colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

/** Shown when there is no planned session: starting a workout must still be one tap. */
@Composable
private fun FreestyleCard(
    blockComplete: Boolean,
    blockEmpty: Boolean,
    mesoName: String?,
    onStart: () -> Unit,
    onOpenProgram: () -> Unit,
) {
    ForgeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                when {
                    blockComplete -> "Block finished"
                    blockEmpty -> "Your block has no days yet"
                    else -> "Ready when you are"
                },
                style = MaterialTheme.typography.displayMedium,
                color = Forge.colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    blockComplete -> "Every week of ${mesoName ?: "the block"} is done, deload included. " +
                        "Start an empty session, or plan the next block."
                    blockEmpty -> "Nothing was generated for it - most likely your gym has too little " +
                        "kit selected. Train freely now and fix the block when you have a minute."
                    else -> "Add exercises as you go. Forge fills in the weights from the last time " +
                        "you did each one, so you never have to remember."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Forge.colors.textSecondary,
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Start workout", Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow, onClick = onStart)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                if (blockComplete) "Plan the next block" else "Build an automated program",
                Modifier.fillMaxWidth(),
                onClick = onOpenProgram,
            )
        }
    }
}

@Composable
private fun NextSessionCard(state: TodayState, onStart: () -> Unit) {
    val meso = state.meso ?: return
    val resuming = state.activeSession != null
    val deload = ProgressionEngine.isDeloadWeek(state.weekIndex, meso.totalWeeks)

    ForgeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(
                    "Week ${state.weekIndex + 1} of ${meso.totalWeeks}",
                    color = Forge.colors.textSecondary,
                )
                Spacer(Modifier.width(8.dp))
                if (deload) {
                    Chip("Deload", color = Forge.colors.cool, background = Forge.colors.surface3)
                } else {
                    Chip("RIR ${ProgressionEngine.rirForWeek(state.weekIndex, meso.totalWeeks)}")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                state.nextDay?.day?.label ?: "Session",
                style = MaterialTheme.typography.displayMedium,
                color = Forge.colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.preview.size} exercises · ${state.preview.sumOf { it.prescription.targets.size }} sets",
                style = MaterialTheme.typography.bodyMedium,
                color = Forge.colors.textSecondary,
            )

            Spacer(Modifier.height(16.dp))
            state.preview.take(6).forEach { plan -> PreviewRow(plan) }
            if (state.preview.size > 6) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "+ ${state.preview.size - 6} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.textTertiary,
                )
            }

            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                if (resuming) "Resume workout" else "Start workout",
                Modifier.fillMaxWidth(),
                icon = Icons.Rounded.PlayArrow,
                onClick = onStart,
            )
        }
    }
}

@Composable
private fun PreviewRow(plan: ExercisePlanUi) {
    val first = plan.prescription.targets.firstOrNull()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Forge.colors.accent)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            plan.exercise.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Forge.colors.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            buildString {
                append("${plan.prescription.targets.size}×")
                append(first?.reps ?: plan.repLow)
                if (first != null && first.weightKg > 0.0) append(" · ${loadWithUnit(first.weightKg)}")
            },
            style = NumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = Forge.colors.textSecondary,
        )
    }
}
