package com.leo.forge.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.forge.domain.model.Load
import com.leo.forge.ui.components.*
import com.leo.forge.ui.theme.Forge
import com.leo.forge.ui.theme.LocalUnits
import com.leo.forge.ui.theme.NumericStyle
import com.leo.forge.ui.theme.loadWithUnit
import com.leo.forge.ui.theme.tonnageText
import com.leo.forge.ui.theme.unitLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun ExerciseDetailScreen(
    exerciseId: String,
    onBack: () -> Unit,
    vm: ExerciseDetailViewModel = viewModel(factory = ExerciseDetailViewModel.factory(exerciseId)),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().background(Forge.colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                SecondaryButton("Back", onClick = onBack)
                Spacer(Modifier.height(14.dp))
                Text(
                    state.exercise?.name ?: "Exercise",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Forge.colors.textPrimary,
                )
                state.exercise?.let {
                    Text(
                        "${it.primaryMuscle.display} · ${it.equipment.display}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Forge.colors.textTertiary,
                    )
                }
            }
        }

        state.exercise?.let { ex ->
            com.leo.forge.data.seed.ExerciseGuide.forExercise(ex.id)?.let { cues ->
                item { SectionHeader("How to do it") }
                item {
                    ForgeCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("SET UP", style = MaterialTheme.typography.labelSmall, color = Forge.colors.accent)
                            Spacer(Modifier.height(4.dp))
                            Text(cues.setup, style = MaterialTheme.typography.bodySmall, color = Forge.colors.textSecondary)
                            Spacer(Modifier.height(12.dp))
                            Text("THE REP", style = MaterialTheme.typography.labelSmall, color = Forge.colors.accent)
                            Spacer(Modifier.height(4.dp))
                            Text(cues.execution, style = MaterialTheme.typography.bodySmall, color = Forge.colors.textSecondary)
                            Spacer(Modifier.height(12.dp))
                            Text("COMMON MISTAKE", style = MaterialTheme.typography.labelSmall, color = Forge.colors.accent)
                            Spacer(Modifier.height(4.dp))
                            Text(cues.mistake, style = MaterialTheme.typography.bodySmall, color = Forge.colors.warn)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Personal bests") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val top = state.topSet
                StatTile(
                    "Best e1RM",
                    if (top != null) Load.format(Load.toDisplay(top.e1rmKg, LocalUnits.current)) else "—",
                    Modifier.weight(1f),
                    unit = unitLabel(),
                    valueColor = Forge.colors.pr,
                )
                StatTile(
                    "Heaviest set",
                    if (state.bestWeight > 0) Load.format(Load.toDisplay(state.bestWeight, LocalUnits.current)) else "—",
                    Modifier.weight(1f),
                    unit = unitLabel(),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Sets logged", state.totalSets.toString(), Modifier.weight(1f))
                val (vol, volUnit) = tonnageText(state.totalVolume)
                StatTile("Total volume", vol, Modifier.weight(1f), unit = volUnit)
            }
        }
        state.topSet?.let { top ->
            item {
                ForgeCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Best set", style = MaterialTheme.typography.labelSmall, color = Forge.colors.textTertiary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${loadWithUnit(top.weightKg)} × ${top.reps}" + (top.rir?.let { " @ $it RIR" } ?: ""),
                                style = NumericStyle.copy(fontSize = 18.sp),
                                color = Forge.colors.textPrimary,
                            )
                        }
                        Text(
                            lastTrainedText(top.completedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = Forge.colors.textTertiary,
                        )
                    }
                }
            }
        }

        item { SectionHeader("Estimated 1RM over time") }
        item {
            ForgeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    E1rmChart(
                        points = state.series.map { it.copy(y = Load.toDisplay(it.y, LocalUnits.current)) },
                        modifier = Modifier.fillMaxWidth(),
                        valueSuffix = unitLabel(),
                    )
                }
            }
        }

        item { SectionHeader("Every set") }
        val grouped = state.sets.groupBy {
            Instant.ofEpochMilli(it.completedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toList().sortedByDescending { it.first }

        grouped.forEach { (date, sets) ->
            item(key = "d_$date") {
                ForgeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            date.format(dayFormat),
                            style = MaterialTheme.typography.labelSmall,
                            color = Forge.colors.textTertiary,
                        )
                        Spacer(Modifier.height(8.dp))
                        sets.sortedBy { it.setIndex }.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${loadWithUnit(s.weightKg)} × ${s.reps}" + (s.rir?.let { "  @ $it RIR" } ?: ""),
                                    style = NumericStyle.copy(fontSize = 15.sp),
                                    color = Forge.colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (s.isPr) Chip("PR", color = Forge.colors.pr, background = Forge.colors.surface3)
                            }
                        }
                    }
                }
            }
        }
    }
}
