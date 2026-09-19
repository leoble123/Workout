package com.leo.forge.ui.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.forge.data.db.entity.GymStationEntity
import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.EquipmentCategory
import com.leo.forge.domain.model.Units
import com.leo.forge.ui.components.*
import com.leo.forge.ui.theme.Forge

@Composable
fun GymScreen(vm: GymViewModel = viewModel(factory = GymViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editingStation by remember { mutableStateOf<GymStationEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(Forge.colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("My gym", style = MaterialTheme.typography.headlineLarge, color = Forge.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "What is actually on the floor. The generator only ever picks from this.",
                style = MaterialTheme.typography.bodySmall,
                color = Forge.colors.textSecondary,
            )
        }

        val gym = state.gym
        if (gym == null) {
            item {
                ForgeCard(Modifier.fillMaxWidth()) {
                    EmptyState("No gym set up", "Add one and the program will be built around it.") {
                        PrimaryButton("Use a full commercial gym") { vm.addGymFromPreset(true, Units.KG) }
                    }
                }
            }
            return@LazyColumn
        }

        // --- units: the single most consequential setting when you travel
        item {
            ForgeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    ForgeTextField(
                        value = gym.name,
                        onValueChange = vm::rename,
                        label = "Gym name",
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("PLATES AND STACKS ARE MARKED IN", style = MaterialTheme.typography.labelSmall, color = Forge.colors.textTertiary)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Units.entries.forEach { u ->
                            val selected = gym.units == u
                            Box(
                                Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) Forge.colors.accent else Forge.colors.surface3)
                                    .clickable { vm.setUnits(u) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    u.display,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) Forge.colors.onAccent else Forge.colors.textSecondary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This is not just a display setting: it decides the size of every load jump " +
                            "the app suggests, so suggestions always land on plates this gym actually has.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Forge.colors.textTertiary,
                    )
                    Spacer(Modifier.height(14.dp))
                    ForgeTextField(
                        value = gym.notes.orEmpty(),
                        onValueChange = vm::setNotes,
                        label = "Notes about this gym",
                        placeholder = "Anything you want to remember - opening hours, which rack wobbles…",
                        singleLine = false,
                    )
                }
            }
        }

        // --- coverage summary
        item {
            val uncovered = state.uncoveredMuscles
            ForgeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "${state.availableCount} exercises available here",
                        style = MaterialTheme.typography.titleMedium,
                        color = Forge.colors.textPrimary,
                    )
                    if (uncovered.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Nothing here trains: ${uncovered.joinToString { it.display }}. " +
                                "Those muscles will be left out of generated programs until you add " +
                                "equipment or a station that covers them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Forge.colors.warn,
                        )
                    }
                }
            }
        }

        // --- stations
        item {
            SectionHeader("Machines & stations") {
                Text(
                    "Add",
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.accent,
                    modifier = Modifier.clickable { vm.addStation("New station") },
                )
            }
        }

        items(state.stations.size) { i ->
            val station = state.stations[i]
            val count = state.exerciseIdsFor(station.id).size
            ForgeCard(Modifier.fillMaxWidth(), onClick = { editingStation = station }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(station.name, style = MaterialTheme.typography.titleMedium, color = Forge.colors.textPrimary)
                        if (!station.brand.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Chip(station.brand)
                        }
                        if (!station.notes.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                station.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = Forge.colors.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "$count exercises",
                            style = MaterialTheme.typography.labelSmall,
                            color = Forge.colors.textTertiary,
                        )
                    }
                }
            }
        }

        // --- equipment categories
        item { SectionHeader("Equipment") }
        EquipmentCategory.entries.forEach { category ->
            item(key = "cat_${category.name}") {
                Text(
                    category.display.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.textTertiary,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
            val inCategory = Equipment.entries.filter { it.category == category }
            items(inCategory.size, key = { "eq_${category.name}_$it" }) { i ->
                val eq = inCategory[i]
                EquipmentRow(
                    equipment = eq,
                    available = state.isAvailable(eq),
                    detail = state.detailFor(eq),
                    onToggle = { vm.setEquipment(eq, it) },
                    onDetail = { vm.setEquipmentDetail(eq, it) },
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Station names are yours to set - the app has no way to verify a model number, " +
                    "so it does not pretend to. What matters is the exercise list on each one.",
                style = MaterialTheme.typography.labelSmall,
                color = Forge.colors.textTertiary,
            )
        }
    }

    editingStation?.let { station ->
        StationEditorSheet(
            station = station,
            state = state,
            onDismiss = { editingStation = null },
            onUpdate = vm::updateStation,
            onToggleExercise = { id, present -> vm.toggleStationExercise(station.id, id, present) },
            onDelete = {
                vm.deleteStation(station)
                editingStation = null
            },
        )
    }
}

@Composable
private fun EquipmentRow(
    equipment: Equipment,
    available: Boolean,
    detail: String,
    onToggle: (Boolean) -> Unit,
    onDetail: (String) -> Unit,
) {
    ForgeCard(Modifier.fillMaxWidth(), border = false, color = Forge.colors.surface1) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    equipment.display,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (available) Forge.colors.textPrimary else Forge.colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = available,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Forge.colors.onAccent,
                        checkedTrackColor = Forge.colors.accent,
                        uncheckedThumbColor = Forge.colors.textTertiary,
                        uncheckedTrackColor = Forge.colors.surface3,
                    ),
                )
            }
            if (available) {
                Spacer(Modifier.height(6.dp))
                ForgeTextField(
                    value = detail,
                    onValueChange = onDetail,
                    label = null,
                    placeholder = "Make, model, limits - e.g. \"dumbbells stop at 40\"",
                )
            }
        }
    }
}

@Composable
private fun StationEditorSheet(
    station: GymStationEntity,
    state: GymState,
    onDismiss: () -> Unit,
    onUpdate: (GymStationEntity) -> Unit,
    onToggleExercise: (String, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(station.id) { mutableStateOf(station.name) }
    var brand by remember(station.id) { mutableStateOf(station.brand.orEmpty()) }
    var notes by remember(station.id) { mutableStateOf(station.notes.orEmpty()) }
    var query by remember { mutableStateOf("") }

    val selected = state.exerciseIdsFor(station.id)
    val matches = remember(query, state.library) {
        val q = query.trim().lowercase()
        state.library
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .sortedBy { it.name }
            .take(if (q.isEmpty()) 400 else 60)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Forge.colors.surface1) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item {
                Text("Station", style = MaterialTheme.typography.headlineMedium, color = Forge.colors.textPrimary)
                Spacer(Modifier.height(12.dp))
                ForgeTextField(name, { name = it; onUpdate(station.copy(name = it)) }, "Name")
                Spacer(Modifier.height(10.dp))
                ForgeTextField(
                    brand, { brand = it; onUpdate(station.copy(brand = it.takeIf(String::isNotBlank))) },
                    "Brand", placeholder = "e.g. Genesis",
                )
                Spacer(Modifier.height(10.dp))
                ForgeTextField(
                    notes, { notes = it; onUpdate(station.copy(notes = it.takeIf(String::isNotBlank))) },
                    "Notes", placeholder = "Attachments, quirks, where it is on the floor…",
                    singleLine = false,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "WHAT THIS STATION CAN DO (${selected.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Forge.colors.textTertiary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Anything ticked here counts as available, even if its equipment category is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Forge.colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                ForgeTextField(query, { query = it }, null, placeholder = "Search exercises")
                Spacer(Modifier.height(8.dp))
            }

            items(matches.size) { i ->
                val exercise = matches[i]
                val isOn = exercise.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExercise(exercise.id, !isOn) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            exercise.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOn) Forge.colors.textPrimary else Forge.colors.textSecondary,
                        )
                        Text(
                            "${exercise.primaryMuscle.display} · ${exercise.equipment.display}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Forge.colors.textTertiary,
                        )
                    }
                    Checkbox(
                        checked = isOn,
                        onCheckedChange = { onToggleExercise(exercise.id, it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Forge.colors.accent,
                            checkmarkColor = Forge.colors.onAccent,
                            uncheckedColor = Forge.colors.outline,
                        ),
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SecondaryButton("Delete this station", Modifier.fillMaxWidth(), icon = Icons.Rounded.Delete, tint = Forge.colors.danger, onClick = onDelete)
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Done", Modifier.fillMaxWidth(), onClick = onDismiss)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
