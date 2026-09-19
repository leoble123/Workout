package com.leo.forge.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.forge.data.db.entity.PlannedExerciseEntity
import com.leo.forge.domain.model.Muscle
import com.leo.forge.domain.model.SplitType
import com.leo.forge.ui.components.*
import com.leo.forge.ui.theme.Forge

/**
 * The plan: which gym you are training at, and the block built from it.
 *
 * Exercise choice is edited exercise by exercise rather than by ticking equipment categories.
 * "I have machines" was never the question - "swap this for that one" is.
 */
@Composable
fun PlanScreen(
    onOpenGym: () -> Unit,
    vm: ProgramViewModel = viewModel(factory = ProgramViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val gym by vm.gymSummary.collectAsStateWithLifecycle()
    var building by remember { mutableStateOf(false) }
    var addToDayId by remember { mutableStateOf<Long?>(null) }
    var swapTarget by remember { mutableStateOf<PlannedExerciseEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(Forge.colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Plan", style = MaterialTheme.typography.headlineLarge, color = Forge.colors.textPrimary)
        }

        item {
            ForgeCard(Modifier.fillMaxWidth(), onClick = onOpenGym) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TRAINING AT", style = MaterialTheme.typography.labelSmall, color = Forge.colors.textTertiary)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            gym.name ?: "No gym set up",
                            style = MaterialTheme.typography.titleMedium,
                            color = Forge.colors.textPrimary,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${gym.availableCount} exercises available" +
                                (gym.units?.let { " · loads in ${it.display}" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = Forge.colors.textTertiary,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Forge.colors.textTertiary)
                }
            }
        }

        if (building || state.meso == null) {
            item {
                BuilderCard(
                    isBuilding = state.building,
                    gymName = gym.name,
                    availableCount = gym.availableCount,
                    gym = gym,
                    onToggleEquipment = vm::setEquipment,
                    onBuild = { split, days, weeks, emphasis ->
                        vm.build("${split.display} block", split, days, weeks, emphasis) { building = false }
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
                            "${meso.split.display} · ${meso.daysPerWeek} days/week · week " +
                                "${meso.currentWeek + 1} of ${meso.totalWeeks} (last one deloads)",
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
                        Spacer(Modifier.height(8.dp))
                        day.exercises.sortedBy { it.orderIndex }.forEach { pe ->
                            PlannedRow(
                                name = state.library[pe.exerciseId]?.name ?: pe.exerciseId,
                                detail = "${pe.baseSets}×${pe.repLow}-${pe.repHigh}",
                                onSwap = { swapTarget = pe },
                                onRemove = { vm.removePlanned(pe) },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Add exercise",
                            style = MaterialTheme.typography.labelSmall,
                            color = Forge.colors.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { addToDayId = day.day.id }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }

    addToDayId?.let { dayId ->
        val day = state.days.firstOrNull { it.day.id == dayId }
        ExercisePickerSheet(
            library = gym.library,
            availableIds = gym.availableIds,
            title = "Add to ${day?.day?.label ?: "day"}",
            confirmLabel = "Add",
            multiSelect = true,
            onDismiss = { addToDayId = null },
            onConfirm = { ids ->
                var next = day?.exercises?.size ?: 0
                ids.forEach { vm.addToDay(dayId, it, next++) }
                addToDayId = null
            },
        )
    }

    swapTarget?.let { target ->
        ExercisePickerSheet(
            library = gym.library,
            availableIds = gym.availableIds,
            title = "Swap for",
            confirmLabel = "Swap",
            multiSelect = false,
            restrictToMuscleOf = state.library[target.exerciseId],
            onDismiss = { swapTarget = null },
            onConfirm = { ids ->
                ids.firstOrNull()?.let { vm.swapPlanned(target, it) }
                swapTarget = null
            },
        )
    }
}

@Composable
private fun PlannedRow(name: String, detail: String, onSwap: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = Forge.colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Forge.colors.textSecondary)
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.MoreVert, "More", tint = Forge.colors.textTertiary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(menu, { menu = false }, containerColor = Forge.colors.surface3) {
                DropdownMenuItem(
                    text = { Text("Swap exercise", color = Forge.colors.textPrimary) },
                    onClick = { menu = false; onSwap() },
                )
                DropdownMenuItem(
                    text = { Text("Remove", color = Forge.colors.danger) },
                    onClick = { menu = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun BuilderCard(
    isBuilding: Boolean,
    gymName: String?,
    availableCount: Int,
    gym: GymSummary,
    onToggleEquipment: (com.leo.forge.domain.model.Equipment, Boolean) -> Unit,
    onBuild: (SplitType, Int, Int, Set<Muscle>) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var split by remember { mutableStateOf(SplitType.PUSH_PULL_LEGS) }
    var days by remember { mutableIntStateOf(5) }
    var weeks by remember { mutableIntStateOf(5) }
    val emphasis = remember { mutableStateListOf<Muscle>() }

    ForgeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("Build a block", style = MaterialTheme.typography.headlineMedium, color = Forge.colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Volume starts at your MEV and ramps toward MRV, then deloads. Exercises are picked " +
                    "from ${gymName ?: "your gym"} - $availableCount available - and you can swap any " +
                    "of them afterwards.",
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
            Wrap { split.daysPerWeek.forEach { d -> SelectChip("$d", d == days) { days = d } } }

            Spacer(Modifier.height(14.dp))
            SectionHeader("Block length")
            Wrap { listOf(4, 5, 6).forEach { w -> SelectChip("$w weeks", w == weeks) { weeks = w } } }

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

            Spacer(Modifier.height(18.dp))
            SectionHeader("What you have")
            Text(
                "Tick exactly what is on the floor. This is the gym profile, so it sticks - " +
                    "and nothing gets generated that you cannot actually perform.",
                style = MaterialTheme.typography.bodySmall,
                color = Forge.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            com.leo.forge.domain.model.EquipmentCategory.entries.forEach { category ->
                Text(
                    category.display.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.textTertiary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                Wrap {
                    com.leo.forge.domain.model.Equipment.entries
                        .filter { it.category == category }
                        .forEach { eq ->
                            val on = gym.has(eq)
                            SelectChip(eq.display, on) { onToggleEquipment(eq, !on) }
                        }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "$availableCount exercises available with this kit.",
                style = MaterialTheme.typography.labelSmall,
                color = if (availableCount < 20) Forge.colors.warn else Forge.colors.good,
            )

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                if (isBuilding) "Building…" else "Generate program",
                Modifier.fillMaxWidth(),
                enabled = !isBuilding,
            ) { onBuild(split, days, weeks, emphasis.toSet()) }
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
