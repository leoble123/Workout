package com.leo.forge.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.SplitType
import com.leo.forge.ui.components.*
import com.leo.forge.ui.theme.Forge

@Composable
fun ProgramScreen(vm: ProgramViewModel = viewModel(factory = ProgramViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    var building by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(Forge.colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Program", style = MaterialTheme.typography.headlineLarge, color = Forge.colors.textPrimary)
        }

        if (building || state.meso == null) {
            item {
                BuilderCard(
                    isBuilding = state.building,
                    onBuild = { name, split, days, weeks, emphasis, equipment ->
                        vm.build(name, split, days, weeks, emphasis, equipment) { building = false }
                    },
                    onCancel = if (state.meso != null) ({ building = false }) else null,
                )
            }
        } else {
            val meso = state.meso!!
            item {
                ForgeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(meso.name, style = MaterialTheme.typography.headlineMedium, color = Forge.colors.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${meso.split.display} · ${meso.daysPerWeek} days/week · ${meso.totalWeeks} weeks " +
                                "(last one is a deload)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Forge.colors.textSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        SecondaryButton("Build a new block", Modifier.fillMaxWidth()) { building = true }
                    }
                }
            }

            items(state.days.size) { i ->
                val day = state.days[i]
                ForgeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(day.day.label, style = MaterialTheme.typography.titleLarge, color = Forge.colors.textPrimary)
                        Spacer(Modifier.height(10.dp))
                        day.exercises.sortedBy { it.orderIndex }.forEach { pe ->
                            val ex = state.library[pe.exerciseId]
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    ex?.name ?: pe.exerciseId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Forge.colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                Text(
                                    "${pe.baseSets}×${pe.repLow}-${pe.repHigh}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Forge.colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuilderCard(
    isBuilding: Boolean,
    onBuild: (String, SplitType, Int, Int, Set<Muscle>, Set<Equipment>) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var split by remember { mutableStateOf(SplitType.PUSH_PULL_LEGS) }
    var days by remember { mutableIntStateOf(5) }
    var weeks by remember { mutableIntStateOf(5) }
    val emphasis = remember { mutableStateListOf<Muscle>() }
    val equipment = remember { mutableStateListOf<Equipment>().apply { addAll(Equipment.entries) } }

    ForgeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("Build a block", style = MaterialTheme.typography.headlineMedium, color = Forge.colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Volume starts at your MEV and ramps toward MRV, then deloads. Sessions are filled in " +
                    "from your history as you go.",
                style = MaterialTheme.typography.bodySmall,
                color = Forge.colors.textSecondary,
            )

            Spacer(Modifier.height(18.dp))
            SectionHeader("Split")
            Wrap {
                SplitType.entries.forEach { s ->
                    SelectChip(s.display, s == split) {
                        split = s
                        days = days.coerceIn(s.daysPerWeek.first, s.daysPerWeek.last)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader("Days per week")
            Wrap {
                (split.daysPerWeek).forEach { d -> SelectChip("$d", d == days) { days = d } }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader("Block length")
            Wrap {
                listOf(4, 5, 6).forEach { w -> SelectChip("$w weeks", w == weeks) { weeks = w } }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader("Emphasis (optional)")
            Wrap {
                listOf(
                    Muscle.CHEST, Muscle.LATS, Muscle.SIDE_DELTS, Muscle.BICEPS,
                    Muscle.TRICEPS, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES,
                ).forEach { m ->
                    SelectChip(m.display, m in emphasis) {
                        if (m in emphasis) emphasis.remove(m) else emphasis.add(m)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader("Equipment you have")
            Wrap {
                Equipment.entries.forEach { e ->
                    SelectChip(e.display, e in equipment) {
                        if (e in equipment) equipment.remove(e) else equipment.add(e)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                if (isBuilding) "Building…" else "Generate program",
                Modifier.fillMaxWidth(),
                enabled = !isBuilding,
            ) {
                onBuild("${split.display} block", split, days, weeks, emphasis.toSet(), equipment.toSet())
            }
            if (onCancel != null) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Cancel", Modifier.fillMaxWidth(), onClick = onCancel)
            }
        }
    }
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Forge.colors.accent else Forge.colors.surface3)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Forge.colors.onAccent else Forge.colors.textSecondary,
        )
    }
}

/** Small wrapping row; keeps chip groups from clipping on narrow screens. */
@Composable
private fun Wrap(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        var x = 0; var y = 0; var rowHeight = 0
        val pos = mutableListOf<Pair<Int, Int>>()
        placeables.forEach { p ->
            if (x + p.width > constraints.maxWidth) { x = 0; y += rowHeight; rowHeight = 0 }
            pos += x to y
            x += p.width
            rowHeight = maxOf(rowHeight, p.height)
        }
        layout(constraints.maxWidth, y + rowHeight) {
            placeables.forEachIndexed { i, p -> p.place(pos[i].first, pos[i].second) }
        }
    }
}
