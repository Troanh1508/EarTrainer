package com.example.eartrainer.ui.progressions

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
import com.example.eartrainer.exercise.ChordVocabulary
import com.example.eartrainer.exercise.ExercisePhase
import com.example.eartrainer.exercise.ExerciseState
import com.example.eartrainer.exercise.ProgressionAttempt
import com.example.eartrainer.exercise.ProgressionQuestion
import com.example.eartrainer.exercise.ProgressionQuestionGenerator
import com.example.eartrainer.exercise.ProgressionSettings
import com.example.eartrainer.exercise.SessionScore
import com.example.eartrainer.music.ChordProgression
import com.example.eartrainer.music.Key
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.ui.practice.ChoiceLabel
import com.example.eartrainer.ui.practice.PracticeRoute
import com.example.eartrainer.ui.practice.PracticeScreen
import com.example.eartrainer.ui.practice.PracticeTexts
import com.example.eartrainer.ui.practice.PracticeUiState
import com.example.eartrainer.ui.practice.PracticeViewModel
import com.example.eartrainer.ui.theme.EarTrainerTheme

typealias ProgressionPracticeViewModel = PracticeViewModel<ChordProgression, ProgressionQuestion, ProgressionSettings>

val ProgressionPracticeFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
        ProgressionPracticeViewModel(
            settings = container.progressionSettings.settings,
            questionSource = { settings ->
                val weights = container.weakSpots.progressionChords.asWeightLookup().takeIf { settings.focusOnWeakSpots }
                ProgressionQuestionGenerator(settings, weightOf = weights)::generate
            },
            recordAttempt = { state, sessionId -> ProgressionAttempt.from(state, sessionId)?.let(container.practiceHistory::record) },
            audioPlayer = container.createAudioPlayer(),
        )
    }
}

/** Progression labels are long, so each choice gets a full-width row. */
private const val CHOICE_COLUMNS = 1

@Composable
private fun progressionTexts() = PracticeTexts<ChordProgression, ProgressionQuestion>(
    title = stringResource(R.string.progressions_title),
    instruction = { stringResource(R.string.progressions_instruction) },
    questionDetail = { it.key.name },
    choiceLabel = { ChoiceLabel(it.label) },
    correctSummary = { question ->
        stringResource(R.string.progressions_summary, question.progression.label, question.key.name, question.progression.symbolsIn(question.key))
    },
    // Listing every note of every chord would be a wall of text; chord names say it better.
    showNotesAsPlayed = false,
)

@Composable
fun ProgressionTrainingRoute(onOpenSettings: () -> Unit, onNavigateUp: () -> Unit) {
    // Scoped to the navigation destination; the key keeps it distinct from other practice ViewModels.
    val viewModel: ProgressionPracticeViewModel = viewModel(key = "progression-practice", factory = ProgressionPracticeFactory)
    PracticeRoute(viewModel, progressionTexts(), onOpenSettings, onNavigateUp, choiceColumns = CHOICE_COLUMNS)
}

@Preview(showBackground = true)
@Composable
private fun ProgressionAnsweredPreview() {
    val options = ProgressionQuestionGenerator.allProgressions(Mode.MAJOR, ChordVocabulary.COMMON, 4)
    val correct = options.first { it.label == "I – vi – IV – V" }
    val question = ProgressionQuestion(
        key = Key(Pitch(63), Mode.MAJOR),
        progression = correct,
        choices = listOf(correct) + options.filter { it.label in setOf("I – vi – ii – V", "I – IV – vi – V", "I – vi – IV – I") },
    )
    EarTrainerTheme {
        PracticeScreen(
            state = PracticeUiState(
                ExerciseState(
                    question = question,
                    questionNumber = 2,
                    phase = ExercisePhase.Answered(question.choices[1], isCorrect = false, answeredAtMillis = 0),
                    score = SessionScore(answered = 2, correct = 1),
                ),
            ),
            texts = progressionTexts(),
            onPlay = {}, onStop = {}, onAnswerSelected = {}, onNext = {}, onOpenSettings = {}, onNavigateUp = {},
            choiceColumns = CHOICE_COLUMNS,
        )
    }
}
