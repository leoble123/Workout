package com.leo.forge.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.leo.forge.data.db.entity.ExerciseEntity
import com.leo.forge.data.seed.ExerciseGuide
import com.leo.forge.ui.theme.Forge

/** The "?" next to an exercise. */
@Composable
fun HelpButton(onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Forge.colors.textTertiary) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.HelpOutline, "How to do this", tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * How to do the movement: set up, execute, and the one mistake worth knowing about.
 *
 * Deliberately three short paragraphs. Mid-set is not the moment for an article, and an
 * exercise you cannot skim in ten seconds is one you will stop checking.
 */
@Composable
fun ExerciseHelpSheet(exercise: ExerciseEntity, onDismiss: () -> Unit) {
    val cues = ExerciseGuide.forExercise(exercise.id)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Forge.colors.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.headlineMedium, color = Forge.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(exercise.primaryMuscle.display)
                    append(" · ")
                    append(exercise.equipment.display)
                    if (exercise.requiresAlso.isNotEmpty()) {
                        append(" · needs ")
                        append(exercise.requiresAlso.joinToString(" + ") { it.display.lowercase() })
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = Forge.colors.textTertiary,
            )
            Spacer(Modifier.height(20.dp))

            if (cues == null) {
                Text(
                    "No notes for this one yet - it came from an import or you added it yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Forge.colors.textSecondary,
                )
            } else {
                CueBlock("Set up", cues.setup, Forge.colors.textSecondary)
                Spacer(Modifier.height(16.dp))
                CueBlock("The rep", cues.execution, Forge.colors.textSecondary)
                Spacer(Modifier.height(16.dp))
                CueBlock("Common mistake", cues.mistake, Forge.colors.warn)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Target range ${exercise.repLow}-${exercise.repHigh} reps.",
                style = MaterialTheme.typography.labelSmall,
                color = Forge.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun CueBlock(label: String, body: String, bodyColor: Color) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Forge.colors.accent)
        Spacer(Modifier.height(5.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = bodyColor)
    }
}
