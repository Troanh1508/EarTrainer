package com.example.eartrainer.ui.chords

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
import com.example.eartrainer.exercise.ChordAnswer
import com.example.eartrainer.exercise.ChordAttempt
import com.example.eartrainer.exercise.ChordExerciseSettings
import com.example.eartrainer.exercise.ChordQuestion
import com.example.eartrainer.exercise.ChordQuestionGenerator
import com.example.eartrainer.exercise.ChordStyle
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.ExercisePhase
import com.example.eartrainer.exercise.ExerciseState
import com.example.eartrainer.exercise.SessionScore
import com.example.eartrainer.music.ChordInversion
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.ui.practice.ChoiceLabel
import com.example.eartrainer.ui.practice.PracticeRoute
import com.example.eartrainer.ui.practice.PracticeScreen
import com.example.eartrainer.ui.practice.PracticeTexts
import com.example.eartrainer.ui.practice.PracticeUiState
import com.example.eartrainer.ui.practice.PracticeViewModel
import com.example.eartrainer.ui.theme.EarTrainerTheme

typealias ChordPracticeViewModel = PracticeViewModel<ChordAnswer, ChordQuestion, ChordExerciseSettings>

val ChordPracticeFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
        ChordPracticeViewModel(
            settings = container.chordSettings.settings,
            questionSource = { ChordQuestionGenerator(it)::generate },
            recordAttempt = { state, sessionId -> ChordAttempt.from(state, sessionId)?.let(container.practiceHistory::record) },
            audioPlayer = container.createAudioPlayer(),
        )
    }
}

@Composable
private fun chordTexts() = PracticeTexts<ChordAnswer, ChordQuestion>(
    title = stringResource(R.string.chords_title),
    instruction = { question ->
        stringResource(
            when (question.target) {
                ChordTarget.QUALITY -> R.string.chords_instruction_quality
                ChordTarget.INVERSION -> R.string.chords_instruction_inversion
            },
        )
    },
    questionDetail = { it.style.displayName },
    choiceLabel = { answer -> ChoiceLabel(answer.displayName, (answer as? ChordAnswer.Inversion)?.let { bassNote(it.inversion) }) },
    correctSummary = { question ->
        stringResource(R.string.chords_summary, question.rootName, question.quality.displayName.lowercase(), question.inversion.displayName)
    },
)

/** Which chord tone the bass note is, shown under each inversion choice. */
@Composable
private fun bassNote(inversion: ChordInversion) = stringResource(
    when (inversion) {
        ChordInversion.ROOT_POSITION -> R.string.chords_bass_root
        ChordInversion.FIRST -> R.string.chords_bass_third
        ChordInversion.SECOND -> R.string.chords_bass_fifth
        ChordInversion.THIRD -> R.string.chords_bass_seventh
    },
)

@Composable
fun ChordTrainingRoute(onOpenSettings: () -> Unit, onNavigateUp: () -> Unit) {
    // Scoped to the navigation destination; the key keeps it distinct from other practice ViewModels.
    val viewModel: ChordPracticeViewModel = viewModel(key = "chord-practice", factory = ChordPracticeFactory)
    PracticeRoute(viewModel, chordTexts(), onOpenSettings, onNavigateUp)
}

@Preview(showBackground = true)
@Composable
private fun ChordAnsweredPreview() {
    val question = ChordQuestion(
        bass = Pitch(64),
        quality = ChordQuality.MAJOR,
        inversion = ChordInversion.FIRST,
        style = ChordStyle.ARPEGGIATED,
        target = ChordTarget.QUALITY,
        choices = listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIMINISHED, ChordQuality.AUGMENTED)
            .map { ChordAnswer.Quality(it) },
    )
    EarTrainerTheme {
        PracticeScreen(
            state = PracticeUiState(
                ExerciseState(
                    question = question,
                    questionNumber = 4,
                    phase = ExercisePhase.Answered(ChordAnswer.Quality(ChordQuality.MINOR), isCorrect = false, answeredAtMillis = 0),
                    score = SessionScore(answered = 4, correct = 3),
                ),
            ),
            texts = chordTexts(),
            onPlay = {}, onStop = {}, onAnswerSelected = {}, onNext = {}, onOpenSettings = {}, onNavigateUp = {},
        )
    }
}
