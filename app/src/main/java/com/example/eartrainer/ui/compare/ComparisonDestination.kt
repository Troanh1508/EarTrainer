package com.example.eartrainer.ui.compare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.AppContainer
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.R
import com.example.eartrainer.exercise.ChordAnswer
import com.example.eartrainer.exercise.ChordAttempt
import com.example.eartrainer.exercise.ChordComparison
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.Comparison
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.exercise.ExerciseQuestion
import com.example.eartrainer.exercise.ExerciseState
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalComparison
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.exercise.ScaleDegreeAttempt
import com.example.eartrainer.exercise.ScaleDegreeComparison
import com.example.eartrainer.music.ChordInversion
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.ScaleDegree
import com.example.eartrainer.ui.practice.PracticeViewModel
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
        ExerciseKind.PROGRESSIONS, ExerciseKind.RHYTHM -> Invalid(onNavigateUp)
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
        )
    }
}
