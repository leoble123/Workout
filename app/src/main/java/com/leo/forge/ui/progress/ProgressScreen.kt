package com.leo.forge.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leo.forge.domain.model.BodyPart
import com.leo.forge.ui.components.*
import com.leo.forge.ui.history.HistoryList
import com.leo.forge.ui.stats.ChartsContent
import com.leo.forge.ui.theme.Forge
import com.leo.forge.ui.theme.NumericStyle
import com.leo.forge.ui.theme.loadWithUnit
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private enum class Section(val label: String) { RECORDS("Records"), HISTORY("History"), CHARTS("Charts") }

/**
 * One place for everything that already happened.
 *
 * Records comes first because "what did I lift last time / what is my best" is the question
 * people actually open this tab to answer, and it should never mean scrolling a feed.
 */
@Composable
fun ProgressScreen(
    onOpenSession: (Long) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(Section.RECORDS) }

    Column(Modifier.fillMaxSize().background(Forge.colors.background)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
            Text("Progress", style = MaterialTheme.typography.headlineLarge, color = Forge.colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            SegmentedRow(
                options = Section.entries.map { it.label },
                selectedIndex = Section.entries.indexOf(section),
                onSelect = { section = Section.entries[it] },
            )
            Spacer(Modifier.height(8.dp))
        }
        when (section) {
            Section.RECORDS -> RecordsTab(onOpenExercise)
            Section.HISTORY -> HistoryList(onOpenSession)
            Section.CHARTS -> ChartsContent()
        }
    }
}

@Composable
fun SegmentedRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Forge.colors.surface2)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) Forge.colors.accent else Forge.colors.surface2)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Forge.colors.onAccent else Forge.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RecordsTab(
    onOpenExercise: (String) -> Unit,
    vm: RecordsViewModel = viewModel(factory = RecordsViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val rows = state.filtered

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ForgeTextField(state.query, vm::search, null, placeholder = "Search your exercises")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilterPill("All", state.bodyPart == null) { vm.filter(null) }
                BodyPart.entries.forEach { bp ->
                    FilterPill(bp.display, state.bodyPart == bp) {
                        vm.filter(if (state.bodyPart == bp) null else bp)
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                ForgeCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        if (state.query.isBlank()) "No records yet" else "Nothing matches",
                        if (state.query.isBlank())
                            "Log a workout, or bring your Hevy history over from Settings, and your bests land here."
                        else "Try a different search.",
                    )
                }
            }
        }

        items(rows.size, key = { rows[it].exercise.id }) { i ->
            val row = rows[i]
            ForgeCard(Modifier.fillMaxWidth(), onClick = { onOpenExercise(row.exercise.id) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Forge.colors.textPrimary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${row.exercise.primaryMuscle.display} · ${row.record.totalSets} sets · " +
                                lastTrainedText(row.record.lastTrained),
                            style = MaterialTheme.typography.labelSmall,
                            color = Forge.colors.textTertiary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            loadWithUnit(row.record.bestE1rm),
                            style = NumericStyle.copy(fontSize = 16.sp),
                            color = Forge.colors.pr,
                        )
                        Text(
                            "best e1RM",
                            style = MaterialTheme.typography.labelSmall,
                            color = Forge.colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
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

internal fun lastTrainedText(epochMillis: Long): String {
    if (epochMillis <= 0) return "never"
    val days = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate(),
        java.time.LocalDate.now(),
    )
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < 7L -> "$days days ago"
        days < 30L -> "${days / 7} wk ago"
        else -> "${days / 30} mo ago"
    }
}
