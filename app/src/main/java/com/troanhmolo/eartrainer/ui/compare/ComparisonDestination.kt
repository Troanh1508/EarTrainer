package com.troanhmolo.eartrainer.ui.compare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.troanhmolo.eartrainer.AppContainer
import com.troanhmolo.eartrainer.EarTrainerApplication
import com.troanhmolo.eartrainer.R
import com.troanhmolo.eartrainer.exercise.ChordAnswer
import com.troanhmolo.eartrainer.exercise.ChordAttempt
import com.troanhmolo.eartrainer.exercise.ChordComparison
import com.troanhmolo.eartrainer.exercise.ChordTarget
import com.troanhmolo.eartrainer.exercise.Comparison
import com.troanhmolo.eartrainer.exercise.ExerciseKind
import com.troanhmolo.eartrainer.exercise.ExerciseQuestion
import com.troanhmolo.eartrainer.exercise.ExerciseState
import com.troanhmolo.eartrainer.exercise.IntervalAttempt
import com.troanhmolo.eartrainer.exercise.IntervalComparison
import com.troanhmolo.eartrainer.exercise.IntervalStyle
import com.troanhmolo.eartrainer.exercise.ProgressionAttempt
import com.troanhmolo.eartrainer.exercise.ProgressionChordComparison
import com.troanhmolo.eartrainer.exercise.RhythmAttempt
import com.troanhmolo.eartrainer.exercise.RhythmCellComparison
import com.troanhmolo.eartrainer.exercise.ScaleDegreeAttempt
import com.troanhmolo.eartrainer.exercise.ScaleDegreeComparison
import com.troanhmolo.eartrainer.music.ChordInversion
import com.troanhmolo.eartrainer.music.ChordQuality
import com.troanhmolo.eartrainer.music.DiatonicChord
import com.troanhmolo.eartrainer.music.Interval
import com.troanhmolo.eartrainer.music.Mode
import com.troanhmolo.eartrainer.music.RhythmCell
import com.troanhmolo.eartrainer.music.ScaleDegree
import com.troanhmolo.eartrainer.ui.practice.PracticeViewModel
import com.troanhmolo.eartrainer.ui.rhythm.RhythmNotation
import kotlinx.coroutines.flow.flowOf

/**
 * Builds the drill for [request] and shows it. The drill's answers are recorded like ordinary
 * practice of that exercise; a dictation mix-up is drilled, and recorded, as scale degrees.
 */
@Composable
fun ComparisonDestination(request: ComparisonRequest, onNavigateUp: () -> Unit) {
    when (request.exercise) {
        ExerciseKind.INTERVALS -> {
            val first = enumOrNull<Interval>(request.first)
            val second = enumOrNull<Interval>(request.second)
            if (first == null || second == null) return Invalid(onNavigateUp)
            val style = request.scope?.let { enumOrNull<IntervalStyle>(it) }
            val viewModel = comparisonViewModel(request, { IntervalComparison(first, second, style) }) { container, state, session ->
                IntervalAttempt.from(state, session)?.let(container.practiceHistory::record)
            }
            ComparisonRoute(viewModel, { it.displayName }, stringResource(R.string.compare_hint_intervals), onNavigateUp)
        }
        ExerciseKind.CHORDS -> {
            val target = request.scope?.let { enumOrNull<ChordTarget>(it) } ?: ChordTarget.QUALITY
            val first = chordAnswerOrNull(target, request.first)
            val second = chordAnswerOrNull(target, request.second)
            if (first == null || second == null) return Invalid(onNavigateUp)
            val viewModel = comparisonViewModel(request, { ChordComparison(first, second) }) { container, state, session ->
                ChordAttempt.from(state, session)?.let(container.practiceHistory::record)
            }
            ComparisonRoute(viewModel, { it.displayName }, stringResource(R.string.compare_hint_chords), onNavigateUp)
        }
        ExerciseKind.SCALE_DEGREES, ExerciseKind.DICTATION -> {
            val first = enumOrNull<ScaleDegree>(request.first)
            val second = enumOrNull<ScaleDegree>(request.second)
            if (first == null || second == null) return Invalid(onNavigateUp)
            val mode = request.scope?.let { enumOrNull<Mode>(it) } ?: ScaleDegreeComparison.modeFor(first, second)
            val viewModel = comparisonViewModel(request, { ScaleDegreeComparison(first, second, mode) }) { container, state, session ->
                ScaleDegreeAttempt.from(state, session)?.let(container.practiceHistory::record)
            }
            ComparisonRoute(viewModel, { "${it.label} · ${it.solfege}" }, stringResource(R.string.compare_hint_degrees), onNavigateUp)
        }
        ExerciseKind.PROGRESSIONS -> {
            // Progressions are always looked at one mode at a time, and the chords are scale steps.
            val mode = request.scope?.let { enumOrNull<Mode>(it) }
            val first = request.first.toIntOrNull()?.takeIf { it in 1..7 }
            val second = request.second.toIntOrNull()?.takeIf { it in 1..7 }
            if (mode == null || first == null || second == null) return Invalid(onNavigateUp)
            val viewModel = comparisonViewModel(
                request,
                { ProgressionChordComparison(DiatonicChord(mode, first), DiatonicChord(mode, second)) },
            ) { container, state, session ->
                ProgressionAttempt.from(state, session)?.let(container.practiceHistory::record)
            }
            ComparisonRoute(viewModel, { it.chords.last().numeral }, stringResource(R.string.compare_hint_progressions), onNavigateUp)
        }
        ExerciseKind.RHYTHM -> {
            val first = enumOrNull<RhythmCell>(request.first)
            val second = enumOrNull<RhythmCell>(request.second)
            if (first == null || second == null) return Invalid(onNavigateUp)
            val viewModel = comparisonViewModel(request, { RhythmCellComparison(first, second) }) { container, state, session ->
                RhythmAttempt.from(state, session)?.let(container.practiceHistory::record)
            }
            ComparisonRoute(
                viewModel,
                labelOf = { it.description.replaceFirstChar(Char::uppercase) },
                examplesHint = stringResource(R.string.compare_hint_rhythm),
                onNavigateUp = onNavigateUp,
                subtitle = stringResource(
                    R.string.compare_subtitle,
                    first.displayName.replaceFirstChar(Char::uppercase),
                    second.displayName,
                ),
                graphicOf = { rhythm -> RhythmNotation(rhythm) },
                // Whole rhythms are long to read out; each example is named by how it starts.
                answerSummary = stringResource(R.string.rhythm_summary),
                exampleLabels = listOf(first, second).sortedBy { it.ordinal }.map { cell ->
                    stringResource(R.string.compare_starts_with, cell.displayName)
                },
                // Notation needs the full width to stay legible.
                columns = 1,
            )
        }
    }
}

/** Leaves at once; requests are built by the app, so this only guards against stale routes. */
@Composable
private fun Invalid(onNavigateUp: () -> Unit) {
    LaunchedEffect(Unit) { onNavigateUp() }
}

private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().find { it.name == name }

private fun chordAnswerOrNull(target: ChordTarget, name: String): ChordAnswer? = when (target) {
    ChordTarget.QUALITY -> enumOrNull<ChordQuality>(name)?.let(ChordAnswer::Quality)
    ChordTarget.INVERSION -> enumOrNull<ChordInversion>(name)?.let(ChordAnswer::Inversion)
}

/** One ViewModel per request, so returning to a drill keeps its round and score. */
@Composable
private fun <A, Q : ExerciseQuestion<A>> comparisonViewModel(
    request: ComparisonRequest,
    comparison: () -> Comparison<A, Q>,
    record: (AppContainer, ExerciseState<A, Q>, String) -> Unit,
): PracticeViewModel<A, Q, Comparison<A, Q>> = viewModel(key = request.route, factory = comparisonFactory(comparison, record))

private fun <A, Q : ExerciseQuestion<A>> comparisonFactory(
    comparison: () -> Comparison<A, Q>,
    record: (AppContainer, ExerciseState<A, Q>, String) -> Unit,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
        PracticeViewModel<A, Q, Comparison<A, Q>>(
            // Built once here, so the examples and every question come from the same drill.
            settings = flowOf(comparison()),
            questionSource = { drill -> drill::nextQuestion },
            recordAttempt = { state, sessionId -> record(container, state, sessionId) },
            audioPlayer = container.createAudioPlayer(),
            // A round ends on the drill's own result screen, which shows only the score.
            itemsOf = { _, _ -> emptyList() },
            sessionLength = Comparison.QUESTIONS_PER_ROUND,
        )
    }
}
