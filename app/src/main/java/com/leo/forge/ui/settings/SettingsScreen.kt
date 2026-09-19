package com.leo.forge.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.forge.data.importer.HevyCsvImporter
import com.leo.forge.ui.components.*
import com.leo.forge.ui.theme.Forge

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Hevy exports are sometimes handed over as octet-stream, so the picker stays permissive
    // rather than hiding the user's own file behind a MIME filter.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importHevy(context.contentResolver, uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Forge.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, color = Forge.colors.textPrimary)

        SectionHeader("During a workout")
        ForgeCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                ToggleRow(
                    "Start rest automatically",
                    "The timer starts the moment a set is logged.",
                    settings.autoStartRest, vm::setAutoStartRest,
                )
                ToggleRow(
                    "Follow me down the list",
                    "Scrolls to the next set so you never hunt for it.",
                    settings.autoAdvance, vm::setAutoAdvance,
                )
                ToggleRow(
                    "Track reps in reserve",
                    "Rate how close each set was to failure. Sharpens the weight suggestions; " +
                        "leave it off and progression runs on reps alone.",
                    settings.showRir, vm::setShowRir,
                )
                ToggleRow("Haptics", "A tap you can feel without looking.", settings.haptics, vm::setHaptics)
                ToggleRow(
                    "Keep the screen on",
                    "Costs battery. Off by default - the rest alarm fires whether the screen is on or not.",
                    settings.keepScreenOn, vm::setKeepScreenOn,
                )
            }
        }

        SectionHeader("Display")
        ForgeCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                ToggleRow(
                    "True black",
                    "On an OLED panel a black pixel is an off pixel, so this genuinely draws less power.",
                    settings.oledBlack, vm::setOled,
                )
            }
        }

        SectionHeader("Your history")
        ForgeCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Import from Hevy", style = MaterialTheme.typography.titleMedium, color = Forge.colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "In Hevy: Settings → Export Data → CSV. Every session, set and RPE comes across, " +
                        "PRs get recalculated, and re-importing the same file will not duplicate anything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Forge.colors.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    if (importState is ImportUiState.Running) "Importing…" else "Choose CSV file",
                    Modifier.fillMaxWidth(),
                    enabled = importState !is ImportUiState.Running,
                ) { picker.launch(arrayOf("*/*")) }

                when (val s = importState) {
                    is ImportUiState.Done -> {
                        Spacer(Modifier.height(14.dp))
                        ImportReport(s.report)
                    }
                    is ImportUiState.Failed -> {
                        Spacer(Modifier.height(14.dp))
                        Text(s.message, style = MaterialTheme.typography.bodySmall, color = Forge.colors.danger)
                    }
                    else -> Unit
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Forge keeps everything on this device. No account, no sync, no network calls.",
            style = MaterialTheme.typography.labelSmall,
            color = Forge.colors.textTertiary,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ImportReport(report: HevyCsvImporter.Report) {
    Column {
        if (!report.ok) {
            Text(report.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Forge.colors.danger)
            return
        }
        Text(
            "${report.sessionsImported} workouts and ${report.setsImported} sets imported.",
            style = MaterialTheme.typography.bodyMedium,
            color = Forge.colors.good,
        )
        val notes = buildList {
            if (report.newExercisesCreated > 0) add("${report.newExercisesCreated} exercises added to your library")
            if (report.sessionsSkippedAsDuplicate > 0) add("${report.sessionsSkippedAsDuplicate} already-imported workouts skipped")
            if (report.rowsSkipped > 0) add("${report.rowsSkipped} rows had no reps (cardio, planks) and were skipped")
        } + report.warnings
        notes.forEach {
            Spacer(Modifier.height(4.dp))
            Text("· $it", style = MaterialTheme.typography.bodySmall, color = Forge.colors.textSecondary)
        }
        if (report.needsMuscleReview.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Confirm the muscle group for: ${report.needsMuscleReview.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = Forge.colors.warn,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Forge.colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Forge.colors.textTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Forge.colors.onAccent,
                checkedTrackColor = Forge.colors.accent,
                uncheckedThumbColor = Forge.colors.textTertiary,
                uncheckedTrackColor = Forge.colors.surface3,
            ),
        )
    }
}
