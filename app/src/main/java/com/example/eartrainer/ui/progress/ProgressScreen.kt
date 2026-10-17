package com.example.eartrainer.ui.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import kotlin.math.roundToInt

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
    val itemModifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp)
    Column(Modifier.padding(top = innerPadding.calculateTopPadding()).fillMaxSize()) {
        ExerciseTabs(state.exercise, onExerciseSelected)
        LazyColumn(
            contentPadding = PaddingValues(top = 12.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { FilterRow(state, onFilterSelected, Modifier.widthIn(max = 560.dp).fillMaxWidth()) }
            item { OverallCard(state, itemModifier) }
            item { SectionTitle(accuracyTitle(state.filter), itemModifier) }
            items(state.accuracy, key = { it.label }) { AccuracyRow(it, itemModifier) }
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
    },
)

@Composable
private fun filterLabel(filter: ProgressFilter): String = when (filter) {
    is ProgressFilter.IntervalStyleFilter -> filter.style?.displayName ?: stringResource(R.string.progress_filter_all)
    is ProgressFilter.ChordTargetFilter -> filter.target.displayName
    is ProgressFilter.ModeFilter -> filter.mode?.displayName ?: stringResource(R.string.progress_filter_all_modes)
    is ProgressFilter.ProgressionModeFilter -> filter.mode.displayName
    is ProgressFilter.DictationModeFilter -> filter.mode.displayName
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
private fun OverallCard(state: ProgressUiState, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(20.dp).semantics(mergeDescendants = true) {}) {
            Text(
                state.overallAccuracy?.let { percent(it) } ?: "—",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                if (state.totalAttempts == 0) {
                    stringResource(R.string.progress_no_answers_for_filter)
                } else {
                    // Progressions and dictations are scored chord by chord or note by note.
                    val counted = when (state.exercise) {
                        ExerciseKind.PROGRESSIONS -> R.plurals.progress_overall_chords
                        ExerciseKind.DICTATION -> R.plurals.progress_overall_notes
                        else -> R.plurals.progress_overall
                    }
                    pluralStringResource(counted, state.totalAttempts, state.totalCorrect, state.totalAttempts)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
private fun AccuracyRow(item: AccuracyItem, modifier: Modifier = Modifier) {
    val accuracy = item.accuracy
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (accuracy == null) {
                    stringResource(R.string.progress_not_practised)
                } else {
                    stringResource(R.string.progress_answer_score, item.correct, item.attempts, percent(accuracy))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // No bar for unpractised answers, so they never look like a 0% score.
        if (accuracy != null) {
            LinearProgressIndicator(
                progress = { accuracy },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
        }
    }
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

private fun percent(fraction: Float) = "${(fraction * 100).roundToInt()}%"

@Preview(showBackground = true)
@Composable
private fun ProgressPreview() {
    EarTrainerTheme {
        ProgressScreen(
            state = ProgressUiState(
                filter = ProgressFilter.IntervalStyleFilter(null),
                accuracy = Interval.entries.mapIndexed { index, interval ->
                    val practised = index % 4 != 3
                    AccuracyItem(interval.displayName, attempts = if (practised) 10 else 0, correct = if (practised) 10 - index % 4 * 3 else 0)
                },
                recentSessions = listOf(
                    SessionSummary("a", ExerciseKind.CHORDS, 1_760_000_000_000, 12, 9),
                    SessionSummary("b", ExerciseKind.INTERVALS, 1_759_900_000_000, 8, 5),
                ),
            ),
            onExerciseSelected = {},
            onFilterSelected = {},
            onNavigateUp = {},
        )
    }
}
