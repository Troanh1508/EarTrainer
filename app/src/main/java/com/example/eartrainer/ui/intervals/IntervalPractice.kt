package com.example.eartrainer.ui.intervals

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.R
import com.example.eartrainer.exercise.ExercisePhase
import com.example.eartrainer.exercise.ExerciseState
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalExerciseSettings
import com.example.eartrainer.exercise.IntervalQuestion
import com.example.eartrainer.exercise.IntervalQuestionGenerator
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.exercise.SessionScore
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.ui.practice.ChoiceLabel
import com.example.eartrainer.ui.practice.PracticeRoute
import com.example.eartrainer.ui.practice.PracticeScreen
import com.example.eartrainer.ui.practice.PracticeTexts
import com.example.eartrainer.ui.practice.PracticeUiState
import com.example.eartrainer.ui.practice.PracticeViewModel
import com.example.eartrainer.ui.theme.EarTrainerTheme

typealias IntervalPracticeViewModel = PracticeViewModel<Interval, IntervalQuestion, IntervalExerciseSettings>

val IntervalPracticeFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
        IntervalPracticeViewModel(
            settings = container.intervalSettings.settings,
            questionSource = { IntervalQuestionGenerator(it)::generate },
            recordAttempt = { state, sessionId -> IntervalAttempt.from(state, sessionId)?.let(container.practiceHistory::record) },
            audioPlayer = container.createAudioPlayer(),
        )
    }
}

@Composable
private fun intervalTexts() = PracticeTexts<Interval, IntervalQuestion>(
    title = stringResource(R.string.intervals_title),
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
