package com.example.eartrainer.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eartrainer.R
import com.example.eartrainer.data.settings.GoalSettings
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.ui.progress.exerciseName
import com.example.eartrainer.ui.progress.percent
import com.example.eartrainer.ui.theme.EarTrainerTheme
import com.example.eartrainer.ui.theme.onWeakSpot
import com.example.eartrainer.ui.theme.onWeakSpotContainer
import com.example.eartrainer.ui.theme.weakSpot
import com.example.eartrainer.ui.theme.weakSpotContainer
import java.util.Calendar

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenExercise: (ExerciseKind) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, onOpenExercise, onOpenProgress, onOpenBackup, onDailyGoalSelected = viewModel::onDailyGoalSelected)
}

@Composable
fun HomeScreen(
    state: HomeUiState?,
    onOpenExercise: (ExerciseKind) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenBackup: () -> Unit,
    onDailyGoalSelected: (Int) -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Scrolls so every exercise stays reachable on small screens and with large text.
            // Insets go outside the scroll, so content scrolls beneath the status bar edge, not over it.
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Header(onOpenBackup)
                if (state != null) {
                    TodayCard(state, onDailyGoalSelected)
                    state.suggestion?.let { SuggestionCard(it, onOpenExercise) }
                }
                Row(Modifier.padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_exercises), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenProgress) { Text(stringResource(R.string.home_see_progress)) }
                }
                val summaries = state?.exercises ?: ExerciseKind.entries.map { ExerciseSummary(it, 0, 0) }
                summaries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { ExerciseCard(it, onOpenExercise, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Text(
                    stringResource(R.string.home_offline_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Text(
                    stringResource(R.string.home_sound_credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(onOpenBackup: () -> Unit) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        }
    }
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(greeting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        OutlinedIconButton(onClick = onOpenBackup, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.home_open_backup))
        }
    }
}

/** A ring filled to [fraction] with [content] in the middle. */
@Composable
private fun Ring(fraction: Float, track: Color, fill: Color, diameter: Dp, thickness: Dp, content: @Composable () -> Unit) {
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = thickness.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (fraction > 0f) {
                drawArc(fill, -90f, 360f * fraction, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
private fun TodayCard(state: HomeUiState, onDailyGoalSelected: (Int) -> Unit) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val ringDescription = pluralStringResource(R.plurals.home_goal_ring, state.dailyGoal, state.answersToday, state.dailyGoal)
    var choosingGoal by rememberSaveable { mutableStateOf(false) }
    if (choosingGoal) {
        DailyGoalDialog(
            current = state.dailyGoal,
            onSelected = { answers ->
                onDailyGoalSelected(answers)
                choosingGoal = false
            },
            onDismiss = { choosingGoal = false },
        )
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = onPrimary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.clearAndSetSemantics { contentDescription = ringDescription }) {
                Ring(state.goalProgress, onPrimary.copy(alpha = 0.25f), onPrimary, diameter = 104.dp, thickness = 12.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.answersToday}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.home_goal_of, state.dailyGoal),
                            style = MaterialTheme.typography.labelMedium,
                            color = onPrimary.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.home_goal_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { choosingGoal = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.home_goal_change), modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    when {
                        state.answersToGoal == 0 -> stringResource(R.string.home_goal_done)
                        state.answersToday == 0 && state.currentStreak > 0 ->
                            pluralStringResource(R.plurals.home_goal_keep_streak, state.currentStreak, state.currentStreak)
                        else -> pluralStringResource(R.plurals.home_goal_remaining, state.answersToGoal, state.answersToGoal)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.currentStreak > 0) {
                    Surface(shape = RoundedCornerShape(12.dp), color = onPrimary, contentColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            pluralStringResource(R.plurals.home_streak, state.currentStreak, state.currentStreak),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The daily goals on offer, each a radio button; picking one saves it and closes the dialog. */
@Composable
private fun DailyGoalDialog(current: Int, onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_goal_dialog_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(
                    stringResource(R.string.home_goal_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                GoalSettings.DAILY_ANSWER_OPTIONS.forEach { answers ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = answers == current, role = Role.RadioButton, onClick = { onSelected(answers) }),
                    ) {
                        RadioButton(selected = answers == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(pluralStringResource(R.plurals.home_goal_option, answers, answers), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_goal_dialog_close)) }
        },
    )
}

@Composable
private fun SuggestionCard(suggestion: Suggestion, onOpenExercise: (ExerciseKind) -> Unit) {
    val names = when (suggestion.items.size) {
        1 -> suggestion.items[0]
        2 -> stringResource(R.string.home_join_two, suggestion.items[0], suggestion.items[1])
        else -> stringResource(R.string.home_join_three, suggestion.items[0], suggestion.items[1], suggestion.items[2])
    }
    val exercise = exerciseName(suggestion.exercise)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.weakSpotContainer,
        contentColor = MaterialTheme.colorScheme.onWeakSpotContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.home_suggest_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                pluralStringResource(R.plurals.home_suggest_text, suggestion.items.size, names, exercise.lowercase()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = { onOpenExercise(suggestion.exercise) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.weakSpot,
                    contentColor = MaterialTheme.colorScheme.onWeakSpot,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.home_suggest_button, exercise.lowercase()), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExerciseCard(summary: ExerciseSummary, onOpenExercise: (ExerciseKind) -> Unit, modifier: Modifier = Modifier) {
    val accuracy = summary.accuracy
    val ringColor = if (summary.needsWork) MaterialTheme.colorScheme.weakSpot else MaterialTheme.colorScheme.primary
    Card(
        onClick = { onOpenExercise(summary.exercise) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.heightIn(min = 150.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(summary.exercise.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.semantics(mergeDescendants = true) {}) {
                    Ring(accuracy ?: 0f, MaterialTheme.colorScheme.surfaceContainerHighest, ringColor, diameter = 44.dp, thickness = 5.dp) {
                        Text(
                            accuracy?.let(::percent) ?: stringResource(R.string.home_new),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exerciseName(summary.exercise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(cardDetail(summary.exercise)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun cardDetail(exercise: ExerciseKind) = when (exercise) {
    ExerciseKind.INTERVALS -> R.string.home_card_intervals
    ExerciseKind.CHORDS -> R.string.home_card_chords
    ExerciseKind.SCALE_DEGREES -> R.string.home_card_degrees
    ExerciseKind.PROGRESSIONS -> R.string.home_card_progressions
    ExerciseKind.DICTATION -> R.string.home_card_dictation
    ExerciseKind.RHYTHM -> R.string.home_card_rhythm
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun HomeScreenPreview() {
    EarTrainerTheme {
        HomeScreen(
            state = HomeUiState(
                answersToday = 18,
                dailyGoal = 30,
                currentStreak = 6,
                exercises = ExerciseKind.entries.mapIndexed { index, exercise -> ExerciseSummary(exercise, if (index == 4) 0 else 40, 22 + index * 3) },
                suggestion = Suggestion(ExerciseKind.INTERVALS, listOf("Minor sixth", "Tritone", "Major seventh")),
            ),
            onOpenExercise = {},
            onOpenProgress = {},
            onOpenBackup = {},
            onDailyGoalSelected = {},
        )
    }
}
