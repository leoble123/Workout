package com.leo.forge.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.forge.domain.volume.VolumeLandmarks
import com.leo.forge.ui.components.*
import com.leo.forge.ui.theme.Forge
import kotlin.math.roundToInt

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().background(Forge.colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Stats", style = MaterialTheme.typography.headlineLarge, color = Forge.colors.textPrimary)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Sessions", state.sessions.toString(), Modifier.weight(1f))
                StatTile(
                    "Tonnage",
                    (state.tonnage / 1000.0).let {
                        if (it >= 10) it.roundToInt().toString() else String.format("%.1f", it)
                    },
                    Modifier.weight(1f),
                    unit = "t",
                )
            }
        }

        item { SectionHeader("Estimated 1RM") }
        item {
            ForgeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (state.trackable.isEmpty()) {
                        Text(
                            "Log a few sets and strength trends appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Forge.colors.textTertiary,
                        )
                    } else {
                        Text(
                            state.selected?.name ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = Forge.colors.textPrimary,
                        )
                        Spacer(Modifier.height(12.dp))
                        // Single series, so no legend: the title above names it.
                        E1rmChart(points = state.series, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            state.trackable.take(20).forEach { ex ->
                                val selected = ex.id == state.selected?.id
                                Box(
                                    Modifier
                                        .padding(end = 6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selected) Forge.colors.accent else Forge.colors.surface3)
                                        .clickable { vm.select(ex.id) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                ) {
                                    Text(
                                        ex.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) Forge.colors.onAccent else Forge.colors.textSecondary,
                                    )
                                }
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
                    }
                }
            }
        }
    }
}
