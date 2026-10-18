package com.example.eartrainer.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eartrainer.R
import com.example.eartrainer.exercise.Comparison
import com.example.eartrainer.exercise.ExerciseQuestion
import com.example.eartrainer.ui.practice.AnswerFeedback
import com.example.eartrainer.ui.practice.AnswerGrid
import com.example.eartrainer.ui.practice.ChoiceLabel
import com.example.eartrainer.ui.practice.PlaybackButton
import com.example.eartrainer.ui.practice.PracticeUiState
import com.example.eartrainer.ui.practice.PracticeViewModel

/**
 * A drill on two answers that are easily mixed up: listen to one example of each, then a round
 * of [Comparison.QUESTIONS_PER_ROUND] questions with only those two choices, then the score.
 */
@Composable
fun <A, Q : ExerciseQuestion<A>> ComparisonRoute(
    viewModel: PracticeViewModel<A, Q, Comparison<A, Q>>,
    labelOf: @Composable (A) -> String,
    /** How the two examples are made alike, e.g. "Both start from the same note." */
    examplesHint: String,
    onNavigateUp: () -> Unit,
    /** What is being compared, under the title; by default the two answers' labels. */
    subtitle: String? = null,
    /** Draws each choice instead of writing it, e.g. rhythm notation; the label is still read aloud. */
    graphicOf: (@Composable (A) -> Unit)? = null,
    columns: Int = 2,
    /** Shorter names for the example buttons, when the answers' labels are long. */
    exampleLabels: List<String>? = null,
    /**
     * Feedback to show instead of naming both answers, for drawn choices whose marks already
     * show what was chosen and what was right.
     */
    answerSummary: String? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val comparison by viewModel.currentSettings.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenHidden() }
    val loadedState = state
    val loadedComparison = comparison
    if (loadedState == null || loadedComparison == null) {
        Surface(Modifier.fillMaxSize()) {}
        return
    }
    ComparisonScreen(
        state = loadedState,
        answers = loadedComparison.answers,
        labelOf = labelOf,
        examplesHint = examplesHint,
        onPlayExample = { index -> viewModel.onPlayPrompts(listOf(loadedComparison.examples[index])) },
        onPlayBothExamples = { viewModel.onPlayPrompts(loadedComparison.examples) },
        onPlay = viewModel::onPlayClick,
        onStop = viewModel::onStopClick,
        onAnswerSelected = viewModel::onAnswerSelected,
        onNext = viewModel::onNextClick,
        onRestart = viewModel::onRestartClick,
        onNavigateUp = onNavigateUp,
        subtitle = subtitle,
        graphicOf = graphicOf,
        columns = columns,
        exampleLabels = exampleLabels,
        answerSummary = answerSummary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <A, Q : ExerciseQuestion<A>> ComparisonScreen(
    state: PracticeUiState<A, Q>,
    answers: List<A>,
    labelOf: @Composable (A) -> String,
    examplesHint: String,
    onPlayExample: (Int) -> Unit,
    onPlayBothExamples: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onAnswerSelected: (A) -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onNavigateUp: () -> Unit,
    subtitle: String? = null,
    graphicOf: (@Composable (A) -> Unit)? = null,
    columns: Int = 2,
    exampleLabels: List<String>? = null,
    answerSummary: String? = null,
) {
    val total = Comparison.QUESTIONS_PER_ROUND
    // Survives rotation; a new round (or a new question) always hides it again.
    var showResult by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val answered = state.answer != null
    LaunchedEffect(answered, state.exercise.questionNumber) {
        if (answered) {
            // The feedback panel appears in the same frame as the answer; wait until the page has
            // been laid out above it, then show the end of the page so both choices stay in view.
            withFrameNanos {}
            withFrameNanos {}
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    val roundFinished = state.exercise.questionNumber >= total && state.answer != null
    val first = labelOf(answers[0])
    val second = labelOf(answers[1])

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.compare_title))
                        Text(
                            subtitle ?: stringResource(R.string.compare_subtitle, first, second),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        bottomBar = {
            val answer = state.answer
            if (answer != null && !showResult) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    AnswerFeedback(
                        isCorrect = answer.isCorrect,
                        selectedLabel = if (answerSummary == null) labelOf(answer.selectedAnswer) else null,
                        correctSummary = answerSummary ?: stringResource(R.string.compare_it_was, labelOf(state.question.correctAnswer)),
                        notesAsPlayed = null,
                        onNext = if (roundFinished) ({ showResult = true }) else onNext,
                        nextLabel = stringResource(if (roundFinished) R.string.compare_see_result else R.string.practice_next),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(scrollState),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                if (showResult) {
                    RoundResult(
                        correct = state.exercise.score.correct,
                        total = state.exercise.score.answered,
                        onAgain = {
                            showResult = false
                            onRestart()
                        },
                        onDone = onNavigateUp,
                    )
                } else {
                    ExamplesCard(exampleLabels ?: listOf(first, second), examplesHint, state.isPlaying, onPlayExample, onPlayBothExamples)
                    RoundProgress(state.exercise.questionNumber, total, state.exercise.score.correct)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        PlaybackButton(
                            isPlaying = state.isPlaying,
                            hasHeardPrompt = state.hasHeardPrompt,
                            onPlay = onPlay,
                            onStop = onStop,
                            modifier = Modifier.widthIn(min = 200.dp),
                        )
                    }
                    AnswerGrid(
                        choices = answers,
                        labelOf = { ChoiceLabel(labelOf(it)) },
                        appearanceOf = state::appearanceOf,
                        onChoiceSelected = onAnswerSelected,
                        columns = columns,
                        graphicOf = graphicOf,
                    )
                }
            }
        }
    }
}

/** One button per example and one for both, so the difference can be heard before answering. */
@Composable
private fun ExamplesCard(labels: List<String>, hint: String, isPlaying: Boolean, onPlay: (Int) -> Unit, onPlayBoth: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.compare_listen_title), style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            labels.forEachIndexed { index, label ->
                OutlinedButton(onClick = { onPlay(index) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.compare_play_example, label))
                }
            }
            TextButton(onClick = onPlayBoth, enabled = !isPlaying, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.compare_play_both))
            }
        }
    }
}

@Composable
private fun RoundProgress(questionNumber: Int, total: Int, correct: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.compare_question, questionNumber, total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                pluralStringResource(R.plurals.compare_right_so_far, correct, correct),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { questionNumber.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun RoundResult(correct: Int, total: Int, onAgain: () -> Unit, onDone: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            pluralStringResource(R.plurals.compare_result, total, correct, total),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(if (correct >= total * GOOD_SHARE) R.string.compare_result_good else R.string.compare_result_keep_going),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.compare_again), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.compare_done), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Share of a round that counts as telling the two apart reliably. */
private const val GOOD_SHARE = 0.9
