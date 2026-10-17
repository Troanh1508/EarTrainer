package com.example.eartrainer.ui.rhythm

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.R
import com.example.eartrainer.data.history.WeakSpots.Companion.asWeightLookup
import com.example.eartrainer.exercise.ExercisePhase
import com.example.eartrainer.exercise.ExerciseState
import com.example.eartrainer.exercise.RhythmAttempt
import com.example.eartrainer.exercise.RhythmQuestion
import com.example.eartrainer.exercise.RhythmQuestionGenerator
import com.example.eartrainer.exercise.RhythmSettings
import com.example.eartrainer.exercise.SessionScore
import com.example.eartrainer.music.Rhythm
import com.example.eartrainer.ui.practice.ChoiceLabel
import com.example.eartrainer.ui.practice.PracticeRoute
import com.example.eartrainer.ui.practice.PracticeScreen
import com.example.eartrainer.ui.practice.PracticeTexts
import com.example.eartrainer.ui.practice.PracticeUiState
import com.example.eartrainer.ui.practice.PracticeViewModel
import com.example.eartrainer.ui.theme.EarTrainerTheme
import kotlin.random.Random

typealias RhythmPracticeViewModel = PracticeViewModel<Rhythm, RhythmQuestion, RhythmSettings>

val RhythmPracticeFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
        RhythmPracticeViewModel(
            settings = container.rhythmSettings.settings,
            questionSource = { settings ->
                val weights = container.weakSpots.rhythmCells.asWeightLookup().takeIf { settings.focusOnWeakSpots }
                RhythmQuestionGenerator(settings, weightOf = weights)::generate
            },
            recordAttempt = { state, sessionId -> RhythmAttempt.from(state, sessionId)?.let(container.practiceHistory::record) },
            audioPlayer = container.createAudioPlayer(),
        )
    }
}

/** Notation needs the full width to stay legible, so choices are stacked. */
private const val CHOICE_COLUMNS = 1

@Composable
private fun rhythmTexts() = PracticeTexts<Rhythm, RhythmQuestion>(
    title = stringResource(R.string.rhythm_title),
    instruction = { stringResource(R.string.rhythm_instruction) },
    questionDetail = { stringResource(R.string.rhythm_detail, it.tempo.bpm) },
    // Read aloud for each drawn choice and used in "You answered …".
    choiceLabel = { ChoiceLabel(it.description) },
    correctSummary = { stringResource(R.string.rhythm_summary) },
    // The notes are all the same pitch, so listing them says nothing.
    showNotesAsPlayed = false,
    choiceGraphic = { rhythm -> RhythmNotation(rhythm) },
    showSelectedInFeedback = false,
)

@Composable
fun RhythmTrainingRoute(onOpenSettings: () -> Unit, onNavigateUp: () -> Unit) {
    // Scoped to the navigation destination; the key keeps it distinct from other practice ViewModels.
    val viewModel: RhythmPracticeViewModel = viewModel(key = "rhythm-practice", factory = RhythmPracticeFactory)
    PracticeRoute(viewModel, rhythmTexts(), onOpenSettings, onNavigateUp, choiceColumns = CHOICE_COLUMNS)
}

@Preview(showBackground = true)
@Composable
private fun RhythmAnsweredPreview() {
    val question = RhythmQuestionGenerator(RhythmSettings(), Random(1)).generate()
    EarTrainerTheme {
        PracticeScreen(
            state = PracticeUiState(
                ExerciseState(
                    question = question,
                    questionNumber = 2,
                    phase = ExercisePhase.Answered(question.choices.first(), isCorrect = question.choices.first() == question.rhythm, answeredAtMillis = 0),
                    score = SessionScore(answered = 2, correct = 1),
                ),
            ),
            texts = rhythmTexts(),
            onPlay = {}, onStop = {}, onAnswerSelected = {}, onNext = {}, onOpenSettings = {}, onNavigateUp = {},
            choiceColumns = CHOICE_COLUMNS,
        )
    }
}
