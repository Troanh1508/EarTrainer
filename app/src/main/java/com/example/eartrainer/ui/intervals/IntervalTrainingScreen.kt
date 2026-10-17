package com.example.eartrainer.ui.intervals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eartrainer.R
import com.example.eartrainer.exercise.ExercisePhase
import com.example.eartrainer.exercise.ExerciseState
import com.example.eartrainer.exercise.IntervalQuestion
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.exercise.SessionScore
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.ui.theme.EarTrainerTheme

@Composable
fun IntervalTrainingRoute(
    viewModel: IntervalTrainingViewModel,
    onNavigateUp: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenHidden() }

    IntervalTrainingScreen(
        state = state,
        onPlay = viewModel::onPlayClick,
        onStop = viewModel::onStopClick,
        onAnswerSelected = viewModel::onAnswerSelected,
        onNext = viewModel::onNextClick,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalTrainingScreen(
    state: IntervalTrainingUiState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onAnswerSelected: (Interval) -> Unit,
    onNext: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.intervals_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        bottomBar = {
            // Feedback lives in a fixed bottom panel so "Next" stays reachable without scrolling.
            state.answer?.let { answer ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    AnswerFeedback(
                        isCorrect = answer.isCorrect,
                        selected = answer.selectedAnswer,
                        correct = state.question.correctAnswer,
                        notesAsPlayed = state.notesAsPlayed,
                        onNext = onNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                SessionHeader(state)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PlaybackButton(
                        isPlaying = state.isPlaying,
                        hasHeardPrompt = state.hasHeardPrompt,
                        onPlay = onPlay,
                        onStop = onStop,
                        modifier = Modifier.widthIn(min = 200.dp),
                    )
                    PlaybackHint(state)
                }
                AnswerGrid(
                    choices = state.question.choices,
                    appearanceOf = state::appearanceOf,
                    onChoiceSelected = onAnswerSelected,
                )
            }
        }
    }
}

@Composable
private fun SessionHeader(state: IntervalTrainingUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.intervals_instruction),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    R.string.intervals_question_number,
                    state.exercise.questionNumber,
                    state.question.style.displayName,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val score = state.exercise.score
            Text(
                if (score.answered == 0) {
                    stringResource(R.string.intervals_score_empty)
                } else {
                    stringResource(R.string.intervals_score, score.correct, score.answered)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PlaybackHint(state: IntervalTrainingUiState) {
    val hint = when {
        state.playback == PlaybackStatus.Failed -> stringResource(R.string.intervals_playback_failed)
        !state.hasHeardPrompt -> stringResource(R.string.intervals_listen_first)
        else -> return
    }
    Text(
        hint,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = if (state.playback == PlaybackStatus.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

private fun previewState(phase: ExercisePhase<Interval>, score: SessionScore) = IntervalTrainingUiState(
    exercise = ExerciseState(
        question = IntervalQuestion(
            lowerPitch = Pitch.MIDDLE_C,
            interval = Interval.MAJOR_THIRD,
            style = IntervalStyle.MELODIC_ASCENDING,
            choices = Interval.entries.subList(1, 7),
        ),
        questionNumber = 7,
        phase = phase,
        score = score,
    ),
)

@Preview(showBackground = true)
@Composable
private fun AwaitingAnswerPreview() {
    EarTrainerTheme {
        IntervalTrainingScreen(
            state = previewState(ExercisePhase.AwaitingAnswer, SessionScore(answered = 6, correct = 4)),
            onPlay = {}, onStop = {}, onAnswerSelected = {}, onNext = {}, onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AnsweredIncorrectlyPreview() {
    EarTrainerTheme(darkTheme = true) {
        IntervalTrainingScreen(
            state = previewState(
                ExercisePhase.Answered(Interval.MINOR_THIRD, isCorrect = false),
                SessionScore(answered = 7, correct = 4),
            ),
            onPlay = {}, onStop = {}, onAnswerSelected = {}, onNext = {}, onNavigateUp = {},
        )
    }
}
