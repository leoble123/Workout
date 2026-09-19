package com.leo.forge.ui.history

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
import com.leo.forge.ui.components.*
import com.leo.forge.ui.theme.Forge
import com.leo.forge.ui.theme.NumericStyle
import com.leo.forge.ui.theme.loadWithUnit
import com.leo.forge.ui.theme.tonnageText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

private fun Long.toLocalDate() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** The session feed. Rendered inside Progress, so it carries no page title of its own. */
@Composable
fun HistoryList(
    onOpenSession: (Long) -> Unit,
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().background(Forge.colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.sessions.isEmpty()) {
            item {
                ForgeCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        "Nothing logged yet",
                        "Finish a session, or bring your Hevy history over from Settings, and it shows up here.",
                    )
                }
            }
        }

        var lastMonth: String? = null
        state.sessions.forEach { session ->
            val month = session.startedAt.toLocalDate().format(monthFormat)
            if (month != lastMonth) {
                lastMonth = month
                item(key = "h_$month") { SectionHeader(month) }
            }
            item(key = session.id) {
                ForgeCard(Modifier.fillMaxWidth(), onClick = { onOpenSession(session.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = Forge.colors.textPrimary,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                buildString {
                                    append(session.startedAt.toLocalDate().format(dayFormat))
                                    val minutes = session.finishedAt?.let { (it - session.startedAt) / 60000L }
                                    if (minutes != null && minutes > 0) append(" · ${minutes}m")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Forge.colors.textTertiary,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val (vol, volUnit) = tonnageText(session.totalVolumeKg)
                            Text(
                                "$vol$volUnit",
                                style = NumericStyle.copy(fontSize = 15.sp),
                                color = Forge.colors.textPrimary,
                            )
                            Text(
                                "${session.totalSets} sets",
                                style = MaterialTheme.typography.labelSmall,
                                color = Forge.colors.textTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    vm: SessionDetailViewModel = viewModel(factory = SessionDetailViewModel.factory(sessionId)),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().background(Forge.colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                SecondaryButton("Back", onClick = onBack)
                Spacer(Modifier.height(14.dp))
                Text(
                    state.session?.label ?: "Session",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Forge.colors.textPrimary,
                )
                state.session?.let { s ->
                    Text(
                        s.startedAt.toLocalDate().format(dayFormat) +
                            " · ${s.totalSets} sets · " + tonnageText(s.totalVolumeKg).let { "${it.first}${it.second}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Forge.colors.textTertiary,
                    )
                }
            }
        }

        state.byExercise.forEach { group ->
            item(key = group.exerciseId) {
                ForgeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium, color = Forge.colors.textPrimary)
                        Spacer(Modifier.height(8.dp))
                        group.sets.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${s.setIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Forge.colors.textTertiary,
                                    modifier = Modifier.width(22.dp),
                                )
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
