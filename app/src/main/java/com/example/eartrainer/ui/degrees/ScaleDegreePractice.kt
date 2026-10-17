package com.example.eartrainer.ui.degrees

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
import com.example.eartrainer.exercise.KeyContext
import com.example.eartrainer.exercise.ScaleDegreeAttempt
import com.example.eartrainer.exercise.ScaleDegreeQuestion
import com.example.eartrainer.exercise.ScaleDegreeQuestionGenerator
import com.example.eartrainer.exercise.ScaleDegreeSettings
import com.example.eartrainer.exercise.SessionScore
import com.example.eartrainer.music.Key
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.music.ScaleDegree
import com.example.eartrainer.ui.practice.ChoiceLabel
import com.example.eartrainer.ui.practice.PracticeRoute
import com.example.eartrainer.ui.practice.PracticeScreen
import com.example.eartrainer.ui.practice.PracticeTexts
import com.example.eartrainer.ui.practice.PracticeUiState
import com.example.eartrainer.ui.practice.PracticeViewModel
import com.example.eartrainer.ui.theme.EarTrainerTheme

typealias ScaleDegreePracticeViewModel = PracticeViewModel<ScaleDegree, ScaleDegreeQuestion, ScaleDegreeSettings>

val ScaleDegreePracticeFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
        ScaleDegreePracticeViewModel(
            settings = container.scaleDegreeSettings.settings,
            questionSource = { settings ->
                val weights = container.weakSpots.scaleDegrees.asWeightLookup().takeIf { settings.focusOnWeakSpots }
                ScaleDegreeQuestionGenerator(settings, weightOf = weights)::generate
            },
            recordAttempt = { state, sessionId -> ScaleDegreeAttempt.from(state, sessionId)?.let(container.practiceHistory::record) },
            audioPlayer = container.createAudioPlayer(),
        )
    }
}

/** Degrees are short ("♭3"), so four fit comfortably in a row. */
private const val CHOICE_COLUMNS = 4

@Composable
private fun scaleDegreeTexts() = PracticeTexts<ScaleDegree, ScaleDegreeQuestion>(
    title = stringResource(R.string.degrees_title),
    instruction = { stringResource(R.string.degrees_instruction) },
    // The key is shown up front: knowing it is part of the exercise, not the answer.
    questionDetail = { it.key.name },
    choiceLabel = { ChoiceLabel(it.label, it.solfege) },
    correctSummary = { question ->
        stringResource(R.string.degrees_summary, question.degree.label, question.degree.solfege, question.key.name)
    },
    noteName = { question, pitch -> question.key.noteName(pitch) + pitch.octave },
)

@Composable
fun ScaleDegreeTrainingRoute(onOpenSettings: () -> Unit, onNavigateUp: () -> Unit) {
    // Scoped to the navigation destination; the key keeps it distinct from other practice ViewModels.
    val viewModel: ScaleDegreePracticeViewModel = viewModel(key = "scale-degree-practice", factory = ScaleDegreePracticeFactory)
    PracticeRoute(viewModel, scaleDegreeTexts(), onOpenSettings, onNavigateUp, choiceColumns = CHOICE_COLUMNS)
}

@Preview(showBackground = true)
@Composable
private fun ScaleDegreeAnsweredPreview() {
    val question = ScaleDegreeQuestion(Key(Pitch(63), Mode.MAJOR), ScaleDegree.SIXTH, KeyContext.CADENCE, Mode.MAJOR.diatonicDegrees)
    EarTrainerTheme {
        PracticeScreen(
            state = PracticeUiState(
                ExerciseState(
                    question = question,
                    questionNumber = 5,
                    phase = ExercisePhase.Answered(ScaleDegree.FIFTH, isCorrect = false, answeredAtMillis = 0),
                    score = SessionScore(answered = 5, correct = 3),
                ),
            ),
            texts = scaleDegreeTexts(),
            onPlay = {}, onStop = {}, onAnswerSelected = {}, onNext = {}, onOpenSettings = {}, onNavigateUp = {},
            choiceColumns = CHOICE_COLUMNS,
        )
    }
}
