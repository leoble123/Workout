package com.leo.forge.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

/**
 * The week at a glance: how much work, and where it landed.
 *
 * Per-exercise strength trends live on the exercise itself, reached from Records - putting
 * them here as well meant two routes to the same chart and a longer scroll to the thing you
 * came for.
 */
@Composable
fun ChartsContent(vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Sessions", state.sessions.toString(), Modifier.weight(1f))
                val (volume, volumeUnit) = tonnageText(state.tonnage)
                StatTile("Tonnage", volume, Modifier.weight(1f), unit = volumeUnit)
            }
        }

        if (state.muscleVolume.isEmpty()) {
            item {
                ForgeCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        "Nothing logged this week",
                        "Once you train, your weekly volume per muscle shows up here against your landmarks.",
                    )
                }
            }
        } else {
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
