package com.example.eartrainer.ui.practice

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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eartrainer.R
import com.example.eartrainer.exercise.ExerciseQuestion

/** Exercise-specific wording for the shared practice screen. */
data class PracticeTexts<A, Q : ExerciseQuestion<A>>(
    val title: String,
    val instruction: @Composable (Q) -> String,
    /** Short description of how this question is played, e.g. "Harmonic". */
    val questionDetail: (Q) -> String,
    val choiceLabel: @Composable (A) -> ChoiceLabel,
    /** What the right answer was, shown after answering. */
    val correctSummary: @Composable (Q) -> String,
)

/**
 * Connects a [PracticeViewModel] to [PracticeScreen] and stops audio when the screen leaves the
 * foreground.
 */
@Composable
fun <A, Q : ExerciseQuestion<A>> PracticeRoute(
    viewModel: PracticeViewModel<A, Q, *>,
    texts: PracticeTexts<A, Q>,
    onOpenSettings: () -> Unit,
    onNavigateUp: () -> Unit,
    choiceColumns: Int = 2,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenHidden() }

    // Settings load from disk almost instantly; show a plain surface rather than a spinner flash.
    val loaded = state ?: run {
        Surface(Modifier.fillMaxSize()) {}
        return
    }
    PracticeScreen(
        state = loaded,
        texts = texts,
        onPlay = viewModel::onPlayClick,
        onStop = viewModel::onStopClick,
        onAnswerSelected = viewModel::onAnswerSelected,
        onNext = viewModel::onNextClick,
        onOpenSettings = onOpenSettings,
        onNavigateUp = onNavigateUp,
        choiceColumns = choiceColumns,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <A, Q : ExerciseQuestion<A>> PracticeScreen(
    state: PracticeUiState<A, Q>,
    texts: PracticeTexts<A, Q>,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onAnswerSelected: (A) -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateUp: () -> Unit,
    choiceColumns: Int = 2,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(texts.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.practice_open_settings))
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
                        selectedLabel = texts.choiceLabel(answer.selectedAnswer).title,
                        correctSummary = texts.correctSummary(state.question),
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
                SessionHeader(state, texts)
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
                    labelOf = texts.choiceLabel,
                    appearanceOf = state::appearanceOf,
                    onChoiceSelected = onAnswerSelected,
                    columns = choiceColumns,
                )
            }
        }
    }
}

@Composable
private fun <A, Q : ExerciseQuestion<A>> SessionHeader(state: PracticeUiState<A, Q>, texts: PracticeTexts<A, Q>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(texts.instruction(state.question), style = MaterialTheme.typography.bodyLarge)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.practice_question_number, state.exercise.questionNumber, texts.questionDetail(state.question)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val score = state.exercise.score
            Text(
                if (score.answered == 0) {
                    stringResource(R.string.practice_score_empty)
                } else {
                    stringResource(R.string.practice_score, score.correct, score.answered)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PlaybackHint(state: PracticeUiState<*, *>) {
    val hint = when {
        state.playback == PlaybackStatus.Failed -> stringResource(R.string.practice_playback_failed)
        !state.hasHeardPrompt -> stringResource(R.string.practice_listen_first)
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
