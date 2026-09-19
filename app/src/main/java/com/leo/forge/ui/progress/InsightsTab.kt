package com.leo.forge.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.forge.domain.volume.VolumeLandmarks
import com.leo.forge.ui.components.*
import com.leo.forge.ui.theme.Forge
import com.leo.forge.ui.theme.tonnageText
import com.leo.forge.ui.theme.unitLabel

/**
 * What changed, and what it means.
 *
 * The write-up is generated on demand rather than continuously: a summary that rewrites
 * itself while you are reading it is worse than one you asked for.
 */
@Composable
fun InsightsTab(vm: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Sessions this week", state.sessions.toString(), Modifier.weight(1f))
                val (volume, volumeUnit) = tonnageText(state.tonnage)
                StatTile("Tonnage", volume, Modifier.weight(1f), unit = volumeUnit)
            }
        }

        item { SectionHeader("Strength change") }
        item {
            ForgeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    MoversChart(
                        rows = state.trends.map { Triple(it.name, it.deltaKg, it.deltaPct) },
                        unitLabel = unitLabel(),
                    )
                }
            }
        }

        item { SectionHeader("Summary") }
        item {
            ForgeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val summary = state.summary
                    if (summary == null) {
                        Text(
                            "Reads the last 90 days of your log and writes up what actually moved - " +
                                "which lifts are climbing, which have stalled, and whether your volume " +
                                "is going anywhere.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Forge.colors.textSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(
                            if (state.generating) "Reading your log…" else "Generate summary",
                            Modifier.fillMaxWidth(),
                            enabled = state.canGenerate && !state.generating,
                            icon = Icons.Rounded.AutoAwesome,
                        ) { vm.generate() }
                        if (!state.canGenerate) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Nothing logged in the last 90 days yet.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Forge.colors.textTertiary,
                            )
                        }
                    } else {
                        AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                            Column {
                                Text(
                                    summary.headline,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Forge.colors.textPrimary,
                                )
                                Spacer(Modifier.height(12.dp))
                                summary.paragraphs.forEach { paragraph ->
                                    Text(
                                        paragraph,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Forge.colors.textSecondary,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                                SecondaryButton(
                                    if (state.generating) "Reading your log…" else "Regenerate",
                                    Modifier.fillMaxWidth(),
                                ) { vm.generate() }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Worked out on your phone from the sets you logged - nothing is sent " +
                                        "anywhere, and no figure here is invented.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Forge.colors.textTertiary,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.muscleVolume.isNotEmpty()) {
            item { SectionHeader("Volume this week") }
            item {
                ForgeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        state.muscleVolume.sortedByDescending { it.sets }.forEach { mv ->
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
