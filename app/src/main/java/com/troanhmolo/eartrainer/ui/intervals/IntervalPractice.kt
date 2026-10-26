package com.troanhmolo.eartrainer.ui.intervals

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.troanhmolo.eartrainer.EarTrainerApplication
import com.troanhmolo.eartrainer.R
import com.troanhmolo.eartrainer.ui.practice.wholeAnswerItems
import com.troanhmolo.eartrainer.ui.practice.PracticeSummary
import com.troanhmolo.eartrainer.exercise.ExerciseKind
import com.troanhmolo.eartrainer.data.history.WeakSpots.Companion.asWeightLookup
import com.troanhmolo.eartrainer.exercise.ExercisePhase
import com.troanhmolo.eartrainer.exercise.ExerciseState
import com.troanhmolo.eartrainer.exercise.IntervalAttempt
import com.troanhmolo.eartrainer.exercise.IntervalExerciseSettings
import com.troanhmolo.eartrainer.exercise.IntervalQuestion
import com.troanhmolo.eartrainer.exercise.IntervalQuestionGenerator
import com.troanhmolo.eartrainer.exercise.IntervalStyle
import com.troanhmolo.eartrainer.exercise.SessionScore
import com.troanhmolo.eartrainer.music.Interval
import com.troanhmolo.eartrainer.music.Pitch
import com.troanhmolo.eartrainer.ui.practice.ChoiceLabel
import com.troanhmolo.eartrainer.ui.practice.PracticeRoute
import com.troanhmolo.eartrainer.ui.practice.PracticeScreen
import com.troanhmolo.eartrainer.ui.practice.PracticeTexts
import com.troanhmolo.eartrainer.ui.practice.PracticeUiState
import com.troanhmolo.eartrainer.ui.practice.PracticeViewModel
import com.troanhmolo.eartrainer.ui.theme.EarTrainerTheme

typealias IntervalPracticeViewModel = PracticeViewModel<Interval, IntervalQuestion, IntervalExerciseSettings>

val IntervalPracticeFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
        IntervalPracticeViewModel(
            settings = container.intervalSettings.settings,
            questionSource = { settings ->
                val weights = container.weakSpots.intervals.asWeightLookup().takeIf { settings.focusOnWeakSpots }
                IntervalQuestionGenerator(settings, weightOf = weights)::generate
            },
            recordAttempt = { state, sessionId -> IntervalAttempt.from(state, sessionId)?.let(container.practiceHistory::record) },
            audioPlayer = container.createAudioPlayer(),
            itemsOf = wholeAnswerItems { it.displayName },
            recentSessions = container.practiceHistory.recentSessions(PracticeSummary.AVERAGE_OVER_SESSIONS + 1, ExerciseKind.INTERVALS),
        )
    }
}

@Composable
private fun intervalTexts() = PracticeTexts<Interval, IntervalQuestion>(
    title = stringResource(R.string.intervals_title),
    exercise = ExerciseKind.INTERVALS,
    instruction = { stringResource(R.string.intervals_instruction) },
    questionDetail = { it.style.displayName },
    choiceLabel = { ChoiceLabel(it.displayName, it.abbreviation) },
    correctSummary = { question ->
        val semitones = question.interval.semitones
        "${question.interval.displayName} · ${pluralStringResource(R.plurals.intervals_semitones, semitones, semitones)}"
    },
)

@Composable
fun IntervalTrainingRoute(onOpenSettings: () -> Unit, onNavigateUp: () -> Unit) {
    // Scoped to the navigation destination: leaving the screen clears the ViewModel, which
    // releases its audio player. The key keeps it distinct from other practice ViewModels.
    val viewModel: IntervalPracticeViewModel = viewModel(key = "interval-practice", factory = IntervalPracticeFactory)
    PracticeRoute(viewModel, intervalTexts(), onOpenSettings, onNavigateUp)
}

private fun previewState(phase: ExercisePhase<Interval>, score: SessionScore) = PracticeUiState(
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
        PracticeScreen(
            state = previewState(ExercisePhase.AwaitingAnswer, SessionScore(answered = 6, correct = 4)),
            texts = intervalTexts(),
            onPlay = {}, onStop = {}, onAnswerSelected = {}, onNext = {}, onOpenSettings = {}, onNavigateUp = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AnsweredIncorrectlyPreview() {
    EarTrainerTheme(darkTheme = true) {
        PracticeScreen(
            state = previewState(
                ExercisePhase.Answered(Interval.MINOR_THIRD, isCorrect = false, answeredAtMillis = 0),
                SessionScore(answered = 7, correct = 4),
            ),
            texts = intervalTexts(),
            onPlay = {}, onStop = {}, onAnswerSelected = {}, onNext = {}, onOpenSettings = {}, onNavigateUp = {},
        )
    }
}
