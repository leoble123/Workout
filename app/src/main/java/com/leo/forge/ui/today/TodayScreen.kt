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

        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("Loading", style = MaterialTheme.typography.bodyMedium, color = Forge.colors.textTertiary)
                }
            }

            state.meso == null -> item {
                ForgeCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        title = "No block running",
                        body = "Forge builds the whole mesocycle for you - split, exercise choice, set counts, " +
                            "deload - then decides each session's loads from how the last one went.",
                        action = { PrimaryButton("Build my program", onClick = onOpenProgram) },
                    )
                }
            }

            state.blockComplete -> item {
                ForgeCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        title = "Block complete",
                        body = "Every week of ${state.meso?.name} is done, deload included. " +
                            "Start the next one and it will pick up from your current strength.",
                        action = { PrimaryButton("Plan the next block", onClick = onOpenProgram) },
                    )
                }
            }

            else -> item {
                NextSessionCard(
                    state = state,
                    onStart = {
                        scope.launch {
                            vm.startWorkout()
                            onStartWorkout()
                        }
                    },
                )
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
                append(first?.reps ?: plan.planned.repLow)
                if (first != null && first.weightKg > 0.0) append(" · ${loadWithUnit(first.weightKg)}")
            },
            style = NumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = Forge.colors.textSecondary,
        )
    }
}
