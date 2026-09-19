package com.leo.forge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.domain.model.BodyPart
import com.leo.forge.ui.theme.Forge

/**
 * Picking an exercise, the way you actually do it: type a few letters, or tap a body part.
 *
 * Kit you do not own is not hidden by default but it is not offered either - it sorts below
 * everything available and says why, because silently omitting an exercise looks like the
 * app simply does not have it.
 */
@Composable
fun ExercisePickerSheet(
    library: List<ExerciseEntity>,
    availableIds: Set<String>?,
    title: String,
    confirmLabel: String,
    multiSelect: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    restrictToMuscleOf: ExerciseEntity? = null,
) {
    var query by remember { mutableStateOf("") }
    var bodyPart by remember {
        mutableStateOf(restrictToMuscleOf?.let { BodyPart.of(it.primaryMuscle) })
    }
    var gymOnly by remember { mutableStateOf(availableIds != null) }
    val selected = remember { mutableStateListOf<String>() }
    var helpFor by remember { mutableStateOf<ExerciseEntity?>(null) }

    val results = remember(query, bodyPart, gymOnly, library, availableIds) {
        val q = query.trim().lowercase()
        library.asSequence()
            .filter { !it.archived }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .filter { bodyPart == null || BodyPart.of(it.primaryMuscle) == bodyPart }
            .filter { !gymOnly || availableIds == null || it.id in availableIds }
            .sortedWith(
                compareBy(
                    { if (availableIds == null || it.id in availableIds) 0 else 1 },
                    { it.name },
                )
            )
            .take(300)
            .toList()
    }

    helpFor?.let { ExerciseHelpSheet(it) { helpFor = null } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Forge.colors.surface1) {
        Column(Modifier.fillMaxWidth().heightIn(max = 700.dp)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Forge.colors.textPrimary)
                Spacer(Modifier.height(12.dp))
                ForgeTextField(query, { query = it }, null, placeholder = "Search exercises")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChipSmall("All", bodyPart == null) { bodyPart = null }
                    BodyPart.entries.forEach { bp ->
                        FilterChipSmall(bp.display, bodyPart == bp) { bodyPart = if (bodyPart == bp) null else bp }
                    }
                }
                if (availableIds != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = gymOnly,
                            onCheckedChange = { gymOnly = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Forge.colors.accent,
                                checkmarkColor = Forge.colors.onAccent,
                                uncheckedColor = Forge.colors.outline,
                            ),
                        )
                        Text(
                            "Only what my gym has",
                            style = MaterialTheme.typography.bodySmall,
                            color = Forge.colors.textSecondary,
                        )
                    }
                }
            }

            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(horizontal = 20.dp)) {
                if (results.isEmpty()) {
                    item {
                        Text(
                            "Nothing matches. Clear the filters, or untick \"only what my gym has\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = Forge.colors.textTertiary,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(results.size) { i ->
                    val exercise = results[i]
                    val unavailable = availableIds != null && exercise.id !in availableIds
                    val isSelected = exercise.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (multiSelect) {
                                    if (isSelected) selected.remove(exercise.id) else selected.add(exercise.id)
                                } else {
                                    onConfirm(listOf(exercise.id))
                                }
                            }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                exercise.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (unavailable) Forge.colors.textTertiary else Forge.colors.textPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (unavailable) "${exercise.primaryMuscle.display} · not at your gym"
                                else "${exercise.primaryMuscle.display} · ${exercise.equipment.display}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (unavailable) Forge.colors.warn else Forge.colors.textTertiary,
                            )
                        }
                        HelpButton(onClick = { helpFor = exercise })
                        if (multiSelect && isSelected) {
                            Box(
                                Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Forge.colors.accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Check, null, tint = Forge.colors.onAccent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (multiSelect) {
                Column(Modifier.padding(20.dp)) {
                    PrimaryButton(
                        if (selected.isEmpty()) confirmLabel else "$confirmLabel (${selected.size})",
                        Modifier.fillMaxWidth(),
                        enabled = selected.isNotEmpty(),
                    ) { onConfirm(selected.toList()) }
                }
            } else {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun FilterChipSmall(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 6.dp)
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
