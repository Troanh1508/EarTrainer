package com.example.eartrainer.ui.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eartrainer.R
import com.example.eartrainer.data.history.SessionSummary
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.music.Interval
import com.example.eartrainer.ui.theme.EarTrainerTheme
import java.text.DateFormat
import java.util.Date
import kotlin.math.absoluteValue

@Composable
fun ProgressRoute(viewModel: ProgressViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressScreen(
        state = state,
        onExerciseSelected = viewModel::onExerciseSelected,
        onFilterSelected = viewModel::onFilterSelected,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    state: ProgressUiState?,
    onExerciseSelected: (ExerciseKind) -> Unit,
    onFilterSelected: (ProgressFilter) -> Unit,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.progress_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state == null -> Surface(Modifier.padding(innerPadding).fillMaxSize()) {}
            !state.hasHistory -> EmptyProgress(Modifier.padding(innerPadding))
            else -> ProgressContent(state, onExerciseSelected, onFilterSelected, innerPadding)
        }
    }
}

@Composable
private fun EmptyProgress(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.progress_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProgressContent(
    state: ProgressUiState,
    onExerciseSelected: (ExerciseKind) -> Unit,
    onFilterSelected: (ProgressFilter) -> Unit,
    innerPadding: PaddingValues,
) {
    val cardModifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp)
    val itemModifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp)
    Column(Modifier.padding(top = innerPadding.calculateTopPadding()).fillMaxSize()) {
        ExerciseTabs(state.exercise, onExerciseSelected)
        LazyColumn(
            contentPadding = PaddingValues(top = 12.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Exercises with a single view have nothing to filter.
            if (state.filterOptions.size > 1) {
                item { FilterRow(state, onFilterSelected, Modifier.widthIn(max = 560.dp).fillMaxWidth()) }
            }
            item { OverviewCard(state, cardModifier) }
            item { TrendCard(state.trend, cardModifier) }
            item { AccuracyCard(state, cardModifier) }
            item { SectionTitle(stringResource(R.string.progress_recent_sessions), itemModifier.padding(top = 12.dp)) }
            items(state.recentSessions, key = { it.sessionId }) { SessionRow(it, itemModifier) }
        }
    }
}

@Composable
private fun ExerciseTabs(selected: ExerciseKind, onSelected: (ExerciseKind) -> Unit) {
    // Scrollable: four exercise names do not fit side by side on a phone.
    PrimaryScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 16.dp) {
        ExerciseKind.entries.forEach { exercise ->
            Tab(
                selected = exercise == selected,
                onClick = { onSelected(exercise) },
                text = { Text(exerciseName(exercise)) },
            )
        }
    }
}

@Composable
private fun exerciseName(exercise: ExerciseKind) = stringResource(
    when (exercise) {
        ExerciseKind.INTERVALS -> R.string.progress_exercise_intervals
        ExerciseKind.CHORDS -> R.string.progress_exercise_chords
        ExerciseKind.SCALE_DEGREES -> R.string.progress_exercise_degrees
        ExerciseKind.PROGRESSIONS -> R.string.progress_exercise_progressions
        ExerciseKind.DICTATION -> R.string.progress_exercise_dictation
        ExerciseKind.RHYTHM -> R.string.progress_exercise_rhythm
    },
)

@Composable
private fun filterLabel(filter: ProgressFilter): String = when (filter) {
    is ProgressFilter.IntervalStyleFilter -> filter.style?.displayName ?: stringResource(R.string.progress_filter_all)
    is ProgressFilter.ChordTargetFilter -> filter.target.displayName
    is ProgressFilter.ModeFilter -> filter.mode?.displayName ?: stringResource(R.string.progress_filter_all_modes)
    is ProgressFilter.ProgressionModeFilter -> filter.mode.displayName
    is ProgressFilter.DictationModeFilter -> filter.mode.displayName
    ProgressFilter.RhythmFilter -> stringResource(R.string.progress_filter_all_rhythms)
}

@Composable
private fun accuracyTitle(filter: ProgressFilter): String = stringResource(
    when (filter) {
        is ProgressFilter.IntervalStyleFilter -> R.string.progress_by_interval
        is ProgressFilter.ChordTargetFilter -> when (filter.target) {
            ChordTarget.QUALITY -> R.string.progress_by_quality
            ChordTarget.INVERSION -> R.string.progress_by_inversion
        }
        is ProgressFilter.ModeFilter -> R.string.progress_by_degree
        is ProgressFilter.ProgressionModeFilter -> R.string.progress_by_chord
        is ProgressFilter.DictationModeFilter -> R.string.progress_by_note
        ProgressFilter.RhythmFilter -> R.string.progress_by_rhythm
    },
)

@Composable
private fun FilterRow(state: ProgressUiState, onSelected: (ProgressFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        state.filterOptions.forEach { filter ->
            FilterChip(
                selected = filter == state.filter,
                onClick = { onSelected(filter) },
                label = { Text(filterLabel(filter)) },
            )
        }
    }
}

@Composable
private fun ChartCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** A card title with a short note at the end of the line. */
@Composable
private fun CardHeader(title: String, note: String?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun OverviewCard(state: ProgressUiState, modifier: Modifier = Modifier) {
    // Progressions, dictations and rhythms are scored chord by chord, note by note or figure by figure.
    val (summary, unit) = when (state.exercise) {
        ExerciseKind.PROGRESSIONS -> R.plurals.progress_overall_chords to R.plurals.progress_unit_chords
        ExerciseKind.DICTATION -> R.plurals.progress_overall_notes to R.plurals.progress_unit_notes
        ExerciseKind.RHYTHM -> R.plurals.progress_overall_figures to R.plurals.progress_unit_figures
        else -> R.plurals.progress_overall to R.plurals.progress_unit_answers
    }
    val description = if (state.totalAttempts == 0) {
        stringResource(R.string.progress_no_answers_for_filter)
    } else {
        pluralStringResource(summary, state.totalAttempts, state.totalCorrect, state.totalAttempts)
    }
    ChartCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AccuracyDonut(state.overallAccuracy, description)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.totalAttempts == 0) {
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(Modifier.semantics(mergeDescendants = true) {}) {
                        Text("${state.totalAttempts}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            pluralStringResource(unit, state.totalAttempts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.trendChange?.let { TrendChangePill(it) }
            }
        }
    }
}

/** How the newer half of the recent sessions compares with the older half. */
@Composable
private fun TrendChangePill(change: Int) {
    val improved = change > 0
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (improved) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (improved) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            when {
                change > 0 -> pluralStringResource(R.plurals.progress_change_up, change, change)
                change < 0 -> pluralStringResource(R.plurals.progress_change_down, change.absoluteValue, change.absoluteValue)
                else -> stringResource(R.string.progress_change_steady)
            },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TrendCard(trend: List<SessionSummary>, modifier: Modifier = Modifier) {
    ChartCard(modifier) {
        CardHeader(
            stringResource(R.string.progress_trend_title),
            if (trend.isEmpty()) null else pluralStringResource(R.plurals.progress_trend_window, trend.size, trend.size),
        )
        if (trend.size >= 2) {
            AccuracyTrendChart(trend)
        } else {
            Text(
                stringResource(R.string.progress_trend_needs_more),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccuracyCard(state: ProgressUiState, modifier: Modifier = Modifier) {
    ChartCard(modifier) {
        CardHeader(accuracyTitle(state.filter), stringResource(R.string.progress_weakest_first))
        state.weakestFirst.forEach { AccuracyBarRow(it) }
        if (state.totalAttempts > 0) AccuracyLegend()
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
private fun SessionRow(session: SessionSummary, modifier: Modifier = Modifier) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(formatter.format(Date(session.startedAtMillis)), style = MaterialTheme.typography.bodyLarge)
            Text(
                exerciseName(session.exercise),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.practice_score, session.correct, session.attempts),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun ProgressPreview() {
    EarTrainerTheme {
        ProgressScreen(
            state = ProgressUiState(
                filter = ProgressFilter.IntervalStyleFilter(null),
                accuracy = Interval.entries.mapIndexed { index, interval ->
                    val practiced = index % 4 != 3
                    AccuracyItem(interval.displayName, attempts = if (practiced) 10 else 0, correct = if (practiced) 10 - index % 4 * 3 else 0)
                },
                recentSessions = listOf(
                    SessionSummary("a", ExerciseKind.CHORDS, 1_760_000_000_000, 12, 9),
                    SessionSummary("b", ExerciseKind.INTERVALS, 1_759_900_000_000, 8, 5),
                ),
                trend = listOf(6, 7, 6, 8, 7, 9).mapIndexed { index, correct ->
                    SessionSummary("t$index", ExerciseKind.INTERVALS, 1_759_000_000_000 + index * 86_400_000L, 10, correct)
                },
            ),
            onExerciseSelected = {},
            onFilterSelected = {},
            onNavigateUp = {},
        )
    }
}
