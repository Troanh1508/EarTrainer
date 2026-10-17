package com.example.eartrainer.ui.dictation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.R
import com.example.eartrainer.data.history.WeakSpots.Companion.asWeightLookup
import com.example.eartrainer.exercise.DictationAttempt
import com.example.eartrainer.exercise.DictationQuestion
import com.example.eartrainer.exercise.DictationQuestionGenerator
import com.example.eartrainer.exercise.DictationSettings
import com.example.eartrainer.exercise.ExercisePhase
import com.example.eartrainer.exercise.ExerciseState
import com.example.eartrainer.exercise.KeyContext
import com.example.eartrainer.exercise.SessionScore
import com.example.eartrainer.music.Key
import com.example.eartrainer.music.Melody
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.music.ScaleDegree
import com.example.eartrainer.ui.practice.AnswerGrid
import com.example.eartrainer.ui.practice.ChoiceAppearance
import com.example.eartrainer.ui.practice.ChoiceLabel
import com.example.eartrainer.ui.practice.PracticeHost
import com.example.eartrainer.ui.practice.PracticeScaffold
import com.example.eartrainer.ui.practice.PracticeTexts
import com.example.eartrainer.ui.practice.PracticeUiState
import com.example.eartrainer.ui.practice.PracticeViewModel
import com.example.eartrainer.ui.theme.EarTrainerTheme

typealias DictationPracticeViewModel = PracticeViewModel<Melody, DictationQuestion, DictationSettings>

val DictationPracticeFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
        DictationPracticeViewModel(
            settings = container.dictationSettings.settings,
            questionSource = { settings ->
                val weights = container.weakSpots.dictationDegrees.asWeightLookup().takeIf { settings.focusOnWeakSpots }
                DictationQuestionGenerator(settings, weightOf = weights)::generate
            },
            recordAttempt = { state, sessionId -> DictationAttempt.from(state, sessionId)?.let(container.practiceHistory::record) },
            audioPlayer = container.createAudioPlayer(),
        )
    }
}

/** Degrees are short, so four keypad buttons fit in a row. */
private const val KEYPAD_COLUMNS = 4

@Composable
private fun dictationTexts() = PracticeTexts<Melody, DictationQuestion>(
    title = stringResource(R.string.dictation_title),
    instruction = { stringResource(R.string.dictation_instruction) },
    questionDetail = { it.key.name },
    choiceLabel = { ChoiceLabel(it.label) },
    correctSummary = { question ->
        stringResource(R.string.dictation_summary, question.melody.label, question.melody.solfege, question.key.name)
    },
    noteName = { question, pitch -> question.key.noteName(pitch) + pitch.octave },
)

@Composable
fun DictationTrainingRoute(onOpenSettings: () -> Unit, onNavigateUp: () -> Unit) {
    // Scoped to the navigation destination; the key keeps it distinct from other practice ViewModels.
    val viewModel: DictationPracticeViewModel = viewModel(key = "dictation-practice", factory = DictationPracticeFactory)
    val texts = dictationTexts()
    PracticeHost(viewModel) { state ->
        DictationScreen(
            state = state,
            texts = texts,
            onPlay = viewModel::onPlayClick,
            onStop = viewModel::onStopClick,
            onSubmit = viewModel::onAnswerSelected,
            onNext = viewModel::onNextClick,
            onOpenSettings = onOpenSettings,
            onNavigateUp = onNavigateUp,
        )
    }
}

@Composable
fun DictationScreen(
    state: PracticeUiState<Melody, DictationQuestion>,
    texts: PracticeTexts<Melody, DictationQuestion>,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onSubmit: (Melody) -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    PracticeScaffold(state, texts, onPlay, onStop, onNext, onOpenSettings, onNavigateUp) {
        DictationInput(state, onSubmit)
    }
}

/**
 * The note slots, keypad and editing buttons. What has been typed is a [DictationDraft] (with its
 * undo history) that only becomes an answer on "Check"; it lives here because it is pure input,
 * and is saved across rotation.
 */
@Composable
private fun DictationInput(state: PracticeUiState<Melody, DictationQuestion>, onSubmit: (Melody) -> Unit) {
    val question = state.question
    val length = question.melody.degrees.size
    // A fresh draft for every new question.
    var entry by rememberSaveable(state.exercise.questionNumber, question, stateSaver = DictationEntry.Saver) {
        mutableStateOf(DictationEntry.start(length))
    }
    val draft = entry.draft
    val answered = state.answer?.selectedAnswer
    val editable = state.canAnswer

    Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        NoteSlots(
            written = answered?.degrees ?: draft.notes,
            correct = question.melody.degrees.takeIf { answered != null },
            selected = draft.selected.takeIf { editable },
            onSelect = { index -> if (editable) entry = entry.select(index) },
        )
        AnswerGrid(
            choices = question.keypad,
            labelOf = { ChoiceLabel(it.label, it.solfege) },
            appearanceOf = { if (editable) ChoiceAppearance.Available else ChoiceAppearance.Disabled },
            onChoiceSelected = { degree -> if (editable) entry = entry.enter(degree) },
            columns = KEYPAD_COLUMNS,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { entry = entry.undo() },
                enabled = editable && entry.canUndo,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.dictation_undo))
            }
            OutlinedButton(
                onClick = { entry = entry.clearAll() },
                enabled = editable && draft.hasWrittenNotes,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.dictation_clear_all))
            }
            Button(
                onClick = { draft.toMelodyOrNull()?.let(onSubmit) },
                enabled = editable && draft.isComplete,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.dictation_check))
            }
        }
    }
}

/**
 * One box per note. The first is the given tonic. While answering, tapping a box selects it for
 * editing; the selected box is outlined. After checking, each box is marked right or wrong (by
 * colour and wording, never colour alone), with the right note under a wrong one.
 */
@Composable
private fun NoteSlots(
    written: List<ScaleDegree?>,
    correct: List<ScaleDegree>?,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val givenLabel = stringResource(R.string.dictation_slot_given)
    val emptyLabel = stringResource(R.string.dictation_slot_empty)
    val rightLabel = stringResource(R.string.practice_choice_correct)
    val selectedLabel = stringResource(R.string.dictation_slot_selected)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        written.indices.forEach { index ->
            val note = written[index]
            val expected = correct?.getOrNull(index)
            val isRight = expected != null && note == expected
            val isSelected = index == selected
            val (container, content, border) = when {
                // Muted, so the given tonic never looks like the selected slot.
                index == 0 -> Triple(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, scheme.outlineVariant)
                isSelected -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
                expected == null -> Triple(scheme.surfaceContainerLow, scheme.onSurface, if (note != null) scheme.outline else scheme.outlineVariant)
                isRight -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
                else -> Triple(scheme.errorContainer, scheme.onErrorContainer, scheme.error)
            }
            val description = when {
                index == 0 -> "${note?.label}, $givenLabel"
                expected == null -> listOfNotNull(note?.label ?: emptyLabel, selectedLabel.takeIf { isSelected }).joinToString(", ")
                isRight -> "${note?.label}, $rightLabel"
                else -> stringResource(R.string.dictation_slot_wrong, note?.label ?: emptyLabel, expected.label)
            }
            Slot(
                note = note,
                correction = expected.takeIf { !isRight && index > 0 },
                container = container,
                content = content,
                border = border,
                emphasised = isSelected || note != null,
                // The tonic is given, and nothing is editable once checked.
                onClick = if (index > 0 && selected != null) ({ onSelect(index) }) else null,
                modifier = Modifier.semantics {
                    contentDescription = description
                    this.selected = isSelected
                },
            )
        }
    }
}

@Composable
private fun Slot(
    note: ScaleDegree?,
    correction: ScaleDegree?,
    container: Color,
    content: Color,
    border: Color,
    emphasised: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val sizing = modifier.widthIn(min = 56.dp).heightIn(min = 64.dp)
    val body: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                note?.label ?: "–",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                textDecoration = if (correction != null) TextDecoration.LineThrough else null,
            )
            Text(
                correction?.label ?: note?.solfege ?: "",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
    val stroke = BorderStroke(if (emphasised) 2.dp else 1.dp, border)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = container,
            contentColor = content,
            border = stroke,
            modifier = sizing,
            content = body,
        )
    } else {
        Surface(shape = shape, color = container, contentColor = content, border = stroke, modifier = sizing, content = body)
    }
}

@Preview(showBackground = true)
@Composable
private fun DictationAnsweredPreview() {
    val melody = Melody(listOf(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.THIRD, ScaleDegree.FIFTH))
    val question = DictationQuestion(Key(Pitch(60), Mode.MAJOR), melody, KeyContext.CADENCE)
    EarTrainerTheme {
        DictationScreen(
            state = PracticeUiState(
                ExerciseState(
                    question = question,
                    questionNumber = 3,
                    phase = ExercisePhase.Answered(
                        Melody(listOf(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.FOURTH, ScaleDegree.FIFTH)),
                        isCorrect = false,
                        answeredAtMillis = 0,
                    ),
                    score = SessionScore(answered = 3, correct = 2),
                ),
            ),
            texts = dictationTexts(),
            onPlay = {}, onStop = {}, onSubmit = {}, onNext = {}, onOpenSettings = {}, onNavigateUp = {},
        )
    }
}
